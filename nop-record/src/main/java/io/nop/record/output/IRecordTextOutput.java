/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.output;

public interface IRecordTextOutput extends Appendable {
    int length();

    IRecordTextOutput append(CharSequence str);

    IRecordTextOutput append(CharSequence str, int start, int end);

    IRecordTextOutput append(char[] chars);

    IRecordTextOutput append(char[] chars, int start, int end);

    IRecordTextOutput append(char c);
}