/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE;

@GlobalInstance
public class GlobalExecutors {
    public static final String GLOBAL_TIMER = "nop-global-timer";
    public static final String CACHED_THREAD_POOL = "nop-cached-thread-pool";

    public static final String GLOBAL_WORKER = "nop-global-worker";

    private static final Map<String, IThreadPoolExecutor> g_executors = new ConcurrentHashMap<>();

    public static Map<String, IThreadPoolExecutor> allExecutors() {
        return g_executors;
    }

    public static IScheduledExecutor globalTimer() {
        return (IScheduledExecutor) g_executors.computeIfAbsent(GLOBAL_TIMER, key -> {
            DefaultScheduledExecutor timer = new DefaultScheduledExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(GLOBAL_TIMER);
            config.setThreadDaemon(true);
            config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.get());
            timer.setConfig(config);
            timer.init();
            return timer;
        });
    }

    public static IThreadPoolExecutor cachedThreadPool() {
        return g_executors.computeIfAbsent(CACHED_THREAD_POOL, key -> {
            DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(CACHED_THREAD_POOL);
            config.setThreadDaemon(true);
            config.setMaxPoolSize(-1); // 不限制线程池大小
            config.setQueueCapacity(0);
            executor.setConfig(config);
            executor.init();
            return executor;
        });
    }

    public static IThreadPoolExecutor globalWorker() {
        return g_executors.computeIfAbsent(GLOBAL_WORKER, key -> {
            DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setName(GLOBAL_WORKER);
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