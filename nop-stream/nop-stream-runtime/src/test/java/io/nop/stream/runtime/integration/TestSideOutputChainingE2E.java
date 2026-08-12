package io.nop.stream.runtime.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.WindowOperator;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import io.nop.stream.core.execution.StreamTaskInvokable;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RL-7 (R15-AR-4) end-to-end verification of the output-contract family fix:
 * {@code WindowOperator.sideOutput(lateDataOutputTag)} → chained {@link ChainingOutput} →
 * registered side-output consumer (plan guide Rule #22 / #23). Without a registered consumer
 * the emission must fail fast ({@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}) — never silently drop.
 *
 * <p>Test location: nop-stream-runtime (the side-output entry point lives in runtime's
 * WindowOperator; nop-stream-core cannot depend on runtime).
 */
public class TestSideOutputChainingE2E {

    private static final long WINDOW_SIZE = 100L;

    /** Window operator exposing internal watermark advancement (runtime-side test helper). */
    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            if (internalTimerService instanceof HeapInternalTimerService) {
                ((HeapInternalTimerService<String, TimeWindow>) internalTimerService).advanceWatermark(timestamp);
            }
        }
    }

    static class SumWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            if (input instanceof SimpleAccumulator) {
                out.collect("sum=" + ((SimpleAccumulator<?>) input).getLocalValue());
            } else {
                out.collect("value=" + input);
            }
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<TimeWindow> duplicate() { return this; }

        @Override
        public TimeWindow createInstance() { return new TimeWindow(0, 0); }

        @Override
        public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }

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

    /** Records everything delivered through the chained input (downstream of the window op). */
    static class RecordingInput implements Input<Object> {
        final List<StreamRecord<?>> received = new ArrayList<>();

        @Override
        public void processElement(StreamRecord<Object> element) {
            received.add(element);
        }

        @Override
        public void processWatermark(Watermark mark) {
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus watermarkStatus) {
        }

        @Override
        public void processLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void setKeyContextElement(StreamRecord<Object> record) {
        }
    }

    private static TestableWindowOperator newWindowOperator(OutputTag<Integer> lateTag) {
        return new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new SumWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                lateTag
        );
    }

    /**
     * End-to-end (Rule #22): WindowOperator.sideOutput(lateDataOutputTag) → ChainingOutput with
     * a registered consumer → the consumer actually receives the late record.
     */
    @Test
    void testWindowOperatorSideOutputReachesChainedConsumer() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);
        List<StreamRecord<Integer>> sideReceived = new ArrayList<>();

        TestableWindowOperator windowOp = newWindowOperator(lateTag);
        RecordingInput downstream = new RecordingInput();
        Map<OutputTag<?>, Consumer<StreamRecord<?>>> consumers = new HashMap<>();
        ChainingOutput<Object> chainOutput = new ChainingOutput<Object>(downstream, "test-chain", consumers);
        chainOutput.registerSideOutputConsumer(lateTag, sideReceived::add);

        windowOp.setOutput((Output) chainOutput);
        windowOp.open();
        try {
            // Element in window [0,100); advance past the window end.
            windowOp.processElement(new StreamRecord<>(5, 10));
            windowOp.advanceInternalWatermark(200);

            // Late element (ts=50 <= watermark 200, allowedLateness=0) → sideOutput path.
            windowOp.processElement(new StreamRecord<>(99, 50));

            assertEquals(1, sideReceived.size(),
                    "RL-7: side-output record must reach the registered consumer");
            assertEquals(99, sideReceived.get(0).getValue());
            assertEquals(50, sideReceived.get(0).getTimestamp());
        } finally {
            windowOp.close();
        }
    }

    /**
     * No-consumer fail-fast (Rule #24): without a registered consumer the side-output emission
     * must throw ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER, not silently drop.
     */
    @Test
    void testWindowOperatorSideOutputFailsFastWithoutConsumer() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);

        TestableWindowOperator windowOp = newWindowOperator(lateTag);
        RecordingInput downstream = new RecordingInput();
        // Shared map WITHOUT any registration for lateTag.
        Map<OutputTag<?>, Consumer<StreamRecord<?>>> consumers = new HashMap<>();
        windowOp.setOutput((Output) new ChainingOutput<Object>(downstream, "test-chain", consumers));
        windowOp.open();
        try {
            windowOp.processElement(new StreamRecord<>(5, 10));
            windowOp.advanceInternalWatermark(200);

            StreamRuntimeException ex = assertThrows(StreamRuntimeException.class,
                    () -> windowOp.processElement(new StreamRecord<>(99, 50)),
                    "RL-7: side output without a registered consumer must fail fast");
            assertTrue(ex.getMessage().contains("late-data"),
                    "fail-fast error must name the unregistered side-output tag, got: " + ex.getMessage());
        } finally {
            windowOp.close();
        }
    }

    /**
     * Cross-task interim fail-fast (Cycle 2 / I4, WI-C2-1): the tail operator's Output is a
     * {@code RecordWriterOutput} (single fan-out writer) or {@code BroadcastingRecordWriterOutput}
     * (2+ fan-out writers), injected by {@link StreamTaskInvokable}'s fanOutWriters tail wiring
     * (GraphExecutionPlan → {@code StreamTaskInvokable(chain, fanOutWriters)} →
     * {@code wireOperators(fanOutWriters)} → {@code setOutput}). A side-output emission through
     * that tail output must fail fast with {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER} — never
     * silently drop (plan guide Rule #22 / #23 / #24).
     */
    @Test
    void testCrossTaskTailOutputFailsFastOnSideOutput() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);

        // RWO path: a single fan-out writer wires the tail operator to RecordWriterOutput.
        RecordWriter<Object> singleWriter = new RecordWriter<>(new ResultPartition());
        TestableWindowOperator rwoWindowOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) rwoWindowOp)),
                List.of(singleWriter));
        assertTrue(rwoWindowOp.getOutput().getClass().getName().endsWith("$RecordWriterOutput"),
                "tail operator must be wired to the cross-task RecordWriterOutput by wireOperators(fanOutWriters)");

        // BRWO path: 2 fan-out writers wire the tail operator to BroadcastingRecordWriterOutput.
        RecordWriter<Object> writer1 = new RecordWriter<>(new ResultPartition());
        RecordWriter<Object> writer2 = new RecordWriter<>(new ResultPartition());
        TestableWindowOperator brwoWindowOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) brwoWindowOp)),
                List.of(writer1, writer2));
        assertTrue(brwoWindowOp.getOutput().getClass().getName().endsWith("$BroadcastingRecordWriterOutput"),
                "2+ fan-out writers must wire the tail operator to the cross-task "
                        + "BroadcastingRecordWriterOutput");

        assertCrossTaskSideOutputFailsFast(rwoWindowOp, lateTag, "RecordWriterOutput");
        assertCrossTaskSideOutputFailsFast(brwoWindowOp, lateTag, "BroadcastingRecordWriterOutput");
    }

    private void assertCrossTaskSideOutputFailsFast(TestableWindowOperator windowOp,
                                                    OutputTag<Integer> lateTag,
                                                    String outputName) throws Exception {
        windowOp.open();
        try {
            windowOp.processElement(new StreamRecord<>(5, 10));
            windowOp.advanceInternalWatermark(200);

            StreamRuntimeException ex = assertThrows(StreamRuntimeException.class,
                    () -> windowOp.processElement(new StreamRecord<>(99, 50)),
                    "cross-task " + outputName + " side-output emission must fail fast, never silently drop");
            assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                    "cross-task fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
            assertTrue(ex.getMessage().contains("late-data"),
                    "fail-fast error must name the unregistered side-output tag, got: " + ex.getMessage());
        } finally {
            windowOp.close();
        }
    }
    /**
     * Wiring verification (Rule #23): a ChainingOutput created by {@link StreamTaskInvokable}
     * wiring (operator chain [windowOp, sinkOp]) must deliver side-output records to a consumer
     * registered on the invokable — runtime connectivity, not just type presence.
     */
    @Test
    void testInvokableWiredChainingOutputDeliversSideOutput() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);
        List<StreamRecord<Integer>> sideReceived = new ArrayList<>();

        TestableWindowOperator windowOp = newWindowOperator(lateTag);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new io.nop.stream.core.common.functions.SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        });

        List<io.nop.stream.core.operators.StreamOperator<?>> operators = new ArrayList<>();
        operators.add(windowOp);
        operators.add(sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        // Registration after wiring: the shared consumer map makes it visible to the
        // ChainingOutput created by wireOperators().
        invokable.registerSideOutputConsumer(lateTag, sideReceived::add);

        windowOp.open();
        sinkOp.open();
        try {
            windowOp.processElement(new StreamRecord<>(5, 10));
            windowOp.advanceInternalWatermark(200);
            windowOp.processElement(new StreamRecord<>(99, 50));

            assertEquals(1, sideReceived.size(),
                    "RL-7: StreamTaskInvokable-wired ChainingOutput must deliver the side output");
            assertEquals(99, sideReceived.get(0).getValue());
        } finally {
            windowOp.close();
            sinkOp.close();
        }
    }
}
