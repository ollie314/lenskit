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
package org.grouplens.lenskit.data.sql;

import com.google.common.collect.ImmutableList;
import org.lenskit.util.io.ObjectStream;
import org.lenskit.util.io.GroupingObjectStream;
import org.lenskit.data.events.Event;
import org.lenskit.data.history.UserHistory;
import org.lenskit.data.history.History;

import javax.annotation.Nonnull;
import javax.annotation.WillCloseWhenClosed;
import java.util.List;

/**
 * Stream that processes (user,timestamp)-sorted stream of events and groups
 * them into user histories.
 *
 * @param <E> The event type.
 */
class UserHistoryObjectStream<E extends Event> extends GroupingObjectStream<UserHistory<E>,E> {
    private ImmutableList.Builder<E> builder;
    private long userId;

    public UserHistoryObjectStream(@WillCloseWhenClosed ObjectStream<? extends E> cur) {
        super(cur);
    }

    @Override
    protected void clearGroup() {
        builder = null;
    }

    @Override
    protected boolean handleItem(@Nonnull E event) {
        if (builder == null) {
            userId = event.getUserId();
            builder = ImmutableList.builder();
        }

         if (userId == event.getUserId()) {
            builder.add(event);
            return true;
         } else {
             return false;
         }
    }

    @Nonnull
    @Override
    protected UserHistory<E> finishGroup() {
        List<E> events = builder.build();
        builder = null;
        return History.forUser(userId, events);
    }
}
