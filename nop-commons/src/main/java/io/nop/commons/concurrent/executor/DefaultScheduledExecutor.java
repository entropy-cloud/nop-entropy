/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.Guard;
import jakarta.annotation.PostConstruct;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DefaultScheduledExecutor implements IScheduledExecutor {
    private ScheduledThreadPoolExecutor executor;

    private ThreadPoolConfig config;

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

        if (this.config != null || this.executor != null) {
            ExecutorHelper.updateThreadPool(executor, config);
        }
    }

    @PostConstruct
    public void init() {
        Guard.checkState(executor == null);
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
            executor.shutdown();
            executor = null;
        }
    }

    @Override
    public boolean isDestroyed() {
        return executor == null || executor.isShutdown();
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return ExecutorHelper.submit(executor, task);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return ExecutorHelper.submit(executor, task, result);
    }

    @Override
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return ExecutorHelper.schedule(executor, callable, delay, unit);
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}