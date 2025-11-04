/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.commons.functional.Functionals;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class SyncThreadPoolExecutor implements IThreadPoolExecutor {
    public static SyncThreadPoolExecutor INSTANCE = new SyncThreadPoolExecutor();

    public void setName(String name) {

    }

    @Override
    public void refreshConfig() {

    }

    @Override
    public String getName() {
        return "sync";
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return null;
    }

    @Override
    public ThreadPoolStats stats() {
        return null;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        CompletableFuture<V> future = new CompletableFuture<>();
        execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(Functionals.asCallable(task, result));
    }

    @Override
    public void destroy() {

    }

    @Override
    public void execute(Runnable command) {
        ContinuationExecutor.INSTANCE.execute(command);
    }
}