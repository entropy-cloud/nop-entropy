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
import io.nop.api.core.util.Guard;
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
        // rate<=0 would make the script compute fill_time=inf and fail with a cryptic Redis-side error
        Guard.checkArgument(config != null && config.getRate() > 0, "rate limiter rate must be positive", config);
        Guard.checkArgument(config.getCapacity() > 0, "rate limiter capacity must be positive", config);
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
                    return parseTokenCount(v);
                })
                .toCompletableFuture();
    }

    /**
     * 小数 rate/capacity 配置下，脚本会把小数 token 余量以 "9.5" 这类文本写入 tokens key，
     * 读回时需要按浮点数解析后取整，而不是 Long.parseLong。
     */
    private static long parseTokenCount(Object value) {
        if (value instanceof Number)
            return ((Number) value).longValue();
        return (long) Double.parseDouble(value.toString().trim());
    }

    @Override
    public long getAvailableTokens() {
        return FutureHelper.syncGet(getAvailableTokensAsync());
    }
}
