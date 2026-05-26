/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchConsumerProvider;
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
        RuntimeException flushError = new RuntimeException("flush failed");
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            throw flushError;
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 1);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> sink.consume("a"));
        assertSame(flushError, thrown, "Exception from consumer should propagate");
    }

    @Test
    void testBufferClearedOnFlushFailure() {
        List<List<String>> captured = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);

        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            captured.add(new ArrayList<>(items));
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("first flush fails");
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 1);

        // First consume triggers flush, which fails
        assertThrows(RuntimeException.class, () -> sink.consume("a"));

        // After failure, buffer should be cleared (Bug N53 fix)
        // Second consume should not re-send "a"
        sink.consume("b");
        sink.close();

        assertEquals(2, captured.size(), "Should have exactly 2 flush calls");
        assertEquals(List.of("a"), captured.get(0), "First flush should contain 'a'");
        assertEquals(List.of("b"), captured.get(1), "Second flush should contain 'b' only (not 'a')");
    }

    @Test
    void testCloseFlushFailurePropagates() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<List<String>> captured = new ArrayList<>();

        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {
            captured.add(new ArrayList<>(items));
            if (callCount.incrementAndGet() > 0) {
                throw new RuntimeException("flush always fails");
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 2);

        sink.consume("a");

        // close() triggers flush with 1 item, which fails
        RuntimeException thrown = assertThrows(RuntimeException.class, sink::close);
        assertEquals("flush always fails", thrown.getMessage());
    }
}
