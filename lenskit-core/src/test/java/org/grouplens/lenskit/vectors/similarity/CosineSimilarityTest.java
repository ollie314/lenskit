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
package org.grouplens.lenskit.vectors.similarity;


import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CosineSimilarityTest {
    private static final double EPSILON = 1.0e-6;
    private CosineVectorSimilarity similarity, dampedSimilarity;

    @Before
    public void setUp() throws Exception {
        similarity = new CosineVectorSimilarity();
        dampedSimilarity = new CosineVectorSimilarity(10);
    }

    private SparseVector emptyVector() {
        long[] keys = {};
        double[] values = {};
        return MutableSparseVector.wrap(keys, values);
    }

    @Test
    public void testEmpty() {
        assertEquals(0, similarity.similarity(emptyVector(), emptyVector()), EPSILON);
    }

    @Test
    public void testEmptyDamped() {
        assertEquals(0, dampedSimilarity.similarity(emptyVector(), emptyVector()), EPSILON);
    }

    @Test
    public void testDisjoint() {
        long[] k1 = {2, 5, 6};
        double[] val1 = {1, 3, 2};
        long[] k2 = {3, 4, 7};
        double[] val2 = {1, 3, 2};
        SparseVector v1, v2;
        v1 = MutableSparseVector.wrap(k1, val1).freeze();
        v2 = MutableSparseVector.wrap(k2, val2).freeze();
        assertEquals(0, similarity.similarity(v1, v2), EPSILON);
        assertEquals(0, dampedSimilarity.similarity(v1, v2), EPSILON);
    }

    @Test
    public void testEqualKeys() {
        long[] keys = {2, 5, 6};
        double[] val1 = {1, 2, 1};
        double[] val2 = {1, 2, 5};
        SparseVector v1 = MutableSparseVector.wrap(keys, val1).freeze();
        SparseVector v2 = MutableSparseVector.wrap(keys, val2).freeze();
        assertEquals(1, similarity.similarity(v1, v1), EPSILON);
        assertEquals(0.745355993, similarity.similarity(v1, v2), EPSILON);
    }

    @Test
    public void testDampedEqualKeys() {
        long[] keys = {2, 5, 6};
        double[] val1 = {1, 2, 1};
        double[] val2 = {1, 2, 5};
        SparseVector v1 = MutableSparseVector.wrap(keys, val1).freeze();
        SparseVector v2 = MutableSparseVector.wrap(keys, val2).freeze();
        assertEquals(0.375, dampedSimilarity.similarity(v1, v1), EPSILON);
        assertEquals(0.42705098, dampedSimilarity.similarity(v1, v2), EPSILON);
    }

    @Test
    public void testOverlap() {
        long[] k1 = {1, 2, 5, 6};
        double[] val1 = {3, 1, 2, 1};
        long[] k2 = {2, 3, 5, 6, 7};
        double[] val2 = {1, 7, 2, 5, 0};
        SparseVector v1 = MutableSparseVector.wrap(k1, val1).freeze();
        SparseVector v2 = MutableSparseVector.wrap(k2, val2).freeze();
        assertEquals(1, similarity.similarity(v1, v1), EPSILON);
        assertEquals(1, similarity.similarity(v2, v2), EPSILON);
        assertEquals(0.29049645, similarity.similarity(v1, v2), EPSILON);
    }
}
