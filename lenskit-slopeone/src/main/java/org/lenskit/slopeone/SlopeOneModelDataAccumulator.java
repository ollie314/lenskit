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
package org.lenskit.slopeone;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.commons.lang3.tuple.Pair;
import org.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.vectors.*;

import java.util.Map;

public class SlopeOneModelDataAccumulator {

    private Long2ObjectMap<MutableSparseVector> workMatrix;
    private double damping;

    /**
     * Creates an accumulator to process rating data and generate the necessary data for
     * a {@code SlopeOneItemScorer}.
     *
     * @param damping   A damping term for deviation calculations.
     * @param dao       The DataAccessObject interfacing with the data for the model
     */
    public SlopeOneModelDataAccumulator(double damping, ItemDAO dao) {
        this.damping = damping;
        LongSet items = dao.getItemIds();

        workMatrix = new Long2ObjectOpenHashMap<>(items.size());
        LongIterator iter = items.iterator();
        while (iter.hasNext()) {
            long item = iter.nextLong();
            workMatrix.put(item, MutableSparseVector.create(items));
            workMatrix.get(item).addChannelVector(SlopeOneModel.CORATINGS_SYMBOL);
        }
    }

    /**
     * Puts the item pair into the accumulator.
     *
     * @param id1      The id of the first item.
     * @param itemVec1 The rating vector of the first item.
     * @param id2      The id of the second item.
     * @param itemVec2 The rating vector of the second item.
     */
    public void putItemPair(long id1, SparseVector itemVec1, long id2, SparseVector itemVec2) {
        if (workMatrix == null) {
            throw new IllegalStateException("Model is already built");
        }

        // to profit from matrix symmetry, always store by the lesser id
        if (id1 < id2) {
            int coratings = 0;
            double deviation = 0.0;
            for (Pair<VectorEntry,VectorEntry> pair: SparseVector.fastIntersect(itemVec1, itemVec2)) {
                coratings++;
                deviation += pair.getLeft().getValue() - pair.getRight().getValue();
            }
            deviation = (coratings == 0) ? Double.NaN : deviation;

            workMatrix.get(id1).set(id2, deviation);
            workMatrix.get(id1).getChannelVector(SlopeOneModel.CORATINGS_SYMBOL).set(id2, coratings);
        }
    }

    /**
     * @return A matrix of item deviation and corating values to be used by
     *         a {@code SlopeOneItemScorer}.
     */
    public Long2ObjectMap<ImmutableSparseVector> buildMatrix() {
        if (workMatrix == null) {
            throw new IllegalStateException("Model is already built");
        }

        Long2ObjectMap<ImmutableSparseVector> matrix =
                new Long2ObjectOpenHashMap<>(workMatrix.size());

        for (MutableSparseVector vec : workMatrix.values()) {
            for (VectorEntry e : vec) {
                double deviation = e.getValue();
                int coratings = (int)vec.getChannelVector(SlopeOneModel.CORATINGS_SYMBOL).get(e);
                vec.set(e, deviation/(coratings + damping));
            }
        }

        for (Map.Entry<Long, MutableSparseVector> e : workMatrix.entrySet()) {
            matrix.put(e.getKey(), e.getValue().freeze());
        }

        workMatrix = null;
        return matrix;
    }
}
