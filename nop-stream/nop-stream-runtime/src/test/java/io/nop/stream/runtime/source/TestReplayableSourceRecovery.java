/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TestReplayableSourceRecovery {

    private List<String> buildData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "element-" + i)
                .collect(Collectors.toList());
    }

    @Test
    void testSeekAndGetCurrentOffset() {
        CollectionReplayableSource<String> source =
                new CollectionReplayableSource<>(buildData(100));

        assertEquals(0, source.getCurrentOffset());

        source.seek(50);
        assertEquals(50, source.getCurrentOffset());
    }

    @Test
    void testRunEmitsAllElements() throws Exception {
        List<String> data = buildData(5);
        CollectionReplayableSource<String> source = new CollectionReplayableSource<>(data);
        List<String> collected = new ArrayList<>();

        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source.run(ctx);
        assertEquals(data, collected);
        assertEquals(5, source.getCurrentOffset());
    }

    @Test
    void testRunFromSeekOffset() throws Exception {
        List<String> data = buildData(100);
        CollectionReplayableSource<String> source = new CollectionReplayableSource<>(data);
        source.seek(50);
        assertEquals(50, source.getCurrentOffset());

        List<String> collected = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source.run(ctx);
        assertEquals(50, collected.size());
        assertEquals("element-50", collected.get(0));
        assertEquals("element-99", collected.get(49));
        assertEquals(100, source.getCurrentOffset());
    }

    @Test
    void testSnapshotStateSavesOffset() throws Exception {
        CollectionReplayableSource<String> source =
                new CollectionReplayableSource<>(buildData(100));
        source.seek(42);

        StreamSourceOperator<String> operator = new StreamSourceOperator<>(source);
        OperatorSnapshotResult snapshot = operator.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        Object offsetObj = snapshot.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY);
        assertNotNull(offsetObj);
        assertTrue(offsetObj instanceof Number);
        assertEquals(42L, ((Number) offsetObj).longValue());
    }

    @Test
    void testRestoreStateSeeksToOffset() throws Exception {
        CollectionReplayableSource<String> originalSource =
                new CollectionReplayableSource<>(buildData(100));
        originalSource.seek(50);

        StreamSourceOperator<String> originalOp = new StreamSourceOperator<>(originalSource);
        OperatorSnapshotResult snapshot = originalOp.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        CollectionReplayableSource<String> restoredSource =
                new CollectionReplayableSource<>(buildData(100));
        assertEquals(0, restoredSource.getCurrentOffset());

        StreamSourceOperator<String> restoredOp = new StreamSourceOperator<>(restoredSource);
        restoredOp.restoreState(snapshot);

        assertEquals(50, restoredSource.getCurrentOffset());
    }

    @Test
    void testFullRecoveryCycle() throws Exception {
        List<String> data = buildData(100);

        CollectionReplayableSource<String> source1 = new CollectionReplayableSource<>(data);
        List<String> firstBatch = new ArrayList<>();
        SourceFunction.SourceContext<String> collectingCtx = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                firstBatch.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source1.run(collectingCtx);
        assertEquals(100, firstBatch.size());

        StreamSourceOperator<String> op1 = new StreamSourceOperator<>(source1);
        OperatorSnapshotResult snapshot = op1.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        Long savedOffset = snapshot.getOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY, Long.class);
        assertNotNull(savedOffset);
        assertEquals(100L, savedOffset.longValue());
        assertEquals(100, savedOffset);

        CollectionReplayableSource<String> source2 = new CollectionReplayableSource<>(data);
        StreamSourceOperator<String> op2 = new StreamSourceOperator<>(source2);
        op2.restoreState(snapshot);
        assertEquals(100, source2.getCurrentOffset());

        List<String> secondBatch = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx2 = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                secondBatch.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source2.run(ctx2);
        assertTrue(secondBatch.isEmpty(), "All elements already emitted; replay from end yields nothing");
    }

    @Test
    void testPartialRecoveryCycle() throws Exception {
        List<String> data = buildData(100);

        CollectionReplayableSource<String> source1 = new CollectionReplayableSource<>(data);
        source1.seek(0);
        List<String> firstBatch = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx1 = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                firstBatch.add(element);
                if (firstBatch.size() == 50) {
                    currentOffset = 50;
                }
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

            private long currentOffset;
        };

        source1.seek(50);
        source1.run(ctx1);
        assertEquals(50, firstBatch.size());

        StreamSourceOperator<String> op1 = new StreamSourceOperator<>(source1);
        OperatorSnapshotResult snapshot = op1.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        CollectionReplayableSource<String> source2 = new CollectionReplayableSource<>(data);
        StreamSourceOperator<String> op2 = new StreamSourceOperator<>(source2);
        op2.restoreState(snapshot);

        assertEquals(100, source2.getCurrentOffset());

        List<String> secondBatch = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx2 = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                secondBatch.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source2.run(ctx2);
        assertTrue(secondBatch.isEmpty());
    }

    @Test
    void testRestoreFromSpecificOffsetThenEmit() throws Exception {
        List<String> data = buildData(100);

        OperatorSnapshotResult snapshot = OperatorSnapshotResult.builder()
                .putOperatorState(StreamSourceOperator.SOURCE_OFFSET_KEY,
                        "30".getBytes(StandardCharsets.UTF_8))
                .build();

        CollectionReplayableSource<String> source = new CollectionReplayableSource<>(data);
        StreamSourceOperator<String> op = new StreamSourceOperator<>(source);
        op.restoreState(snapshot);
        assertEquals(30, source.getCurrentOffset());

        List<String> collected = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source.run(ctx);
        assertEquals(70, collected.size());
        assertEquals("element-30", collected.get(0));
        assertEquals("element-99", collected.get(69));
    }

    @Test
    void testEmptyData() throws Exception {
        CollectionReplayableSource<String> source =
                new CollectionReplayableSource<>(Collections.emptyList());
        assertEquals(0, source.getCurrentOffset());

        List<String> collected = new ArrayList<>();
        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

        source.run(ctx);
        assertTrue(collected.isEmpty());
        assertEquals(0, source.getCurrentOffset());
    }
}
