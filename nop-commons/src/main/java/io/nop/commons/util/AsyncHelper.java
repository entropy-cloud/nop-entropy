package io.nop.commons.util;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.AsyncJoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncHelper {
    static final Logger LOG = LoggerFactory.getLogger(AsyncHelper.class);

    public static CompletableFuture<Void> waitAsync(List<? extends CompletionStage<?>> promises, AsyncJoinType joinType) {
        Guard.checkArgument(!promises.isEmpty(), "promises");

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        int n = promises.size();
        for (CompletionStage<?> promise : promises) {
            promise.whenComplete((ret, err) -> {
                if (err != null) {
                    LOG.error("nop.wait-async-err", err);

                    if (failure.get() == null)
                        failure.set(err);
                }
                if (joinType == AsyncJoinType.anyFailure) {
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
                    if ((joinType == AsyncJoinType.allSuccess || joinType == null) && failure.get() != null) {
                        // 如果要求所有分支都成功，但是存在异常，则作为异常返回
                        FutureHelper.complete(future, null, failure.get());
                    } else {
                        // allComplete时总是成功返回
                        FutureHelper.complete(future, null, null);
                    }
                }
            });
        }
        return future;
    }
}
