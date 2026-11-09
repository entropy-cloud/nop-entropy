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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ARG_PARAM_NAME;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM;
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

    /**
     * C6-001（plan 2548）：尚无终态的一次性提交 future 登记表。submit/schedule 经
     * {@code ExecutorHelper.*Tracked} 登记至此，完结时自动摘除；destroy() 对残余项统一异常终态，
     * 消除「排队任务被丢弃 → 调用方 future 永不完成」的悬挂面。
     */
    private final Set<CompletableFuture<?>> pendingFutures = ConcurrentHashMap.newKeySet();

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
        // scheduled形态专用热更路径（scheduled容量旋钮唯一为corePoolSize）：不经通用updateThreadPool、
        // 无max钳制——STPE的maxPoolSize是惰性展示字段，若套用通用路径的max比较分支，
        // core热增值大于惰性默认max时会被静默钳回（复刻死配置形态）。
        // 本路径与periodicTasks登记表、destroy()拒绝路径无交互（不枚举、不增删守卫登记）
        // C1-003 同型读点快照化：判空与解引用之间不再存在 destroy 置空窗（行为零变化：null 快照静默 return 保持）
        ScheduledThreadPoolExecutor exec = this.executor;
        if (this.config == null || exec == null)
            return;

        int core = config.getCorePoolSize();
        // STPE core<=0无合法钳制目标（core=0的STPE永不执行任务=静默死配置），失败前置优于静默永不执行
        if (core < 1) {
            throw new NopException(ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM)
                    .param(ARG_NAME, getName()).param(ARG_PARAM_NAME, "corePoolSize").param(ARG_VALUE, core);
        }

        try {
            if (core != exec.getCorePoolSize()) {
                exec.setCorePoolSize(core);
            }
        } catch (IllegalArgumentException e) {
            throw new NopException(ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM, e)
                    .param(ARG_NAME, getName()).param(ARG_PARAM_NAME, "corePoolSize").param(ARG_VALUE, core);
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
        // C1-003 七读点快照化：destroy 置空后 stats 取类型化终态而非裸 NPE
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        return ExecutorHelper.getStats(exec);
    }

    public void destroy() {
        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        // timer 销毁时给所有尚无终态的绝缘链统一走拒绝终态：底层一次性触发器被 shutdownNow
        // 批量取消后不会向包装 future 传播完结，这里统一收口（关闭竞态下 future 有终态、不悬挂）。
        // C6-001 顺序约束（plan 2548 §四.1）：必须先于 pendingFutures 结算——链先获得拒绝终态后，
        // watchNativeHandle 的 !chain.isDone() 守卫短路，避免 listener 双触发。
        for (InsulatedPeriodicTask guard : periodicTasks) {
            guard.rejectChain(new NopException(ERR_SERVICE_NOT_ACTIVE).param("service", "DefaultScheduledExecutor"));
        }
        periodicTasks.clear();
        // C6-001：对尚无终态的一次性提交 future 统一异常终态（排队任务已被 shutdownNow 丢弃、
        // 其 wrapper 永不执行；completeExceptionally 幂等，与在飞任务并发安全——在飞任务随后
        // 成功的结果被丢弃，这是「即时停机 + future 必有终态」的显式取舍）。
        for (CompletableFuture<?> future : pendingFutures) {
            future.completeExceptionally(
                    new NopException(ERR_SERVICE_NOT_ACTIVE).param("service", "DefaultScheduledExecutor"));
        }
        pendingFutures.clear();
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

    /**
     * C1-003 七读点局部快照：一次 volatile 读取得代际快照，判空与后续解引用使用同一快照，
     * 消除 checkEnabled-then-use 与 destroy 置空之间的竞态窗。
     * 快照为 null → 类型化 ERR_SERVICE_NOT_ACTIVE；快照非 null 但代际已死（shutdownNow 后提交）→
     * JDK REE 在提交点捕获并转类型化 ERR_SERVICE_NOT_ACTIVE（{@link #adaptRejected}）。
     */
    private ScheduledThreadPoolExecutor snapshotExecutor() {
        ScheduledThreadPoolExecutor exec = this.executor;
        if (exec == null) {
            throw new NopException(ERR_SERVICE_NOT_ACTIVE)
                    .param("service", "DefaultScheduledExecutor");
        }
        return exec;
    }

    /**
     * 死代际拒绝的类型化收敛：窗口内提交要么成功（快照先于 destroy），要么获类型化
     * ERR_SERVICE_NOT_ACTIVE（cause 保留原 REE）——与 R1-01「future 有终态、不悬挂」对齐，裸 NPE/REE 消失。
     */
    private RuntimeException adaptRejected(RejectedExecutionException e) {
        return new NopException(ERR_SERVICE_NOT_ACTIVE, e)
                .param("service", "DefaultScheduledExecutor");
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        try {
            // C6-001：登记到 pendingFutures，destroy() 时统一终态
            return ExecutorHelper.submitTracked(exec, task, pendingFutures);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e);
        }
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        try {
            // C6-001：登记到 pendingFutures，destroy() 时统一终态
            return ExecutorHelper.submitTracked(exec, task, result, pendingFutures);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e);
        }
    }

    @Override
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        try {
            // C6-001：登记到 pendingFutures，destroy() 时统一终态
            return ExecutorHelper.scheduleTracked(exec, callable, delay, unit, pendingFutures);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e);
        }
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        // 委托六参直连底层路径，代际快照在六参内统一执行
        return scheduleAtFixedRate(command, initialDelay, period, unit, getName(), null);
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit,
                                         String taskName, IPeriodicTaskListener listener) {
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        // 直接接线底层 JDK 原生周期机制（不得经由本类的四参方法，否则守卫双层包装）：
        // 守卫保证任务体 Throwable 不逃逸，JDK 的「异常抑制后续执行」行为被结构性绕开，
        // 原生周期本身成为重臂机制；fixedRate 速率契约不被失败改写
        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask(taskName, command, listener, chain);
        try {
            Future<?> handle = exec.scheduleAtFixedRate(guard, initialDelay, period, unit);
            chain.setNativeHandle(handle);
            PeriodicTaskHelper.watchNativeHandle(chain, handle);
        } catch (RejectedExecutionException e) {
            // 死代际拒绝在 rejectChain 前转换，拒绝终态 cause 类型化（R1-01 绝缘语义不变，仅 cause 类型化）
            guard.rejectChain(adaptRejected(e));
        } catch (Throwable e) {
            guard.rejectChain(e);
        }
        trackPeriodicTask(guard);
        return chain;
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        // 委托六参绝缘路径；逐轮一次性调度经 this.schedule(...) 走快照检查
        return scheduleWithFixedDelay(command, initialDelay, delay, unit, getName(), null);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                            String taskName, IPeriodicTaskListener listener) {
        // fixedDelay 绝缘核心：逐轮一次性调度 + 失败感知重臂（含退避），任务体运行在 timer 线程。
        // 逐轮重排经 this.schedule(...) 走快照检查，死代际类型化错误落入绝缘拒绝终态（R1-01 语义不变）
        return PeriodicTaskHelper.scheduleWithFixedDelay(this, this, taskName, command, initialDelay, delay, unit,
                listener);
    }

    @Override
    public void execute(Runnable command) {
        ScheduledThreadPoolExecutor exec = snapshotExecutor();
        try {
            // 1.7-C3-013（plan 2547 裁定 F；执行期勘误 E-2547-2）：不包装——周期任务命令
            // 由 PeriodicTaskHelper 守卫管理，外层包装会使拒绝回调重复触发。载体限定 DTE。
            exec.execute(command);
        } catch (RejectedExecutionException e) {
            throw adaptRejected(e);
        }
    }
}