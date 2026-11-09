/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.FutureHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 具体执行任务时总是调度到某个特定的Executor上执行
 */
public class BindScheduledExecutor implements IScheduledExecutor {
    static final Logger LOG = LoggerFactory.getLogger(BindScheduledExecutor.class);

    private final IScheduledExecutor timer;
    private final Executor executor;

    public BindScheduledExecutor(IScheduledExecutor timer, Executor executor) {
        this.timer = timer;
        this.executor = executor;
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return timer.getConfig();
    }

    @Override
    public void refreshConfig() {
        timer.refreshConfig();
    }

    @Override
    public IScheduledExecutor executeOn(Executor executor) {
        if (this.executor == executor)
            return this;

        return new BindScheduledExecutor(timer, executor);
    }

    @Override
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        CompletableFuture<V> future = new CompletableFuture<>();
        // C3-004（裁定②，接入后置于裁定① bindCancel 守卫修复）：返回 future 的取消传播到底层
        // timer 调度——cancel 从 timer 队列移除待触发任务，延迟任务不再照常执行
        Future<?> timerFuture = timer.schedule(() -> {
            executeOn(callable, future);
            return null;
        }, delay, unit);
        FutureHelper.bindCancel(future, timerFuture);
        return future;
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, period, unit, timer.getName(), null);
    }

    @Override
    public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit,
                                         String taskName, IPeriodicTaskListener listener) {
        InsulatedPeriodicChain chain = new InsulatedPeriodicChain();
        InsulatedPeriodicTask guard = new InsulatedPeriodicTask(taskName, command, listener, chain);
        // 定时器轮次只负责把任务体投递到 worker 执行；worker 提交拒绝属于设施级拒绝 → 链终态可见
        // （守卫在 worker 线程内运行，任务体 Throwable 不会逃逸到 timer 的周期机制）
        Runnable hop = () -> {
            try {
                executor.execute(guard);
            } catch (Throwable e) {
                guard.rejectChain(e);
            }
        };
        try {
            Future<?> handle = timer.scheduleAtFixedRate(hop, initialDelay, period, unit);
            chain.setNativeHandle(handle);
            PeriodicTaskHelper.watchNativeHandle(chain, handle);
        } catch (Throwable e) {
            guard.rejectChain(e);
        }
        return chain;
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelay(command, initialDelay, delay, unit, timer.getName(), null);
    }

    @Override
    public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                            String taskName, IPeriodicTaskListener listener) {
        // timer 触发 + worker 执行的手工重臂链：任务体失败由守卫观测并按退避节奏重臂，
        // 链存活至显式 cancel 或设施提交拒绝（不再复现「future 异常完结 → isDone 守卫退出」的杀链语义）
        return PeriodicTaskHelper.scheduleWithFixedDelay(timer, executor, taskName, command, initialDelay, delay,
                unit, listener);
    }

    @Override
    public String getName() {
        return timer.getName();
    }

    @Override
    public ThreadPoolStats stats() {
        return timer.stats();
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        CompletableFuture<V> future = new CompletableFuture<>();
        // C3-004：同 schedule——返回 future 的取消传播到底层 timer 包装任务
        CompletableFuture<V> timerFuture = timer.submit(() -> {
            executeOn(callable, future);
        }, null);
        FutureHelper.bindCancel(future, timerFuture);
        return future;
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(Executors.callable(task, result));
    }

    private <V> void executeOn(Callable<V> callable, CompletableFuture<V> future) {
        // C3-004：排队窗口取消的兜底守卫——包装 future 已完结（取消终态已由 future 承载）时
        // 直接短路，不投递 worker；该守卫不替代 bindCancel 传播
        if (future.isDone())
            return;
        try {
            executor.execute(() -> {
                try {
                    future.complete(callable.call());
                } catch (Throwable e) {
                    LOG.error("nop.err.thread-pool.execute-fail",e);
                    future.completeExceptionally(e);
                }
            });
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    @Override
    public void destroy() {
        timer.destroy();
    }

    @Override
    public void execute(Runnable command) {
        // 1.7-C3-013（plan 2547 裁定 F；执行期勘误 E-2547-2）：本入口**不包装**——周期任务的命令
        // 由 PeriodicTaskHelper 的绝缘守卫管理（其 rejectChain 语义依赖命令身份/时序），
        // 外层再包一层会导致拒绝回调重复触发（TestPeriodicTaskInsulation 实测 rejections=2）。
        // 上下文卫生的载体限定为 DefaultThreadPoolExecutor（globalWorker/cached 的实际载体）。
        executor.execute(command);
    }
}
