/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchConsumerProvider;
import org.junit.jupiter.api.Test;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class TestBatchConsumerSinkFunction {

    @Test
    void testBufferAndFlush() throws Exception {
        List<List<String>> flushed = new CopyOnWriteArrayList<>();
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> flushed.add(new ArrayList<>(items));

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 3);

        sink.consume("a");
        sink.consume("b");
        assertTrue(flushed.isEmpty(), "should not flush before batch is full");

        sink.consume("c");
        assertEquals(1, flushed.size());
        assertEquals(List.of("a", "b", "c"), flushed.get(0));

        sink.close();
        assertTrue(flushed.size() == 1, "no remaining items to flush");
    }

    @Test
    void testCloseFlushesRemaining() throws Exception {
        List<List<Integer>> flushed = new CopyOnWriteArrayList<>();
        IBatchConsumerProvider<Integer> provider = ctx -> (items, chunkCtx) -> flushed.add(new ArrayList<>(items));

        BatchConsumerSinkFunction<Integer> sink = new BatchConsumerSinkFunction<>(provider, 10);

        sink.consume(1);
        sink.consume(2);
        sink.consume(3);
        assertTrue(flushed.isEmpty());

        sink.close();
        assertEquals(1, flushed.size());
        assertEquals(List.of(1, 2, 3), flushed.get(0));
    }

    @Test
    void testMultipleFlushes() throws Exception {
        List<List<String>> flushed = new CopyOnWriteArrayList<>();
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> flushed.add(new ArrayList<>(items));

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 2);

        sink.consume("a");
        sink.consume("b");
        sink.consume("c");
        sink.consume("d");
        sink.consume("e");

        assertEquals(2, flushed.size());
        assertEquals(List.of("a", "b"), flushed.get(0));
        assertEquals(List.of("c", "d"), flushed.get(1));

        sink.close();
        assertEquals(3, flushed.size());
        assertEquals(List.of("e"), flushed.get(2));
    }

    @Test
    void testNullProviderRejected() {
        assertThrows(StreamException.class, () -> new BatchConsumerSinkFunction<>(null));
    }

    @Test
    void testBatchSizeOne() throws Exception {
        List<List<String>> flushed = new CopyOnWriteArrayList<>();
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> flushed.add(new ArrayList<>(items));

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 1);

        sink.consume("x");
        sink.consume("y");
        assertEquals(2, flushed.size());
        assertEquals(List.of("x"), flushed.get(0));
        assertEquals(List.of("y"), flushed.get(1));

        sink.close();
        assertEquals(2, flushed.size(), "nothing left to flush");
    }

    // ---- P1-15 boundary tests ----

    @Test
    void testBatchSizeZeroRejected() {
        // P1-15: batchSize=0 must be rejected at construction (would cause
        // buffer to never fill / infinite loop on flush threshold).
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {};
        assertThrows(StreamException.class, () -> new BatchConsumerSinkFunction<>(provider, 0),
                "batchSize=0 must be rejected");
    }

    @Test
    void testBatchSizeNegativeRejected() {
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {};
        assertThrows(StreamException.class, () -> new BatchConsumerSinkFunction<>(provider, -1),
                "negative batchSize must be rejected");
    }

    @Test
    void testConsumeNullRejected() {
        // P1-15: consume(null) must be rejected at the boundary rather than
        // accepted into the buffer (where it would surface as an opaque NPE
        // during flush, or worse be silently persisted).
        IBatchConsumerProvider<String> provider = ctx -> (items, chunkCtx) -> {};
        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 10);
        assertThrows(StreamException.class, () -> sink.consume(null),
                "consume(null) must be rejected");
    }
}
