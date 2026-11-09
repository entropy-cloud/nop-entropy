/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.nop.commons.CommonErrors.ERR_CONCURRENT_CYCLE_ALREADY_BEGIN;

/**
 * 每个处理周期产生一个Promise，可以通过waitCycleCompleted来等待本次处理周期结束
 */
public class CycleSynchronizer {
    static final Logger LOG = LoggerFactory.getLogger(CycleSynchronizer.class);

    private volatile boolean finished;
    private volatile boolean processing;

    private List<Runnable> onCycleCompleted;
    private CompletableFuture<Boolean> cyclePromise;

    /**
     * 1.7-C1-010（plan 2547 裁定 C）：单调周期代号。{@code beginCycle} 递增并记录当前代号；
     * {@code resolvePromise} 只在「被收尾的周期 == 当前周期」时才清 processing/complete promise。
     * 原实现无代号：旧周期的 finish 与新周期的 beginCycle 交叠时，旧周期收尾会把**新周期**的
     * processing 状态与回调列表一并清掉（潜伏缺陷，当前零生产调用方）。
     */
    private long cycleGeneration;

    public boolean isFinished() {
        return finished;
    }

    public boolean isProcessing() {
        return processing;
    }

    public synchronized boolean beginCycle() {
        if (finished)
            return false;

        if (processing)
            throw new NopException(ERR_CONCURRENT_CYCLE_ALREADY_BEGIN);

        processing = true;
        this.cyclePromise = null;
        this.cycleGeneration++;
        return true;
    }

    public synchronized CompletableFuture<Boolean> waitCycleCompleted(Runnable onCompleted) {
        if (!processing) {
            if (onCompleted != null)
                onCompleted.run();
            return CompletableFuture.completedFuture(finished);
        }

        if (onCompleted != null) {
            if (onCycleCompleted == null)
                this.onCycleCompleted = new ArrayList<>();
            onCycleCompleted.add(onCompleted);
        }

        if (cyclePromise == null)
            cyclePromise = new CompletableFuture<>();
        return cyclePromise;
    }

    public void finish() {
        resolvePromise(true, snapshotGeneration());
    }

    public void completeCycle() {
        resolvePromise(false, snapshotGeneration());
    }

    /** 1.7-C1-010：取发起收尾时的周期代号快照（收尾可能在锁外延迟执行）。 */
    private synchronized long snapshotGeneration() {
        return cycleGeneration;
    }

    /**
     * 1.7-C1-010 + 1.7-C2-004（plan 2547 裁定 C，同 diff）：
     * <ul>
     *   <li><b>C1-010 代号</b>：只在「本次收尾的就是当前周期」时才清 processing/cyclePromise；
     *       旧周期的延迟收尾直接忽略（不污染新周期状态）。</li>
     *   <li><b>C2-004 回调出锁域</b>：回调列表在监视器内**快照并摘下**，在锁外逐个执行——
     *       避免持 {@code this} 锁跑任意回调（自死锁 / 跨实例锁序环 / 回调异常中断后续回调）。</li>
     *   <li><b>次序</b>：先 {@code promise.complete(finished)} 再跑回调，消除「回调内 await
     *       本周期 promise」的自死锁面。</li>
     *   <li><b>隔离</b>：每个回调独立 try/catch，一个抛异常不影响其余回调，也不以异常完成 promise。</li>
     * </ul>
     */
    private CompletableFuture<Boolean> resolvePromise(boolean finished, long expectedGeneration) {
        CompletableFuture<Boolean> promise;
        List<Runnable> callbacks = null;
        long generation;
        synchronized (this) {
            generation = this.cycleGeneration;
            // 1.7-C1-010 守卫（closure audit R1 B-2：原实现只取值不比较 = 死字段）：
            // 收尾请求携带「发起时的代号」expectedGeneration；若期间 beginCycle 已把
            // cycleGeneration 递增（新周期已开始），则本次收尾属旧周期，**直接忽略**——
            // 不得清掉新周期的 processing / cyclePromise / 回调列表。
            if (expectedGeneration != generation)
                return cyclePromise;
            promise = cyclePromise;
            if (promise != null) {
                cyclePromise = null;
            }
            this.finished = finished;
            this.processing = false;
            callbacks = this.onCycleCompleted;
            this.onCycleCompleted = null;
        }

        if (promise != null) {
            promise.complete(finished);
        }

        if (callbacks != null) {
            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (Throwable e) {
                    // 1.7-C2-004：逐回调隔离——单个回调失败不得中断后续、不得以异常完成 promise
                    LOG.error("nop.concurrent.cycle-complete-callback-fail:generation={}", generation, e);
                }
            }
        }
        return promise;
    }

    public void restart() {
        resolvePromise(false, snapshotGeneration());
    }
}