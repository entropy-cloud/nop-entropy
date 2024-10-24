/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

public class SimpleRecordTextReader implements IRecordTextReader {
    private final CharSequence text;
    private int offset;

    public SimpleRecordTextReader(CharSequence text) {
        this.text = text;
    }

    @Override
    public int available() {
        return text.length() - offset;
    }

    @Override
    public void skip(int n) {
        if (offset + n > text.length())
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);

        offset += n;
    }

    @Override
    public boolean isEof() {
        return offset >= text.length();
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public String read(int len) {
        return text.subSequence(offset, offset + len).toString();
    }

    @Override
    public int readChar() {
        if (offset >= text.length())
            return -1;
        return text.charAt(offset);
    }

    @Override
    public String readLine(int maxLength) {
        int avail = available();
        if (avail <= 0)
            return "";

        int n = Math.min(maxLength, avail);
        for (int i = 0; i < n; i++) {
            char c = text.charAt(offset + i);
            if (c == '\r') {
                int end = offset + i;
                String str = text.subSequence(offset, end).toString();
                offset = i + 1;
                if (end < text.length() - 1 && text.charAt(end + 1) == '\n') {
                    offset++;
                }
                return str;
            } else if (c == '\n') {
                String str = text.subSequence(offset, offset + i).toString();
                offset = i + 1;
                return str;
            }
        }

        return text.subSequence(offset, offset + n).toString();
    }

    @Override
    public int pos() {
        return offset;
    }

    @Override
    public void reset() {
        offset = 0;
    }

    @Override
    public IRecordTextReader detach() {
        SimpleRecordTextReader input = new SimpleRecordTextReader(text);
        input.skip(offset);
        return input;
    }

    @Override
    public boolean isDetached() {
        return true;
    }
}