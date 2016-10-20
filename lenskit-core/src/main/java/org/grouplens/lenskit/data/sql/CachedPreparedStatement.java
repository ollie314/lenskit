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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * A simple object that wraps a lazy initialized {@link PreparedStatement}.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class CachedPreparedStatement implements Callable<PreparedStatement>, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(CachedPreparedStatement.class);

    private PreparedStatement cache = null;
    private final String sql;
    private final Connection dbc;

    /**
     * Create a new cached prepared statement.
     * @param dbc The database connection.
     * @param sql The SQL string.
     */
    public CachedPreparedStatement(Connection dbc, String sql) {
        this.dbc = dbc;
        this.sql = sql;
    }

    /**
     * Get the prepared statement, creating one if necessary.
     * @return The prepared statement.
     * @throws SQLException if there is an error preparing the statement.
     */
    @Override
    public PreparedStatement call() throws SQLException {
        if (cache == null) {
            logger.debug("preparing statement {} (on {})", sql, dbc);
            cache = dbc.prepareStatement(sql);
        }
        return cache;
    }

    /**
     * Close the prepared statement.
     *
     * @throws IOException if there is an error closing the statement.
     */
    @Override
    public void close() throws IOException {
        if (cache != null) {
            try {
                cache.close();
            } catch (SQLException e) {
                throw new IOException("error closing statement", e);
            } finally {
                cache = null;
            }
        }
    }

}
