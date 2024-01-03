/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    @Override
    public void run() {
        do {
            int batchSize = config.getBatchSize();
            List<S> items = new ArrayList<>(batchSize);
            try {
                source.drainTo(items, batchSize, config.getMinWaitMillis(), config.getMaxWaitMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
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