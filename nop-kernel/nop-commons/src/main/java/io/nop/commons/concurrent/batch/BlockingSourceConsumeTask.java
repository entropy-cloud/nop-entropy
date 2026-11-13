/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.batch;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ISpecialMessageProcessor;
import io.nop.commons.concurrent.IBlockingConsumer;
import io.nop.commons.concurrent.IBlockingSource;
import io.nop.commons.lang.impl.Cancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 从阻塞队列中不断读取消息，转发到IBlockingConsumer接口上。相当于将pull模式转换为push模式
 */
public class BlockingSourceConsumeTask<S> extends Cancellable implements Runnable {
    static final Logger LOG = LoggerFactory.getLogger(BlockingSourceConsumeTask.class);

    private final IBlockingSource<S> source;
    private final IBlockingConsumer<S> consumer;
    private final BatchConsumeConfig config;
    private final ISpecialMessageProcessor specialMessageProcessor;

    private final AtomicInteger processingCount = new AtomicInteger();

    public BlockingSourceConsumeTask(IBlockingSource<S> source, IBlockingConsumer<S> consumer,
                                     BatchConsumeConfig config, ISpecialMessageProcessor specialMessageProcessor) {
        this.source = source;
        this.consumer = consumer;
        this.config = config;
        this.specialMessageProcessor = specialMessageProcessor;
    }

    public int getProcessingCount() {
        return processingCount.get();
    }

    /**
     * 1.7-C3-011（plan 2547 裁定 E）：原实现里循环体只要抛出非 Exception 的 {@link Error}
     * （或在 drainTo 处抛出未经处理的异常），整条消费线程就会**永久死亡**且不可感知——
     * 队列继续积压、无人消费、也无任何告警。改为：最外层 {@code catch (Throwable)} + ERROR 记录 +
     * 继续下一轮（仅 cancel 或中断才退出）。
     */
    @Override
    public void run() {
        do {
            try {
                int batchSize = config.getBatchSize();
                List<S> items = new ArrayList<>(batchSize);
                try {
                    source.drainTo(items, batchSize, config.getMinWaitMillis(), config.getMaxWaitMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 中断 + 已取消 → 正常退出；中断但未取消 → 记录并继续（避免静默死亡）
                    if (isCancelled())
                        return;
                    LOG.warn("nop.concurrent.batch-process-interrupted", e);
                    continue;
                }

                int size = items.size();
                processingCount.addAndGet(size);
                try {
                    filterMessages(items);
                    if (!items.isEmpty()) {
                        consumer.acceptList(items);
                    }
                } catch (Exception e) {
                    if (isCancelled()) {
                        LOG.debug("nop.concurrent.batch-process-cancelled", e);
                    } else {
                        LOG.error("nop.concurrent.batch-process-fail", e);
                    }
                } finally {
                    processingCount.addAndGet(-size);
                }
            } catch (Throwable e) {
                // 1.7-C3-011：Error 或非 Exception 逃逸不得杀死消费循环
                if (isCancelled()) {
                    LOG.debug("nop.concurrent.batch-process-cancelled", e);
                    return;
                }
                LOG.error("nop.concurrent.batch-process-fatal", e);
            }
        } while (!isCancelled());
    }

    void filterMessages(List<S> items) {
        if (specialMessageProcessor != null && !items.isEmpty()) {
            for (int i = 0, n = items.size(); i < n; i++) {
                if (specialMessageProcessor.process(items.get(i))) {
                    items.remove(i);
                    i--;
                    n--;
                }
            }
        }
    }
}