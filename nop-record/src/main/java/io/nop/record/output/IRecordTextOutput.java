/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.output;

import java.io.IOException;

public interface IRecordTextOutput extends Appendable {
    int length();

    IRecordTextOutput append(CharSequence str) throws IOException;

    IRecordTextOutput append(CharSequence str, int start, int end) throws IOException;

    IRecordTextOutput append(char[] chars) throws IOException;

    IRecordTextOutput append(char[] chars, int start, int end) throws IOException;

    IRecordTextOutput append(char c) throws IOException;

    void flush() throws IOException;

    void close() throws IOException;
}