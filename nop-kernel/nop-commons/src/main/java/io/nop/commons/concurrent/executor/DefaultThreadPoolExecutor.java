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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ARG_PARAM_NAME;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM;

public class DefaultThreadPoolExecutor implements IThreadPoolExecutor {
    static final Logger LOG = LoggerFactory.getLogger(DefaultThreadPoolExecutor.class);

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
        // 工厂入口fail-fast（C4-003）：poolSize>=1且queueSize>=1。负值不再被静默解释为
        // 0-core cached语义（显式cached形态只能经配置重载的maxPoolSize<0表达），
        // 非法值在构造期类型化报错，不再深爆裸IAE
        checkParam(poolSize >= 1, threadName, "poolSize", poolSize);
        checkParam(queueSize >= 1, threadName, "queueSize", queueSize);

        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setCorePoolSize(poolSize);
        config.setMaxPoolSize(poolSize);
        config.setName(threadName);
        config.setThreadDaemon(daemon);
        config.setQueueCapacity(queueSize);

        return newExecutor(config);
    }

    public static DefaultThreadPoolExecutor newExecutor(ThreadPoolConfig config) {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();
        return executor;
    }

    private static void checkParam(boolean valid, String threadName, String paramName, Object value) {
        if (!valid) {
            throw new NopException(ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM)
                    .param(ARG_NAME, threadName).param(ARG_PARAM_NAME, paramName).param(ARG_VALUE, value);
        }
    }

    public String getName() {
        return config.getName();
    }

    @Override
    public void refreshConfig() {
        // core>max热更输入：钳制而非抛异常（热更派发链不应被单次配置错误中断），但必须显式告示
        if (config.getMaxPoolSize() > 0 && config.getCorePoolSize() > config.getMaxPoolSize()) {
            LOG.warn("nop.warn.commons.thread-pool.config-clamped:pool={} corePoolSize={} is clamped to maxPoolSize={}",
                    config.getName(), config.getCorePoolSize(), config.getMaxPoolSize());
            config.setCorePoolSize(config.getMaxPoolSize());
        }

        if (executor != null) {
            try {
                ExecutorHelper.updateThreadPool(executor, config);
            } catch (IllegalArgumentException e) {
                // 纵深防御：正确排序下不应触达，捕获裸IAE转类型化异常，保证热更违例带诊断上下文
                throw new NopException(ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM, e)
                        .param(ARG_NAME, getName()).param(ARG_PARAM_NAME, "corePoolSize/maxPoolSize")
                        .param(ARG_VALUE, config.getCorePoolSize() + "/" + config.getMaxPoolSize());
            }
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