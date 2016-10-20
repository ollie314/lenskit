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
package org.lenskit.data.ratings;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class RatingTest {
    @Test
    public void testGetValueOfRating() {
        Rating rating = Rating.create(1, 2, 3.0, 3);
        assertThat(rating.hasValue(), equalTo(true));
        assertThat(rating.getValue(), equalTo(3.0));
    }
    
    @Test
    public void testGetValueOfUnrate() {
        Rating rating = Rating.createUnrate(1, 3, 5);
        assertThat(rating.hasValue(), equalTo(false));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedFactories() {
        Rating rating = Ratings.make(1, 2, 3.0);
        Rating withTS = Ratings.make(1, 2, 3.0, 1030);
        assertThat(rating.getUserId(), equalTo(1L));
        assertThat(rating.getItemId(), equalTo(2L));
        assertThat(rating.getValue(), equalTo(3.0));

        assertThat(withTS.getUserId(), equalTo(1L));
        assertThat(withTS.getItemId(), equalTo(2L));
        assertThat(withTS.getValue(), equalTo(3.0));
        assertThat(withTS.getTimestamp(), equalTo(1030L));
    }

    @Test
    public void testSimpleEquality() {
        Rating r1 = Rating.create(1, 2, 3.0, 0);
        Rating r1a = Rating.create(1, 2, 3.0, 0);
        Rating r2 = Rating.create(1, 3, 2.5, 1);
        Rating rn = Rating.createUnrate(1, 2, 0);
        assertThat(r1, equalTo(r1));
        assertThat(r1a, equalTo(r1));
        assertThat(r2, not(equalTo(r1)));
        assertThat(r1, not(equalTo(r2)));
        assertThat(rn, not(equalTo(r1)));
        assertThat(r1, not(equalTo(rn)));
    }

    @Test
    public void testEmptyURV() {
        List<Rating> ratings = Collections.emptyList();
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(true));
        assertThat(urv.size(), equalTo(0));
    }

    @Test
    public void testURVRatingsInOrder() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 3));
        ratings.add(Rating.create(1, 3, 4.5, 7));
        ratings.add(Rating.create(1, 5, 2.3, 10));
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(3));
        assertThat(urv.get(2), closeTo(3.0, 1.0e-6));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(5), closeTo(2.3, 1.0e-6));
        assertThat(urv.containsKey(1), equalTo(false));
    }

    @Test
    public void testURVRatingsOutOfOrder() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 3));
        ratings.add(Rating.create(1, 5, 2.3, 7));
        ratings.add(Rating.create(1, 3, 4.5, 10));
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(3));
        assertThat(urv.get(2), closeTo(3.0, 1.0e-6));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(5), closeTo(2.3, 1.0e-6));
        assertThat(urv.containsKey(1), equalTo(false));
    }

    @Test
    public void testURVRatingsDup() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 3));
        ratings.add(Rating.create(1, 5, 2.3, 4));
        ratings.add(Rating.create(1, 3, 4.5, 5));
        ratings.add(Rating.create(1, 5, 3.7, 6));
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(3));
        assertThat(urv.get(2), closeTo(3.0, 1.0e-6));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(5), closeTo(3.7, 1.0e-6));
        assertThat(urv.containsKey(1), equalTo(false));
    }

    @Test
    public void testURVRatingsRmv() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 3));
        ratings.add(Rating.create(1, 5, 2.3, 5));
        ratings.add(Rating.createUnrate(1, 2, 7));
        ratings.add(Rating.create(1, 3, 4.5, 8));
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(2));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(5), closeTo(2.3, 1.0e-6));
        assertThat(urv.containsKey(1), equalTo(false));
        assertThat(urv.containsKey(2), equalTo(false));
    }

    @Test
    public void testURVRatingsDupOutOfOrder() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 3));
        ratings.add(Rating.create(1, 5, 2.3, 7));
        ratings.add(Rating.create(1, 3, 4.5, 5));
        ratings.add(Rating.create(1, 5, 3.7, 6));
        Long2DoubleMap urv = Ratings.userRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(3));
        assertThat(urv.get(2), closeTo(3.0, 1.0e-6));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(5), closeTo(2.3, 1.0e-6));
        assertThat(urv.containsKey(1), equalTo(false));
    }

    @Test
    public void testEmptyIRV() {
        List<Rating> ratings = Collections.emptyList();
        Long2DoubleMap urv = Ratings.itemRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(true));
        assertThat(urv.size(), equalTo(0));
    }

    @Test
    public void testIRVRatings() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.create(1, 2, 3.0, 1));
        ratings.add(Rating.create(3, 2, 4.5, 2));
        ratings.add(Rating.create(2, 2, 2.3, 3));
        ratings.add(Rating.create(3, 2, 4.5, 10));
        Long2DoubleMap urv = Ratings.itemRatingVector(ratings);
        assertThat(urv.isEmpty(), equalTo(false));
        assertThat(urv.size(), equalTo(3));
        assertThat(urv.get(1), closeTo(3.0, 1.0e-6));
        assertThat(urv.get(3), closeTo(4.5, 1.0e-6));
        assertThat(urv.get(2), closeTo(2.3, 1.0e-6));
        assertThat(urv.containsKey(5), equalTo(false));
    }
}
