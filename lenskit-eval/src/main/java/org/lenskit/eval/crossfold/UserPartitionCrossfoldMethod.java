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
package org.lenskit.eval.crossfold;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.lenskit.data.ratings.Rating;

import java.util.Random;

class UserPartitionCrossfoldMethod extends UserBasedCrossfoldMethod {
    public UserPartitionCrossfoldMethod(SortOrder ord, HistoryPartitionMethod pa) {
        super(ord, pa);
    }

    @Override
    protected Long2IntMap splitUsers(LongSet users, int np, Random rng) {
        Long2IntMap userMap = new Long2IntOpenHashMap(users.size());
        logger.info("Splitting {} users into {} partitions", users.size(), np);
        long[] userArray = users.toLongArray();
        LongArrays.shuffle(userArray, rng);
        for (int i = 0; i < userArray.length; i++) {
            final long user = userArray[i];
            userMap.put(user, i % np);
        }
        return userMap;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof UserPartitionCrossfoldMethod) {
            UserPartitionCrossfoldMethod om = (UserPartitionCrossfoldMethod) obj;
            EqualsBuilder eqb = new EqualsBuilder();
            return eqb.append(order, om.order)
                    .append(partition, om.partition)
                    .isEquals();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(order)
                                    .append(partition)
                                    .toHashCode();
    }
}
