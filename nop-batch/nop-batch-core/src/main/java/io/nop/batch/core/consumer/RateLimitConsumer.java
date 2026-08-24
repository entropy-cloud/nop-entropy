/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;

import java.util.Collection;

import static io.nop.batch.core.BatchErrors.ARG_ITEM_COUNT;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT;

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
        // 按本次实际消费的记录数获取许可。不能按chunkContext.getChunkItems().size()获取整chunk的许可数：
        // singleMode等场景下每条记录会单独调用一次consume，此时chunkItems是整个chunk的大小，
        // 会导致限流许可被放大batchSize倍，实际吞吐被压到配置值的1/batchSize
        int permits = items.size();
        if (permits > 0 && !rateLimiter.tryAcquire(permits, RATE_LIMIT_TIMEOUT)) {
            // 超时拿不到许可时不能静默放行，否则限流器作为下游保护机制会fail-open
            throw new NopTimeoutException(ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT).param(ARG_ITEM_COUNT, permits);
        }
        consumer.consume(items, chunkContext);
    }
}
