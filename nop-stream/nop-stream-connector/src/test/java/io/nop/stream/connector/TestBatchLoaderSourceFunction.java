/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestBatchLoaderSourceFunction {

    private <T> SourceFunction.SourceContext<T> collectingContext(List<T> target) {
        return new SourceFunction.SourceContext<>() {
            @Override
            public void collect(T element) {
                target.add(element);
            }

            @Override
            public void collectWithTimestamp(T element, long timestamp) {
                target.add(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    @Test
    void testEmitAllRecords() throws Exception {
        List<String> data = new ArrayList<>(Arrays.asList("a", "b", "c"));
        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> {
            if (data.isEmpty()) return Collections.emptyList();
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                batch.add(data.remove(0));
            }
            return batch;
        };

        List<String> collected = new ArrayList<>();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider);
        source.run(collectingContext(collected));

        assertEquals(Arrays.asList("a", "b", "c"), collected);
    }

    @Test
    void testCancel() throws Exception {
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 1000; i++) data.add(i);

        IBatchLoaderProvider<Integer> provider = ctx -> (batchSize, chunkCtx) -> {
            synchronized (data) {
                if (data.isEmpty()) return Collections.emptyList();
                List<Integer> batch = new ArrayList<>();
                for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                    batch.add(data.remove(0));
                }
                return batch;
            }
        };

        List<Integer> collected = new ArrayList<>();
        BatchLoaderSourceFunction<Integer> source = new BatchLoaderSourceFunction<>(provider);

        Thread runner = new Thread(() -> {
            try {
                source.run(collectingContext(collected));
            } catch (Exception ignored) {
            }
        });
        runner.start();

        Thread.sleep(50);
        source.cancel();
        runner.join(2000);

        assertFalse(runner.isAlive());
    }

    @Test
    void testBatchSizeParameter() throws Exception {
        List<String> data = new ArrayList<>(Arrays.asList("x", "y", "z"));
        final int[] capturedBatchSize = {0};

        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> {
            capturedBatchSize[0] = batchSize;
            if (data.isEmpty()) return Collections.emptyList();
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                batch.add(data.remove(0));
            }
            return batch;
        };

        List<String> collected = new ArrayList<>();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider, 3);
        source.run(collectingContext(collected));

        assertEquals(3, capturedBatchSize[0]);
        assertEquals(Arrays.asList("x", "y", "z"), collected);
    }

    @Test
    void testNullProviderRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BatchLoaderSourceFunction<>(null));
    }

    @Test
    void testEmptyLoaderCompletes() throws Exception {
        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> Collections.emptyList();

        List<String> collected = new ArrayList<>();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider);
        source.run(collectingContext(collected));

        assertTrue(collected.isEmpty());
    }
}
