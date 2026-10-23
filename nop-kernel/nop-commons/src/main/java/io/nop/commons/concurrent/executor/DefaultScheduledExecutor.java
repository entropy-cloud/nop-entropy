/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import jakarta.annotation.PostConstruct;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.nop.commons.CommonErrors.ERR_SERVICE_NOT_ACTIVE;

public class DefaultScheduledExecutor implements IScheduledExecutor {
    private ScheduledThreadPoolExecutor executor;

    private ThreadPoolConfig config;

    private boolean enabled = true;

    /**
     * 尚无终态的绝缘周期链守卫登记表：timer 销毁时统一走拒绝终态（观测 + listener + future 结算），
     * 保证「future 有终态、不悬挂、链不复活」。链结算（失败/取消/拒绝）时自动移除。
     */
    private final Set<InsulatedPeriodicTask> periodicTasks = ConcurrentHashMap.newKeySet();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static DefaultScheduledExecutor newSingleThreadTimer(String threadName) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setName(threadName);

        DefaultScheduledExecutor executor = new DefaultScheduledExecutor();
        executor.setConfig(config);
        executor.init();
        return executor;
    }

    public String getName() {
        return config.getName();
    }

    public ThreadPoolConfig getConfig() {
        return config;
    }

    public void setConfig(ThreadPoolConfig config) {
        this.config = config;
    }

    @Override
    public void refreshConfig() {
        if (config.getMaxPoolSize() > 0 && config.getCorePoolSize() > config.getMaxPoolSize()) {
            config.setCorePoolSize(config.getMaxPoolSize());
        }

        if (this.config != null && this.executor != null) {
            ExecutorHelper.updateThreadPool(executor, config);
        }
    }

    @PostConstruct
    public void init() {
        Guard.checkState(executor == null);
        if (!enabled) {
            return;
        }
        if (config == null)
            config = new ThreadPoolConfig();
        this.executor = ExecutorHelper.newScheduledExecutor(config);
    }

    @Override
    public ThreadPoolStats stats() {
        return ExecutorHelper.getStats(executor);
    }

    public void destroy() {
        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        // timer 销毁时给所有尚无终态的绝缘链统一走拒绝终态：底层一次性触发器被 shutdownNow
        // 批量取消后不会向包装 future 传播完结，这里统一收口（关闭竞态下 future 有终态、不悬挂）
        for (InsulatedPeriodicTask guard : periodicTasks) {
            guard.rejectChain(new NopException(ERR_SERVICE_NOT_ACTIVE).param("service", "DefaultScheduledExecutor"));
        }
        periodicTasks.clear();
    }

    /**
     * 登记一条绝缘周期链的守卫，链结算时自动移除。供绝缘核心（本类周期方法与 PeriodicTaskHelper）在
     * timer 为本实现时登记，使 destroy() 能够以统一拒绝路径覆盖「销毁时刻仍在等待触发的轮次」。
     */
    void trackPeriodicTask(InsulatedPeriodicTask guard) {
        periodicTasks.add(guard);
        InsulatedPeriodicChain chain = guard.chain();
        if (chain != null)
            chain.whenComplete((result, e) -> periodicTasks.remove(guard));
    }

    @Override
    public boolean isDestroyed() {
        return executor == null || executor.isShutdown();
    }

    private void checkEnabled() {
        if (executor == null) {
            throw new NopException(ERR_SERVICE_NOT_ACTIVE)
                    .param("service", "DefaultScheduledExecutor");
        }
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        checkEnabled();
        return ExecutorHelper.submit(executor, task);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        checkEnabled();
        return ExecutorHelper.submit(executor, task, result);
    }

    @Override
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        checkEnabled();
        return ExecutorHelper.schedule(executor, callable, delay, unit);
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkEnabled();
        return scheduleAtFixedRate(command, initialDelay, period, unit, getName(), null);
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit,
                                         String taskName, IPeriodicTaskListener listener) {
        checkEnabled();
        // 直接接线底层 JDK 原生周期机制（不得经由本类的四参方法，否则守卫双层包装）：
        // 守卫保证任务体 Throwable 不逃逸，JDK 的「异常抑制后续执行」行为被结构性绕开，
        // 原生周期本身成为重臂机制；fixedRate 速率契约不被失败改写
        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask(taskName, command, listener, chain);
        try {
            Future<?> handle = executor.scheduleAtFixedRate(guard, initialDelay, period, unit);
            chain.setNativeHandle(handle);
            PeriodicTaskHelper.watchNativeHandle(chain, handle);
        } catch (Throwable e) {
            guard.rejectChain(e);
        }
        trackPeriodicTask(guard);
        return chain;
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        checkEnabled();
        return scheduleWithFixedDelay(command, initialDelay, delay, unit, getName(), null);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                            String taskName, IPeriodicTaskListener listener) {
        checkEnabled();
        // fixedDelay 绝缘核心：逐轮一次性调度 + 失败感知重臂（含退避），任务体运行在 timer 线程
        return PeriodicTaskHelper.scheduleWithFixedDelay(this, this, taskName, command, initialDelay, delay, unit,
                listener);
    }

    @Override
    public void execute(Runnable command) {
        checkEnabled();
        executor.execute(command);
    }
}