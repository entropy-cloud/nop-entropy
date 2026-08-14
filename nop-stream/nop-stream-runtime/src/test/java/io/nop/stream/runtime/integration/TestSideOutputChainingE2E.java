package io.nop.stream.runtime.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Minimal head operator for the consumer task (side-output routing never reaches headInput). */
    static class NoopOperator extends io.nop.stream.core.operators.AbstractStreamOperator<Object>
            implements Input<Object> {
        @Override
        public void processElement(StreamRecord<Object> element) {
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
     * Cross-task side-output forwarding (HG-01, 2026-08-14 — replaces the Cycle 2 / I4 interim
     * fail-fast): the tail operator's Output is a {@code RecordWriterOutput} (single fan-out
     * writer) or {@code BroadcastingRecordWriterOutput} (2+ fan-out writers), injected by
     * {@link StreamTaskInvokable}'s fanOutWriters tail wiring. A side-output emission through
     * that tail output must wrap the tagged record into a {@code SideOutputElement} and
     * broadcast it into every downstream partition — the RWO single-writer variant covers the
     * plain delivery path; the BRWO 2-writer variant covers the multi-subtask broadcast
     * semantics (every partition receives its own element instance, D5 — no aliasing).
     */
    @Test
    void testCrossTaskTailOutputForwardsSideOutput() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);

        // RWO path: a single fan-out writer wires the tail operator to RecordWriterOutput.
        ResultPartition rwoPartition = new ResultPartition();
        RecordWriter<Object> singleWriter = new RecordWriter<>(rwoPartition);
        TestableWindowOperator rwoWindowOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) rwoWindowOp)),
                List.of(singleWriter));
        assertTrue(rwoWindowOp.getOutput().getClass().getName().endsWith("$RecordWriterOutput"),
                "tail operator must be wired to the cross-task RecordWriterOutput by wireOperators(fanOutWriters)");

        // BRWO path: 2 fan-out writers wire the tail operator to BroadcastingRecordWriterOutput.
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        RecordWriter<Object> writer1 = new RecordWriter<>(p1);
        RecordWriter<Object> writer2 = new RecordWriter<>(p2);
        TestableWindowOperator brwoWindowOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) brwoWindowOp)),
                List.of(writer1, writer2));
        assertTrue(brwoWindowOp.getOutput().getClass().getName().endsWith("$BroadcastingRecordWriterOutput"),
                "2+ fan-out writers must wire the tail operator to the cross-task "
                        + "BroadcastingRecordWriterOutput");

        assertCrossTaskSideOutputForwards(rwoWindowOp, lateTag, new ResultPartition[]{rwoPartition},
                "RecordWriterOutput");
        assertCrossTaskSideOutputForwards(brwoWindowOp, lateTag, new ResultPartition[]{p1, p2},
                "BroadcastingRecordWriterOutput");
    }

    private void assertCrossTaskSideOutputForwards(TestableWindowOperator windowOp,
                                                   OutputTag<Integer> lateTag,
                                                   ResultPartition[] partitions,
                                                   String outputName) throws Exception {
        windowOp.open();
        try {
            windowOp.processElement(new StreamRecord<>(5, 10));
            windowOp.advanceInternalWatermark(200);

            windowOp.processElement(new StreamRecord<>(99, 50));
            // The fired window's main-path record (5) also flows through the same RWO, so the
            // partition holds the main record first, then the side-output element. Locate the
            // side-output element instead of asserting an exact queue size.
            java.util.List<io.nop.stream.core.streamrecord.SideOutputElement> received =
                    new ArrayList<>();
            int perPartitionTotal = 0;
            for (ResultPartition partition : partitions) {
                perPartitionTotal = 0;
                while (partition.size() > 0) {
                    perPartitionTotal++;
                    io.nop.stream.core.streamrecord.StreamElement element = partition.read();
                    if (element.isSideOutput()) {
                        received.add(element.asSideOutput());
                    }
                }
                assertEquals(2, perPartitionTotal,
                        "cross-task " + outputName + " partition must hold main record + side-output element");
            }
            assertTrue(!received.isEmpty(),
                    "cross-task " + outputName + " must enqueue at least one tagged side-output element");
            for (io.nop.stream.core.streamrecord.SideOutputElement side : received) {
                assertEquals("late-data", side.getOutputTagId(),
                        "forwarded element must carry the side-output tag id");
                assertEquals(99, side.getRecord().getValue(),
                        "forwarded element must carry the late record value");
            }
            if (received.size() > 1) {
                assertNotSame(received.get(0).getRecord(), received.get(1).getRecord(),
                        "BRWO fan-out must not alias the inner record across partitions (D5)");
                assertNotSame(received.get(0), received.get(1),
                        "BRWO fan-out must not share one element instance across partitions (D5)");
            }
        } finally {
            windowOp.close();
        }
    }

    /**
     * End-to-end cross-task delivery (HG-01, 2026-08-14): a producer tail operator emits a late
     * element → the tail RecordWriterOutput wraps it into a SideOutputElement and broadcasts it
     * through the wire partition → the consumer task's InputGate reads it → processInputGate
     * routes it to the consumer registered by tag id. Assertions on the registered consumer's
     * receipt (not just existence) — plan guide Rule #22 (runtime connectivity).
     */
    @Test
    void testCrossTaskSideOutputDeliveryReachesRegisteredConsumer() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);

        ResultPartition producerPartition = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(producerPartition);
        TestableWindowOperator producerOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) producerOp)),
                List.of(writer));

        List<StreamRecord<Integer>> sideReceived = new ArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Throwable> consumerError = new AtomicReference<>();
        InputGate consumerGate = new InputGate(List.of(new InputChannel(producerPartition)), null, false);
        StreamTaskInvokable consumer = new StreamTaskInvokable(
                new OperatorChain(List.of((StreamOperator<?>) new NoopOperator())),
                new ArrayList<RecordWriter<Object>>(), consumerGate);
        consumer.registerSideOutputConsumer(lateTag, record -> {
            sideReceived.add((StreamRecord<Integer>) (StreamRecord<?>) record);
            delivered.countDown();
        });

        Thread consumerThread = new Thread(() -> {
            try {
                consumer.invoke();
            } catch (Throwable t) {
                consumerError.set(t);
            }
        });
        consumerThread.start();

        try {
            producerOp.open();
            producerOp.processElement(new StreamRecord<>(5, 10));
            producerOp.advanceInternalWatermark(200);
            producerOp.processElement(new StreamRecord<>(99, 50));
            producerOp.close();
        } finally {
            // Close the producer partition so the consumer gate finishes and invoke() exits.
            try {
                producerPartition.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerThread.join(10_000);
        }

        assertNull(consumerError.get(),
                "consumer task must not fail, got: " + consumerError.get());
        assertTrue(delivered.getCount() == 0, "consumer must receive the cross-task side-output record");
        assertEquals(1, sideReceived.size(), "exactly one side-output record must be delivered");
        assertEquals(99, sideReceived.get(0).getValue());
    }

    /**
     * No-consumer fail-fast (HG-01, 2026-08-14): a side-output element arriving on the consumer
     * task's input gate with NO registered consumer for its tag must fail the consumer task with
     * {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER} at the routing point — never silently drop
     * (plan guide Rule #24).
     */
    @Test
    void testCrossTaskSideOutputNoConsumerFailsFastOnConsumerTask() throws Exception {
        OutputTag<Integer> lateTag = new OutputTag<>("late-data", BasicTypeInfo.INT);

        ResultPartition producerPartition = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(producerPartition);
        TestableWindowOperator producerOp = newWindowOperator(lateTag);
        new StreamTaskInvokable(new OperatorChain(List.of((StreamOperator<?>) producerOp)),
                List.of(writer));

        InputGate consumerGate = new InputGate(List.of(new InputChannel(producerPartition)), null, false);
        StreamTaskInvokable consumer = new StreamTaskInvokable(
                new OperatorChain(List.of((StreamOperator<?>) new NoopOperator())),
                new ArrayList<RecordWriter<Object>>(), consumerGate);
        // No registerSideOutputConsumer — the tag has no consumer on this task.

        AtomicReference<Throwable> consumerError = new AtomicReference<>();
        Thread consumerThread = new Thread(() -> {
            try {
                consumer.invoke();
            } catch (Throwable t) {
                consumerError.set(t);
            }
        });
        consumerThread.start();

        try {
            producerOp.open();
            producerOp.processElement(new StreamRecord<>(5, 10));
            producerOp.advanceInternalWatermark(200);
            producerOp.processElement(new StreamRecord<>(99, 50));
            producerOp.close();
        } finally {
            try {
                producerPartition.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerThread.join(10_000);
        }

        assertTrue(consumerError.get() != null,
                "unregistered side-output tag must fail the consumer task, never silently drop");
        assertTrue(consumerError.get() instanceof StreamRuntimeException,
                "fail-fast must surface as StreamRuntimeException, got: " + consumerError.get());
        StreamRuntimeException ex = (StreamRuntimeException) consumerError.get();
        assertEquals(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER.getErrorCode(), ex.getErrorCode(),
                "fail-fast must use ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER");
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
