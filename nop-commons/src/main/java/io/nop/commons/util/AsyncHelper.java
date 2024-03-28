package io.nop.commons.util;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.AsyncJoinType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncHelper {
    public static CompletableFuture<Void> waitAsync(List<? extends CompletionStage<?>> promises, AsyncJoinType joinType) {
        Guard.checkArgument(!promises.isEmpty(), "promises");

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        int n = promises.size();
        for (CompletionStage<?> promise : promises) {
            promise.whenComplete((ret, err) -> {
                if (joinType == AsyncJoinType.anyComplete) {
                    FutureHelper.complete(future, null, err);
                } else if (joinType == AsyncJoinType.anyFailure) {
                    if (err != null) {
                        FutureHelper.complete(future, null, err);
                    }
                } else if (joinType == AsyncJoinType.anySuccess) {
                    if (err == null) {
                        FutureHelper.complete(future, null, null);
                    }
                }
                completedCount.incrementAndGet();
                if (completedCount.get() == n) {
                    // allComplete时总是成功返回
                    FutureHelper.complete(future, null, null);
                }
            });
        }
        return future;
    }
}
