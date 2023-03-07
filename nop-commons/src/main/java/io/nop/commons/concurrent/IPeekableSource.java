/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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