/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2016 LensKit Contributors.  See CONTRIBUTORS.md.
 * Work on LensKit has been funded by the National Science Foundation under
 * grants IIS 05-34939, 08-08692, 08-12148, and 10-17697.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.data.sql;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.lenskit.util.io.ObjectStream;
import org.lenskit.util.io.ObjectStreams;
import org.lenskit.data.dao.*;
import org.lenskit.data.events.Event;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.history.History;
import org.lenskit.data.history.ItemEventCollection;
import org.lenskit.data.history.UserHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.WillCloseWhenClosed;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Rating DAO backed by a JDBC connection.  This DAO can only store rating data;
 * no other events are supported.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class JDBCRatingDAO implements EventDAO, UserEventDAO, ItemEventDAO, UserDAO, ItemDAO {
    /**
     * User ID column number.
     */
    public static final int COL_USER_ID = 1;
    /**
     * Item ID column number.
     */
    public static final int COL_ITEM_ID = 2;
    /**
     * Rating column number.
     */
    public static final int COL_RATING = 3;
    /**
     * Timestamp column number.
     */
    public static final int COL_TIMESTAMP = 4;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Connection connection;
    protected final boolean closeConnection;

    private final SQLStatementFactory statementFactory;

    private final CachedPreparedStatement userStatement;
    private final CachedPreparedStatement itemStatement;
    private final Map<SortOrder,CachedPreparedStatement> eventStatements =
            new EnumMap<>(SortOrder.class);
    private final CachedPreparedStatement userEventStatement;
    private final CachedPreparedStatement itemEventStatement;
    private final CachedPreparedStatement itemUserStatement;
    private final Cache<QueryKey, Object> queryCache;

    /**
     * Create a new JDBC DAO builder.
     * @return The new builder.
     */
    public static JDBCRatingDAOBuilder newBuilder() {
        return new JDBCRatingDAOBuilder();
    }

    /**
     * Create a new JDBC rating DAO.  The resulting DAO will be uncached.
     *
     * @param dbc  The database connection. The connection will be closed
     *             when the DAO is closed.
     * @param sfac The statement factory.
     * @deprecated Use {@link #newBuilder()}.
     */
    @Deprecated
    public JDBCRatingDAO(@WillCloseWhenClosed Connection dbc, SQLStatementFactory sfac) {
        this(dbc, sfac, true);
    }

    /**
     * Create a new JDBC rating DAO.  The resulting DAO will be uncached.
     *
     * @param dbc   The database connection.
     * @param sfac  The statement factory.
     * @param close Whether to close the database connection when the DAO is closed.
     * @deprecated Use {@link #newBuilder()}.
     */
    @Deprecated
    public JDBCRatingDAO(Connection dbc, SQLStatementFactory sfac, boolean close) {
        this(dbc, sfac, close,
             CacheBuilder.from(CacheBuilderSpec.disableCaching()).<QueryKey, Object>build());
    }

    JDBCRatingDAO(Connection dbc, SQLStatementFactory factory, boolean close,
                  Cache<QueryKey, Object> cache) {
        connection = dbc;
        closeConnection = close;
        statementFactory = factory;

        queryCache = cache;

        userStatement = new CachedPreparedStatement(dbc, statementFactory.prepareUsers());
        itemStatement = new CachedPreparedStatement(dbc, statementFactory.prepareItems());
        for (SortOrder order : SortOrder.values()) {
            switch (order) {
            case ASCENDING:
            case DESCENDING:
                break;
            default:
                eventStatements.put(order, new CachedPreparedStatement(dbc,
                                                                       statementFactory.prepareEvents(order)));
            }
        }
        userEventStatement = new CachedPreparedStatement(dbc, statementFactory.prepareUserEvents());
        itemEventStatement = new CachedPreparedStatement(dbc, statementFactory.prepareItemEvents());
        itemUserStatement = new CachedPreparedStatement(dbc, statementFactory.prepareItemUsers());
    }

    private boolean closeStatement(CachedPreparedStatement s) {
        try {
            s.close();
            return true;
        } catch (IOException e) {
            logger.error("Error closing statement: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Close the connection and all open statements.
     */
    public void close() {
        boolean failed;
        try {
            failed = !closeStatement(userStatement);
            failed |= !closeStatement(itemStatement);
            for (CachedPreparedStatement s : eventStatements.values()) {
                failed |= !closeStatement(s);
            }
            failed |= !closeStatement(userEventStatement);
            failed |= !closeStatement(itemEventStatement);
            if (closeConnection) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(e);
        }
        if (failed) {
            throw new DatabaseAccessException("Error closing statement (see log for details)");
        }
    }

    protected LongSet getIdSet(PreparedStatement s) throws SQLException {
        try (ResultSet results = s.executeQuery()) {
            LongSet ids = new LongOpenHashSet();
            while (results.next()) {
                ids.add(results.getLong(1));
            }
            return ids;
        }
    }

    @Override
    public LongSet getUserIds() {
        try {
            return (LongSet) queryCache.get(QueryKey.userList(), new Callable<Object>() {
                @Override
                public Object call() throws SQLException {
                    return getIdSet(userStatement.call());
                }
            });
        } catch (ExecutionException e) {
            throw new DatabaseAccessException("Error getting user list", e.getCause());
        }
    }

    @Override
    public LongSet getItemIds() {
        try {
            return (LongSet) queryCache.get(QueryKey.itemList(), new Callable<Object>() {
                @Override
                public Object call() throws SQLException {
                    return getIdSet(itemStatement.call());
                }
            });
        } catch (ExecutionException e) {
            throw new DatabaseAccessException("Error getting item list", e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObjectStream<Event> streamEvents() {
        return (ObjectStream) streamEvents(Rating.class, SortOrder.ANY);
    }

    @Override
    public <E extends Event> ObjectStream<E> streamEvents(Class<E> type) {
        return streamEvents(type, SortOrder.ANY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Event> ObjectStream<E> streamEvents(Class<E> type, SortOrder order) {
        if (!type.isAssignableFrom(Rating.class)) {
            return ObjectStreams.empty();
        }

        try {
            return (ObjectStream<E>) new ResultSetRatingObjectStream(eventStatements.get(order).call());
        } catch (SQLException e) {
            throw new DatabaseAccessException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public UserHistory<Event> getEventsForUser(final long userId) {
        List<Rating> cached;
        try {
            cached = (List) queryCache.get(QueryKey.user(userId), new Callable<List<Rating>>() {
                @Override
                public List<Rating> call() throws Exception {
                    PreparedStatement s = userEventStatement.call();
                    s.setLong(1, userId);
                    try (ObjectStream<Rating> ratings = new ResultSetRatingObjectStream(s)) {
                        return ImmutableList.copyOf(ratings);
                    }
                }
            });
        } catch (ExecutionException e) {
            throw new DataAccessException("error fetching user " + userId, e.getCause());
        }
        if (cached.isEmpty()) {
            return null;
        } else {
            return History.<Event>forUser(userId, cached);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Event> UserHistory<E> getEventsForUser(long uid, Class<E> type) {
        UserHistory<Event> history = getEventsForUser(uid);
        if (history != null) {
            if (type.isAssignableFrom(Rating.class)) {
                return (UserHistory<E>) history;
            } else {
                return History.forUser(uid);
            }
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Event> getEventsForItem(final long itemId) {
        List<Rating> events;
        try {
            events = (List) queryCache.get(QueryKey.item(itemId), new Callable<List<Rating>>() {
                @Override
                public List<Rating> call() throws Exception {
                    PreparedStatement s = itemEventStatement.call();
                    s.setLong(1, itemId);
                    try (ObjectStream<Rating> ratings = new ResultSetRatingObjectStream(s)) {
                        return ImmutableList.copyOf(ratings);
                    }
                }
            });
        } catch (ExecutionException e) {
            throw new DatabaseAccessException("error fetching item " + itemId, e.getCause());
        }
        if (events.isEmpty()) {
            return null;
        } else {
            // this copy is near-free, but type-safe
            return ImmutableList.<Event>copyOf(events);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Event> List<E> getEventsForItem(long iid, Class<E> type) {
        List<Event> events = getEventsForItem(iid);
        if (events != null) {
            if (type.isAssignableFrom(Rating.class)) {
                return (List<E>) events;
            } else {
                return Collections.emptyList();
            }
        } else {
            return null;
        }
    }

    @Override
    public LongSet getUsersForItem(long item) {
        try (PreparedStatement s = itemUserStatement.call()) {
            s.setLong(1, item);
            return getIdSet(s);
        } catch (SQLException e) {
            throw new DatabaseAccessException(e);
        }
    }

    @Override
    public ObjectStream<UserHistory<Event>> streamEventsByUser() {
        return streamEventsByUser(Event.class);
    }

    @Override
    public <E extends Event> ObjectStream<UserHistory<E>> streamEventsByUser(Class<E> type) {
        return new UserHistoryObjectStream<>(streamEvents(type, SortOrder.USER));
    }

    @Override
    public ObjectStream<ItemEventCollection<Event>> streamEventsByItem() {
        return streamEventsByItem(Event.class);
    }

    @Override
    public <E extends Event> ObjectStream<ItemEventCollection<E>> streamEventsByItem(Class<E> type) {
        return new ItemCollectionObjectStream<>(streamEvents(type, SortOrder.USER));
    }

}
