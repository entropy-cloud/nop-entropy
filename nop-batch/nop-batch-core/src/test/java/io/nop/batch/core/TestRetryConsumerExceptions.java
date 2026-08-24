/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.consumer.RetryAllBatchConsumer;
import io.nop.batch.core.consumer.SingleModeBatchConsumer;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.RetryBatchLoader;
import io.nop.commons.util.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestRetryConsumerExceptions {

    private IBatchChunkContext newChunkContext() {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();
        chunkCtx.setConcurrency(1);
        chunkCtx.setThreadIndex(0);
        return chunkCtx;
    }

    /**
     * 重试阶段最终失败时抛出的异常必须是最后一次的失败原因，
     * 首次异常作为suppressed保留在异常链上，不能直接丢弃
     */
    @Test
    public void testRetryConsumerThrowsFinalExceptionWithFirstSuppressed() {
        IBatchConsumer<String> consumer = new IBatchConsumer<String>() {
            int calls;

            @Override
            public void consume(Collection<String> items, IBatchChunkContext context) {
                if (calls++ == 0)
                    throw new IllegalStateException("first-failure");
                throw new IllegalStateException("second-failure");
            }
        };

        RetryAllBatchConsumer<String> retryConsumer =
                new RetryAllBatchConsumer<>(consumer, RetryPolicy.retryNTimes(2), null);

        IBatchChunkContext chunkCtx = newChunkContext();
        // NopException.adapt对RuntimeException原样透传，最终抛出的是最后一次失败的异常本身
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> retryConsumer.consume(Arrays.asList("a", "b"), chunkCtx));
        assertEquals("second-failure", ex.getMessage());

        boolean hasFirst = false;
        for (Throwable s : ex.getSuppressed()) {
            if ("first-failure".equals(s.getMessage()))
                hasFirst = true;
        }
        assertTrue(hasFirst, "first-failure should be attached as suppressed exception");
    }

    /**
     * singleMode包装的内层consume抛异常时，标志必须恢复，不能泄漏到后续处理
     */
    @Test
    public void testSingleModeFlagRestoredWhenConsumeFails() {
        IBatchConsumer<String> consumer = (items, ctx) -> {
            if (items.iterator().next().equals("b"))
                throw new IllegalStateException("boom");
        };

        IBatchChunkContext chunkCtx = newChunkContext();
        chunkCtx.setSingleMode(false);

        SingleModeBatchConsumer<String> single = new SingleModeBatchConsumer<>(consumer);
        try {
            single.consume(Arrays.asList("a", "b", "c"), chunkCtx);
            fail("should throw");
        } catch (RuntimeException e) {
            // ignore
        }
        assertFalse(chunkCtx.isSingleMode());
    }

    /**
     * 首次加载即抛出取消异常时必须直接透传，不能进入重试流程
     */
    @Test
    public void testRetryLoaderRethrowsCancelWithoutRetry() {
        IBatchLoader<String> loader = (batchSize, ctx) -> {
            throw new BatchCancelException(BatchErrors.ERR_BATCH_CANCEL_PROCESS);
        };
        RetryBatchLoader<String> retryLoader = new RetryBatchLoader<>(loader, RetryPolicy.retryNTimes(3));

        IBatchChunkContext chunkCtx = newChunkContext();
        assertThrows(BatchCancelException.class, () -> retryLoader.load(10, chunkCtx));
        assertEquals(0, chunkCtx.getLoadRetryCount());
    }
}
