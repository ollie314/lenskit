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
package org.lenskit.data.packed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.SerializationUtils;
import org.lenskit.util.io.ObjectStreams;
import org.lenskit.data.dao.SortOrder;
import org.lenskit.data.events.Event;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.history.UserHistory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class BinaryRatingDAOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    List<Rating> ratings;

    @Before
    public void createRatingList() {
        ImmutableList.Builder<Rating> bld = ImmutableList.builder();
        bld.add(Rating.create(42, 105, 3.5, 100L))
           .add(Rating.create(42, 120, 2.5, 110L))
           .add(Rating.create(39, 120, 4.5, 120L));
        ratings = bld.build();
    }

    @Test
    public void testEmptyDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        packer.close();

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        assertThat(ObjectStreams.makeList(dao.streamEvents()),
                   hasSize(0));
        assertThat(dao.getUserIds(), hasSize(0));
        assertThat(dao.getItemIds(), hasSize(0));
    }

    @Test
    public void testSimpleDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        try {
            packer.writeRatings(ratings);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        verifySimpleDAO(dao);
    }

    @Test
    public void testTimestampedDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file, BinaryFormatFlag.TIMESTAMPS);
        try {
            packer.writeRatings(ratings);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        assertThat(ObjectStreams.makeList(dao.streamEvents(Rating.class)),
                   equalTo(ratings));
    }

    @Test
    public void testOutOfOrderDAO() throws IOException {
        List<Rating> reordered = new ArrayList<>();
        for (int i = 0; i < ratings.size(); i++) {
            reordered.add(ratings.get((i + 1) % ratings.size()));
        }

        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file, BinaryFormatFlag.TIMESTAMPS);
        try {
            packer.writeRatings(reordered);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        assertThat(ObjectStreams.makeList(dao.streamEvents(Rating.class)),
                   equalTo(ratings));
        assertThat(dao.getEventsForUser(42), hasSize(2));
        assertThat(dao.getEventsForUser(42, Rating.class),
                   contains(ratings.get(0), ratings.get(1)));
        assertThat(dao.getEventsForUser(39), hasSize(1));
        assertThat(dao.getEventsForUser(39, Rating.class),
                   contains(ratings.get(2)));
    }

    @Test
    public void testUpgradedDAO() throws IOException {
        List<Rating> all = Lists.newArrayList(ratings);
        all.add(Rating.create(39L, Integer.MAX_VALUE + 100L, Math.PI, 23049L));

        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file, BinaryFormatFlag.TIMESTAMPS);
        try {
            packer.writeRatings(all);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        assertThat(ObjectStreams.makeList(dao.streamEvents(Rating.class)),
                   equalTo(all));
        assertThat(dao.getEventsForUser(42), hasSize(2));
        assertThat(dao.getEventsForUser(42, Rating.class),
                   contains(ratings.get(0), ratings.get(1)));
        assertThat(dao.getEventsForUser(39), hasSize(2));
        assertThat(dao.getEventsForUser(39, Rating.class),
                   contains(ratings.get(2), all.get(3)));
    }

    @Test
    public void testSerializedDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        try {
            packer.writeRatings(ratings);
        } finally {
            packer.close();
        }

        BinaryRatingDAO dao = BinaryRatingDAO.open(file);
        BinaryRatingDAO clone = SerializationUtils.clone(dao);
        verifySimpleDAO(clone);
    }

    @Test
    public void testBufferDAO() throws IOException {
        File file = folder.newFile("ratings.bin");
        BinaryRatingPacker packer = BinaryRatingPacker.open(file);
        try {
            packer.writeRatings(ratings);
        } finally {
            packer.close();
        }

        ByteBuffer buffer;
        try (FileInputStream istr = new FileInputStream(file)) {
            FileChannel chan = istr.getChannel();
            buffer = chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size());
        }

        BinaryRatingDAO dao = BinaryRatingDAO.fromBuffer(buffer);
        verifySimpleDAO(dao);
    }

    private void verifySimpleDAO(BinaryRatingDAO dao) {
        assertThat(ObjectStreams.makeList(dao.streamEvents()),
                   hasSize(3));
        assertThat(dao.getUserIds(), containsInAnyOrder(42L, 39L));
        assertThat(dao.getItemIds(), containsInAnyOrder(105L, 120L));
        assertThat(dao.getUsersForItem(105), containsInAnyOrder(42L));
        assertThat(dao.getUsersForItem(120), containsInAnyOrder(42L, 39L));
        assertThat(dao.getEventsForUser(39, Rating.class),
                   contains(Rating.create(39, 120, 4.5)));
        assertThat(dao.getEventsForUser(42, Rating.class),
                   containsInAnyOrder(Rating.create(42, 120, 2.5),
                                      Rating.create(42, 105, 3.5)));
        assertThat(dao.getEventsForItem(105, Rating.class),
                   contains(Rating.create(42, 105, 3.5)));
        assertThat(dao.getEventsForItem(120, Rating.class),
                   containsInAnyOrder(Rating.create(39, 120, 4.5),
                                      Rating.create(42, 120, 2.5)));
        assertThat(dao.getEventsForItem(42), nullValue());
        assertThat(dao.getEventsForUser(105), nullValue());

        List<UserHistory<Event>> histories = ObjectStreams.makeList(dao.streamEventsByUser());
        assertThat(histories, hasSize(2));
        assertThat(histories.get(0).getUserId(), equalTo(39L));
        assertThat(histories.get(0),
                   equalTo(dao.getEventsForUser(39)));
        assertThat(histories.get(1).getUserId(), equalTo(42L));
        assertThat(histories.get(1),
                   equalTo(dao.getEventsForUser(42)));

        List<Rating> events = ObjectStreams.makeList(dao.streamEvents(Rating.class, SortOrder.USER));
        assertThat(events, hasSize(3));
        assertThat(events.get(0).getUserId(), equalTo(39L));

        events = ObjectStreams.makeList(dao.streamEvents(Rating.class, SortOrder.ITEM));
        assertThat(events, hasSize(3));
        assertThat(events.get(0).getUserId(), equalTo(42L));
        assertThat(events.get(0).getItemId(), equalTo(105L));
    }
}
