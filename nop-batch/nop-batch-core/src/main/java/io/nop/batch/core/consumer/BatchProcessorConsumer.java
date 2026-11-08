/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.util.ICancellable;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.batch.core.BatchConfigs.CFG_BATCH_ASYNC_PROCESS_TIMEOUT;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_TOO_MANY_PROCESSING_ITEMS;

/**
 * 逐条处理输入数据，并收集所有的输出数据到列表结构中，然后统一调用一次下游的consumer
 *
 * @param <S> 输入数据类型
 * @param <R> 产生的结果数据类型
 */
public class BatchProcessorConsumer<S, R> implements IBatchConsumer<S> {
    private final IBatchProcessor<S, R> processor;
    private final IBatchConsumer<R> consumer;
    private final boolean async;
    private final long asyncProcessTimeout;

    /**
     * 1.7-C5-003（plan 2545 W-05）：单 chunk 输出集上界。原实现把本 chunk 全部输出累积到
     * {@code outputs} 后才一次性下发；`useBatchRequestGenerator` 路径下单个 chunk item 能在
     * process 内循环展开 `totalCount` 条输出（batchSize 只约束输入条数，不约束展开数）——
     * totalCount 50 万 × 500B 即数百 MB。0/负值 = 不限制（保持既有部署行为）。
     */
    private int maxOutputItemsPerChunk = 0;

    public BatchProcessorConsumer(IBatchProcessor<S, R> processor,
                                  IBatchConsumer<R> consumer, boolean async, long asyncProcessTimeout) {
        this.processor = processor;
        this.consumer = consumer;
        this.async = async;
        this.asyncProcessTimeout = asyncProcessTimeout;
    }

    public BatchProcessorConsumer(IBatchProcessor<S, R> processor, IBatchConsumer<R> consumer) {
        this(processor, consumer, false, 0L);
    }

    /** 1.7-C5-003（plan 2545 W-05）：设置单 chunk 输出集上界（&lt;=0 = 不限制）。 */
    public void setMaxOutputItemsPerChunk(int maxOutputItemsPerChunk) {
        this.maxOutputItemsPerChunk = maxOutputItemsPerChunk;
    }

    public int getMaxOutputItemsPerChunk() {
        return maxOutputItemsPerChunk;
    }

    @Override
    public void consume(Collection<S> items, IBatchChunkContext batchChunkCtx) {

        IBatchTaskMetrics metrics = batchChunkCtx.getTaskContext().getMetrics();

        if (async) {
            batchChunkCtx.initChunkLatch(new CountDownLatch(items.size()));
        }

        // 假定为同步处理模型。这里缓存所有输出数据，至于当整个列表中的元素都被成功消费以后，才会处理输出数据
        Collection<R> outputs = newOutputs();
        // 1.7-C5-003（plan 2545 W-05）：输出计数独立维护——异步路径 outputs 是 ConcurrentLinkedQueue，
        // 其 size() 为 O(n)，在每个输出热路径上调用会退化为 O(n²)。
        AtomicInteger outputCount = new AtomicInteger();

        for (S item : items) {
            Object meter = metrics == null ? null : metrics.beginProcess();
            boolean success = false;
            try {
                batchChunkCtx.incProcessCount();
                batchChunkCtx.getTaskContext().incProcessItemCount(1);
                // processor内部可能异步执行。如果是异步执行，执行完毕后需要调用batchChunkCtx.countDown()
                processor.process(item, output -> {
                    // 1.7-C5-003（plan 2545 W-05）：输出集上界——超限即 fail-fast，不静默膨胀
                    if (maxOutputItemsPerChunk > 0 && outputCount.get() >= maxOutputItemsPerChunk)
                        throw new NopException(ERR_BATCH_TOO_MANY_PROCESSING_ITEMS);
                    outputCount.incrementAndGet();
                    outputs.add(output);
                }, batchChunkCtx);
                success = true;
            } finally {
                if (metrics != null)
                    metrics.endProcess(meter, success);
            }

            if (batchChunkCtx.getTaskContext().isCancelled())
                throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

            if (batchChunkCtx.isCancelled())
                throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);
        }

        if (async) {
            try {
                long timeout = asyncProcessTimeout;
                if (timeout <= 0)
                    timeout = CFG_BATCH_ASYNC_PROCESS_TIMEOUT.get().toMillis();

                if (!batchChunkCtx.getChunkLatch().await(timeout, TimeUnit.MILLISECONDS)) {
                    batchChunkCtx.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
                    throw new NopTimeoutException();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        }

        // 即使是空集合也执行一次consumer，有可能会触发orm flush
        consumeResult(outputs, batchChunkCtx);
    }

    protected Collection<R> newOutputs() {
        return async ? new ConcurrentLinkedQueue<>() : new ArrayList<>();
    }

    void consumeResult(Collection<R> outputs, IBatchChunkContext context) {
        IBatchTaskMetrics metrics = context.getTaskContext().getMetrics();
        Object meter = metrics == null ? null : metrics.beginConsume(outputs.size());

        boolean success = false;
        try {
            context.getTaskContext().fireConsumeBegin(outputs, context);
            consumer.consume(outputs, context);
            context.getTaskContext().fireConsumeEnd(context, null);
            success = true;
        } catch (Exception e) {
            context.getTaskContext().fireConsumeEnd(context, e);
            throw NopException.adapt(e);
        } finally {
            if (metrics != null) {
                metrics.endConsume(meter, outputs.size(), success);
            }
        }
    }
}