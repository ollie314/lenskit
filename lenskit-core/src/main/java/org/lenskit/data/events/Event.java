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
package org.lenskit.data.events;


/**
 * An event in a user's history. Events are associated with items. In some
 * cases, it makes sense that a user may have events not associated with an item
 * (such as login/logout events). We recommend that integrators use a designated
 * item ID in such cases.  If that item type never shows up in events used by
 * recommenders, then it should not affect the recommenders.
 *
 * <p>
 * Event objects should always be immutable, and have well-defined
 * {@link Object#equals(Object)} and {@link Object#hashCode()}
 * methods.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @compat Public
 */
public interface Event {
    /**
     * Get the ID of the user responsible for the event.
     *
     * @return The event's user ID.
     */
    long getUserId();

    /**
     * Get the item ID.
     *
     * @return The item ID of the event.
     */
    long getItemId();

    /**
     * Get the event timestamp. A timestamp of -1 indicates that the event has
     * no timestamp; such events should generally be considered to happen
     * infinitely long ago.
     *
     * @return The event timestamp, or -1 if there is no timestamp.
     */
    long getTimestamp();
}
