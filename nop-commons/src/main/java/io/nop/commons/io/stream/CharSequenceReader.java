/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;

public class CharSequenceReader implements ICharReader {
    private final CharSequence sequence;
    private final int len;
    private int pos;
    private int nRead;

    public CharSequenceReader(CharSequence sequence) {
        this.sequence = sequence;
        this.len = sequence.length();
        this.pos = 0;
    }

    public CharSequence getSequence() {
        return sequence;
    }

    public int getReadCount() {
        return nRead;
    }

    @Override
    public String currentState() {
        return StringHelper.shortText(sequence, pos - 1, 30);
    }

    @Override
    public int read() {
        if (pos >= len)
            return -1;
        nRead++;
        char c = sequence.charAt(pos++);
        return c;
    }

    @Override
    public int peek() {
        if (pos >= len)
            return -1;
        return sequence.charAt(pos);
    }

    @Override
    public int peek(int n) {
        Guard.positiveInt(n, "peek index");

        if (pos + n - 1 >= len)
            return -1;

        return sequence.charAt(pos + n - 1);
    }

    @Override
    public long skip(long n) {
        if (pos + n >= len) {
            int r = len - pos;
            nRead += r;
            pos = len;
            return r;
        }

        nRead += n;
        pos += n;
        return n;
    }

    @Override
    public void close() throws Exception {

    }
}