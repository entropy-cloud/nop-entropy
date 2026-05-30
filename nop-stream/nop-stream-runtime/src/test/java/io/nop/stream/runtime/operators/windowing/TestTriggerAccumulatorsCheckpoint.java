package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestTriggerAccumulatorsCheckpoint {

    private TestOutput<String> output;
    private TestableWindowOperator operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();

        operator = new TestableWindowOperator(
                GlobalWindows.create(),
                new SimpleGlobalWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction(),
                CountTrigger.of(10),
                0L,
                null
        );

        operator.setOutput((Output) output);
        operator.open();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    @Test
    void testTriggerAccumulatorsSnapshotRestoreRoundTrip() throws Exception {
        operator.processElement(new StreamRecord<>(1, 10));
        operator.processElement(new StreamRecord<>(2, 20));
        operator.processElement(new StreamRecord<>(3, 30));

        Map<String, ?> accumulatorsBefore = operator.getTriggerAccumulators();
        assertNotNull(accumulatorsBefore, "triggerAccumulators should exist after processing elements");
        assertFalse(accumulatorsBefore.isEmpty(), "CountTrigger should have created trigger state via getSimpleAccumulator()");

        OperatorSnapshotResult snapshot = operator.snapshotState(new StateSnapshotContext(1, System.currentTimeMillis()));

        @SuppressWarnings("unchecked")
        Map<String, Object> triggerAccState = (Map<String, Object>) snapshot.getOperatorState("trigger-accumulators");
        assertNotNull(triggerAccState, "triggerAccumulators should be in snapshot");
        assertEquals(accumulatorsBefore.size(), triggerAccState.size());

        operator.close();

        TestableWindowOperator restored = new TestableWindowOperator(
                GlobalWindows.create(),
                new SimpleGlobalWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new PassthroughWindowFunction(),
                CountTrigger.of(10),
                0L,
                null
        );

        TestOutput<String> restoredOutput = new TestOutput<>();
        restored.setOutput((Output) restoredOutput);
        restored.restoreState(snapshot);
        restored.open();

        assertNotNull(restored.getTriggerAccumulators(), "triggerAccumulators should be restored");
        assertEquals(triggerAccState.size(), restored.getTriggerAccumulators().size(),
                "restored triggerAccumulators count should match snapshot");
    }

    @Test
    void testTriggerAccumulatorsEmptySnapshot() throws Exception {
        OperatorSnapshotResult snapshot = operator.snapshotState(new StateSnapshotContext(1, System.currentTimeMillis()));

        @SuppressWarnings("unchecked")
        Map<String, Object> triggerAccState = (Map<String, Object>) snapshot.getOperatorState("trigger-accumulators");
        assertNotNull(triggerAccState, "even empty triggerAccumulators HashMap should be snapshot'd");
    }

    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, GlobalWindow> {

        public TestableWindowOperator(
                GlobalWindows windowAssigner,
                TypeSerializer<GlobalWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, GlobalWindow> windowFunction,
                CountTrigger<GlobalWindow> trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        public Map<String, ?> getTriggerAccumulators() {
            return triggerAccumulators;
        }
    }

    static class PassthroughWindowFunction implements InternalWindowFunction<Object, String, String, GlobalWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, GlobalWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(GlobalWindow window, InternalWindowContext context) {
        }
    }

    static class SimpleGlobalWindowSerializer implements TypeSerializer<GlobalWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<GlobalWindow> duplicate() { return this; }

        @Override
        public GlobalWindow createInstance() { return GlobalWindow.get(); }

        @Override
        public GlobalWindow copy(GlobalWindow from) { return from; }

        @Override
        public GlobalWindow copy(GlobalWindow from, GlobalWindow reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<String> duplicate() { return this; }

        @Override
        public String createInstance() { return ""; }

        @Override
        public String copy(String from) { return from; }

        @Override
        public String copy(String from, String reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }
}
