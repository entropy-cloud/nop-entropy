package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowAggregationOperatorSnapshotRestore {

    private static final class SumAggFunction
            implements WindowAggregationFunction<Integer, Long, Long, String, TimeWindow> {
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Integer value, Long accumulator) {
            return accumulator + value;
        }

        @Override
        public void emitResult(String key, TimeWindow window, Long accumulator, Collector<Long> out) {
            out.collect(accumulator);
        }
    }

    private static final class StringKeySelector implements KeySelector<Integer, String> {
        private final String fixedKey;

        StringKeySelector(String fixedKey) {
            this.fixedKey = fixedKey;
        }

        @Override
        public String getKey(Integer value) {
            return fixedKey;
        }
    }

    private List<Long> collected;
    private Output<StreamRecord<Long>> output;

    @BeforeEach
    void setUp() {
        collected = new ArrayList<>();
        output = new Output<StreamRecord<Long>>() {
            @Override
            public void collect(StreamRecord<Long> record) {
                collected.add(record.getValue());
            }

            @Override
            public void close() {
            }

            @Override
            public void emitWatermark(Watermark mark) {
            }

            @Override
            public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            }

            @Override
            public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            }

            @Override
            public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            }

            @Override
            public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            }
        };
    }

    private WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> createSumOperator() {
        return new WindowAggregationOperator<>(
                TumblingEventTimeWindows.of(100),
                EventTimeTrigger.create(),
                new SumAggFunction(),
                new StringKeySelector("key1"));
    }

    @Test
    void testSnapshotReturnsOperatorState() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op = createSumOperator();
        op.open();
        op.output = output;

        op.setCurrentKey("key1");
        op.processElement(new StreamRecord<>(10, 10L));
        op.processElement(new StreamRecord<>(20, 20L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult result = op.snapshotState(ctx);

        assertNotNull(result);
        assertNull(result.getKeyedStates().get("keyed-state"));
        assertNotNull(result.getOperatorState("window-aggregation-state"));
    }

    @Test
    void testRestoreSingleWindow() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(10, 10L));
        op1.processElement(new StreamRecord<>(20, 20L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot);
        op2.output = output;

        op2.setCurrentKey("key1");
        op2.processElement(new StreamRecord<>(30, 30L));

        op2.processWatermark(new Watermark(150L));

        assertEquals(1, collected.size());
        assertEquals(60L, collected.get(0));
    }

    @Test
    void testRestoreMultipleKeys() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 =
                new WindowAggregationOperator<>(
                        TumblingEventTimeWindows.of(100),
                        EventTimeTrigger.create(),
                        new SumAggFunction(),
                        null);
        op1.open();
        op1.output = output;

        op1.setCurrentKey("a");
        op1.processElement(new StreamRecord<>(10, 10L));
        op1.setCurrentKey("b");
        op1.processElement(new StreamRecord<>(20, 15L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 =
                new WindowAggregationOperator<>(
                        TumblingEventTimeWindows.of(100),
                        EventTimeTrigger.create(),
                        new SumAggFunction(),
                        null);
        op2.restoreState(snapshot);
        op2.output = output;

        op2.setCurrentKey("a");
        op2.processElement(new StreamRecord<>(5, 20L));
        op2.setCurrentKey("b");
        op2.processElement(new StreamRecord<>(5, 25L));

        op2.processWatermark(new Watermark(150L));

        assertEquals(2, collected.size());
        assertTrue(collected.contains(15L));
        assertTrue(collected.contains(25L));
    }

    @Test
    void testTimerRestoreAndFire() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(10, 10L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot);
        op2.output = output;

        op2.processWatermark(new Watermark(150L));

        assertEquals(1, collected.size());
        assertEquals(10L, collected.get(0));
    }

    @Test
    void testRestoreThrowsOnMissingState() throws Exception {
        OperatorSnapshotResult empty = new OperatorSnapshotResult();
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op = createSumOperator();

        assertThrows(IllegalStateException.class, () -> op.restoreState(empty));
    }

    @Test
    void testRestoreThrowsOnVersionMismatch() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;
        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(10, 10L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        Object stateObj = snapshot.getOperatorState("window-aggregation-state");
        if (stateObj instanceof WindowAggregationState) {
            ((WindowAggregationState) stateObj).setVersion(999);
        }

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        assertThrows(IllegalStateException.class, () -> op2.restoreState(snapshot));
    }

    @Test
    void testRestoreWatermarkExceedsTimerTimestamp() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(10, 10L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot);
        op2.output = output;

        op2.processWatermark(new Watermark(Long.MAX_VALUE - 1));

        assertFalse(collected.isEmpty());
        assertEquals(10L, collected.get(0));
    }

    @Test
    void testCheckpointRestoreFullCycle() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(1, 5L));
        op1.processElement(new StreamRecord<>(2, 15L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot);
        op2.output = output;

        op2.setCurrentKey("key1");
        op2.processElement(new StreamRecord<>(3, 25L));
        op2.processElement(new StreamRecord<>(4, 35L));

        op2.processWatermark(new Watermark(150L));

        assertEquals(1, collected.size());
        assertEquals(10L, collected.get(0));
    }

    @Test
    void testSnapshotEmptyState() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op = createSumOperator();
        op.open();
        op.output = output;

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult result = op.snapshotState(ctx);

        assertNotNull(result);
        assertNotNull(result.getOperatorState("window-aggregation-state"));
    }

    @Test
    void testRestoreThenSnapshotRoundTrip() throws Exception {
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        op1.setCurrentKey("key1");
        op1.processElement(new StreamRecord<>(10, 10L));

        StateSnapshotContext ctx1 = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot1 = op1.snapshotState(ctx1);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot1);
        op2.output = output;

        op2.setCurrentKey("key1");
        op2.processElement(new StreamRecord<>(20, 20L));

        StateSnapshotContext ctx2 = new StateSnapshotContext(2, System.currentTimeMillis());
        OperatorSnapshotResult snapshot2 = op2.snapshotState(ctx2);

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op3 = createSumOperator();
        op3.restoreState(snapshot2);
        op3.output = output;

        op3.processWatermark(new Watermark(150L));

        assertEquals(1, collected.size());
        assertEquals(30L, collected.get(0));
    }

    @Test
    void testRestoreWatermarkZeroPreservedAfterOpen() throws Exception {
        // Regression test: currentWatermark == 0 used to be overwritten to Long.MIN_VALUE by open()
        // because the init check couldn't distinguish "unset" from "legitimately zero".
        // After fix, watermarkInitialized flag is used instead.

        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op1 = createSumOperator();
        op1.open();
        op1.output = output;

        // Advance watermark to 0 (from Long.MIN_VALUE default)
        op1.processWatermark(new Watermark(0L));

        op1.setCurrentKey("key1");
        // Element at timestamp 50, within window [0,100), should be accepted if watermark is 0
        op1.processElement(new StreamRecord<>(10, 50L));

        StateSnapshotContext ctx = new StateSnapshotContext(1, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op1.snapshotState(ctx);

        // Verify snapshot contains watermark == 0
        Object stateObj = snapshot.getOperatorState("window-aggregation-state");
        if (stateObj instanceof WindowAggregationState) {
            WindowAggregationState state = (WindowAggregationState) stateObj;
            assertEquals(0L, state.getCurrentWatermark(),
                    "Snapshot should preserve currentWatermark == 0");
        }

        // Restore into a new operator (simulates checkpoint recovery)
        WindowAggregationOperator<Integer, Long, Long, String, TimeWindow> op2 = createSumOperator();
        op2.restoreState(snapshot);
        // open() is called by the execution framework after restoreState()
        op2.open();
        op2.output = output;

        // Add more elements — these should be processed correctly with watermark at 0
        op2.setCurrentKey("key1");
        op2.processElement(new StreamRecord<>(20, 60L));

        // Fire the window with watermark at 150
        op2.processWatermark(new Watermark(150L));

        // Window [0,100) should fire and produce sum = 30 (10 + 20)
        assertFalse(collected.isEmpty(), "Window should have fired after restore");
        assertEquals(30L, collected.get(0), "Window sum should be 30 (10+20)");
    }
}
