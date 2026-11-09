/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE;
import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_UNKNOWN_THREAD_POOL;

@GlobalInstance
public class GlobalExecutors {
    static final Logger LOG = LoggerFactory.getLogger(GlobalExecutors.class);

    public static final String NOP_GLOBAL_TIMER = "nop-global-timer";
    public static final String NOP_CACHED_THREAD_POOL = "nop-cached-thread-pool";

    public static final String NOP_GLOBAL_WORKER = "nop-global-worker";

    public static final String NOP_VIRTUAL_THREAD = "nop-virtual-thread";

    public static final String NOP_SYNC_EXECUTOR = "nop-sync-executor";

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
            // STPE容量=corePoolSize（maxPoolSize是惰性展示字段，保留默认20为展示值，不再设置）。
            // 容量配置驱动：新键 nop.commons.concurrent.global-timer.corePoolSize（默认4=历史实际生效容量）
            config.setCorePoolSize(resolveGlobalTimerCorePoolSize());
            timer.setConfig(config);
            timer.init();
            return timer;
        });
    }

    /**
     * globalTimer容量解析（C4-001死配置修复）：新键为权威配置；仅当废弃旧键
     * {@code global-timer.maxPoolSize}被显式配置且新键未配置时回读旧值并记单次WARN
     * （globalTimer单例构造期执行一次，不存在"配了不认"的静默断档）。两者皆未配置返回新键默认4。
     */
    static int resolveGlobalTimerCorePoolSize() {
        Object legacy = AppConfig.var(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.getName());
        if (legacy == null)
            return CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.get();

        if (AppConfig.var(CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.getName()) == null) {
            LOG.warn("nop.warn.commons.global-timer.legacy-max-pool-size: config key '{}' is deprecated, "
                    + "use '{}' instead. Legacy value {} is applied as the timer corePoolSize for now",
                    CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.getName(),
                    CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.getName(), legacy);
            return Integer.parseInt(legacy.toString());
        }
        return CFG_CONCURRENT_GLOBAL_TIMER_CORE_POOL_SIZE.get();
    }

    public static IThreadPoolExecutor getExecutor(String name) {
        IThreadPoolExecutor executor = g_executors.get(name);
        if (executor == null) {
            if (NOP_GLOBAL_TIMER.equals(name))
                return globalTimer();
            if (NOP_CACHED_THREAD_POOL.equals(name))
                return cachedThreadPool();
            if (NOP_GLOBAL_WORKER.equals(name))
                return globalWorker();
        }
        return executor;
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

    public static IThreadPoolExecutor syncExecutor() {
        return SyncThreadPoolExecutor.INSTANCE;
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

    /**
     * C4-005（plan 2548）：平台池唯一受支持的销毁入口——先从注册表移除再 destroy（原子序）。
     * 消除「借用方 destroy 后注册表仍持死实例 → 后续 getExecutor 返回已销毁对象」的 use-after-destroy 面。
     * residual：借用方绕过本入口直调 {@code executor.destroy()} 仍会留下死实例（watch-only，owner doc 声明）。
     */
    public static IThreadPoolExecutor destroyAndUnregister(String name) {
        IThreadPoolExecutor executor = g_executors.remove(name);
        if (executor != null) {
            executor.destroy();
        }
        return executor;
    }

    /**
     * C6-003（plan 2548）：平台级关闭入口——遍历注册表逐个 unregister + destroy。
     * 宿主（nop-boot）接线归 roadmap 8.55（R4-15），本批只提供 API。
     */
    public static void destroyAll() {
        for (String name : g_executors.keySet().toArray(new String[0])) {
            destroyAndUnregister(name);
        }
    }
}