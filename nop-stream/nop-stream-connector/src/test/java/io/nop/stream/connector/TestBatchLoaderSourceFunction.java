/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    private <T> Output<StreamRecord<T>> nopOutput() {
        return new Output<>() {
            @Override public void collect(StreamRecord<T> record) {}
            @Override public void close() {}
            @Override public void emitWatermark(Watermark watermark) {}
            @Override public void emitWatermarkStatus(WatermarkStatus status) {}
            @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}
            @Override public void emitLatencyMarker(LatencyMarker latencyMarker) {}
            @Override public void emitBarrier(CheckpointBarrier barrier) {}
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
            } catch (Exception e) {
                throw new StreamException("Source.run() failed", e);
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
        assertThrows(StreamException.class, () -> new BatchLoaderSourceFunction<>(null));
    }

    @Test
    void testEmptyLoaderCompletes() throws Exception {
        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> Collections.emptyList();

        List<String> collected = new ArrayList<>();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider);
        source.run(collectingContext(collected));

        assertTrue(collected.isEmpty());
    }

    @Test
    void testReplayableSourceFunctionGetCurrentOffset() throws Exception {
        List<String> data = new ArrayList<>(Arrays.asList("a", "b", "c"));
        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> {
            if (data.isEmpty()) return Collections.emptyList();
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                batch.add(data.remove(0));
            }
            return batch;
        };

        ReplayableSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider, 1);
        List<String> collected = new ArrayList<>();
        source.run(new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) { collected.add(element); }
            @Override public void collectWithTimestamp(String element, long timestamp) { collected.add(element); }
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        });

        assertEquals(3, collected.size());
        assertEquals(2, source.getCurrentOffset());
    }

    @Test
    void testSeekSetsOffset() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(ctx -> (batchSize, chunkCtx) -> Collections.emptyList(), 1);
        assertEquals(-1, source.getCurrentOffset());
        source.seek(42);
        assertEquals(42, source.getCurrentOffset());
    }

    @Test
    void testStreamSourceOperatorCheckpointRestoreWithBatchLoader() throws Exception {
        List<String> data = new ArrayList<>(Arrays.asList("x", "y", "z"));
        IBatchLoaderProvider<String> provider = ctx -> (batchSize, chunkCtx) -> {
            if (data.isEmpty()) return Collections.emptyList();
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                batch.add(data.remove(0));
            }
            return batch;
        };

        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider, 1);
        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        operator.setOutput(nopOutput());

        source.run(new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) {}
            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        });

        assertEquals(2, source.getCurrentOffset());

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        assertEquals(2L, snapshot.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY));

        AtomicLong restoredOffset = new AtomicLong(-1);
        BatchLoaderSourceFunction<String> restoredSource = new BatchLoaderSourceFunction<>(provider, 1) {
            @Override
            public void seek(long offset) {
                super.seek(offset);
                restoredOffset.set(offset);
            }
        };
        StreamSourceOperator<String> restoredOp = new StreamSourceOperator<>(restoredSource);
        restoredOp.setOutput(nopOutput());
        restoredOp.restoreState(snapshot);

        assertEquals(2L, restoredOffset.get());
    }
}
