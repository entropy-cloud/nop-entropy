/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * 周期任务绝缘链的返回 future。
 *
 * <p>终态语义（设计契约见 {@code ai-dev/design/concurrency/periodic-task-insulation.md} §3.2）：
 * 任务体首次失败或设施提交拒绝时 {@code completeExceptionally}（各只结算一次）；调用方 {@link #cancel}
 * 终止链。返回 future 是<b>失败/取消/拒绝信号，不是链存活指标</b>——首次失败后 isDone 即为 true 而链
 * 继续运行，因此链存活判定必须用 {@link #isStopped()}，禁止用 isDone 判活。
 *
 * <p>本类上禁止任何「任意完结 → 自动取消 native 触发器」的接线（FutureHelper.bindCancel 形态）：
 * native 触发器的取消只有 {@link #cancel(boolean)} 一条显式路径，避免扩大 C3-016 误取消触发面。
 */
final class InsulatedPeriodicChain extends CompletableFuture<Void> {
    private volatile Future<?> nativeHandle;
    private volatile boolean rejected;
    private volatile boolean stopRequested;

    void setNativeHandle(Future<?> nativeHandle) {
        this.nativeHandle = nativeHandle;
        // cancel/reject 与调度提交之间的竞态：链已终止时补取消，防止已终止链残留 pending 触发器
        if (isStopped() && !nativeHandle.isDone())
            nativeHandle.cancel(false);
    }

    Future<?> nativeHandle() {
        return nativeHandle;
    }

    boolean isRejected() {
        return rejected;
    }

    /**
     * 链是否已终止（调用方取消[含对已因失败完结的链的 cancel]、设施拒绝）。轮次入口守卫与续排判定只认此方法。
     */
    boolean isStopped() {
        return rejected || stopRequested || isCancelled();
    }

    /**
     * 设施级拒绝终态：标记链终止并按拒绝原因异常完结。
     */
    void reject(Throwable cause) {
        rejected = true;
        completeExceptionally(cause);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 先终态自身再取消 native 触发器：native 的完成回调据此区分「调用方取消」与「设施批量取消」。
        // 对已因失败完结的链（super.cancel 返回 false），停止动作仍然生效（stopRequested）——
        // 否则失败后的链将永远无法被调用方停止
        boolean result = super.cancel(mayInterruptIfRunning);
        stopRequested = true;
        Future<?> handle = nativeHandle;
        if (handle != null)
            handle.cancel(mayInterruptIfRunning);
        return result;
    }
}
