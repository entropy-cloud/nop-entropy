/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.output;

import io.nop.commons.text.MutableString;

import java.io.IOException;
import java.io.Writer;

public class SimpleTextOutput implements IRecordTextOutput {
    private final Appendable buf;
    private int length;

    public SimpleTextOutput(Appendable buf) {
        this.buf = buf;
    }

    public SimpleTextOutput() {
        this(new StringBuilder());
    }

    public int length() {
        return length;
    }

    @Override
    public IRecordTextOutput append(CharSequence str) throws IOException {
        buf.append(str);
        length += str.length();
        return this;
    }

    @Override
    public IRecordTextOutput append(CharSequence str, int start, int end) throws IOException {
        buf.append(str, start, end);
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextOutput append(char[] chars) throws IOException {
        return append(chars, 0, chars.length);
    }

    @Override
    public IRecordTextOutput append(char[] chars, int start, int end) throws IOException {
        if (buf instanceof StringBuilder) {
            ((StringBuilder) buf).append(chars, start, end);
        } else {
            buf.append(new MutableString(chars, start, end), start, end);
        }
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextOutput append(char c) throws IOException {
        buf.append(c);
        length++;
        return this;
    }

    @Override
    public void flush() throws IOException {
        if (buf instanceof Writer) {
            ((Writer) buf).flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (buf instanceof Writer) {
            ((Writer) buf).close();
        }
    }
}