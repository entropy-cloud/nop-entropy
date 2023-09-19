/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.Guard;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

public class DefaultThreadPoolExecutor implements IThreadPoolExecutor {
    private ThreadPoolConfig config;
    private ThreadPoolExecutor executor;

    public ThreadPoolConfig getConfig() {
        return config;
    }

    public void setConfig(ThreadPoolConfig config) {
        this.config = config;
    }

    public static DefaultThreadPoolExecutor newExecutor(String threadName, int poolSize, int queueSize) {
        return newExecutor(threadName, poolSize, queueSize, false);
    }

    public static DefaultThreadPoolExecutor newExecutor(String threadName, int poolSize, int queueSize,
                                                        boolean daemon) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setCorePoolSize(poolSize);
        config.setMaxPoolSize(poolSize);
        config.setName(threadName);
        config.setThreadDaemon(daemon);
        config.setQueueCapacity(queueSize);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();
        return executor;
    }

    public String getName() {
        return config.getName();
    }

    @Override
    public void refreshConfig() {
        if (config.getMaxPoolSize() > 0 && config.getCorePoolSize() > config.getMaxPoolSize()) {
            config.setCorePoolSize(config.getMaxPoolSize());
        }

        if (executor != null) {
            ExecutorHelper.updateThreadPool(executor, config);
        }
    }

    @PostConstruct
    public void init() {
        Guard.checkState(executor == null);
        if (config == null)
            config = new ThreadPoolConfig();
        this.executor = ExecutorHelper.newThreadPoolExecutor(config);
    }

    @Override
    public ThreadPoolStats stats() {
        return ExecutorHelper.getStats(executor);
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return ExecutorHelper.submit(executor, callable);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return ExecutorHelper.submit(executor, task, result);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}