/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ScriptOutputType;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.nosql.core.INosqlRateLimiter;
import io.nop.nosql.core.RateLimitResult;
import io.nop.nosql.core.RateLimiterConfig;
import io.nop.nosql.core.script.RedisScripts;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The rate limiter configuration (rate, capacity) is fixed per instance.
 * Callers must use consistent configuration across all calls to the same rate limiter key.
 */
public class LettuceRateLimiter extends AbstractLettuceOperations implements INosqlRateLimiter {
    private final String key;
    private final RateLimiterConfig config;

    public LettuceRateLimiter(LettuceRedisConnectionProvider client, String key, RateLimiterConfig config) {
        super(client);
        this.key = key;
        this.config = config;
    }

    @Override
    public CompletableFuture<RateLimitResult> tryAcquireAsync(int permits) {
        String tokensKey = key + ":tokens";
        String timestampKey = key + ":timestamp";
        long now = System.currentTimeMillis() / 1000;

        return LettuceExecutor.evalScript(async(), RedisScripts.RATE_LIMIT,
                        ScriptOutputType.MULTI,
                        new String[]{tokensKey, timestampKey},
                        new Object[]{String.valueOf(config.getRate()),
                                String.valueOf(config.getCapacity()),
                                String.valueOf(now),
                                String.valueOf(permits)})
                .thenApply(result -> {
                    Object[] arr;
                    if (result instanceof Object[]) {
                        arr = (Object[]) result;
                    } else if (result instanceof List) {
                        arr = ((List<?>) result).toArray();
                    } else {
                        throw NopException.adapt(new IllegalStateException(
                                "Rate limiter script returned unexpected result type: " + result));
                    }
                    boolean allowed = Long.valueOf(1L).equals(arr[0]);
                    long remaining = ((Number) arr[1]).longValue();
                    return new RateLimitResult(allowed, remaining);
                })
                .toCompletableFuture();
    }

    @Override
    public RateLimitResult tryAcquire(int permits) {
        return FutureHelper.syncGet(tryAcquireAsync(permits));
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
        return FutureHelper.syncGet(getAvailableTokensAsync());
    }
}
