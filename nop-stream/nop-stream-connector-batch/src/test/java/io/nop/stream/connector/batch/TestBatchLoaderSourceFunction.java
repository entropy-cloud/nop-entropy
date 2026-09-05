/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.stream.connector.batch.testsupport.TestAwait;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // 确定性信号：等 runner 线程真正进入源读取阻塞点后再 cancel
        TestAwait.untilThreadSettles("source runner entered read loop", runner);
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
        // AR-13 (next-index convention, aligned with CollectionReplayableSource): the
        // offset reports the EMITTED RECORD COUNT (3), not the last-emitted index (2).
        assertEquals(3, source.getCurrentOffset());
    }

    @Test
    void testRunAfterCancelResumesDataFlow() throws Exception {
        // P0 (region restart): SupervisionLoop Phase 1 cancels the task, which calls
        // StreamSourceOperator.close() -> sourceFunction.cancel() (running=false).
        // Phase 3 rebuilds the task with a deep-copied chain that SHARES this source
        // instance and calls run() again. run() must reset the running flag and emit
        // data again instead of returning immediately (silent EOS).
        //
        // AR-13 (new semantics): the provider models a DETERMINISTIC re-traversal —
        // each run() re-reads the dataset from position 0 (like a re-executed query
        // with ORDER BY). Run 1 is cancelled mid-stream after 2 records (offset=2);
        // the restart run() client-side skips those 2 already-emitted records and
        // resumes from record 2 — data flow resumes with no duplicate re-emission.
        List<String> dataset = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        java.util.concurrent.CountDownLatch thirdRecordGate = new java.util.concurrent.CountDownLatch(1);
        IBatchLoaderProvider<String> provider = ctx -> {
            final int[] pos = {0};
            return (batchSize, chunkCtx) -> {
                if (pos[0] == 2) {
                    // deterministic cancellation point: run 1 parks after a,b are
                    // served, before the 3rd record — the test cancels here.
                    try {
                        thirdRecordGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (pos[0] >= dataset.size()) {
                    return Collections.emptyList();
                }
                List<String> batch = new ArrayList<>(dataset.subList(pos[0],
                        Math.min(pos[0] + batchSize, dataset.size())));
                pos[0] += batch.size();
                return batch;
            };
        };

        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider, 1);

        List<String> collected1 = Collections.synchronizedList(new ArrayList<>());
        Thread runner = new Thread(() -> {
            try {
                source.run(collectingContext(collected1));
            } catch (Exception e) {
                throw new StreamException("Source.run() failed", e);
            }
        });
        runner.start();
        while (collected1.size() < 2) {
            Thread.sleep(5);
        }
        Thread.sleep(50); // let the runner park on the gate
        // Region restart Phase 1: cancel interrupts the in-flight run after a,b.
        source.cancel();
        thirdRecordGate.countDown();
        runner.join(2000);
        assertFalse(runner.isAlive());
        assertEquals(Arrays.asList("a", "b"), collected1);
        assertEquals(2, source.getCurrentOffset());

        // Region restart Phase 3: the rebuilt task re-runs the SAME instance.
        List<String> collected2 = new ArrayList<>();
        source.run(collectingContext(collected2));
        assertEquals(Arrays.asList("c", "d"), collected2,
                "run() after cancel() must reset the running flag, skip the 2 already-emitted "
                        + "records of the deterministic re-traversal, and resume emission");
        assertEquals(4, source.getCurrentOffset());
    }

    @Test
    void testSeekSetsOffset() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(ctx -> (batchSize, chunkCtx) -> Collections.emptyList(), 1);
        // AR-13: next-index convention — 0 means "nothing emitted yet".
        assertEquals(0, source.getCurrentOffset());
        source.seek(42);
        assertEquals(42, source.getCurrentOffset());
    }

    @Test
    void testSeekNegativeOffsetRejectedTyped() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(
                ctx -> (batchSize, chunkCtx) -> Collections.emptyList(), 1);
        // AR-13: negative offset is a typed rejection, never a silent clamp.
        StreamException ex = assertThrows(StreamException.class, () -> source.seek(-1));
        assertTrue(String.valueOf(ex).contains("offset"),
                "rejection names the offending argument: " + ex);
    }

    @Test
    void testSeekThenRunResumesFromOffsetWithoutFullReplay() throws Exception {
        // Stable dataset re-supplied per run (deterministic loader traversal — the
        // documented precondition of client-side skip).
        List<String> dataset = Arrays.asList("a", "b", "c", "d", "e");
        List<String> data1 = new ArrayList<>(dataset);
        List<String> data2 = new ArrayList<>(dataset);

        // Run 1: full emission, offset = emitted count (next-index convention).
        List<String> collected1 = new ArrayList<>();
        BatchLoaderSourceFunction<String> source1 = new BatchLoaderSourceFunction<>(
                providerOver(data1), 2);
        source1.run(collectingContext(collected1));
        assertEquals(dataset, collected1);
        assertEquals(5, source1.getCurrentOffset(),
                "offset after full run = emitted record count");

        // Recovery: seek(checkpointed offset=3 — after a,b,c were emitted and ckpt'd)
        // then re-run over the SAME dataset traversal: only records 3..4 may be emitted.
        List<String> collected2 = new ArrayList<>();
        BatchLoaderSourceFunction<String> source2 = new BatchLoaderSourceFunction<>(
                providerOver(data2), 2);
        source2.seek(3);
        source2.run(collectingContext(collected2));

        assertEquals(Arrays.asList("d", "e"), collected2,
                "seek(3) + run must resume from record 3 — no full re-emission, no duplicates of a/b/c");
        assertEquals(5, source2.getCurrentOffset(),
                "checkpoint-reported offset after continuation equals total emitted");
        assertFalse(collected2.contains("a") || collected2.contains("b") || collected2.contains("c"),
                "records before the checkpointed offset must NOT be re-emitted");
    }

    @Test
    void testSkipShortfallFailsTypedInsteadOfSilentMisposition() throws Exception {
        // Dataset of 3 records, but the checkpoint claims 10 emitted: the loader cannot
        // traverse 10 records — run must fail typed (deterministic-order precondition
        // violated / dataset shrank), never silently emit from a wrong position.
        List<String> data = new ArrayList<>(Arrays.asList("x", "y", "z"));
        List<String> collected = new ArrayList<>();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(
                providerOver(data), 1);
        source.seek(10);

        StreamException ex = assertThrows(StreamException.class,
                () -> source.run(collectingContext(collected)));
        assertTrue(String.valueOf(ex).contains("skip shortfall"),
                "failure explains the skip shortfall: " + ex);
        assertEquals(Collections.emptyList(), collected,
                "nothing is emitted when the skip cannot be fulfilled");
    }

    private static <T> IBatchLoaderProvider<T> providerOver(List<T> data) {
        return ctx -> (batchSize, chunkCtx) -> {
            if (data.isEmpty()) return Collections.emptyList();
            List<T> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && !data.isEmpty(); i++) {
                batch.add(data.remove(0));
            }
            return batch;
        };
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

        assertEquals(3, source.getCurrentOffset());

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        // AR-13 (next-index convention): the snapshot carries the EMITTED COUNT (3),
        // not the last-emitted index (2) — restore + seek(3) + run skips exactly the
        // first 3 records of a deterministic re-traversal.
        assertEquals(3L, snapshot.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY));

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

        assertEquals(3L, restoredOffset.get());
    }
}
