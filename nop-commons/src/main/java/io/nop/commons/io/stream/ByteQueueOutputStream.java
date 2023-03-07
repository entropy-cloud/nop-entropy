/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream based on a ByteQueue implementation.
 */
public class ByteQueueOutputStream
        extends OutputStream {
    private final ByteQueue buffer;

    public ByteQueueOutputStream() {
        this(new ByteQueue());
    }

    public ByteQueueOutputStream(ByteQueue buffer) {
        this.buffer = buffer;
    }

    public ByteQueue getBuffer() {
        return buffer;
    }

    public synchronized void write(int b) throws IOException {
        buffer.write(b);
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        buffer.write(b, off, len);
    }
}