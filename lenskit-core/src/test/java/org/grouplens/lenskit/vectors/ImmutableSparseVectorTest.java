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
package org.grouplens.lenskit.vectors;

import it.unimi.dsi.fastutil.longs.Long2DoubleMaps;
import org.junit.Test;

import static org.grouplens.lenskit.util.test.ExtraMatchers.notANumber;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class ImmutableSparseVectorTest extends SparseVectorTestCommon {
    @Override
    protected ImmutableSparseVector emptyVector() {
        return new ImmutableSparseVector(Long2DoubleMaps.EMPTY_MAP);
    }

    @Override
    protected ImmutableSparseVector simpleVector() {
        long[] keys = {3, 7, 8};
        double[] values = {1.5, 3.5, 2};
        return MutableSparseVector.wrap(keys, values).freeze();
    }

    @Override
    protected ImmutableSparseVector simpleVector2() {
        long[] keys = {3, 5, 8};
        double[] values = {2, 2.3, 1.7};
        return MutableSparseVector.wrap(keys, values).freeze();
    }

    @Override
    protected ImmutableSparseVector singleton() {
        return MutableSparseVector.wrap(new long[]{5}, new double[]{Math.PI}).freeze();
    }

    @Test
    public void testEmptyConstructor() {
        SparseVector v = new ImmutableSparseVector();
        assertThat(v.isEmpty(), equalTo(true));
        assertThat(v.get(15, Double.NaN), notANumber());
    }

    @Test
    public void testImmutable() {
        ImmutableSparseVector v = simpleVector();
        assertThat(v.immutable(), sameInstance(v));
    }

    @Test
    public void testOverSize() {
        long[] keys = {3, 7, 9};
        double[] values = {Math.PI, Math.E, 0.42};
        ImmutableSparseVector v = MutableSparseVector.wrap(keys, values, 2).freeze();
        assertThat(v.size(), equalTo(2));
        assertThat(v.containsKey(9), equalTo(false));
        assertThat(v.get(9, Double.NaN), notANumber());
        assertThat(v.get(3), closeTo(Math.PI));
        assertThat(v.containsKey(9), equalTo(false));
    }
}
