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
package org.lenskit.bias;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.data.ratings.RatingSummary;
import org.lenskit.data.ratings.RatingVectorPDAO;
import org.lenskit.inject.Transient;
import org.lenskit.util.IdBox;
import org.lenskit.util.io.ObjectStream;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Compute a bias model with users' average ratings.
 */
public class UserItemAverageRatingBiasModelProvider implements Provider<UserItemBiasModel> {
    private final RatingSummary summary;
    private final RatingVectorPDAO dao;

    @Inject
    public UserItemAverageRatingBiasModelProvider(RatingSummary rs, @Transient RatingVectorPDAO dao) {
        summary = rs;
        this.dao = dao;
    }

    @Override
    public UserItemBiasModel get() {
        double intercept = summary.getGlobalMean();

        Long2DoubleMap itemOff = summary.getItemOffets();

        Long2DoubleMap map = new Long2DoubleOpenHashMap();
        try (ObjectStream<IdBox<Long2DoubleMap>> stream = dao.streamUsers()) {
            for (IdBox<Long2DoubleMap> user : stream) {
                Long2DoubleMap uvec = user.getValue();

                double usum = 0;

                for (Long2DoubleMap.Entry e: uvec.long2DoubleEntrySet()) {
                    usum += e.getDoubleValue() - intercept - itemOff.get(e.getLongKey());
                }

                map.put(user.getId(), usum / uvec.size());
            }
        }

        return new UserItemBiasModel(intercept, map, itemOff);
    }
}
