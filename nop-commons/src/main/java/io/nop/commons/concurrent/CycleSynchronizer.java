/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent;

import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.nop.commons.CommonErrors.ERR_CONCURRENT_CYCLE_ALREADY_BEGIN;

/**
 * 每个处理周期产生一个Promise，可以通过waitCycleCompleted来等待本次处理周期结束
 */
public class CycleSynchronizer {
    private volatile boolean finished;
    private volatile boolean processing;

    private List<Runnable> onCycleCompleted;
    private CompletableFuture<Boolean> cyclePromise;

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
        resolvePromise(true);
    }

    public void completeCycle() {
        resolvePromise(false);
    }

    private CompletableFuture<Boolean> resolvePromise(boolean finished) {
        CompletableFuture<Boolean> promise = null;
        try {
            synchronized (this) {
                promise = cyclePromise;
                if (promise != null) {
                    cyclePromise = null;
                }
                this.finished = finished;
                this.processing = false;
                List<Runnable> callbacks = this.onCycleCompleted;
                if (callbacks != null) {
                    this.onCycleCompleted = null;
                    for (Runnable callback : callbacks) {
                        callback.run();
                    }
                }
            }

            if (promise != null) {
                promise.complete(finished);
            }
        } catch (Exception e) {
            if (promise != null) {
                promise.completeExceptionally(e);
            }
        }
        return promise;
    }

    public void restart() {
        resolvePromise(false);
    }
}