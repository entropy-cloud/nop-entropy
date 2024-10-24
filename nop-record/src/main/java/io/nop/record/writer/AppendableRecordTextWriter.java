/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.writer;

import io.nop.commons.text.MutableString;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

public class AppendableRecordTextWriter implements IRecordTextWriter {
    private final Appendable buf;
    private int length;

    public AppendableRecordTextWriter(Appendable buf) {
        this.buf = buf;
    }

    public AppendableRecordTextWriter() {
        this(new StringBuilder());
    }

    public int length() {
        return length;
    }

    @Override
    public IRecordTextWriter append(CharSequence str) throws IOException {
        buf.append(str);
        length += str.length();
        return this;
    }

    @Override
    public IRecordTextWriter append(CharSequence str, int start, int end) throws IOException {
        buf.append(str, start, end);
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextWriter append(char[] chars) throws IOException {
        return append(chars, 0, chars.length);
    }

    @Override
    public IRecordTextWriter append(char[] chars, int start, int end) throws IOException {
        if (buf instanceof StringBuilder) {
            ((StringBuilder) buf).append(chars, start, end);
        } else {
            buf.append(new MutableString(chars, start, end), start, end);
        }
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextWriter append(char c) throws IOException {
        buf.append(c);
        length++;
        return this;
    }

    @Override
    public void flush() throws IOException {
        if (buf instanceof Flushable) {
            ((Flushable) buf).flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (buf instanceof Closeable) {
            ((Closeable) buf).close();
        }
    }
}