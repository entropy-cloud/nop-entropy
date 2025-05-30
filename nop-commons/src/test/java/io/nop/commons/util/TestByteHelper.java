
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

// copy from Kylin Project

import io.nop.commons.bytes.ByteString;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestByteHelper {

    @Test
    public void testUUID() {
        UUID uuid = UUID.randomUUID();

        ByteString bs = ByteString.fromUUID(uuid);
        assertEquals(uuid.toString(), bs.toUUID().toString());
        assertEquals(StringHelper.replace(uuid.toString(),"-",""),bs.toString());
    }

    @Test
    public void test() {
        ByteBuffer buffer = ByteBuffer.allocate(10000);
        int[] x = new int[]{1, 2, 3};
        ByteHelper.writeIntArray(x, buffer);
        buffer.flip();

        byte[] buf = new byte[buffer.limit()];
        System.arraycopy(buffer.array(), 0, buf, 0, buffer.limit());

        ByteBuffer newBuffer = ByteBuffer.wrap(buf);
        int[] y = ByteHelper.readIntArray(newBuffer);
        assertEquals(y[2], 3);
    }

    @Test
    public void testBooleanArray() {
        ByteBuffer buffer = ByteBuffer.allocate(10000);
        boolean[] x = new boolean[]{true, false, true};
        ByteHelper.writeBooleanArray(x, buffer);
        buffer.flip();
        boolean[] y = ByteHelper.readBooleanArray(buffer);
        assertEquals(y[2], true);
        assertEquals(y[1], false);
    }

    @Test
    public void testReadable() {
        String x = "\\x00\\x00\\x00\\x00\\x00\\x01\\xFC\\xA8";
        byte[] bytes = ByteHelper.fromReadableText(x);
        String y = ByteHelper.toHex(bytes);
        assertEquals(x, y);
    }
}