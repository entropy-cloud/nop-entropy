/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.nosql.core.INosqlRateLimiter;
import io.nop.nosql.core.RateLimitResult;
import io.nop.nosql.core.RateLimiterConfig;
import io.nop.nosql.core.script.RedisScripts;

import java.util.concurrent.CompletableFuture;

public class LettuceRateLimiter implements INosqlRateLimiter {
    private final LettuceRedisConnectionProvider client;
    private final String key;
    private final RateLimiterConfig config;

    public LettuceRateLimiter(LettuceRedisConnectionProvider client, String key, RateLimiterConfig config) {
        this.client = client;
        this.key = key;
        this.config = config;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    /**
     * Uses rate_limit.lua script which implements token bucket algorithm.
     * 
     * KEYS[1] = tokens_key (key + ":tokens")
     * KEYS[2] = timestamp_key (key + ":timestamp")
     * ARGV[1] = rate (tokens per second)
     * ARGV[2] = capacity (max burst)
     * ARGV[3] = now (current timestamp in ms)
     * ARGV[4] = requested (number of permits)
     * 
     * Returns: {allowed (0/1), remaining_tokens}
     */
    @Override
    public CompletableFuture<RateLimitResult> tryAcquireAsync(int permits) {
        String tokensKey = key + ":tokens";
        String timestampKey = key + ":timestamp";
        long now = System.currentTimeMillis();

        return LettuceExecutor.evalScript(async(), RedisScripts.RATE_LIMIT,
                        ScriptOutputType.MULTI,
                        new String[]{tokensKey, timestampKey},
                        new Object[]{String.valueOf(config.getRate()),
                                String.valueOf(config.getCapacity()),
                                String.valueOf(now),
                                String.valueOf(permits)})
                .thenApply(result -> {
                    // Lua returns {allowed (0 or 1), new_tokens}
                    Object[] arr = (Object[]) result;
                    boolean allowed = Long.valueOf(1L).equals(arr[0]);
                    long remaining = ((Number) arr[1]).longValue();
                    return new RateLimitResult(allowed, remaining);
                })
                .toCompletableFuture();
    }

    @Override
    public RateLimitResult tryAcquire(int permits) {
        return tryAcquireAsync(permits).join();
    }

    @Override
    public CompletableFuture<Long> getAvailableTokensAsync() {
        String tokensKey = key + ":tokens";
        return async().get(tokensKey)
                .thenApply(v -> {
                    if (v == null) return config.getCapacity() > 0
                            ? (long) config.getCapacity() : 0L;
                    return Long.parseLong(v.toString());
                })
                .toCompletableFuture();
    }

    @Override
    public long getAvailableTokens() {
        return getAvailableTokensAsync().join();
    }
}
