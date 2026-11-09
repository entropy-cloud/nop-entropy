/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE;
import static io.nop.commons.CommonErrors.ARG_MAX_POOL_SIZE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ARG_PARAM_NAME;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_CACHED_POOL_SATURATED;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM;

public class ExecutorHelper {
    static volatile Executor SYNC_EXECUTOR = task -> task.run();
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
        String name = executorName(config);
        // scheduled形态的容量旋钮唯一为corePoolSize（JDK ScheduledThreadPoolExecutor固有：maximumPoolSize
        // 是惰性展示字段，不生效）。corePoolSize必须>=1：STPE core=0永远不会执行任务，等同于静默死配置
        checkConfigParam(config.getCorePoolSize() >= 1, name, "corePoolSize", config.getCorePoolSize());
        // maxPoolSize为惰性展示字段：不校验、不改写（不得修改调用方持有的config对象）

        ThreadFactory factory = new NamedThreadFactory(name, config.isThreadDaemon());

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(config.getCorePoolSize(), factory);
        // C6-004（plan 2548）：指标注册移交给池实例（DefaultScheduledExecutor.init）持有句柄，destroy 时注销
        return executor;
    }

    public static ThreadPoolExecutor newThreadPoolExecutor(ThreadPoolConfig config) {
        String name = executorName(config);
        ThreadFactory factory = new NamedThreadFactory(name, config.isThreadDaemon());

        ThreadPoolExecutor executor;
        if (config.getMaxPoolSize() < 0) {
            // 显式cached形态（maxPoolSize<0是平台唯一的cached表达形）：core=0、SynchronousQueue、keepAlive=60s。
            // config的corePoolSize/queueCapacity在该分支不适用（cached语义固有）。
            // 上界=nop.commons.concurrent.cached-pool.maxPoolSize（构造期读取）：无上界cached形态自平台废除，
            // 达界提交获得显式拒绝信号（熔断可见于进程濒死之前）
            int maxPoolSize = CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE.get();
            executor = new ThreadPoolExecutor(0, maxPoolSize, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                    factory, new CachedPoolRejectHandler(name, maxPoolSize));
        } else {
            // Standard形态：core<=max是JDK构造不变量，queueCapacity>=1否则队列构造深爆裸IAE
            checkConfigParam(config.getCorePoolSize() >= 0, name, "corePoolSize", config.getCorePoolSize());
            checkConfigParam(config.getMaxPoolSize() != 0, name, "maxPoolSize", config.getMaxPoolSize());
            checkConfigParam(config.getMaxPoolSize() >= config.getCorePoolSize(), name, "maxPoolSize",
                    config.getMaxPoolSize());
            checkConfigParam(config.getQueueCapacity() >= 1, name, "queueCapacity", config.getQueueCapacity());
            if (config.getQueueCapacity() == Integer.MAX_VALUE) {
                LOG.warn("nop.warn.commons.thread-pool.unbounded-queue:pool={} queueCapacity=Integer.MAX_VALUE, "
                        + "back-pressure is disabled for this pool", name);
            }

            BlockingQueue<Runnable> queue = new TaskQueue(config.getQueueCapacity());

            executor = new StandardThreadPoolExecutor(config.getCorePoolSize(), config.getMaxPoolSize(),
                    config.getKeepAliveSeconds(), TimeUnit.SECONDS, queue, factory);
        }

        // C6-004（plan 2548）：指标注册移交给池实例（DefaultThreadPoolExecutor.init）持有句柄，destroy 时注销
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

    /**
     * 热更线程池尺寸。JDK不变量{@code corePoolSize <= maximumPoolSize}必须在每次setter之后成立
     * （违反即裸IAE），因此调整顺序为：真缩容先降core消化超额线程（JDK语义：空闲线程优雅终止，
     * 不杀在飞任务），再降max；放大先升max再对齐core（不预抬core，避免线程创建-销毁抖动）。
     * cached形态（maxPoolSize<0）的core/max为构造期固定（0/配置上界），热更不调整尺寸。
     * keepAliveSeconds>0且变化时对齐。
     */
    public static void updateThreadPool(ThreadPoolExecutor executor, ThreadPoolConfig config) {
        int targetCore = config.getCorePoolSize();
        int targetMax = config.getMaxPoolSize();

        if (targetMax > 0) {
            if (targetMax < executor.getCorePoolSize()) {
                // 真缩容：先降core才能降max（core<=max不变量在每次setter后成立）
                executor.setCorePoolSize(targetMax);
            }
            if (targetMax != executor.getMaximumPoolSize()) {
                executor.setMaximumPoolSize(targetMax);
            }
            if (targetCore != executor.getCorePoolSize()) {
                executor.setCorePoolSize(targetCore);
            }
        } else if (targetMax == 0 && targetCore > 0 && targetCore != executor.getCorePoolSize()) {
            executor.setCorePoolSize(targetCore);
        }

        if (config.getKeepAliveSeconds() > 0
                && config.getKeepAliveSeconds() != executor.getKeepAliveTime(TimeUnit.SECONDS)) {
            executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);
        }
    }

    private static String executorName(ThreadPoolConfig config) {
        String name = config.getName();
        if (StringHelper.isEmpty(name))
            name = "nop-thread-executor";
        return name;
    }

    private static void checkConfigParam(boolean valid, String poolName, String paramName, Object value) {
        if (!valid) {
            throw new NopException(ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM).param(ARG_NAME, poolName)
                    .param(ARG_PARAM_NAME, paramName).param(ARG_VALUE, value);
        }
    }

    /**
     * cached形态池的达界拒绝handler（Abort语义：不排队不丢弃），抛出携带注册错误键
     * {@code nop.err.commons.concurrent.cached-pool-saturated}与池名/上界的
     * {@link RejectedExecutionException}——Executor.execute的JDK契约通道是REE，
     * 熔断信号必须在进程濒死之前可见。
     */
    static class CachedPoolRejectHandler implements RejectedExecutionHandler {
        private final String poolName;
        private final int maxPoolSize;

        CachedPoolRejectHandler(String poolName, int maxPoolSize) {
            this.poolName = poolName;
            this.maxPoolSize = maxPoolSize;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            throw new RejectedExecutionException(new NopException(ERR_CONCURRENT_CACHED_POOL_SATURATED)
                    .param(ARG_NAME, poolName).param(ARG_MAX_POOL_SIZE, maxPoolSize).getMessage());
        }
    }

    public static <T> CompletableFuture<T> submit(ExecutorService executor, Callable<T> task) {
        return submitTracked(executor, task, null);
    }

    /**
     * C6-001（plan 2548）：带「待结算登记表」的提交入口。future 在提交前登记到 {@code pending}，完结（正常 /
     * 异常 / 取消）时自动摘除；底层 {@code executor.submit} 抛 REE 时先摘除再原样抛出（不泄漏）。
     * {@code pending == null} 等价于不登记（原语义）。destroy 侧由持有该表的执行器统一终态化。
     */
    public static <T> CompletableFuture<T> submitTracked(ExecutorService executor, Callable<T> task,
                                                         Set<CompletableFuture<?>> pending) {
        CompletableFuture<T> future = new CompletableFuture<>();
        trackPending(pending, future);
        Future<?> f;
        try {
            f = executor.submit(new Callable<Object>() {
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
                    } finally {
                        // 确保ThreadLocal上的IContext被清除
                        ContextProvider.instance().detachContext();
                        // C3-016：complete之后清除真取消路径注入的残留中断标志，不依赖载体TPE
                        // getTask吸收中断（shutdownNow窗口内的中断被清除可接受：worker退出由队列排空保证）
                        Thread.interrupted();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            untrackPending(pending, future);
            throw e;
        }

        FutureHelper.bindCancel(future, f);
        return future;
    }

    public static <T> CompletableFuture<T> submit(ExecutorService executor, Runnable task, T result) {
        return submitTracked(executor, task, result, null);
    }

    /**
     * C6-001（plan 2548）：同 {@link #submitTracked(ExecutorService, Callable, Set)} 的 Runnable 形态
     * （4 参，与 Callable 版 3 参不同 arity，避免重载解析歧义）。
     */
    public static <T> CompletableFuture<T> submitTracked(ExecutorService executor, Runnable task, T result,
                                                         Set<CompletableFuture<?>> pending) {
        CompletableFuture<T> future = new CompletableFuture<>();
        trackPending(pending, future);
        Future<?> f;
        try {
            f = executor.submit(new Callable<Object>() {
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
                    } finally {
                        // 确保ThreadLocal上的IContext被清除
                        ContextProvider.instance().detachContext();
                        // C3-016：同上，清除残留中断标志
                        Thread.interrupted();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            untrackPending(pending, future);
            throw e;
        }

        FutureHelper.bindCancel(future, f);
        return future;
    }

    public static <T> CompletableFuture<T> schedule(ScheduledThreadPoolExecutor executor, Callable<T> task, long delay,
                                                    TimeUnit timeUnit) {
        return scheduleTracked(executor, task, delay, timeUnit, null);
    }

    /**
     * C6-001（plan 2548）：同 {@link #submitTracked(ExecutorService, Callable, Set)} 的定时形态。
     */
    public static <T> CompletableFuture<T> scheduleTracked(ScheduledThreadPoolExecutor executor, Callable<T> task,
                                                           long delay, TimeUnit timeUnit,
                                                           Set<CompletableFuture<?>> pending) {
        CompletableFuture<T> future = new CompletableFuture<>();
        trackPending(pending, future);
        Future<?> f;
        try {
            f = executor.schedule(new Callable<Object>() {
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
                    } finally {
                        // 确保ThreadLocal上的IContext被清除
                        ContextProvider.instance().detachContext();
                        // C3-016：同上，清除残留中断标志
                        Thread.interrupted();
                    }
                }
            }, delay, timeUnit);
        } catch (RejectedExecutionException e) {
            untrackPending(pending, future);
            throw e;
        }

        FutureHelper.bindCancel(future, f);
        return future;
    }

    private static void trackPending(Set<CompletableFuture<?>> pending, CompletableFuture<?> future) {
        if (pending == null)
            return;
        pending.add(future);
        future.whenComplete((result, error) -> pending.remove(future));
    }

    private static void untrackPending(Set<CompletableFuture<?>> pending, CompletableFuture<?> future) {
        if (pending == null)
            return;
        pending.remove(future);
    }

    /**
     * C6-004（plan 2548 裁定 C）：指标绑定注销对称。注册后返回 {@link AutoCloseable} 句柄，由池实例持有，
     * {@code destroy()} 时 {@code close()} 注销本次绑定的全部 meter。返回值永不为 null
     * （未启用指标或无 registry 时为 no-op 句柄）。不做「换 registry 自动重绑」（R-G 范围）。
     */
    public static AutoCloseable registerMetrics(ExecutorService executor, ThreadPoolConfig config) {
        if (!(config.isEnableMetrics() && !StringHelper.isEmpty(config.getName()))) {
            return () -> {
            };
        }
        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry == null) {
            return () -> {
            };
        }
        Set<Meter> before = new HashSet<>(registry.getMeters());
        new ExecutorServiceMetrics(executor, config.getName(), Collections.emptyList()).bindTo(registry);
        List<Meter> added = new ArrayList<>();
        for (Meter meter : registry.getMeters()) {
            if (!before.contains(meter))
                added.add(meter);
        }
        return () -> {
            for (Meter meter : added) {
                registry.remove(meter);
            }
        };
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

    public static CompletableFuture<Void> throttleExecute(Executor executor, Semaphore semaphore, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        semaphore.acquireUninterruptibly();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable e) {
                    // catch宽度为Throwable（C3-005）：任务体抛Error也必须结算future，否则调用方
                    // syncGet(waitAll)无超时挂死（与submit/schedule的Throwable样本对齐）
                    future.completeExceptionally(e);
                } finally {
                    semaphore.release();
                }
            });
        } catch (Throwable e) {
            semaphore.release();
            future.completeExceptionally(e);
        }
        return future;
    }
}
