/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.commons.utils;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class VertxHelper {
    public static <T> void runTask(Supplier<?> task, Promise<T> promise) {
        try {
            Object ret = task.get();
            if (ret instanceof CompletionStage) {
                ((CompletionStage<T>) ret).whenComplete((v, e) -> {
                    if (e != null) {
                        promise.fail(e);
                    } else {
                        promise.complete(v);
                    }
                });
            } else {
                promise.complete((T) ret);
            }
        } catch (Exception e) {
            promise.fail(e);
        }
    }
    public static <T> CompletableFuture<T> toCompletableFuture(Future<T> future) {
        CompletableFuture<T> promise = new CompletableFuture<>();
        future.onSuccess(promise::complete).onFailure(promise::completeExceptionally);
        return promise;
    }
}