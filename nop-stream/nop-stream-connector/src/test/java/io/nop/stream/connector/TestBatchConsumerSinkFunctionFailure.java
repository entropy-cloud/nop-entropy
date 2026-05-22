/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchConsumerProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Bug N53: flush failure causes duplicate processing. Fix should pass.")
public class TestBatchConsumerSinkFunctionFailure {

    @Test
    void testFlushFailureThrowsException() {
        RuntimeException flushError = new RuntimeException("flush failed");
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            throw flushError;
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 1);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> sink.consume("a"));
        assertSame(flushError, thrown, "Exception from consumer should propagate");
    }

    @Test
    void testFlushFailureOnClose() {
        AtomicInteger callCount = new AtomicInteger(0);
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            if (callCount.incrementAndGet() > 1) {
                throw new RuntimeException("second flush failed");
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 2);

        sink.consume("a");

        RuntimeException thrown = assertThrows(RuntimeException.class, sink::close);
        assertEquals("second flush failed", thrown.getMessage());
    }
}
