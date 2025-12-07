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

import static io.nop.record.RecordErrors.ARG_EXPECTED;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

public interface ITextDataReader extends IDataReaderBase {
    long available() throws IOException;

    void skip(int n) throws IOException;

    String tryReadFully(int len) throws IOException;

    default String readFully(int len) throws IOException {
        String data = tryReadFully(len);
        if (data.length() != len) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param(ARG_POS, pos())
                    .param(ARG_EXPECTED, len)
                    .param(ARG_LENGTH, data.length());
        }
        return data;
    }


    default String readAvailableText() throws IOException {
        long len = available();
        if (len < 0)
            return null;
        if (len == 0)
            return "";
        return readFully((int) len);
    }

    int readChar() throws IOException;

    String readLine(int maxLength) throws IOException;

    long pos() throws IOException;

    void seek(long pos) throws IOException;

    default boolean startsWith(String text) throws IOException {
        return text.equals(peek(text.length()));
    }

    default String peek(int len) throws IOException {
        long pos = pos();
        String data = tryReadFully(len);
        seek(pos);
        return data;
    }

    default String peekNext(int offset, int len) throws IOException {
        long pos = pos();
        skip(offset);
        String data = tryReadFully(len);
        seek(pos);
        return data;
    }

    /**
     * 重置offset为0
     */
    void reset() throws IOException;

    default ITextDataReader subInput(long maxLength) {
        return new SubTextDataReader(this, maxLength);
    }

    ITextDataReader detach() throws IOException;

    boolean isDetached();

    default long currentLineNumber(){
        return -1L;
    }
}