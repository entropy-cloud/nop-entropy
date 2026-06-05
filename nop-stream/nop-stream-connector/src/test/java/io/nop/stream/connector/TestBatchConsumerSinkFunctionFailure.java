/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bug N53 fix: flush() must clear buffer even when consumer.consume() throws.
 */
public class TestBatchConsumerSinkFunctionFailure {

    @Test
    void testFlushFailurePropagatesException() {
        StreamException flushError = new StreamException("flush failed");
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            throw flushError;
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 1);

        StreamException thrown = assertThrows(StreamException.class, () -> sink.consume("a"));
        assertNotNull(thrown.getCause(), "Original exception should be wrapped as cause");
    }

    @Test
    void testBufferRetainedOnFlushFailureForRetry() {
        List<List<String>> captured = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);

        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            captured.add(new ArrayList<>(items));
            if (callCount.incrementAndGet() == 1) {
                throw new StreamException("first flush fails");
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 2);

        sink.consume("a");
        // consume "b" fills buffer to 2, triggers flush. First call fails, buffer retained.
        assertThrows(RuntimeException.class, () -> sink.consume("b"));
        assertEquals(1, captured.size(), "First flush should have been attempted");
        assertEquals(List.of("a", "b"), captured.get(0), "First flush should contain a and b");

        // consume "c" to add to retained buffer, then close triggers flush (callCount=2 succeeds)
        sink.consume("c");
        sink.close();

        assertTrue(captured.size() >= 2, "Should have at least 2 flush calls");
    }

    @Test
    void testCloseFlushFailureSwallowed() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<List<String>> captured = new ArrayList<>();

        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            captured.add(new ArrayList<>(items));
            if (callCount.incrementAndGet() > 0) {
                throw new StreamException("flush always fails");
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 2);

        sink.consume("a");

        assertDoesNotThrow(sink::close);
        assertEquals(1, captured.size(), "close() should have attempted flush");
    }
}
