/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import io.nop.api.core.util.Guard;
import io.nop.commons.text.MutableString;
import io.nop.commons.util.StringHelper;

public class CharArrayReader implements ICharReader {
    private final char[] sequence;
    private final int len;
    private int pos;
    private int nRead;

    public CharArrayReader(CharSequence sequence) {
        this.sequence = sequence.toString().toCharArray();
        this.len = sequence.length();
        this.pos = 0;
    }

    public CharArrayReader(char[] array) {
        this.sequence = array;
        this.len = array.length;
        this.pos = 0;
    }

    public int getReadCount() {
        return nRead;
    }

    @Override
    public String currentState() {
        return StringHelper.shortText(new MutableString(sequence, 0, len), pos - 1, 30);
    }

    @Override
    public int read() {
        if (pos >= len)
            return -1;
        nRead++;
        char c = sequence[pos++];
        return c;
    }

    @Override
    public int peek() {
        if (pos >= len)
            return -1;
        return sequence[pos];
    }

    @Override
    public int peek(int n) {
        Guard.positiveInt(n, "peek index");

        if (pos + n - 1 >= len)
            return -1;

        return sequence[pos + n - 1];
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
