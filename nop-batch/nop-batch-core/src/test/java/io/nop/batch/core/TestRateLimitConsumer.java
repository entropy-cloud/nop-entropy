/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.consumer.RateLimitConsumer;
import io.nop.batch.core.consumer.SingleModeBatchConsumer;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRateLimitConsumer {

    static class RecordingLimiter implements IRateLimiter {
        int totalPermits;
        boolean allow = true;

        @Override
        public boolean tryAcquire(int permits, long timeout) {
            if (!allow)
                return false;
            totalPermits += permits;
            return true;
        }

        @Override
        public double getPermitsPerSecond() {
            return 0;
        }

        @Override
        public long getAcquireSuccessCount() {
            return 0;
        }

        @Override
        public long getAcquireFailCount() {
            return 0;
        }

        @Override
        public void resetStats() {
        }
    }

    private IBatchChunkContext newChunkContext() {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();
        chunkCtx.setConcurrency(1);
        chunkCtx.setThreadIndex(0);
        return chunkCtx;
    }

    @Test
    public void testPermitsFollowItemsNotChunkItems() {
        RecordingLimiter limiter = new RecordingLimiter();
        List<String> consumed = new ArrayList<>();
        IBatchConsumer<String> consumer = (items, ctx) -> consumed.addAll(items);

        IBatchChunkContext chunkCtx = newChunkContext();
        chunkCtx.setChunkItems(Arrays.asList("s1", "s2", "s3", "s4", "s5"));

        // 直接批量消费3条输出记录（如processor过滤后），应只取3个许可，而不是chunkItems的5个
        new RateLimitConsumer<>(consumer, limiter).consume(Arrays.asList("r1", "r2", "r3"), chunkCtx);
        assertEquals(3, consumed.size());
        assertEquals(3, limiter.totalPermits);
    }

    @Test
    public void testSingleModeDoesNotAmplifyPermits() {
        RecordingLimiter limiter = new RecordingLimiter();
        List<String> consumed = new ArrayList<>();
        IBatchConsumer<String> consumer = (items, ctx) -> consumed.addAll(items);

        IBatchChunkContext chunkCtx = newChunkContext();
        List<String> chunk = Arrays.asList("a", "b", "c", "d", "e");
        chunkCtx.setChunkItems(chunk);

        // singleMode下逐条调用内层consume，每次的items只有1条。
        // 许可数必须按实际消费条数计算（共5个），不能按chunkItems.size()放大（会变成5*5=25个）
        SingleModeBatchConsumer<String> single = new SingleModeBatchConsumer<>(
                new RateLimitConsumer<>(consumer, limiter));
        single.consume(chunk, chunkCtx);

        assertEquals(5, consumed.size());
        assertEquals(5, limiter.totalPermits);
    }

    @Test
    public void testAcquireTimeoutFailsClosed() {
        RecordingLimiter limiter = new RecordingLimiter();
        limiter.allow = false;

        List<String> consumed = new ArrayList<>();
        IBatchConsumer<String> consumer = (items, ctx) -> consumed.addAll(items);

        IBatchChunkContext chunkCtx = newChunkContext();
        chunkCtx.setChunkItems(Arrays.asList("a", "b"));

        NopTimeoutException ex = assertThrows(NopTimeoutException.class,
                () -> new RateLimitConsumer<>(consumer, limiter).consume(Arrays.asList("a", "b"), chunkCtx));
        assertEquals(BatchErrors.ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT.getErrorCode(), ex.getErrorCode());
        // 超时未获取到许可时不能静默放行消费
        assertTrue(consumed.isEmpty());
    }
}
