/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExecutorHelper {
    static Executor SYNC_EXECUTOR = task -> task.run();
    static final Logger LOG = LoggerFactory.getLogger(ExecutorHelper.class);

    public static Executor syncExecutor() {
        return SYNC_EXECUTOR;
    }

    public static Executor continuationExecutor() {
        return ContinuationExecutor.INSTANCE;
    }

    public static ThreadPoolExecutor newSingleThreadExecutor(String name, int queueSize, boolean daemon) {
        ThreadFactory factory = new NamedThreadFactory(name, daemon);
        BlockingQueue<Runnable> queue = queueSize == 0 ? new SynchronousQueue<>()
                : new LinkedBlockingQueue<>(queueSize);
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, factory);
    }

    public static ScheduledThreadPoolExecutor newScheduledExecutor(ThreadPoolConfig config) {
        String name = config.getName();
        if (StringHelper.isEmpty(name))
            name = "nop-thread-executor";

        ThreadFactory factory = new NamedThreadFactory(name, config.isThreadDaemon());
        // ScheduledThreadPoolExecutor的maxPoolSize没有被使用
        config.setMaxPoolSize(0);

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(config.getCorePoolSize(), factory);
        // if (config.getMaxPoolSize() > 0) {
        // executor.setMaximumPoolSize(config.getMaxPoolSize());
        // }
        registerMetrics(executor, config);
        return executor;
    }

    public static ThreadPoolExecutor newThreadPoolExecutor(ThreadPoolConfig config) {
        String name = config.getName();
        if (StringHelper.isEmpty(name))
            name = "nop-thread-executor";

        ThreadFactory factory = new NamedThreadFactory(name, config.isThreadDaemon());

        ThreadPoolExecutor executor;
        if (config.getMaxPoolSize() < 0) {
            executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                    factory);
        } else {
            BlockingQueue<Runnable> queue = new TaskQueue(config.getQueueCapacity());

            executor = new StandardThreadPoolExecutor(config.getCorePoolSize(), config.getMaxPoolSize(),
                    config.getKeepAliveSeconds(), TimeUnit.SECONDS, queue, factory);
        }

        registerMetrics(executor, config);
        return executor;
    }

    public static ThreadPoolStats getStats(ThreadPoolExecutor executor) {
        ThreadPoolStats ret = new ThreadPoolStats();
        ret.setActiveCount(executor.getActiveCount());
        ret.setCorePoolSize(executor.getCorePoolSize());
        ret.setCompletedTaskCount(executor.getCompletedTaskCount());
        ret.setLargestPoolSize(executor.getLargestPoolSize());
        ret.setQueueSize(executor.getQueue().size());
        ret.setMaxPoolSize(executor.getMaximumPoolSize());
        return ret;
    }

    public static void updateThreadPool(ThreadPoolExecutor executor, ThreadPoolConfig config) {
        if (config.getMaxPoolSize() > 0 && config.getMaxPoolSize() != executor.getMaximumPoolSize()) {
            if (config.getMaxPoolSize() > executor.getCorePoolSize()) {
                // 如果是缩小线程池，需要先缩小corePoolSize才能缩小maxPoolSize
                executor.setCorePoolSize(config.getMaxPoolSize());
            }

            executor.setMaximumPoolSize(config.getMaxPoolSize());

            if (config.getCorePoolSize() > 0 && config.getCorePoolSize() != executor.getCorePoolSize()) {
                executor.setCorePoolSize(config.getCorePoolSize());
            }
        } else if (config.getCorePoolSize() > 0 && config.getCorePoolSize() != executor.getCorePoolSize()) {
            executor.setCorePoolSize(config.getCorePoolSize());
        }

        if (config.getKeepAliveSeconds() > 0
                && config.getKeepAliveSeconds() != executor.getKeepAliveTime(TimeUnit.SECONDS)) {
            executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);
        }
    }

    public static <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Future<?> f = executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    T result = task.call();
                    future.complete(result);
                    return result;
                } catch (Throwable e) {
                    LOG.error("nop.executor.execute-fail", e);
                    future.completeExceptionally(e);
                    throw e;
                }
            }
        });

        FutureHelper.bindCancel(future, f);
        return future;
    }

    public static <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, Runnable task, T result) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Future<?> f = executor.submit(new Callable<Object>() {
            @Override
            public Object call() {
                try {
                    task.run();
                    future.complete(result);
                    return result;
                } catch (Throwable e) {
                    LOG.error("nop.executor.execute-fail", e);
                    future.completeExceptionally(e);
                    throw e;
                }
            }
        });

        FutureHelper.bindCancel(future, f);
        return future;
    }

    public static <T> CompletableFuture<T> schedule(ScheduledThreadPoolExecutor executor, Callable<T> task, long delay,
                                                    TimeUnit timeUnit) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Future<?> f = executor.schedule(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    T result = task.call();
                    future.complete(result);
                    return result;
                } catch (Throwable e) {
                    LOG.error("nop.executor.execute-fail", e);
                    future.completeExceptionally(e);
                    throw e;
                }
            }
        }, delay, timeUnit);

        FutureHelper.bindCancel(future, f);
        return future;
    }

    public static void registerMetrics(ExecutorService executor, ThreadPoolConfig config) {
        if (config.isEnableMetrics() && !StringHelper.isEmpty(config.getName())) {
            MeterRegistry registry = GlobalMeterRegistry.instance();
            if (registry != null) {
                new ExecutorServiceMetrics(executor, config.getName(), Collections.emptyList()).bindTo(registry);
            }
        }
    }

    public static <T> CompletableFuture<T> scheduleWithRandomDelay(
            IScheduledExecutor executor, Runnable task, long initialDelay,
            long minDelay, long maxDelay,
            TimeUnit timeUnit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        AtomicReference<Future<?>> ref = new AtomicReference<>();
        Callable<Void> command = new Callable<>() {
            @Override
            public Void call() {
                try {
                    task.run();
                    ref.set(executor.schedule(this, MathHelper.random().nextLong(minDelay, maxDelay), timeUnit));
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
                return null;
            }
        };

        Future<?> f = executor.schedule(command, initialDelay, timeUnit);
        ref.set(f);

        future.exceptionally(ex -> {
            ref.get().cancel(false);
            return null;
        });
        return future;
    }
}
