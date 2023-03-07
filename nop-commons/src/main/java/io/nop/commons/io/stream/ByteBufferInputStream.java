/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.stream;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Not thread safe!
 * <p>
 * Please note that the reads will cause position movement on wrapped ByteBuffer.
 */
public class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buf;

    public ByteBufferInputStream(ByteBuffer buf) {
        this.buf = buf;
    }

    /**
     * Reads the next byte of data from this input stream. The value byte is returned as an <code>int</code> in the
     * range <code>0</code> to <code>255</code>. If no byte is available because the end of the stream has been reached,
     * the value <code>-1</code> is returned.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the stream has been reached.
     */
    public int read() {
        if (this.buf.hasRemaining()) {
            return (this.buf.get() & 0xff);
        }
        return -1;
    }

    /**
     * Reads up to next <code>len</code> bytes of data from buffer into passed array(starting from given offset).
     *
     * @param b   the array into which the data is read.
     * @param off the start offset in the destination array <code>b</code>
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes actually read into the buffer, or <code>-1</code> if not even 1 byte can be
     * read because the end of the stream has been reached.
     */
    public int read(byte b[], int off, int len) {
        int avail = available();
        if (avail <= 0) {
            return -1;
        }

        if (len > avail) {
            len = avail;
        }
        if (len <= 0) {
            return 0;
        }

        this.buf.get(b, off, len);
        return len;
    }

    /**
     * Skips <code>n</code> bytes of input from this input stream. Fewer bytes might be skipped if the end of the input
     * stream is reached. The actual number <code>k</code> of bytes to be skipped is equal to the smaller of
     * <code>n</code> and remaining bytes in the stream.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     */
    public long skip(long n) {
        long k = Math.min(n, available());
        if (k < 0) {
            k = 0;
        }
        this.buf.position((int) (this.buf.position() + k));
        return k;
    }

    /**
     * @return the number of remaining bytes that can be read (or skipped over) from this input stream.
     */
    public int available() {
        return this.buf.remaining();
    }
}