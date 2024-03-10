/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.text.MutableString;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.commons.CommonErrors.ARG_PEEK_COUNT;
import static io.nop.commons.CommonErrors.ERR_IO_PEEK_COUNT_EXCEED_LIMIT;

public class FastBufferedReader extends Reader implements ICharReader, IBufferedStream {
    // private static final int DEFAULT_BUF_SIZE = 8 * 1024;
    private static final int DEFAULT_KEEP_COUNT = 16;

    private final Reader reader;

    private final char[] buffer;

    // 缓冲区消费完之后读取下一个缓冲区数据时，保留上一个缓冲区的n个字节的数据。currentState函数内部需要用到
    private int keepCount = DEFAULT_KEEP_COUNT;

    /**
     * buffer中当前读取的位置
     */
    private int pos;

    /**
     * buffer中的有效数据长度
     */
    private int buffered;

    private long nRead;

    public FastBufferedReader(Reader reader, int maxBufSize) {
        this.reader = reader;
        this.buffer = new char[Math.max(256, maxBufSize)];
        this.pos = 0;
        this.buffered = 0;
    }

    public FastBufferedReader(Reader reader) {
        this(reader, CFG_IO_DEFAULT_BUF_SIZE.get());
    }

    protected boolean hasNext() {
        if (pos >= buffered) {
            try {
                int n;
                if (buffered < buffer.length) {
                    n = reader.read(buffer, buffered, buffer.length - buffered);
                    if (n <= 0) {
                        return false;
                    }
                    buffered += n;
                } else {
                    n = reader.read(buffer);
                    if (n <= 0) {
                        return false;
                    }
                    buffered = n;
                    pos = 0;
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        return true;
    }

    @Override
    public int read() {
        if (!hasNext())
            return -1;
        nRead++;
        return buffer[pos++];
    }

    @Override
    public int read(@Nonnull char[] cbuf, int offset, int length) throws IOException {
        Guard.checkOffsetLength(cbuf.length, offset, length);

        if (!hasNext())
            return -1;

        if (length <= buffered - pos) {
            System.arraycopy(buffer, pos, cbuf, offset, length);
            pos += length;
            nRead += length;
            return length;
        }

        final int head = buffered - pos;
        System.arraycopy(buffer, pos, cbuf, offset, head);

        pos = 0;
        buffered = 0;

        nRead += head;

        return head;
    }

    public long getReadCount() {
        return nRead;
    }

    @Override
    public int peek() {
        return peek(1);
    }

    @Override
    public int peek(int n) {
        Guard.positiveInt(n, "peek index");
        n--;
        if (pos + n < buffered) {
            return buffer[pos + n];
        }

        // 已经读取的缓存数据不够, 则向前移动腾出空间
        if (pos > keepCount && pos <= buffered) {
            int pos2 = pos - keepCount;
            System.arraycopy(buffer, pos2, buffer, 0, buffered - pos2);
            buffered -= pos2;
            pos = keepCount;
        }

        if (pos + n >= buffer.length)
            throw new NopException(ERR_IO_PEEK_COUNT_EXCEED_LIMIT).param(ARG_PEEK_COUNT, n);

        try {
            int nRead = IoHelper.read(reader, buffer, buffered, buffer.length - buffered);
            if (nRead < 0)
                return -1;

            buffered += nRead;

            if (pos + n >= buffered)
                return -1;

            return buffer[pos + n];
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public String toString() {
        return getClass().getSimpleName() + "[" + currentState() + "]";
    }

    public String currentState() {
        return nRead + ":" + StringHelper.shortText(new MutableString(buffer, 0, buffered), pos - 1, 30);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public long skip(long n) {
        if (pos + n < buffered) {
            nRead += n;
            pos += n;
            return n;
        }

        n -= buffered - pos;
        nRead += buffered - pos;
        pos = 0;
        buffered = 0;

        try {
            long r = n;
            while (r > 0) {
                int nc = reader.read(buffer, 0, buffer.length);
                if (nc == -1)
                    break;
                buffered = nc;
                pos = (int) Math.min(nc, r);
                r -= pos;
            }
            nRead += n - r;
            return n - r;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}