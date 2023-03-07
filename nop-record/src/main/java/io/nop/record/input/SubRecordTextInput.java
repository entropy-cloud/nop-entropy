/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.input;

import io.nop.api.core.exceptions.NopException;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

/**
 * 限制从input中读取的长度不超过length
 */
public class SubRecordTextInput implements IRecordTextInput {
    private final IRecordTextInput input;
    private final int startOffset;
    private final int maxLength;

    public SubRecordTextInput(IRecordTextInput input, int maxLength) {
        this(input, input.pos(), maxLength);
    }

    public SubRecordTextInput(IRecordTextInput input, int startOffset, int maxLength) {
        this.input = input;
        this.startOffset = startOffset;
        this.maxLength = maxLength;
    }

    @Override
    public int available() {
        int offset = pos();
        if (offset >= maxLength)
            return 0;

        int n = input.available();
        if (n < 0) {
            return n;
        }

        return Math.min(n, maxLength - offset);
    }

    @Override
    public void skip(int n) {
        int avail = maxLength - pos();
        if (avail < n)
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        input.skip(n);
    }

    @Override
    public String read(int len) {
        int avail = maxLength - pos();
        if (avail < len)
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        return input.read(len);
    }

    @Override
    public int readChar() {
        if (pos() >= maxLength)
            return -1;
        return input.readChar();
    }

    @Override
    public String readLine(int maxLength) {
        int avail = this.maxLength - pos();
        maxLength = Math.min(maxLength, avail);
        return input.readLine(maxLength);
    }

    @Override
    public int pos() {
        return input.pos() - startOffset;
    }

    @Override
    public void reset() {
        input.reset();
        input.skip(startOffset);
    }

    @Override
    public IRecordTextInput detach() {
        return new SubRecordTextInput(input.detach(), startOffset, maxLength);
    }

    @Override
    public boolean isDetached() {
        return input.isDetached();
    }
}