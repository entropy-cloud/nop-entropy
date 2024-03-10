/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

public interface IPeekableSource<T> extends IBlockingSource<T> {
    /**
     * 返回队首记录。如果当前队列为空， 则返回null。
     *
     * @return
     */
    T peek();

    boolean isNextAvailable();
}