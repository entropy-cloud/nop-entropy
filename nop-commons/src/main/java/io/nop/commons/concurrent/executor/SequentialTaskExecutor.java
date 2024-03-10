/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.IBlockingSinkNotifier;
import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import io.nop.commons.service.ISuspendable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 确保在单线程上执行
 */
public class SequentialTaskExecutor implements Executor, ISuspendable, IBlockingSinkNotifier {
    static final Logger LOG = LoggerFactory.getLogger(SequentialTaskExecutor.class);

    // actor没有执行任务
    private static final int STATUS_IDLE = 0;

    // 已经调度到任务队列中等待执行，或者正在执行过程中
    private static final int STATUS_SCHEDULED = 1;

    private static final int MASK_STATUS = 0x3;

    private static final int FLAG_SUSPENDED = 8;

    final private IBlockingQueue<Runnable> queue;
    final private Executor executor;

    final private AtomicInteger status = new AtomicInteger();

    private final long quotaNanos;

    // 每次向队列中派发任务后都会将此参数设置为true, 然后尝试调度执行
    private volatile boolean hasTask;

    // nanos
    private long totalExecutionTime;

    private final Runnable doTask;

    public SequentialTaskExecutor(Executor executor, IBlockingQueue<Runnable> queue, long quotaMillis) {
        // Guard.assertTrue(quota > 0, "concurrent.err_runner_invalid_quota", quota);
        this.queue = Guard.notNull(queue, "taskQueue");
        this.executor = Guard.notNull(executor, "taskExecutor");
        this.quotaNanos = quotaMillis < 0 ? -1 : TimeUnit.MILLISECONDS.toNanos(quotaMillis);
        this.doTask = this::doTask;
    }

    public SequentialTaskExecutor(Executor executor, int queueCapacity, QueueOverflowPolicy overflowPolicy,
                                  int quotaMillis) {
        this(executor, new OverflowBlockingQueue<>(queueCapacity, overflowPolicy), quotaMillis);
    }

    public long getTotalExecutionTime() {
        return TimeUnit.NANOSECONDS.convert(totalExecutionTime, TimeUnit.MILLISECONDS);
    }

    public void clearQueue() {
        queue.clear();
    }

    @Override
    public void execute(Runnable command) {
        queue.offer(command);
        onMessageQueued();
    }

    @Override
    public void onMessageQueued() {
        this.trySchedule(true);
    }

    @Override
    public boolean isSuspended() {
        return (status.get() & FLAG_SUSPENDED) != 0;
    }

    @Override
    public void suspend() {
        int st;
        do {
            st = status.get();
            if ((st & FLAG_SUSPENDED) != 0)
                return;
        } while (!status.compareAndSet(st, st | FLAG_SUSPENDED));
    }

    @Override
    public void resume() {
        int st;
        do {
            st = status.get();
            if ((st & FLAG_SUSPENDED) == 0)
                break;
        } while (!status.compareAndSet(st, st & ~FLAG_SUSPENDED));

        trySchedule(false);
    }

    private void updateStatus(int newStatus) {
        int st, n;
        do {
            st = status.get();
            n = (st & ~MASK_STATUS) | newStatus;
        } while (!status.compareAndSet(st, n));
    }

    private void doTask() {
        long begin = CoreMetrics.nanoTime();
        long diff;

        boolean mayHasTask = true;
        do {
            Runnable task = queue.poll();
            if (task == null) {
                // 设置hasTask=false的唯一地方
                this.hasTask = false;
                task = queue.poll();
            }
            if (task == null) {
                diff = CoreMetrics.nanoTimeDiff(begin);
                mayHasTask = false;
                break;
            }
            try {
                task.run();
            } catch (Throwable e) {
                LOG.error("nop.err.executor.run-task-fail", e);
            }
            diff = CoreMetrics.nanoTimeDiff(begin);
        } while (quotaNanos < 0 || diff < quotaNanos);

        totalExecutionTime += diff;

        try {
            updateStatus(STATUS_IDLE);
            trySchedule(mayHasTask);
        } catch (Exception e) {
            LOG.error("nop.err.executor.schedule-fail", e);
        }
    }

    private void trySchedule(boolean mayHasTask) {
        if (mayHasTask) {
            // 设置hasTask=true的唯一地方
            this.hasTask = true;
        } else {
            mayHasTask = this.hasTask;
        }

        // 如果没有任务则不需要再调度
        if (!mayHasTask)
            return;

        int st;
        do {
            st = status.get();
            if ((st & (FLAG_SUSPENDED | STATUS_SCHEDULED)) != 0)
                return;

        } while (!status.compareAndSet(st, STATUS_SCHEDULED));

        // 此时状态为SCHEDULED
        executor.execute(this.doTask);
    }
}