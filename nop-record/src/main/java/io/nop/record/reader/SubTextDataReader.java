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

/**
 * 限制从input中读取的长度不超过length
 */
public class SubTextDataReader implements ITextDataReader {
    private final ITextDataReader input;
    private final int startOffset;
    private final int maxLength;

    public SubTextDataReader(ITextDataReader input, int maxLength) {
        this(input, pos(input), maxLength);
    }

    static int pos(ITextDataReader input) {
        try {
            return (int) input.pos();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public SubTextDataReader(ITextDataReader input, int startOffset, int maxLength) {
        this.input = input;
        this.startOffset = startOffset;
        this.maxLength = maxLength;
    }

    @Override
    public boolean isEof() throws IOException {
        long offset = pos();
        if (offset >= maxLength)
            return true;
        return input.isEof();
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public long available() throws IOException {
        long offset = pos();
        if (offset >= maxLength)
            return 0;

        long n = input.available();
        if (n <= 0) {
            return n;
        }

        return Math.min(n, maxLength - offset);
    }

    @Override
    public void skip(int n) throws IOException {
        long avail = maxLength - pos();
        if (avail < n)
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        input.skip(n);
    }

    @Override
    public String read(int len) throws IOException {
        long avail = maxLength - pos();
        if (avail < len)
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        return input.read(len);
    }

    @Override
    public int readChar() throws IOException {
        if (pos() >= maxLength)
            return -1;
        return input.readChar();
    }

    @Override
    public String readLine(int maxLength) throws IOException {
        long avail = this.maxLength - pos();
        maxLength = (int) Math.min(maxLength, avail);
        return input.readLine(maxLength);
    }

    @Override
    public long pos() throws IOException {
        return input.pos() - startOffset;
    }

    @Override
    public void seek(long pos) throws IOException {
        input.seek(pos + startOffset);
    }

    @Override
    public void reset() throws IOException {
        input.reset();
        input.skip(startOffset);
    }

    @Override
    public ITextDataReader detach() {
        return new SubTextDataReader(input.detach(), startOffset, maxLength);
    }

    @Override
    public boolean isDetached() {
        return input.isDetached();
    }
}