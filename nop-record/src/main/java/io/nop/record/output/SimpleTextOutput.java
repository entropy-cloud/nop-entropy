/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.output;

public class SimpleTextOutput implements IRecordTextOutput {
    private final StringBuilder buf;
    private int length;

    public SimpleTextOutput(StringBuilder buf) {
        this.buf = buf;
    }

    public SimpleTextOutput() {
        this(new StringBuilder());
    }

    public int length() {
        return length;
    }

    @Override
    public IRecordTextOutput append(CharSequence str) {
        buf.append(str);
        length += str.length();
        return this;
    }

    @Override
    public IRecordTextOutput append(CharSequence str, int start, int end) {
        buf.append(str, start, end);
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextOutput append(char[] chars) {
        buf.append(chars);
        length += chars.length;
        return this;
    }

    @Override
    public IRecordTextOutput append(char[] chars, int start, int end) {
        buf.append(chars, start, end);
        length += end - start;
        return this;
    }

    @Override
    public IRecordTextOutput append(char c) {
        buf.append(c);
        length++;
        return this;
    }
}