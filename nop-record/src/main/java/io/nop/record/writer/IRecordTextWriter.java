/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.writer;

import java.io.IOException;

public interface IRecordTextWriter extends Appendable, IRecordWriterBase {
    int length();

    IRecordTextWriter append(CharSequence str) throws IOException;

    IRecordTextWriter append(CharSequence str, int start, int end) throws IOException;

    IRecordTextWriter append(char[] chars) throws IOException;

    IRecordTextWriter append(char[] chars, int start, int end) throws IOException;

    IRecordTextWriter append(char c) throws IOException;

}