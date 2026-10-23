/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.Guard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 周期任务绝缘层的统一核心。fixedDelay 走一次性调度 + 失败感知手工重臂（支持退避）；
 * fixedRate 走「任务体守卫 + JDK 原生周期机制」（速率契约不被失败改写）。
 *
 * <p>三条不变式（设计契约见 {@code ai-dev/design/concurrency/periodic-task-insulation.md} §3.1/§3.2）：
 * <ul>
 * <li>任务体 Throwable 在守卫内捕获观测，链强制续排直至显式 cancel 或设施提交拒绝；</li>
 * <li>轮次入口守卫只认 {@link InsulatedPeriodicChain#isStopped()}（cancel/拒绝），禁止用 isDone 判活——
 * 首次失败后返回 future 已异常完结而链必须继续；</li>
 * <li>拒绝路径（timer/runner 提交失败）链终止、终态可见、不复活；重排策略归调用方模块级监督。</li>
 * </ul>
 */
final class PeriodicTaskHelper {
    static final Logger LOG = LoggerFactory.getLogger(PeriodicTaskHelper.class);

    private PeriodicTaskHelper() {
    }

    /**
     * fixedDelay 绝缘核心：逐轮一次性调度。任务体提交到 runner 执行（runner == timer 时运行在
     * timer 线程）。注意： {@code timer} 的 schedule 方法必须是一次性语义，不得内部再包周期机制；
     * {@link DefaultScheduledExecutor} 与 {@link BindScheduledExecutor} 均满足。
     */
    static Future<?> scheduleWithFixedDelay(IScheduledExecutor timer, Executor runner, String taskName,
                                            Runnable command, long initialDelay, long delay, TimeUnit unit,
                                            IPeriodicTaskListener listener) {
        Guard.notNull(timer, "timer");
        Guard.notNull(runner, "runner");
        Guard.notNull(command, "command");
        Guard.notNull(unit, "unit");

        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask(taskName, command, listener, chain);
        // timer 是 DefaultScheduledExecutor 时登记守卫：其 destroy() 需要以统一拒绝路径收口
        // 仍在等待触发的轮次（ExecutorHelper 的一次性包装 future 在 shutdownNow 批量取消下不会自行完结）
        if (timer instanceof DefaultScheduledExecutor)
            ((DefaultScheduledExecutor) timer).trackPeriodicTask(guard);
        long baseDelayMillis = Math.max(0, unit.toMillis(delay));
        long initialDelayMillis = Math.max(0, unit.toMillis(initialDelay));
        scheduleRound(timer, runner, guard, chain, baseDelayMillis, initialDelayMillis);
        return chain;
    }

    /**
     * fixedRate 绝缘装配：任务体守卫包装后交由 executor 的原生周期机制调度。守卫保证任务体 Throwable
     * 不逃逸，原生机制的「异常抑制后续执行」行为因此被结构性绕开，原生周期本身成为重臂机制。
     *
     * <p>只可传入「原生周期」实现（如 {@link DefaultScheduledExecutor} 的底层 JDK executor）。
     * 若传入的 executor 四参方法内部已包守卫（如 Default 四参本身），将产生双层守卫——
     * Default/Bind 必须重写六参方法直接接线原生机制。
     */
    static Future<?> scheduleAtFixedRate(IScheduledExecutor executor, Runnable command, long initialDelay,
                                         long period, TimeUnit unit, String taskName, IPeriodicTaskListener listener) {
        Guard.notNull(executor, "executor");

        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask(taskName, command, listener, chain);
        try {
            Future<?> handle = executor.scheduleAtFixedRate(guard, initialDelay, period, unit);
            chain.setNativeHandle(handle);
            watchNativeHandle(chain, handle);
        } catch (Throwable e) {
            guard.rejectChain(e);
        }
        return chain;
    }

    /**
     * 监听 native 触发器的异常终结：触发器被设施批量取消（如 timer shutdownNow）而链尚无终态时，
     * 补拒绝终态，保证「关闭竞态下 future 有终态、不悬挂」。调用方 cancel 与正常轮次完结不触发
     * （前者链已 done，后者 error 为 null）。
     */
    static void watchNativeHandle(InsulatedPeriodicChain chain, Future<?> handle) {
        if (!(handle instanceof CompletionStage))
            return;

        ((CompletionStage<?>) handle).whenComplete((result, error) -> {
            if (error != null && !chain.isDone())
                chain.reject(error);
        });
    }

    private static void scheduleRound(IScheduledExecutor timer, Executor runner, InsulatedPeriodicTask guard,
                                      InsulatedPeriodicChain chain, long baseDelayMillis, long delayMillis) {
        if (chain.isStopped())
            return;

        try {
            Future<?> handle = timer.schedule(() -> {
                // 轮次入口守卫：只认链终止（cancel/拒绝）。禁止用 isDone 判活——失败终态后链必须继续重臂
                if (!chain.isStopped())
                    dispatchRound(timer, runner, guard, chain, baseDelayMillis);
                return null;
            }, delayMillis, TimeUnit.MILLISECONDS);
            chain.setNativeHandle(handle);
            watchNativeHandle(chain, handle);
        } catch (Throwable e) {
            // timer 销毁/关闭：链终止、终态可见、不复活
            guard.rejectChain(e);
        }
    }

    private static void dispatchRound(IScheduledExecutor timer, Executor runner, InsulatedPeriodicTask guard,
                                      InsulatedPeriodicChain chain, long baseDelayMillis) {
        try {
            runner.execute(() -> {
                if (chain.isStopped())
                    return;

                boolean success = guard.runBody();
                if (!chain.isStopped()) {
                    // 失败轮次按退避节奏重臂；速率语义的 fixedRate 路径不走此手工重臂
                    long nextDelayMillis = success ? baseDelayMillis : guard.nextRearmDelayMillis(baseDelayMillis);
                    scheduleRound(timer, runner, guard, chain, baseDelayMillis, nextDelayMillis);
                }
            });
        } catch (Throwable e) {
            // worker 池拒绝等设施级提交失败：链终止、终态可见、不复活；自愈归调用方监督（onChainRejected）
            guard.rejectChain(e);
        }
    }
}
