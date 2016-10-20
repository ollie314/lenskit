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
package org.lenskit.util;

import it.unimi.dsi.fastutil.longs.*;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.MutableSparseVector;

import java.util.Collections;
import java.util.List;

/**
 * Scored item accumulator with no upper bound.
 */
public final class UnlimitedScoredIdAccumulator implements ScoredIdAccumulator {
    private ScoredIdListBuilder scores;

    public UnlimitedScoredIdAccumulator() {}

    @Override
    public boolean isEmpty() {
        return scores == null || scores.size() == 0;
    }

    @Override
    public int size() {
        return scores == null ? 0 : scores.size();
    }

    @Override
    public void put(long item, double score) {
        if (scores == null) {
            scores = ScoredIds.newListBuilder();
        }
        scores.add(item, score);
    }

    @Override
    public List<ScoredId> finish() {
        if (scores == null) {
            return Collections.emptyList();
        }
        List<ScoredId> list = scores.sort(ScoredIds.scoreOrder().reverse()).finish();
        scores = null;
        return list;
    }

    @Override
    public MutableSparseVector finishVector() {
        if (scores == null) {
            return MutableSparseVector.create();
        }

        MutableSparseVector vec = scores.buildVector().mutableCopy();
        scores.clear();
        scores = null;
        return vec;
    }

    @Override
    public Long2DoubleMap finishMap() {
        if (scores == null) {
            return Long2DoubleMaps.EMPTY_MAP;
        }
        // FIXME Make this efficient
        Long2DoubleMap set = new Long2DoubleOpenHashMap(scores.size());
        for (ScoredId id: finish()) {
            set.put(id.getId(), id.getScore());
        }
        return set;
    }

    @Override
    public LongSet finishSet() {
        if (scores == null) {
            return LongSets.EMPTY_SET;
        }

        LongSet set = new LongOpenHashSet(scores.size());
        for (ScoredId id: finish()) {
            set.add(id.getId());
        }
        return set;
    }

    @Override
    public LongList finishList() {
        if (scores == null) {
            return LongLists.EMPTY_LIST;
        }

        LongList list = new LongArrayList(scores.size());
        for (ScoredId id: finish()) {
            list.add(id.getId());
        }
        return list;
    }
}
