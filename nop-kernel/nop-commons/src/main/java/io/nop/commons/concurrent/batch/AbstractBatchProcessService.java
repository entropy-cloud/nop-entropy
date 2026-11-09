/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.batch;

import io.nop.api.core.message.ISpecialMessageProcessor;
import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBatchProcessService<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractBatchProcessService.class);
    private IThreadPoolExecutor executor;

    private BatchQueueConfig queueConfig;
    private IBlockingQueue<T> queue;
    private BlockingSourceConsumeTask<T> task;
    private ISpecialMessageProcessor specialMessageProcessor;

    /**
     * 1.7-C3-012（plan 2547 裁定 E）：接收位。destroy 时置 false，生产者应据此拒绝新入队，
     * 使消费循环能把既有队列 drain 空（否则 cancel 后循环立即退出、队列残量被静默丢弃）。
     */
    private volatile boolean accepting = true;

    /** 1.7-C3-012：destroy 时等待队列 drain 空的上界（缺省 5s）。 */
    private long drainTimeoutMillis = 5000L;

    public boolean isAccepting() {
        return accepting;
    }

    public void setDrainTimeoutMillis(long drainTimeoutMillis) {
        this.drainTimeoutMillis = drainTimeoutMillis;
    }

    public void setQueueConfig(BatchQueueConfig config) {
        this.queueConfig = config;
    }

    public void setSpecialMessageProcessor(ISpecialMessageProcessor specialMessageProcessor) {
        this.specialMessageProcessor = specialMessageProcessor;
    }

    public IBlockingQueue<T> getQueue() {
        return queue;
    }

    /**
     * 1.7-C3-012：经本服务的安全入队入口——destroy 后拒绝新条目（返回 false）。
     * 直接 {@code getQueue().send()} 绕过本方法的生产方不在 drain 契约覆盖内。
     */
    public boolean send(T message) throws InterruptedException {
        if (!accepting)
            return false;
        queue.send(message);
        return true;
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

    /**
     * 1.7-C3-012（plan 2547 裁定 E；draft review R1 B-5 次序勘正）：原实现先 {@code task.cancel()}
     * 再 destroy——但消费循环是 {@code do { drainTo } while (!isCancelled())}，cancel 后下一轮即退出、
     * 不再 drain，于是队列中已入队条目被直接丢弃且 {@code isAllProcessed()} 永远不可能变真。
     * <p>
     * 正确次序：① 关闭「接收」位（拒绝新入队）→ ② 有界等待消费循环把队列 drain 空 →
     * ③ 再 cancel → ④ 销毁 executor。超时则 drain 残量并逐条记录丢弃清单后告警。
     * <p>
     * 边界：{@code accepting} 位只覆盖经本服务的入队路径；绕过它直调 {@code getQueue().send()} 的
     * 外部生产方不在本机制覆盖内（已在设计 §五 登记）。
     */
    @PreDestroy
    public void destroy() {
        this.accepting = false;

        if (task != null) {
            long deadline = CoreMetrics.currentTimeMillis() + drainTimeoutMillis;
            while (!isAllProcessed() && CoreMetrics.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!isAllProcessed()) {
                // 1.7-C3-012：超时残量显式记录（IBlockingQueue 无快照 API，用 drainTo 取残量）
                List<T> residual = new ArrayList<>();
                try {
                    queue.drainTo(residual, Integer.MAX_VALUE, 0, 0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("nop.concurrent.batch-destroy-discarded:count={},taskName={}",
                        residual.size(), getClass().getName());
            }

            task.cancel();
        }

        if (executor != null)
            executor.destroy();
    }

    protected abstract void doProcess(List<T> list);
}
