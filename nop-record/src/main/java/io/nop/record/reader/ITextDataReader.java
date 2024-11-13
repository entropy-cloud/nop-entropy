/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.reader;

import java.io.IOException;

public interface ITextDataReader extends IDataReaderBase {
    int available() throws IOException;

    void skip(int n) throws IOException;

    String read(int len) throws IOException;

    default String readAvailableText() throws IOException {
        int len = available();
        if (len < 0)
            return null;
        if (len == 0)
            return "";
        return read(len);
    }

    int readChar() throws IOException;

    String readLine(int maxLength) throws IOException;

    int pos();

    /**
     * 重置offset为0
     */
    void reset() throws IOException;

    default ITextDataReader subInput(int maxLength) {
        return new SubTextDataReader(this, maxLength);
    }

    ITextDataReader detach();

    boolean isDetached();
}