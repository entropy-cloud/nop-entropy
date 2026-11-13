/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

/**
 * 周期任务绝缘链的观测钩子。listener 实例按任务注册，业务标识通过调度入口的 taskName 参数携带
 * （日志与 metric 均带 taskName 上下文），listener 自身只需关注事件语义。
 *
 * <p>设计契约见 {@code ai-dev/design/concurrency/periodic-task-insulation.md} §3.1/§3.3。
 * 四个回调全部为 default 空实现，消费方按需覆写；listener 回调运行在调度线程上，必须快速返回，
 * 不得在回调内抛出异常（抛出会被设施按普通任务体失败处理，不影响链存活）。
 */
public interface IPeriodicTaskListener {
    /**
     * 任务体单轮执行成功后回调。连续失败恢复时同样触发（此时此前必有对应次数的 onRunFailure）。
     */
    default void onRunSuccess() {
    }

    /**
     * 任务体单轮执行失败后回调。每次失败必达，不受日志抑制区间影响。
     *
     * @param exception           任务体抛出的 Throwable（含 Error）
     * @param consecutiveFailures 当前连续失败轮数（从 1 开始计数）
     */
    default void onRunFailure(Throwable exception, long consecutiveFailures) {
    }

    /**
     * 连续失败达到升级阈值（每达到阈值的整数倍触发一次）时回调。升级只是观测级别提升，
     * 不改变链存活：链仍按退避节奏续排，不会因此终止。
     *
     * @param consecutiveFailures 触发升级时的连续失败轮数
     */
    default void onFailureStreakExceeded(long consecutiveFailures) {
    }

    /**
     * 设施级提交拒绝导致链终止时回调（timer 销毁 / 线程池关闭 / 队列拒绝）。
     * 链终止后不复活；需要自愈的调用方在此回调中自行带退避重排（模块级监督职责）。
     *
     * @param exception 提交失败的异常（如 RejectedExecutionException）
     */
    default void onChainRejected(Throwable exception) {
    }
}
