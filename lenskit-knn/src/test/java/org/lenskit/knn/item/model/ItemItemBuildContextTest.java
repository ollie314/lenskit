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
package org.lenskit.knn.item.model;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.lenskit.knn.item.model.ItemItemBuildContext.ItemVecPair;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.junit.Test;
import org.lenskit.util.keys.SortedKeyIndex;

import static org.junit.Assert.*;

public class ItemItemBuildContextTest {

    /**
     * Test ItemItemBuildContext when all items have rating data.
     */
    @Test
    public void testAllItemsData() {
        SortedKeyIndex items = SortedKeyIndex.create(1, 2, 3, 4);

        long[] userIds = {101, 102, 103, 104};
        double[] ratings1 = {4.0, 3.0, 2.5, 2.0};
        double[] ratings2 = {3.0, 2.5, 4.0, 1.0};
        double[] ratings3 = {5.0, 3.5, 0.5, 1.0};
        double[] ratings4 = {4.5, 3.0, 3.5, 1.5};
        SparseVector v1 = MutableSparseVector.wrap(userIds, ratings1);
        SparseVector v2 = MutableSparseVector.wrap(userIds, ratings2);
        SparseVector v3 = MutableSparseVector.wrap(userIds, ratings3);
        SparseVector v4 = MutableSparseVector.wrap(userIds, ratings4);

        SparseVector[] ratings = { v1, v2, v3, v4 };
        ItemItemBuildContext context = new ItemItemBuildContext(items, ratings,
                                                                new Long2ObjectOpenHashMap<LongSortedSet>());

        testRatingIntegrity(items, ratings, context);
    }

    /**
     * Test ItemItemBuildContext when some items have rating data.
     */
    @Test
    public void testSomeItemsData() {
        SortedKeyIndex items = SortedKeyIndex.create(1, 2, 3, 4);

        long[] userIds = {101, 102, 103, 104};
        double[] ratings1 = {4.0, 3.0, 2.5, 2.0};
        double[] ratings4 = {4.5, 3.0, 3.5, 1.5};
        SparseVector v1 = MutableSparseVector.wrap(userIds, ratings1);
        SparseVector v4 = MutableSparseVector.wrap(userIds, ratings4);

        SparseVector[] ratingMap = {
                v1,
                MutableSparseVector.create(),
                MutableSparseVector.create(),
                v4
        };
        ItemItemBuildContext context = new ItemItemBuildContext(items, ratingMap,
                                                                new Long2ObjectOpenHashMap<LongSortedSet>());

        testRatingIntegrity(items, ratingMap, context);
    }

    /**
     * Test ItemItemBuildContext when no items have rating data.
     */
    @Test
    public void testNoItemsData() {
        SortedKeyIndex items = SortedKeyIndex.create(1, 2, 3, 4);

        SparseVector[] ratingMap = {
                MutableSparseVector.create(),
                MutableSparseVector.create(),
                MutableSparseVector.create(),
                MutableSparseVector.create()
        };
        ItemItemBuildContext context = new ItemItemBuildContext(items, ratingMap,
                                                                new Long2ObjectOpenHashMap<LongSortedSet>());

        testRatingIntegrity(items, ratingMap, context);
    }

    /**
     * Test ItemItemBuildContext when there is no rating data.
     */
    @Test
    public void testEmpty() {
        SortedKeyIndex items = SortedKeyIndex.create();
        SparseVector[] ratingMap = new SparseVector[] {};
        ItemItemBuildContext context = new ItemItemBuildContext(items, ratingMap,
                                                                new Long2ObjectOpenHashMap<LongSortedSet>());

        testRatingIntegrity(items, ratingMap, context);
    }

    @SuppressWarnings("deprecation")
    private void testRatingIntegrity(SortedKeyIndex items, SparseVector[] trueRatings, ItemItemBuildContext context) {
        for (long itemId : context.getItems()) {
            assertEquals(trueRatings[items.tryGetIndex(itemId)], context.itemVector(itemId));
        }

        for (ItemVecPair pair : context.getItemPairs()) {
            assertEquals(trueRatings[items.tryGetIndex(pair.itemId1)], pair.vec1);
            assertEquals(trueRatings[items.tryGetIndex(pair.itemId2)], pair.vec2);
        }
    }
}
