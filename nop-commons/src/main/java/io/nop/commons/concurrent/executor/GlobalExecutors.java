/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_UNKNOWN_THREAD_POOL;

@GlobalInstance
public class GlobalExecutors {
    public static final String NOP_GLOBAL_TIMER = "nop-global-timer";
    public static final String NOP_CACHED_THREAD_POOL = "nop-cached-thread-pool";

    public static final String NOP_GLOBAL_WORKER = "nop-global-worker";

    public static final String NOP_VIRTUAL_THREAD = "nop-virtual-thread";

    private static final Map<String, IThreadPoolExecutor> g_executors = new ConcurrentHashMap<>();

    public static Map<String, IThreadPoolExecutor> allExecutors() {
        return g_executors;
    }

    public static IScheduledExecutor globalTimer() {
        return (IScheduledExecutor) g_executors.computeIfAbsent(NOP_GLOBAL_TIMER, key -> {
            DefaultScheduledExecutor timer = new DefaultScheduledExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(NOP_GLOBAL_TIMER);
            config.setThreadDaemon(true);
            config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.get());
            timer.setConfig(config);
            timer.init();
            return timer;
        });
    }

    public static IThreadPoolExecutor getExecutor(String name) {
        return g_executors.get(name);
    }

    public static IThreadPoolExecutor requireExecutor(String name) {
        IThreadPoolExecutor executor = getExecutor(name);
        if (executor == null)
            throw new NopException(ERR_CONCURRENT_UNKNOWN_THREAD_POOL).param(ARG_NAME, name);
        return executor;
    }

    public static IThreadPoolExecutor cachedThreadPool() {
        return g_executors.computeIfAbsent(NOP_CACHED_THREAD_POOL, key -> {
            DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(NOP_CACHED_THREAD_POOL);
            config.setThreadDaemon(true);
            config.setMaxPoolSize(-1); // 不限制线程池大小
            config.setQueueCapacity(0);
            executor.setConfig(config);
            executor.init();
            return executor;
        });
    }

    public static IThreadPoolExecutor globalWorker() {
        return g_executors.computeIfAbsent(NOP_GLOBAL_WORKER, key -> {
            DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(NOP_GLOBAL_WORKER);
            config.setThreadDaemon(true);
            config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE.get());
            executor.setConfig(config);
            executor.init();
            return executor;
        });
    }

    public static void register(IThreadPoolExecutor executor) {
        IThreadPoolExecutor old = g_executors.put(executor.getName(), executor);
        if (old != null) {
            old.destroy();
        }
    }

    public static IThreadPoolExecutor unregister(String name) {
        return g_executors.remove(name);
    }
}