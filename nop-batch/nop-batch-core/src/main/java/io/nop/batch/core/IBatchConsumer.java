/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import java.util.List;

/**
 * 批量消费一组数据对象。
 *
 * @param <R>
 */
public interface IBatchConsumer<R, C> {
    /**
     * @param items   待处理的对象集合
     * @param context 上下文对象
     */
    void consume(List<R> items, C context);
}