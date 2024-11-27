/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;

import java.util.ArrayList;
import java.util.List;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;

/**
 * 逐条处理输入数据，并收集所有的输出数据到列表结构中，然后统一调用一次下游的consumer
 *
 * @param <S> 输入数据类型
 * @param <R> 产生的结果数据类型
 */
public class BatchProcessorConsumer<S, R> implements IBatchConsumer<S> {
    private final IBatchProcessor<S, R> processor;
    private final IBatchConsumer<R> consumer;

    public BatchProcessorConsumer(IBatchProcessor<S, R> processor,
                                  IBatchConsumer<R> consumer) {
        this.processor = processor;
        this.consumer = consumer;
    }

    @Override
    public void consume(List<S> items, IBatchChunkContext context) {

        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();

        // 假定为同步处理模型。这里缓存所有输出数据，至于当整个列表中的元素都被成功消费以后，才会处理输出数据
        List<R> collector = new ArrayList<>();

        for (S item : items) {
            Object meter = metrics == null ? null : metrics.beginProcess();
            boolean success = false;
            try {
                context.incProcessCount();
                context.getTaskContext().incProcessItemCount(1);
                processor.process(item, collector::add, context);
                success = true;
            } finally {
                if (metrics != null)
                    metrics.endProcess(meter, success);
            }

            if (context.getTaskContext().isCancelled())
                throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

            if (context.isCancelled())
                throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);
        }

        consumeResult(collector, context);
    }

    void consumeResult(List<R> collector, IBatchChunkContext context) {
        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        Object meter = metrics == null ? null : metrics.beginConsume(collector.size());

        boolean success = false;
        try {
            consumer.consume(collector, context);
            success = true;
        } finally {
            if (metrics != null) {
                metrics.endConsume(meter, collector.size(), success);
            }
        }
    }
}