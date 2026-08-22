/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ResolvedPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ContextTaskQueue {
    static final Logger LOG = LoggerFactory.getLogger(ContextTaskQueue.class);

    private final Deque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition queueReady = lock.newCondition();

    /**
     * 同一时刻只允许一个线程在执行任务
     */
    private volatile Thread processingThread;

    /**
     * 当前正在同步等待的线程个数
     */
    private int syncing;

    /**
     * 任务处理的重入嵌套次数
     */
    private int processing;

    public <T> T syncGet(CompletionStage<T> promise) {
        final AtomicReference<ResolvedPromise<T>> result = new AtomicReference<>();
        promise.whenComplete((value, err) -> {
            lock.lock();
            try {
                result.set(ResolvedPromise.complete(value, err));
                queueReady.signalAll();
            } finally {
                lock.unlock();
            }
        });

        do {
            ResolvedPromise<T> f = result.get();
            if (f != null)
                return f.syncGet();

            if (beginProcess()) {
                try {
                    do {
                        Runnable task = tasks.poll();
                        if (task == null)
                            break;

                        try {
                            task.run();
                        } catch (Throwable e) {
                            LOG.error("nop.core.context.run-task-fail", e);
                        }

                        f = result.get();
                        if (f != null)
                            return f.syncGet();
                    } while (true);
                } finally {
                    endProcess();
                }
            }

            lock.lock();
            try {
                syncing++;
                f = result.get();
                if (f != null)
                    return f.syncGet();

                try {
                    queueReady.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }
            } finally {
                syncing--;
                lock.unlock();
            }

            // 被唤醒，但是promise尚未结束，则继续检查是否有工作需要执行
        } while (true);
    }

    /**
     * 将任务加入队列。如果当前有线程正在处理此任务队列，则返回true，否则返回false
     *
     * @param task 待执行的任务
     * @return 当前是否有线程正在执行此任务队列
     */
    public boolean enqueue(Runnable task) {
        tasks.add(task);
        lock.lock();
        try {
            if (syncing > 0) {
                queueReady.signalAll();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        if (!beginProcess())
            return;
        try {
            do {
                flushTasks();
            } while (!endProcessIfIdle());
        } catch (Throwable t) {
            endProcess();
            throw t;
        }
    }

    private void flushTasks() {
        // 任务执行时刻并不持有lock，从而避免锁重入时死锁
        do {
            Runnable task = tasks.poll();
            if (task == null)
                return;
            try {
                task.run();
            } catch (Throwable e) {
                LOG.error("nop.core.context.run-task-fail", e);
            }
        } while (true);
    }

    /**
     * 处理线程退出处理状态前的原子判定：必须在锁内确认队列为空之后才清空processingThread。
     * 最后一次poll返回null到真正退出之间存在check-then-act窗口，期间其他线程enqueue后调用flush
     * 会因processingThread仍为本线程而放弃执行；若不二次确认，该任务将滞留队列无人处理。
     *
     * @return true 表示已退出处理状态；false 表示退出窗口内队列又收到新任务，需要继续处理
     */
    private boolean endProcessIfIdle() {
        lock.lock();
        try {
            if (processing == 1 && !tasks.isEmpty())
                return false;
            if (--processing == 0) {
                processingThread = null;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean beginProcess() {
        lock.lock();
        try {
            Thread current = Thread.currentThread();
            // 如果已经有其他线程负责处理，则直接放弃执行
            if (processingThread != current && processingThread != null)
                return false;
            processingThread = current;
            processing++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void endProcess() {
        lock.lock();
        try {
            if (--processing == 0) {
                processingThread = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isProcessingThread() {
        return Thread.currentThread() == processingThread;
    }

    public boolean isProcessing() {
        return processingThread != null;
    }
}