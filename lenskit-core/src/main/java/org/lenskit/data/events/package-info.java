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
/**
 * LensKit's events and related types. Events are the core way of tracking user
 * and item data in LensKit; users have events associated with items. Ratings
 * are events, as are purchases, clicks, and other forms of user-item
 * interaction data.
 *
 * In some cases, it makes sense for an event to not be associated with any
 * particular item (e.g. "user logged in"). In this case, we recommend that
 * integrators use a designated item ID. So long as this ID does not show up in
 * any events used by the recommender implementation, it should not affect
 * recommendation. Likewise, a designated user ID can be used for item-only
 * events.
 */
package org.lenskit.data.events;
