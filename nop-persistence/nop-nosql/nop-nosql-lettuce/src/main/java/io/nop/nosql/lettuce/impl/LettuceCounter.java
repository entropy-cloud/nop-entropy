/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.nosql.core.INosqlCounter;

import java.util.concurrent.CompletableFuture;

public class LettuceCounter extends AbstractLettuceOperations implements INosqlCounter {
    private final String key;

    public LettuceCounter(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
    }

    private Long toLong(Object v) {
        if (v == null)
            return 0L;
        if (v instanceof Number)
            return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public CompletableFuture<Long> incrementAsync(long delta) {
        return async().incrby(key, delta).toCompletableFuture();
    }

    @Override
    public long increment(long delta) {
        return FutureHelper.syncGet(incrementAsync(delta));
    }

    @Override
    public CompletableFuture<Long> getAsync() {
        return async().get(key).thenApply(this::toLong).toCompletableFuture();
    }

    @Override
    public long get() {
        return FutureHelper.syncGet(getAsync());
    }

    @Override
    public CompletableFuture<Long> getAndIncrementAsync(long delta) {
        return async().incrby(key, delta).thenApply(newValue -> newValue - delta).toCompletableFuture();
    }

    @Override
    public long getAndIncrement(long delta) {
        return FutureHelper.syncGet(getAndIncrementAsync(delta));
    }

    @Override
    public CompletableFuture<Void> resetAsync(long value) {
        return async().set(key, Long.toString(value)).thenApply(s -> (Void) null).toCompletableFuture();
    }

    @Override
    public void reset(long value) {
        FutureHelper.syncGet(resetAsync(value));
    }

    @Override
    public CompletableFuture<Long> getAndResetAsync() {
        return async().getset(key, "0").thenApply(this::toLong).toCompletableFuture();
    }

    @Override
    public long getAndReset() {
        return FutureHelper.syncGet(getAndResetAsync());
    }
}
