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
package org.lenskit.data.packed;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.commons.lang3.tuple.Pair;
import org.grouplens.grapht.annotation.DefaultProvider;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.lenskit.util.io.ObjectStream;
import org.lenskit.util.io.ObjectStreams;
import org.lenskit.data.dao.*;
import org.lenskit.data.events.Event;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.history.History;
import org.lenskit.data.history.ItemEventCollection;
import org.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.util.io.Describable;
import org.grouplens.lenskit.util.io.DescriptionWriter;
import org.lenskit.util.BinarySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * DAO implementation using binary-packed data.  This DAO reads ratings from a compact binary format
 * using memory-mapped IO, so the data is efficiently readable (subject to available memory and
 * operating system caching logic) without expanding the Java heap.
 * <p/>
 * To create a file compatible with this DAO, use the {@link BinaryRatingPacker} class or the
 * <tt>pack</tt> command in the LensKit command line tool.
 * <p/>
 * Currently, serializing a binary rating DAO puts all the rating data into the serialized output
 * stream. When deserialized, the data be written back to a direct buffer (allocated with
 * {@link ByteBuffer#allocateDirect(int)}).  When deserializing this DAO, make sure your
 * system has enough virtual memory (beyond what is allowed for Java) to contain the entire data set.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @since 2.1
 */
@ThreadSafe
@DefaultProvider(BinaryRatingDAO.Loader.class)
public class BinaryRatingDAO implements EventDAO, UserEventDAO, ItemEventDAO, UserDAO, ItemDAO, Serializable, Describable {
    private static final long serialVersionUID = -1L;
    private static final Logger logger = LoggerFactory.getLogger(BinaryRatingDAO.class);

    @Nullable
    private final transient File backingFile;
    private final BinaryHeader header;
    private final ByteBuffer ratingData;
    private final BinaryIndexTable userTable;
    private final BinaryIndexTable itemTable;
    private final int limitIndex;
    private final long limitTimestamp;

    private BinaryRatingDAO(@Nullable File file, BinaryHeader hdr, ByteBuffer data, BinaryIndexTable users, BinaryIndexTable items, int idx, Long timestamp) {
        Preconditions.checkArgument(data.position() == 0, "data is not at position 0");
        backingFile = file;
        header = hdr;
        ratingData = data;
        userTable = users;
        itemTable = items;
        limitIndex = idx;
        limitTimestamp = timestamp;
    }

    static BinaryRatingDAO fromBuffer(ByteBuffer buffer) {
        // start by reading header from the buffer
        BinaryHeader header = BinaryHeader.fromHeader(buffer);
        assert buffer.position() >= BinaryHeader.HEADER_SIZE;
        // the header read advanced the buffer position past the header; prepare a data slice
        // first slice to remove the header
        ByteBuffer data = buffer.slice();
        // then limit to the rating data size
        data.limit(header.getRatingDataSize());
        assert data.remaining() == header.getRatingDataSize();

        // prepare to read tables
        ByteBuffer tableBuffer = buffer.duplicate();
        // skip the header and the rating data
        tableBuffer.position(tableBuffer.position() + header.getRatingDataSize());
        // each of the following reads advances the buffer by the amount read
        BinaryIndexTable utbl = BinaryIndexTable.fromBuffer(header.getUserCount(), tableBuffer);
        BinaryIndexTable itbl = BinaryIndexTable.fromBuffer(header.getItemCount(), tableBuffer);

        return new BinaryRatingDAO(null, header, data, utbl, itbl, header.getRatingCount(), Long.MAX_VALUE);
    }

    /**
     * Open a binary rating DAO.
     *
     * @param file The file to open.
     * @return A DAO backed by {@code file}.
     * @throws IOException If there is
     */
    public static BinaryRatingDAO open(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            FileChannel channel = input.getChannel();
            BinaryHeader header = BinaryHeader.read(channel);
            logger.info("Loading DAO with {} ratings of {} items from {} users",
                        header.getRatingCount(), header.getItemCount(), header.getUserCount());
            // the channel position has been advanced to end of header

            ByteBuffer data = channel.map(FileChannel.MapMode.READ_ONLY,
                                          channel.position(), header.getRatingDataSize());
            channel.position(channel.position() + header.getRatingDataSize());

            ByteBuffer tableBuffer = channel.map(FileChannel.MapMode.READ_ONLY,
                                                 channel.position(), channel.size() - channel.position());
            BinaryIndexTable utbl = BinaryIndexTable.fromBuffer(header.getUserCount(), tableBuffer);
            BinaryIndexTable itbl = BinaryIndexTable.fromBuffer(header.getItemCount(), tableBuffer);

            return new BinaryRatingDAO(file, header, data, utbl, itbl, header.getRatingCount(), Long.MAX_VALUE);
        }
    }

    public BinaryRatingDAO createWindowedView(long timestamp) {

        if (timestamp >= limitTimestamp) {
            return this;
        }

        List<Rating> ratingsList = getRatingList();
        SearchBinaryRating search = new SearchBinaryRating(timestamp, ratingsList);
        int idx = search.search(0, getRatingList().size());

        idx = BinarySearch.resultToIndex(idx);

        ByteBuffer data = ratingData.duplicate();
        data.limit(idx * header.getFormat().getRatingSize());

        BinaryIndexTable utbl = userTable.createLimitedView(idx);
        BinaryIndexTable itbl = itemTable.createLimitedView(idx);
        return new BinaryRatingDAO(null, header, data, utbl, itbl, idx, timestamp);
    }

    private Object writeReplace() {
        return new SerialProxy(header, ratingData, userTable, itemTable, limitIndex, limitTimestamp);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        throw new InvalidObjectException("attempted to read BinaryRatingDAO without proxy");
    }

    private BinaryRatingList getRatingList() {
        return getRatingList(CollectionUtils.interval(0, limitIndex));
    }

    private BinaryRatingList getRatingList(IntList indexes) {
        return new BinaryRatingList(header.getFormat(), ratingData, indexes);
    }

    public Long getLimitTimestamp() {
        return limitTimestamp;
    }

    @Override
    public ObjectStream<Event> streamEvents() {
        return streamEvents(Event.class);
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

        final ObjectStream<Rating> stream;

        switch (order) {
        case ANY:
        case TIMESTAMP:
            stream = getRatingList().objectStream();
            break;
        case USER:
            stream = ObjectStreams.concat(Iterables.transform(userTable.entries(),
                                                              new EntryToStreamTransformer()));
            break;
        case ITEM:
            stream = ObjectStreams.concat(Iterables.transform(itemTable.entries(),
                                                              new EntryToStreamTransformer()));
            break;
        default:
            throw new IllegalArgumentException("unexpected sort order");
        }

        return (ObjectStream<E>) stream;
    }

    @Override
    public LongSet getItemIds() {
        return itemTable.getKeys();
    }

    @Override
    public ObjectStream<ItemEventCollection<Event>> streamEventsByItem() {
        return streamEventsByItem(Event.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Event> ObjectStream<ItemEventCollection<E>> streamEventsByItem(Class<E> type) {
        if (type.isAssignableFrom(Rating.class)) {
            // cast is safe, Rating extends E
            return (ObjectStream) ObjectStreams.wrap(Collections2.transform(itemTable.entries(),
                                                                            new ItemEntryTransformer()));
        } else {
            return ObjectStreams.empty();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Event> getEventsForItem(long item) {
        return getEventsForItem(item, Event.class);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <E extends Event> List<E> getEventsForItem(long item, Class<E> type) {
        IntList index = itemTable.getEntry(item);
        if (index == null) {
            return null;
        }

        if (!type.isAssignableFrom(Rating.class)) {
            // we only have ratings
            return ImmutableList.of();
        }

        return (List<E>) getRatingList(index);
    }

    @Nullable
    @Override
    public LongSet getUsersForItem(long item) {
        List<Rating> ratings = getEventsForItem(item, Rating.class);
        if (ratings == null) {
            return null;
        }

        LongSet users = new LongOpenHashSet(ratings.size());
        for (Rating rating : ratings) {
            users.add(rating.getUserId());
        }
        return users;
    }

    @Override
    public LongSet getUserIds() {
        return userTable.getKeys();
    }

    @Override
    public ObjectStream<UserHistory<Event>> streamEventsByUser() {
        return streamEventsByUser(Event.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Event> ObjectStream<UserHistory<E>> streamEventsByUser(Class<E> type) {
        if (type.isAssignableFrom(Rating.class)) {
            // cast is safe, E super Rating
            return (ObjectStream) ObjectStreams.wrap(Collections2.transform(userTable.entries(),
                                                                            new UserEntryTransformer()));
        } else {
            return ObjectStreams.empty();
        }
    }

    @Nullable
    @Override
    public UserHistory<Event> getEventsForUser(long user) {
        return getEventsForUser(user, Event.class);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <E extends Event> UserHistory<E> getEventsForUser(long user, Class<E> type) {
        IntList index = userTable.getEntry(user);
        if (index == null) {
            return null;
        }

        if (!type.isAssignableFrom(Rating.class)) {
            return History.forUser(user);
        }

        return (UserHistory<E>) new BinaryUserHistory(user, getRatingList(index));
    }

    @Override
    public void describeTo(DescriptionWriter writer) {
        if (backingFile != null) {
            writer.putField("file", backingFile.getAbsolutePath())
                  .putField("mtime", backingFile.lastModified());
        } else {
            writer.putField("file", "/dev/null")
                  .putField("mtime", 0);
        }
        writer.putField("header", header.render());
    }

    private class EntryToStreamTransformer implements Function<Pair<Long, IntList>, ObjectStream<Rating>> {
        @Nonnull
        @Override
        public ObjectStream<Rating> apply(Pair<Long, IntList> input) {
            Preconditions.checkNotNull(input, "input entry");
            return ObjectStreams.wrap(getRatingList(input.getRight()));
        }
    }

    private class ItemEntryTransformer implements Function<Pair<Long, IntList>, ItemEventCollection<Rating>> {
        @Nonnull
        @Override
        public ItemEventCollection<Rating> apply(Pair<Long, IntList> input) {
            return new BinaryItemCollection(input.getLeft(), getRatingList(input.getRight()));
        }
    }

    private class UserEntryTransformer implements Function<Pair<Long, IntList>, UserHistory<Rating>> {
        @Nonnull
        @Override
        public UserHistory<Rating> apply(Pair<Long, IntList> input) {
            return new BinaryUserHistory(input.getLeft(), getRatingList(input.getRight()));
        }
    }

    public static class Loader implements Provider<BinaryRatingDAO>, Serializable {
        public static final long serialVersionUID = 1L;

        private final File dataFile;

        @Inject
        public Loader(@BinaryRatingFile File file) {
            dataFile = file;
        }

        @Override
        public BinaryRatingDAO get() {
            try {
                return open(dataFile);
            } catch (IOException e) {
                throw new DataAccessException("cannot open rating file", e);
            }
        }
    }

    private static class SerialProxy implements Serializable {
        private static final long serialVersionUID = 2L;

        private BinaryHeader header;
        private ByteBuffer ratingData;
        private BinaryIndexTable userTable;
        private BinaryIndexTable itemTable;
        private int limitIndex;
        private long limitTimestamp;


        public SerialProxy(BinaryHeader hdr, ByteBuffer ratings, BinaryIndexTable users, BinaryIndexTable items, int limitIdx, long limitTms) {
            header = hdr;
            ratingData = ratings.duplicate();
            userTable = users;
            itemTable = items;
            limitIndex = limitIdx;
            limitTimestamp = limitTms;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            byte[] headerBytes = new byte[BinaryHeader.HEADER_SIZE];
            ByteBuffer headBuffer = ByteBuffer.wrap(headerBytes);
            header.render(headBuffer);
            headBuffer.flip();
            out.writeInt(BinaryHeader.HEADER_SIZE);
            out.write(headerBytes);
            out.writeObject(userTable);
            out.writeObject(itemTable);
            out.writeInt(limitIndex);
            out.writeLong(limitTimestamp);

            // TODO Write this with a compound file
            ByteBuffer write = ratingData.duplicate();
            write.clear();
            out.writeInt(write.limit());
            byte[] buf = new byte[4096];
            while (write.hasRemaining()) {
                final int n = Math.min(4096, write.remaining());
                write.get(buf, 0, n);
                out.write(buf, 0, n);
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            int headSize = in.readInt();
            if (headSize != BinaryHeader.HEADER_SIZE) {
                throw new InvalidObjectException("incorrect header size");
            }
            byte[] headerBytes = new byte[BinaryHeader.HEADER_SIZE];
            int nbs = in.read(headerBytes);
            if (nbs != headSize) {
                throw new InvalidObjectException("not enough bytes for header");
            }
            ByteBuffer headBuf = ByteBuffer.wrap(headerBytes);
            header = BinaryHeader.fromHeader(headBuf);

            userTable = (BinaryIndexTable) in.readObject();
            itemTable = (BinaryIndexTable) in.readObject();
            limitIndex = in.readInt();
            limitTimestamp = in.readLong();

            int dataLength = in.readInt();
            byte[] buf = new byte[4096];
            ByteBuffer data = ByteBuffer.allocateDirect(dataLength);
            assert data.position() == 0;
            assert data.limit() == dataLength;
            while (data.hasRemaining()) {
                final int n = Math.min(4096, data.remaining());
                int read = in.read(buf, 0, n);
                if (read < 0) {
                    throw new InvalidObjectException("unexpected EOF");
                }
                data.put(buf, 0, read);
            }
            data.clear();
            ratingData = data;
        }

        private Object readResolve() throws ObjectStreamException {
            return new BinaryRatingDAO(null, header, ratingData, userTable, itemTable, limitIndex, limitTimestamp);
        }
    }

    private class SearchBinaryRating extends BinarySearch {

        Long timeStamp;
        List<Rating> ratingsList;

        SearchBinaryRating(Long timeStmp, List<Rating> ratings) {
            timeStamp = timeStmp;
            ratingsList = ratings;
        }

        protected int test(int pos) {
            return timeStamp.compareTo(ratingsList.get(pos).getTimestamp());
        }


    }

}

