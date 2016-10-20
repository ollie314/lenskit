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

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import org.lenskit.data.ratings.Rating;
import org.lenskit.util.io.AbstractObjectStream;
import org.lenskit.util.io.ObjectStream;

import java.nio.ByteBuffer;
import java.util.AbstractList;

/**
 * A list of ratings backed by a buffer.  This is not thread-safe.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
class BinaryRatingList extends AbstractList<Rating> {
    private final BinaryFormat format;
    private final ByteBuffer buffer;
    private final IntList positions;
    private final int ratingSize;

    /**
     * Create a new binary rating list.
     * @param buf The buffer. It is duplicated, so it can be repositioned later.
     */
    public BinaryRatingList(BinaryFormat fmt, ByteBuffer buf, IntList idxes) {
        format = fmt;
        buffer = buf.slice();
        positions = idxes;

        ratingSize = fmt.getRatingSize();
    }

    @Override
    public Rating get(int index) {
        int position = positions.getInt(index);
        return getRating(position);
    }

    private Rating getRating(int position) {
        int bidx = position * ratingSize;
        ByteBuffer buf = buffer.slice();
        buf.position(bidx);
        assert buf.remaining() >= format.getRatingSize();
        return format.readRating(buf);
    }

    @Override
    public int size() {
        return positions.size();
    }

    public ObjectStream<Rating> objectStream() {
        return new ObjectStreamImpl();
    }

    private class ObjectStreamImpl extends AbstractObjectStream<Rating> {
        private IntIterator posIter = positions.iterator();

        @Override
        public Rating readObject() {
            if (posIter.hasNext()) {
                return getRating(posIter.nextInt());
            } else {
                return null;
            }
        }
    }
}
