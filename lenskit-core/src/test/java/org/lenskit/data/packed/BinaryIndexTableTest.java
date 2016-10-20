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

import org.apache.commons.lang3.SerializationUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class BinaryIndexTableTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testEmpty() {
        BinaryIndexTable tbl = BinaryIndexTable.fromBuffer(0, ByteBuffer.allocate(0));
        assertThat(tbl.getKeys(), hasSize(0));
        assertThat(tbl.getEntry(42), nullValue());
    }

    @Test
    public void testSingleEntry() throws IOException {
        File file = folder.newFile();
        FileChannel chan = new RandomAccessFile(file, "rw").getChannel();
        BinaryIndexTableWriter w = BinaryIndexTableWriter.create(BinaryFormat.create(), chan, 1);
        w.writeEntry(42, new int[] {0});

        MappedByteBuffer buf = chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size());
        BinaryIndexTable tbl = BinaryIndexTable.fromBuffer(1, buf);
        assertThat(tbl.getKeys(), contains(42L));
        assertThat(tbl.getEntry(42), contains(0));
        assertThat(tbl.getEntry(43), nullValue());
    }

    @Test
    public void testMultipleEntries() throws IOException {
        File file = folder.newFile();
        FileChannel chan = new RandomAccessFile(file, "rw").getChannel();
        BinaryIndexTableWriter w = BinaryIndexTableWriter.create(BinaryFormat.create(), chan, 3);
        w.writeEntry(42, new int[] {0});
        w.writeEntry(49, new int[] {1,3});
        w.writeEntry(67, new int[] {2,4});

        MappedByteBuffer buf = chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size());
        BinaryIndexTable tbl = BinaryIndexTable.fromBuffer(3, buf);
        assertThat(tbl.getKeys(), contains(42L, 49L, 67L));
        assertThat(tbl.getEntry(42), contains(0));
        assertThat(tbl.getEntry(49), contains(1,3));
        assertThat(tbl.getEntry(67), contains(2,4));
        assertThat(tbl.getEntry(-1), nullValue());
    }

    @Test
    public void testLimitedView() throws IOException {
        File file = folder.newFile();
        FileChannel chan = new RandomAccessFile(file, "rw").getChannel();
        BinaryIndexTableWriter writer = BinaryIndexTableWriter.create(BinaryFormat.create(), chan, 3);
        writer.writeEntry(12, new int[] {0});
        writer.writeEntry(17, new int[] {1,3});
        writer.writeEntry(19, new int[] {4,5});

        MappedByteBuffer buffer = chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size());
        BinaryIndexTable tbl = BinaryIndexTable.fromBuffer(3, buffer);
        tbl = tbl.createLimitedView(2);
        assertThat(tbl.getKeys(), hasSize(2));
        assertThat(tbl.getKeys(), contains(12L,17L));
        assertThat(tbl.getEntry(12), contains(0));
        assertThat(tbl.getEntry(17), contains(1));
        assertThat(tbl.getEntry(19), nullValue());
        assertThat(tbl.getEntry(-1), nullValue());

        BinaryIndexTable serializedTbl = SerializationUtils.clone(tbl);
        assertThat(serializedTbl.getKeys(), hasSize(2));
        assertThat(serializedTbl.getKeys(), contains(12L,17L));
        assertThat(serializedTbl.getEntry(12), contains(0));
        assertThat(serializedTbl.getEntry(17), contains(1));
        assertThat(serializedTbl.getEntry(19), nullValue());
        assertThat(serializedTbl.getEntry(-1), nullValue());
    }
}
