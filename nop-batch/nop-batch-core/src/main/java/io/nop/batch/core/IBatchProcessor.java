/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.batch.core.processor.BatchProcessorWithListener;
import io.nop.batch.core.processor.CompositeBatchProcessor;

import java.util.function.Consumer;

/**
 * 逐条处理数据，可能产生后续数据（一条或多条），也可能不产生数据。类似于flatMap操作
 *
 * @param <S> 来源记录类型
 * @param <R> 处理后产生的记录类型
 */
public interface IBatchProcessor<S, R, C> {
    /**
     * 执行类似flatMap的操作
     *
     * @param item     输入数据对象
     * @param consumer 接收返回结果，可能为一条或者多条。也可能不产生数据导致consumer不会被调用
     * @param context  上下文信息
     */
    void process(S item, Consumer<R> consumer, C context);

    default IBatchProcessor<S, R, C> withListener(IBatchProcessListener<S, R, C> listener) {
        return new BatchProcessorWithListener<>(this, listener);
    }

    /**
     * 两个processor合成为一个processor
     *
     * @param processor
     * @param <T>
     * @return
     */
    default <T> IBatchProcessor<S, T, C> then(IBatchProcessor<R, T, C> processor) {
        return new CompositeBatchProcessor<>(this, processor);
    }
}