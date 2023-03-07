/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.stream;

public interface ICharReader extends AutoCloseable {

    /**
     * 返回当前的读取位置附近的文本信息
     */
    String currentState();

    int read();

    int peek();

    int peek(int n);

    long skip(long n);
}