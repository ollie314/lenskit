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
package org.grouplens.lenskit.util;

import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Various type utilities used in LensKit.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class TypeUtils {
    private TypeUtils() {
    }

    /**
     * Build the set of types implemented by the objects' classes. This includes
     * all supertypes which are themselves subclasses of <var>parent</var>.  The
     * resulting set is the set of all subclasses of <var>parent</var> such that
     * there exists some object in <var>objects</var> assignable to one of them.
     *
     * @param objects A collection of objects.  This iterable may be fast (returning a modified
     *                version of the same object).
     * @param parent  The parent type of interest.
     * @return The set of types applicable to objects in <var>objects</var>.
     */
    public static <T> Set<Class<? extends T>> findTypes(Iterable<? extends T> objects, Class<T> parent) {
        // Build a set of all object classes in use
        Set<Class<?>> objTypes = new HashSet<>();
        for (T obj: objects) {
            objTypes.add(obj.getClass());
        }

        // accumulate all classes reachable from an object type that are subtypes of parent
        Set<Class<? extends T>> allTypes = new HashSet<>();
        for (Class<?> t : objTypes) {
            for (Class<?> type: typeClosure(t)) {
                if (parent.isAssignableFrom(type)) {
                    allTypes.add(type.asSubclass(parent));
                }
            }
        }
        return allTypes;
    }

    /**
     * Return the supertype closure of a type (the type and all its transitive
     * supertypes).
     *
     * @param type The type.
     * @return All supertypes of the type, including the type itself.
     */
    public static Set<Class<?>> typeClosure(Class<?> type) {
        if (type == null) {
            return Collections.emptySet();
        }

        Set<Class<?>> supertypes = new HashSet<>();
        supertypes.add(type);
        supertypes.addAll(typeClosure(type.getSuperclass()));
        for (Class<?> iface : type.getInterfaces()) {
            supertypes.addAll(typeClosure(iface));
        }

        return supertypes;
    }

    /**
     * A predicate that accepts classes which are subtypes of (assignable to) the parent class.
     * @param parent The parent class.
     * @return A predicate that returns {@code true} when applied to a subtype of {@code parent}.
     *         That is, it implements {@code paret.isAssignableFrom(type)}.
     */
    public static Predicate<Class<?>> subtypePredicate(final Class<?> parent) {
        return new Predicate<Class<?>>() {
            @Override
            public boolean apply(@Nullable Class<?> input) {
                return parent.isAssignableFrom(input);
            }
        };
    }
}
