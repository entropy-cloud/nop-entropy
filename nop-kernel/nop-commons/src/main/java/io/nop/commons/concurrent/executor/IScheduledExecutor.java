/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface IScheduledExecutor extends IThreadPoolExecutor {

    /**
     * 返回一个包装后的executor，它再调度任务时投递到Executor上执行
     *
     * @param executor 任务执行器
     * @return 一个包装后的Executor
     */
    default IScheduledExecutor executeOn(Executor executor) {
        return new BindScheduledExecutor(this, executor);
    }

    <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * 以固定速率周期执行任务。任务体异常绝缘契约（设计权威来源
     * {@code ai-dev/design/concurrency/periodic-task-insulation.md}）：
     *
     * <ul>
     * <li>任务体抛出的任何 Throwable（含 Error）由设施捕获并观测（日志/metric/listener），
     * 周期链不因此终止；fixedRate 路径保持速率契约，失败不改写触发节奏；</li>
     * <li>任务体首次失败使返回 future completeExceptionally（仅结算一次），但周期继续运行——
     * 返回 future 是失败/取消/拒绝信号，<b>不是链存活指标</b>；</li>
     * <li>调用方 {@code cancel} 返回的 future 后，在飞轮次自然执行完毕，后续轮次不再触发；
     * cancel 对已因失败完结的返回 future 同样终止链（返回值遵循 CompletableFuture 既有语义，已完结时为 false）；</li>
     * <li>设施提交拒绝（线程池关闭/队列拒绝）时返回 future completeExceptionally，周期终止且不复活。</li>
     * </ul>
     */
    Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * 以固定间隔周期执行任务（上一轮执行完成到下一轮开始的间隔）。任务体异常绝缘契约同
     * {@link #scheduleAtFixedRate(Runnable, long, long, TimeUnit)}；差异点：连续失败时下一轮按指数退避
     * 推迟（下限 1 秒、封顶 60 秒），任务体成功一轮后恢复原周期——防止小周期失败任务在共享 timer 上
     * 形成失败风暴与日志洪泛。
     */
    Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    /**
     * {@link #scheduleAtFixedRate(Runnable, long, long, TimeUnit)} 的带业务标识与观测钩子形态。
     *
     * @param taskName 任务业务标识，用于日志与 metric（nop.periodic.task.*）上下文
     * @param listener 观测钩子（可为 null），语义见 {@link IPeriodicTaskListener}
     */
    default Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit,
                                          String taskName, IPeriodicTaskListener listener) {
        return PeriodicTaskHelper.scheduleAtFixedRate(this, command, initialDelay, period, unit, taskName, listener);
    }

    /**
     * {@link #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} 的带业务标识与观测钩子形态。
     *
     * @param taskName 任务业务标识，用于日志与 metric（nop.periodic.task.*）上下文
     * @param listener 观测钩子（可为 null），语义见 {@link IPeriodicTaskListener}
     */
    default Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                             String taskName, IPeriodicTaskListener listener) {
        return PeriodicTaskHelper.scheduleWithFixedDelay(this, this, taskName, command, initialDelay, delay, unit,
                listener);
    }

    default Future<?> scheduleWithRandomDelay(Runnable command, long initialDelay,
                                              long minDelay, long maxDelay, TimeUnit unit) {
        return ExecutorHelper.scheduleWithRandomDelay(this, command, initialDelay, minDelay, maxDelay, unit);
    }
}
