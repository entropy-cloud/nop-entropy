/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import java.io.InputStream;

/**
 * InputStream based on a ByteQueue implementation.
 */
public class ByteQueueInputStream extends InputStream {
    private final ByteQueue buffer;

    public ByteQueueInputStream(ByteQueue buffer) {
        this.buffer = buffer;
    }

    public ByteQueueInputStream() {
        this(new ByteQueue());
    }

    public synchronized void addBytes(byte[] buf) {
        buffer.addData(buf, 0, buf.length);
    }

    public synchronized void addBytes(byte[] buf, int bufOff, int bufLen) {
        buffer.addData(buf, bufOff, bufLen);
    }

    public synchronized int peek(byte[] buf) {
        return buffer.peek(buf);
    }

    public synchronized int read() {
        return buffer.read();
    }

    public synchronized int read(byte[] b) {
        return read(b, 0, b.length);
    }

    public synchronized int read(byte[] b, int off, int len) {
        return buffer.read(b, off, len);
    }

    public synchronized long skip(long n) {
        return buffer.skip(n);
    }

    public synchronized int available() {
        return buffer.available();
    }

    public void close() {
    }
}
