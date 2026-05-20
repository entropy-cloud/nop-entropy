/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.nosql.core.INosqlCounter;

import java.util.concurrent.CompletableFuture;

public class LettuceCounter implements INosqlCounter {
    private final LettuceRedisConnectionProvider client;
    private final String key;

    public LettuceCounter(LettuceRedisConnectionProvider client, String key) {
        this.client = client;
        this.key = key;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    private Long toLong(Object v) {
        if (v == null)
            return 0L;
        if (v instanceof Number)
            return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    @Override
    public CompletableFuture<Long> incrementAsync(long delta) {
        return async().incrby(key, delta).toCompletableFuture();
    }

    @Override
    public long increment(long delta) {
        return incrementAsync(delta).join();
    }

    @Override
    public CompletableFuture<Long> getAsync() {
        return async().get(key).thenApply(this::toLong).toCompletableFuture();
    }

    @Override
    public long get() {
        return getAsync().join();
    }

    @Override
    public CompletableFuture<Long> getAndIncrementAsync(long delta) {
        return async().incrby(key, delta).thenApply(newValue -> newValue - delta).toCompletableFuture();
    }

    @Override
    public long getAndIncrement(long delta) {
        return getAndIncrementAsync(delta).join();
    }

    @Override
    public CompletableFuture<Void> resetAsync(long value) {
        return async().set(key, Long.toString(value)).thenApply(s -> (Void) null).toCompletableFuture();
    }

    @Override
    public void reset(long value) {
        resetAsync(value).join();
    }

    @Override
    public CompletableFuture<Long> getAndResetAsync() {
        return async().getset(key, "0").thenApply(this::toLong).toCompletableFuture();
    }

    @Override
    public long getAndReset() {
        return getAndResetAsync().join();
    }
}
