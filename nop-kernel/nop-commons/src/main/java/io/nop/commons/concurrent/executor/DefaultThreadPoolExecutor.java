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
import java.util.concurrent.TimeUnit;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ARG_PARAM_NAME;
import java.util.concurrent.RejectedExecutionException;

import static io.nop.commons.CommonErrors.ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_THREAD_POOL_NOT_INITIALIZED;
import static io.nop.commons.CommonErrors.ERR_SERVICE_NOT_ACTIVE;

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

    /**
     * C6-004（plan 2548）：本池绑定的 meter 句柄，destroy 时 close 注销（绑定/注销对称）。
     */
    private volatile AutoCloseable metricsHandle;

    /**
     * destroy 幂等契约（close-then-close 场景）：已销毁后重复 destroy 为无害 no-op；
     * NOT_INITIALIZED 类型化拒绝仅适用于 execute/submit 等使用路径（snapshotExecutor）。
     */
    private volatile boolean destroyed;

    @PostConstruct
    public void init() {
        Guard.checkState(executor == null);
        if (config == null)
            config = new ThreadPoolConfig();
        this.executor = ExecutorHelper.newThreadPoolExecutor(config);
        this.metricsHandle = ExecutorHelper.registerMetrics(this.executor, config);
        this.destroyed = false;
    }

    @Override
    public ThreadPoolStats stats() {
        // destroy 后返回 null 终态（观测契约：DbToolTerminationHelper.waitTerminated 以
        // stats==null 判定已停并结束等待——观测面不得抛 typed，否则等待循环被异常打断）。
        // 类型化拒绝仅保留在服务路径 execute/submit（snapshotExecutor）。
        ThreadPoolExecutor pool = this.executor;
        if (pool == null || this.destroyed)
            return null;
        return ExecutorHelper.getStats(pool);
    }

    /**
     * destroy 置空竞态的类型化收敛（与姊妹类 {@link DefaultScheduledExecutor#snapshotExecutor} 同型）：
     * 一次快照读消除判空与解引用之间的 destroy 置空窗——快照为 null 抛类型化
     * {@code ERR_SERVICE_NOT_ACTIVE}，不再裸 NPE。
     */
    private ThreadPoolExecutor snapshotExecutor() {
        ThreadPoolExecutor exec = this.executor;
        if (exec == null) {
            throw new NopException(ERR_SERVICE_NOT_ACTIVE)
                    .param(ARG_NAME, config == null ? null : config.getName());
        }
        return exec;
    }

    /**
     * 与快照窗口内 shutdown 竞态的类型化收敛：仅当快照代际确已 shutdown 时把 REE 转类型化
     * {@code ERR_SERVICE_NOT_ACTIVE}（cause 保留原 REE）。活代际上的 REE（如 cached 形态的
     * 饱和熔断 {@code CachedPoolRejectHandler}）必须保持 REE 原样穿透——Executor.execute 的
     * JDK 契约通道是 REE，熔断信号不允许被改写。
     */
    private RuntimeException adaptRejected(RejectedExecutionException e, ThreadPoolExecutor exec) {
        if (exec.isShutdown())
            return new NopException(ERR_SERVICE_NOT_ACTIVE, e)
                    .param(ARG_NAME, config == null ? null : config.getName());
        return e;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        ThreadPoolExecutor exec = snapshotExecutor();
        try {
            return ExecutorHelper.submit(exec, callable);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e, exec);
        }
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        ThreadPoolExecutor exec = snapshotExecutor();
        try {
            return ExecutorHelper.submit(exec, task, result);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e, exec);
        }
    }

    /**
     * 1.7-C4-002（plan 2547 裁定 D）：
     * <ul>
     *   <li><b>init 前 null 守卫</b>：原实现直接 {@code executor.shutdown()}，未调用 {@code init()}
     *       时抛裸 NPE——改为类型化异常。</li>
     *   <li><b>配置项生效</b>：{@code waitForTasksToCompleteOnShutdown} 与
     *       {@code awaitTerminationSeconds} 从死值变为生效。**仅当 二者同时满足（true && >0）
     *       才有界等待**；否则保持原 {@code shutdown()} 语义——避免 {@code awaitTerminationSeconds=0}
     *       退化成「shutdown + 立即 shutdownNow」而更激进地中断在飞任务。</li>
     * </ul>
     */
    @Override
    public void destroy() {
        ThreadPoolExecutor pool = this.executor;
        if (pool == null)
            return; // 未初始化/已销毁：destroy 为无害 no-op（close 幂等契约）；NOT_INITIALIZED 类型化拒绝仅适用于 execute/submit（snapshotExecutor）
        this.destroyed = true;

        // C6-004：先置空提交路径并注销本池的 meter（绑定/注销对称），再走池关闭流程
        AutoCloseable handle = this.metricsHandle;
        this.metricsHandle = null;
        this.executor = null;
        try {
            if (handle != null)
                handle.close();
        } catch (Exception e) {
            LOG.warn("nop.warn.commons.thread-pool.metrics-close-fail", e);
        }

        boolean waitForCompletion = config != null
                && config.isWaitForTasksToCompleteOnShutdown()
                && config.getAwaitTerminationSeconds() > 0;

        if (!waitForCompletion) {
            pool.shutdown();
            return;
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(config.getAwaitTerminationSeconds(), TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void execute(Runnable command) {
        // 1.7-C3-013（plan 2547 裁定 F）：execute 直通原先不做上下文卫生，池线程上的
        // getOrCreateContext 创建的 IContext 会永久驻留在 worker 线程（跨任务泄漏）。
        // 补与 ExecutorHelper.submit/schedule 一致的 finally detach（唯一行为改动，理由=已确认泄漏面）。
        ThreadPoolExecutor exec = snapshotExecutor();
        try {
            exec.execute(() -> {
                try {
                    command.run();
                } finally {
                    // 仅 detach，不清中断标志——周期任务的绝缘守卫依赖中断/取消标志的传递，
                    // 清理中断会改变其语义（执行期勘误 E-2547-4）。
                    io.nop.api.core.context.ContextProvider.instance().detachContext();
                }
            });
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e, exec);
        }
    }
}