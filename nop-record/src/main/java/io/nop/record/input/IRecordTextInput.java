/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.input;

public interface IRecordTextInput {
    int available();

    void skip(int n);

    String read(int len);

    default String readAvailableText() {
        int len = available();
        if (len < 0)
            return null;
        if (len == 0)
            return "";
        return read(len);
    }

    int readChar();

    String readLine(int maxLength);

    int pos();

    /**
     * 重置offset为0
     */
    void reset();

    default IRecordTextInput subInput(int maxLength) {
        return new SubRecordTextInput(this, maxLength);
    }

    IRecordTextInput detach();

    boolean isDetached();
}