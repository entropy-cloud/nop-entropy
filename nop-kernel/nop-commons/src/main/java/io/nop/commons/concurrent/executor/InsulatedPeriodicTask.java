/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.util.Guard;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 周期任务体的绝缘守卫：捕获任务体的任何 Throwable（含 Error），执行观测与失败计数后返回，
 * 绝不让异常逃逸到调度设施——这是「一次 Throwable 即静默永久停摆」缺陷（2.1-C3-001）的修复核心。
 * catch 分支必须同时具备观测与计数两个动作，禁止退化为吞异常。
 *
 * <p>失败治理契约（设计文档 §3.1）：连续失败按 {@link #LOG_SUPPRESS_INTERVAL} 抑制日志、
 * 按 {@link #BACKOFF_MULTIPLIER} 指数退避（下限 {@link #MIN_FAILURE_REARM_MILLIS}、
 * 封顶 {@link #MAX_BACKOFF_MILLIS}），每达到 {@link #STREAK_ESCALATION_THRESHOLD} 的整数倍
 * 触发升级钩子。升级只是观测提升，不改写链存活。
 */
final class InsulatedPeriodicTask implements Runnable {
    static final Logger LOG = LoggerFactory.getLogger(InsulatedPeriodicTask.class);

    static final String METRIC_FAILURES = "nop.periodic.task.failures";
    static final String METRIC_ESCALATIONS = "nop.periodic.task.escalations";
    static final String METRIC_TAG_TASK = "task";

    /**
     * 连续失败日志抑制区间：第 1 次失败全量 ERROR，此后每第 N 次连续失败记一条汇总 ERROR，中间抑制。
     * 形态与 JdbcConfigSource 的 errorCount&lt;10 分级抑制一致。
     */
    static final int LOG_SUPPRESS_INTERVAL = 10;

    /**
     * 连续失败升级阈值：每达到 N 的整数倍触发 {@link IPeriodicTaskListener#onFailureStreakExceeded(long)}
     * 与 escalations metric。
     */
    static final int STREAK_ESCALATION_THRESHOLD = 10;

    /**
     * 连续失败重臂退避乘子（仅 fixedDelay 手工重臂路径；fixedRate 保持速率契约不退避）。
     */
    static final int BACKOFF_MULTIPLIER = 2;

    /**
     * 进入失败状态后的重臂延迟下限（毫秒）：防止小周期失败任务在共享 timer 上形成紧失败循环。
     */
    static final long MIN_FAILURE_REARM_MILLIS = 1000L;

    /**
     * 失败退避封顶（毫秒）。
     */
    static final long MAX_BACKOFF_MILLIS = 60_000L;

    private final String taskName;
    private final Runnable task;
    private final IPeriodicTaskListener listener;
    private final InsulatedPeriodicChain chain;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    private final Counter failureCounter; // GlobalMeterRegistry 未初始化时为 null，metric 静默跳过
    private final Counter escalationCounter;

    InsulatedPeriodicTask(String taskName, Runnable task, IPeriodicTaskListener listener,
                          InsulatedPeriodicChain chain) {
        this.taskName = taskName == null ? "periodic-task" : taskName;
        this.task = Guard.notNull(task, "task");
        this.listener = listener;
        this.chain = chain;

        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry != null) {
            this.failureCounter = registry.counter(METRIC_FAILURES, METRIC_TAG_TASK, this.taskName);
            this.escalationCounter = registry.counter(METRIC_ESCALATIONS, METRIC_TAG_TASK, this.taskName);
        } else {
            this.failureCounter = null;
            this.escalationCounter = null;
        }
    }

    @Override
    public void run() {
        // 供 JDK 原生周期机制（fixedRate 路径）直接调度：异常不逃逸，JDK 的「异常抑制后续执行」
        // 行为因此被结构性绕开，原生周期机制本身成为重臂机制
        runBody();
    }

    /**
     * 执行一轮任务体。绝缘语义核心：任务体的任何 Throwable 都不会从此方法逃逸；
     * 观测（listener/日志/metric）与失败计数在返回前完成，由调用方根据返回值与
     * {@link #nextRearmDelayMillis(long)} 决定续排节奏。
     *
     * @return true 表示本轮成功；false 表示失败或链已取消（取消引发的在飞中断不计失败）
     */
    boolean runBody() {
        try {
            task.run();
        } catch (Throwable e) {
            if (chain != null && chain.isStopped()) {
                // 链正在终止（调用方取消引发的在飞中断等）：不计失败
                return false;
            }
            onFailure(e);
            return false;
        }
        onSuccess();
        return true;
    }

    /**
     * 连续失败状态下下一轮的重臂延迟（毫秒）：max(base, 1s) 起步按乘子放大，封顶 60s。
     * 无失败在身时返回原周期。
     */
    long nextRearmDelayMillis(long baseDelayMillis) {
        int fails = consecutiveFailures.get();
        if (fails <= 0)
            return baseDelayMillis;

        long delay = Math.max(baseDelayMillis, MIN_FAILURE_REARM_MILLIS);
        for (int i = 1; i < fails && delay < MAX_BACKOFF_MILLIS; i++) {
            delay = Math.min(delay * BACKOFF_MULTIPLIER, MAX_BACKOFF_MILLIS);
        }
        return delay;
    }

    /**
     * 设施级提交拒绝的统一终态：观测（日志 + listener）+ 链终止 + native 触发器取消，链不复活。
     */
    void rejectChain(Throwable e) {
        LOG.error("nop.err.periodic-task.chain-rejected: task={}", taskName, e);
        if (listener != null)
            listener.onChainRejected(e);
        if (chain != null) {
            // 先 reject 再取消 native：顺序保证 native 的完成回调不会以取消原因覆盖真实拒绝原因
            chain.reject(e);
            Future<?> handle = chain.nativeHandle();
            if (handle != null && !handle.isDone())
                handle.cancel(false);
        }
    }

    /**
     * 链引用（可为 null 的装配出口），供 timer 销毁等设施侧批量拒绝路径使用。
     */
    InsulatedPeriodicChain chain() {
        return chain;
    }

    private void onSuccess() {
        int fails = consecutiveFailures.getAndSet(0);
        if (fails > 0)
            LOG.info("nop.info.periodic-task.recover: task={}, failedRounds={}", taskName, fails);
        if (listener != null)
            listener.onRunSuccess();
    }

    private void onFailure(Throwable e) {
        long streak = consecutiveFailures.incrementAndGet();

        if (listener != null)
            listener.onRunFailure(e, streak);

        if (shouldLogFailure(streak))
            LOG.error("nop.err.periodic-task.fail: task={}, consecutiveFailures={}", taskName, streak, e);

        if (streak % STREAK_ESCALATION_THRESHOLD == 0) {
            if (listener != null)
                listener.onFailureStreakExceeded(streak);
            if (escalationCounter != null)
                escalationCounter.increment();
        }

        if (failureCounter != null)
            failureCounter.increment();

        if (chain != null)
            // 失败终态结算（只结算一次，completeExceptionally 幂等）：返回 future 是失败/取消/拒绝信号；
            // 结算不携带任何取消语义，链继续按退避节奏重臂
            chain.completeExceptionally(e);
    }

    static boolean shouldLogFailure(long streak) {
        return streak == 1 || streak % LOG_SUPPRESS_INTERVAL == 0;
    }
}
