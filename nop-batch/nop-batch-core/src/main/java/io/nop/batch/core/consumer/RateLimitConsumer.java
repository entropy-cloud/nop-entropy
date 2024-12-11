/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;

import java.util.Collection;

public class RateLimitConsumer<R> implements IBatchConsumer<R> {
    static final long RATE_LIMIT_TIMEOUT = 1000 * 60 * 20L; // 20分钟

    private final IBatchConsumer<R> consumer;
    private final IRateLimiter rateLimiter;

    public RateLimitConsumer(IBatchConsumer<R> consumer, IRateLimiter rateLimiter) {
        this.consumer = consumer;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext chunkContext) {
        rateLimiter.tryAcquire(chunkContext.getChunkItems().size(), RATE_LIMIT_TIMEOUT);
        consumer.consume(items, chunkContext);
    }
}