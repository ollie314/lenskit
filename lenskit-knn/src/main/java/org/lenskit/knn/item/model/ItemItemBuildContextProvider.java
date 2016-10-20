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

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.lenskit.data.ratings.RatingVectorPDAO;
import org.lenskit.inject.Transient;
import org.lenskit.transform.normalize.UserVectorNormalizer;
import org.lenskit.util.IdBox;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.io.ObjectStream;
import org.lenskit.util.keys.SortedKeyIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provider that sets up an {@link ItemItemBuildContext}.
 * 
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class ItemItemBuildContextProvider implements Provider<ItemItemBuildContext> {

    private static final Logger logger = LoggerFactory.getLogger(ItemItemBuildContextProvider.class);

    private final RatingVectorPDAO rvDAO;
    private final UserVectorNormalizer normalizer;

    /**
     * Construct an item-item build context provider.
     *
     * @param rvd The rating vector DAO.
     * @param normalizer The user vector normalizer.
     */
    @Inject
    public ItemItemBuildContextProvider(@Transient RatingVectorPDAO rvd,
                                        @Transient UserVectorNormalizer normalizer) {
        rvDAO = rvd;
        this.normalizer = normalizer;
    }

    /**
     * Constructs and returns a new ItemItemBuildContext.
     *
     * @return a new ItemItemBuildContext.
     */
    @Override
    public ItemItemBuildContext get() {
        logger.info("constructing build context");
        logger.debug("using normalizer {}", normalizer);

        logger.debug("Building item data");
        Long2ObjectMap<ScoredIdListBuilder> itemRatingData = new Long2ObjectOpenHashMap<>(1000);
        Long2ObjectMap<LongSortedSet> userItems = new Long2ObjectOpenHashMap<>(1000);
        buildItemRatings(itemRatingData, userItems);

        SortedKeyIndex items = SortedKeyIndex.fromCollection(itemRatingData.keySet());
        final int n = items.size();
        assert n == itemRatingData.size();
        // finalize the item data into vectors
        SparseVector[] itemRatings = new SparseVector[n];

        for (int i = 0; i < n; i++) {
            final long item = items.getKey(i);
            ScoredIdListBuilder ratings = itemRatingData.get(item);
            SparseVector v = ratings.buildVector();
            assert v.size() == ratings.size();
            itemRatings[i] = v;
            // release some memory
            ratings.clear();
        }

        logger.debug("item data completed");
        return new ItemItemBuildContext(items, itemRatings, userItems);
    }

    /**
     * Transpose the user matrix so we have a matrix of item ids to ratings. Accumulate user item vectors into
     * the candidate sets for each item
     *
     * @param itemRatings    mapping from item ids to (userId: rating) maps (to be filled)
     * @param userItems mapping of user IDs to rated item sets to be filled.
     */
    private void buildItemRatings(Long2ObjectMap<ScoredIdListBuilder> itemRatings,
                                  Long2ObjectMap<LongSortedSet> userItems) {
        // initialize the transposed array to collect item vector data
        try (ObjectStream<IdBox<Long2DoubleMap>> stream = rvDAO.streamUsers()) {
            for (IdBox<Long2DoubleMap> user : stream) {
                long uid = user.getId();
                Long2DoubleMap ratings = user.getValue();
                SparseVector summary = ImmutableSparseVector.create(ratings);
                MutableSparseVector normed = summary.mutableCopy();
                normalizer.normalize(uid, summary, normed);

                for (VectorEntry rating : normed) {
                    final long item = rating.getKey();
                    // get the item's rating accumulator
                    ScoredIdListBuilder ivect = itemRatings.get(item);
                    if (ivect == null) {
                        ivect = ScoredIds.newListBuilder(100);
                        itemRatings.put(item, ivect);
                    }
                    ivect.add(uid, rating.getValue());
                }

                // get the item's candidate set
                userItems.put(uid, LongUtils.packedSet(summary.keySet()));
            }
        }
    }
}
