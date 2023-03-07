/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.batch;

import io.nop.api.core.message.ISpecialMessageProcessor;
import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

public abstract class AbstractBatchProcessService<T> {
    private IThreadPoolExecutor executor;

    private BatchQueueConfig queueConfig;
    private IBlockingQueue<T> queue;
    private BlockingSourceConsumeTask<T> task;
    private ISpecialMessageProcessor specialMessageProcessor;

    public void setQueueConfig(BatchQueueConfig config) {
        this.queueConfig = config;
    }

    public void setSpecialMessageProcessor(ISpecialMessageProcessor specialMessageProcessor) {
        this.specialMessageProcessor = specialMessageProcessor;
    }

    public IBlockingQueue<T> getQueue() {
        return queue;
    }

    protected String getThreadPoolName() {
        return getClass().getSimpleName() + "-executor";
    }

    public int getQueueSize() {
        return queue.size();
    }

    @PostConstruct
    public void start() {
        if (queueConfig == null)
            queueConfig = new BatchQueueConfig();

        int poolSize = queueConfig.getThreadPoolSize();
        executor = DefaultThreadPoolExecutor.newExecutor(getThreadPoolName(), poolSize, 1, true);

        if (queueConfig == null)
            queueConfig = new BatchQueueConfig();

        this.queue = new OverflowBlockingQueue<>(queueConfig.getQueueSize(), queueConfig.getOverflowPolicy());
        this.task = new BlockingSourceConsumeTask<>(queue, this::doProcess, queueConfig, this.specialMessageProcessor);
        for (int i = 0; i < poolSize; i++) {
            executor.submit(task, null);
        }
    }

    public int getProcessingCount() {
        return task.getProcessingCount();
    }

    public boolean isAllProcessed() {
        return getQueueSize() == 0 && getProcessingCount() == 0;
    }

    @PreDestroy
    public void destroy() {
        if (task != null)
            task.cancel();

        if (executor != null)
            executor.destroy();
    }

    protected abstract void doProcess(List<T> list);
}
