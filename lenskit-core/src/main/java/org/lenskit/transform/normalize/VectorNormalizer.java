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
package org.lenskit.transform.normalize;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.grouplens.grapht.annotation.DefaultImplementation;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.lenskit.util.InvertibleFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Normalize vectors by applying a reversible transformation with respect to
 * a reference vector.  The reference vector is used to compute the normalization,
 * and it is applied to the target vector; this allows e.g. the user's average
 * rating to be subtracted from a set of ratings.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
@DefaultImplementation(IdentityVectorNormalizer.class)
public interface VectorNormalizer {

    /**
     * Normalize a vector in-place with a reference vector.
     *
     * <p>
     * To understand the relationship of <var>reference</var> and
     * <var>vector</var>, consider wanting to subtract the user's mean rating
     * from a set of ratings. To do that, the user's rating vector is
     * <var>reference</var>, and the vector of ratings to be adjusted is
     * <var>vector</var>.
     *
     * <p>
     * This method is equivalent to {@code makeTransformation(reference).apply(target)}.
     *
     * @param reference The reference used to compute whatever transformation is
     *                  needed (e.g. the mean value).
     * @param target    The vector to normalize. If {@code null}, a new mutable
     *                  copy of <var>reference</var> is created.
     * @return <var>target</var>, or a normalized mutable copy of
     *         <var>reference</var> if <var>target</var> is {@code null}.
     * @deprecated Old vectors are going away.
     */
    @Deprecated
    MutableSparseVector normalize(@Nonnull SparseVector reference,
                                  @Nullable MutableSparseVector target);

    /**
     * Create a vector transformation that normalizes and denormalizes vectors
     * with respect to the specified entity. This allows transformations to be
     * applied multiple times to different vectors and also unapplied.
     * <p>
     * If the reference vector is empty, the returned transformation should be
     * the identity transform. Results are undefined if the reference vector is
     * not complete or contains NaN values.
     * <p>
     * If the normalization needs to retain a copy of the sparse vector, it will
     * take an immutable copy.
     *
     * @param reference The reference vector.
     * @return A transformation built from the reference vector.
     * @deprecated Use {@link #makeTransformation(Long2DoubleMap)}.
     */
    @Deprecated
    VectorTransformation makeTransformation(SparseVector reference);

    /**
     * Create a vector transformation that normalizes and denormalizes vectors
     * with respect to a reference vector.  The reference vector is used to compute any data needed for the
     * normalization.  For example, a mean-centering normalization will subtract the mean of the reference vector
     * from any vector to which it is applied, and add back the reference mean when it is unapplied.
     *
     * <p>This allows transformations to be applied multiple times to different vectors and also unapplied.
     * <p>
     * If the reference vector is empty, the returned transformation should be
     * the identity transform. Results are undefined if the reference vector is
     * not complete or contains NaN values.
     * <p>
     * If the normalization needs to retain a copy of the sparse vector, it will
     * take an immutable copy.
     *
     * @param reference The reference vector.
     * @return A transformation built from the reference vector.
     */
    InvertibleFunction<Long2DoubleMap,Long2DoubleMap> makeTransformation(Long2DoubleMap reference);
}
