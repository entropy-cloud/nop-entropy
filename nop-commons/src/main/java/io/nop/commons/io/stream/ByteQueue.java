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
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

// copy from org.bouncycastle.tls.ByteQueue

/**
 * A queue for bytes. This file could be more optimized.
 */
public class ByteQueue {
    static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * @return The smallest number which can be written as 2^x which is bigger than i.
     */
    public static int nextTwoPow(int i) {
        /*
         * This code is based of a lot of code I found on the Internet which mostly
         * referenced a book called "Hacking delight".
         */
        i |= (i >> 1);
        i |= (i >> 2);
        i |= (i >> 4);
        i |= (i >> 8);
        i |= (i >> 16);
        return i + 1;
    }

    /**
     * The buffer where we store our data.
     */
    private byte[] databuf;

    /**
     * How many bytes at the beginning of the buffer are skipped.
     */
    private int skipped = 0;

    /**
     * How many bytes in the buffer are valid data.
     */
    private int available = 0;

    private boolean readOnlyBuf = false;

    public ByteQueue() {
        this(0);
    }

    public ByteQueue(int capacity) {
        databuf = capacity == 0 ? EMPTY_BYTES : new byte[capacity];
    }

    public ByteQueue(byte[] buf, int off, int len) {
        this.databuf = buf;
        this.skipped = off;
        this.available = len;
        this.readOnlyBuf = true;
    }

    /**
     * Add some data to our buffer.
     *
     * @param buf A byte-array to read data from.
     * @param off How many bytes to skip at the beginning of the array.
     * @param len How many bytes to read from the array.
     */
    public void addData(byte[] buf, int off, int len) {
        if (readOnlyBuf) {
            throw new IllegalStateException("Cannot add data to read-only buffer");
        }

        if (available == 0) {
            if (len > databuf.length) {
                int desiredSize = nextTwoPow(len | 256);
                databuf = new byte[desiredSize];
            }
            skipped = 0;
        } else if ((skipped + available + len) > databuf.length) {
            int desiredSize = nextTwoPow(available + len);
            if (desiredSize > databuf.length) {
                byte[] tmp = new byte[desiredSize];
                System.arraycopy(databuf, skipped, tmp, 0, available);
                databuf = tmp;
            } else {
                System.arraycopy(databuf, skipped, databuf, 0, available);
            }
            skipped = 0;
        }

        System.arraycopy(buf, off, databuf, skipped + available, len);
        available += len;
    }

    /**
     * @return The number of bytes which are available in this buffer.
     */
    public int available() {
        return available;
    }

    /**
     * Copy some bytes from the beginning of the data to the provided {@link OutputStream}.
     *
     * @param output The {@link OutputStream} to copy the bytes to.
     * @param length How many bytes to copy.
     */
    public void copyTo(OutputStream output, int length) throws IOException {
        if (length > available) {
            throw new IllegalStateException("Cannot copy " + length + " bytes, only got " + available);
        }

        output.write(databuf, skipped, length);
    }

    /**
     * Read data from the buffer.
     *
     * @param buf    The buffer where the read data will be copied to.
     * @param offset How many bytes to skip at the beginning of buf.
     * @param len    How many bytes to read at all.
     * @param skip   How many bytes from our data to skip.
     */
    public void read(byte[] buf, int offset, int len, int skip) {
        if ((buf.length - offset) < len) {
            throw new IllegalArgumentException("Buffer size of " + buf.length
                    + " is too small for a read of " + len + " bytes");
        }
        if ((available - skip) < len) {
            throw new IllegalStateException("Not enough data to read");
        }
        System.arraycopy(databuf, skipped + skip, buf, offset, len);
    }

    /**
     * Read data from the buffer.
     *
     * @param buf  The {@link ByteBuffer} where the read data will be copied to.
     * @param len  How many bytes to read at all.
     * @param skip How many bytes from our data to skip.
     */
    public void read(ByteBuffer buf, int len, int skip) {
        int remaining = buf.remaining();
        if (remaining < len) {
            throw new IllegalArgumentException(
                    "Buffer size of " + remaining + " is too small for a read of " + len + " bytes");
        }
        if ((available - skip) < len) {
            throw new IllegalStateException("Not enough data to read");
        }
        buf.put(databuf, skipped + skip, len);
    }

    /**
     * Remove some bytes from our data from the beginning.
     *
     * @param i How many bytes to remove.
     */
    public void removeData(int i) {
        if (i > available) {
            throw new IllegalStateException("Cannot remove " + i + " bytes, only got " + available);
        }

        /*
         * Skip the data.
         */
        available -= i;
        skipped += i;
    }

    /**
     * Remove data from the buffer.
     *
     * @param buf  The buffer where the removed data will be copied to.
     * @param off  How many bytes to skip at the beginning of buf.
     * @param len  How many bytes to read at all.
     * @param skip How many bytes from our data to skip.
     */
    public void removeData(byte[] buf, int off, int len, int skip) {
        read(buf, off, len, skip);
        removeData(skip + len);
    }

    /**
     * Remove data from the buffer.
     *
     * @param buf  The {@link ByteBuffer} where the removed data will be copied to.
     * @param len  How many bytes to read at all.
     * @param skip How many bytes from our data to skip.
     */
    public void removeData(ByteBuffer buf, int len, int skip) {
        read(buf, len, skip);
        removeData(skip + len);
    }

    public byte[] removeData(int len, int skip) {
        byte[] buf = new byte[len];
        removeData(buf, 0, len, skip);
        return buf;
    }

    public void shrink() {
        if (available == 0) {
            databuf = EMPTY_BYTES;
            skipped = 0;
        } else {
            int desiredSize = nextTwoPow(available);
            if (desiredSize < databuf.length) {
                byte[] tmp = new byte[desiredSize];
                System.arraycopy(databuf, skipped, tmp, 0, available);
                databuf = tmp;
                skipped = 0;
            }
        }
    }

    public void write(int b) {
        addData(new byte[]{(byte) b}, 0, 1);
    }

    public void write(byte[] b, int off, int len) {
        addData(b, off, len);
    }

    public void write(byte b[]) {
        write(b, 0, b.length);
    }

    public void clear() {
        available = 0;
        skipped = 0;
    }

    public int peek(byte[] buf) {
        int bytesToRead = Math.min(available(), buf.length);
        read(buf, 0, bytesToRead, 0);
        return bytesToRead;
    }

    public int read() {
        if (available() == 0) {
            return -1;
        }
        return removeData(1, 0)[0] & 0xFF;
    }

    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) {
        int bytesToRead = Math.min(available(), len);
        removeData(b, off, bytesToRead, 0);
        return bytesToRead;
    }

    public long skip(long n) {
        int bytesToRemove = Math.min((int) n, available());
        removeData(bytesToRemove);
        return bytesToRemove;
    }


    public String readToString(Charset charset) {
        StringBuilder sb = new StringBuilder();
        ByteQueueInputStream in = new ByteQueueInputStream(this);
        InputStreamReader reader = new InputStreamReader(in, charset);
        try {
            do {
                int c = reader.read();
                if (c < 0)
                    break;
                sb.append((char) c);
            } while (true);
        } catch (IOException e) { // NOPMD
            // ignore error
        }
        return sb.toString();
    }
}
