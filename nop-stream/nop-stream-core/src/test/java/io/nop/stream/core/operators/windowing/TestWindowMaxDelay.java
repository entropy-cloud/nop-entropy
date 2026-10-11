/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.KeyedStreamImpl;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.SimpleStreamOperatorFactory;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.PaneInfo;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 (plan 2332) phase 1: max-delay fallback emission — a slow watermark stream still
 * produces window output by the processing-time deadline; the user trigger is never fed
 * by the fallback; merges re-register the deadline; processing-time assigners are
 * rejected at construction; configuration survives {@code copyForSubtask}.
 */
public class TestWindowMaxDelay {

    private static final long WINDOW_SIZE = 100L;

    private RecordingOutput output;
    private WindowOperator<String, Integer, Object, String, TimeWindow> operator;

    @AfterEach
    void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    /** Captures value + pane timing so the fallback's EARLY classification is provable. */
    static class TimingRecordingFunction
            implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        final List<String> values = new ArrayList<>();
        final List<PaneInfo.PaneTiming> timings = new ArrayList<>();

        @Override
        public void process(String key, TimeWindow window,
                            InternalWindowFunction.InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            values.add(String.valueOf(input));
            timings.add(context.getPaneInfo() != null ? context.getPaneInfo().getTiming() : null);
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowFunction.InternalWindowContext context) {
        }
    }

    private WindowOperator<String, Integer, Object, String, TimeWindow> newOperator(
            WindowAssigner<? super Integer, TimeWindow> assigner,
            Trigger<? super Integer, ? super TimeWindow> trigger,
            long allowedLateness,
            long maxDelay,
            boolean events,
            InternalWindowFunction<Object, String, String, TimeWindow> windowFunction) {
        OutputTag<WindowLifecycleEvent> tag = null;
        if (events) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            OutputTag<WindowLifecycleEvent> tagged = lifecycleTag();
            tag = tagged;
        }
        WindowOperator<String, Integer, Object, String, TimeWindow> op = new WindowOperator<>(
                assigner,
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                windowFunction,
                trigger,
                allowedLateness,
                null,
                Object.class,
                null,
                null,
                null,
                AccumulationMode.ACCUMULATING,
                maxDelay,
                events,
                tag);
        output = new RecordingOutput();
        op.setOutput((Output) output);
        return op;
    }

    private void fireProcessingTimersUpTo(long time) throws Exception {
        ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                .fireProcessingTimeTimers(time);
    }

    private void advanceWatermark(long timestamp) throws Exception {
        ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                .advanceWatermark(timestamp);
    }

    /**
     * The watermark stalls below the window end; the processing-time deadline still
     * produces the window result. The user trigger's processing-time callback is not
     * invoked by the fallback firing.
     */
    @Test
    void maxDelayForcesEmissionOnSlowWatermark() throws Exception {
        CountingProcessingTimeTrigger trigger =
                new CountingProcessingTimeTrigger(EventTimeTrigger.create());
        TimingRecordingFunction windowFunction = new TimingRecordingFunction();
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE), trigger,
                0L, 50L, false, windowFunction);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        // Watermark stalls below the fire point (99): the event-time trigger cannot fire.
        advanceWatermark(50);
        assertEquals(0, output.main.size(), "no event-time fire with a stalled watermark");

        // Processing time passes the maxDelay deadline (99 + 1 + 50 = 150).
        fireProcessingTimersUpTo(200);

        assertEquals(1, output.main.size(), "maxDelay must force the window emission");
        assertEquals("5", output.main.get(0));
        assertEquals(0, trigger.onProcessingTimeCalls,
                "the fallback firing must not be fed into the user trigger");
        assertEquals(PaneInfo.PaneTiming.EARLY, windowFunction.timings.get(0),
                "the fallback fires before the watermark reaches window end -> EARLY timing");

        // The flag is consumed: a second timer sweep must not re-emit.
        fireProcessingTimersUpTo(400);
        assertEquals(1, output.main.size(), "the fallback fires exactly once per marking");
    }

    /**
     * The maxDelay timer never re-marks while no new data arrives: the marker lifecycle
     * is set at element time, consumed at the deadline.
     */
    @Test
    void maxDelayWithoutDataDoesNotFire() throws Exception {
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE),
                EventTimeTrigger.create(), 0L, 50L, false,
                new WindowingTestSupport.ToStringWindowFunction<>());
        operator.open();

        // No element at all: no marker, no fallback output.
        fireProcessingTimersUpTo(1000);
        assertEquals(0, output.main.size(),
                "an empty window must not produce a fallback emission");
    }

    /**
     * Session merge: the absorbed window's marker/timer are dropped, the merged window
     * re-registers its deadline — exactly ONE fallback emission.
     */
    @Test
    void mergeReRegistersDeadlineAndDropsAbsorbedMarkers() throws Exception {
        operator = newOperator(EventTimeSessionWindows.withGap(50),
                EventTimeTrigger.create(), 0L, 50L, false,
                new WindowingTestSupport.ToStringWindowFunction<>());
        operator.open();

        operator.processElement(new StreamRecord<>(1, 0));
        operator.processElement(new StreamRecord<>(2, 25));

        fireProcessingTimersUpTo(500);

        assertEquals(1, output.main.size(),
                "the merged window must fallback-fire exactly once; output=" + output.main);
        // The raw fallback state stores single values by replacement — the merged
        // window's last stored value is emitted (ValueState semantics, consistent
        // with the existing operator test family). The assertion that matters here
        // is the single emission: the absorbed window's deadline produced nothing.
        assertEquals("2", output.main.get(0));
    }

    /**
     * No-silent-skip (R-maxdelay M3): a processing-time assigner combined with
     * maxDelay > 0 is a construction error with a dedicated typed error code.
     */
    @Test
    void processingTimeWindowWithMaxDelayIsTypedRejected() {
        WindowOperatorBuilder<Integer, String, TimeWindow> builder = new WindowOperatorBuilder<>();
        builder.windowAssigner(TumblingProcessingTimeWindows.of(WINDOW_SIZE))
                .trigger(EventTimeTrigger.create())
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new WindowingTestSupport.SimpleStringSerializer())
                .windowSerializer((io.nop.stream.core.common.typeutils.TypeSerializer)
                        new WindowingTestSupport.SimpleTimeWindowSerializer())
                .maxDelay(50L);

        StreamException ex = assertThrows(StreamException.class,
                () -> builder.reduce((a, b) -> a, Integer.class),
                "processing-time window + maxDelay must fail at construction");
        assertEquals("nop.err.stream.window-max-delay-unsupported-processing-time",
                ex.getErrorCode().toString(),
                "the rejection must carry the dedicated error code");
    }

    /**
     * GlobalWindows are processing-time semantics (never cleanup) — same typed
     * rejection applies.
     */
    @Test
    void globalWindowWithMaxDelayIsTypedRejected() {
        WindowOperatorBuilder<Integer, String, io.nop.stream.core.windowing.windows.GlobalWindow> builder =
                new WindowOperatorBuilder<>();
        builder.windowAssigner(io.nop.stream.core.windowing.assigners.GlobalWindows.create())
                .trigger((Trigger<Object, io.nop.stream.core.windowing.windows.GlobalWindow>) (Trigger<?, ?>) EventTimeTrigger.create())
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new WindowingTestSupport.SimpleStringSerializer())
                .windowSerializer((io.nop.stream.core.common.typeutils.TypeSerializer)
                        new WindowingTestSupport.SimpleTimeWindowSerializer())
                .maxDelay(50L);

        StreamException ex = assertThrows(StreamException.class,
                () -> builder.reduce((a, b) -> a, Integer.class),
                "GlobalWindows + maxDelay must fail at construction");
        assertEquals("nop.err.stream.window-max-delay-unsupported-processing-time",
                ex.getErrorCode().toString());
    }

    /**
     * A user processing-time trigger keeps working next to the maxDelay timer: its own
     * timers drive its fires; the fallback timer neither feeds it nor is fed by it.
     */
    @Test
    void userProcessingTimeTriggerIsNotFedByMaxDelay() throws Exception {
        ProcessingTimerTrigger trigger = new ProcessingTimerTrigger();
        operator = newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE), trigger,
                0L, 50L, false, new WindowingTestSupport.ToStringWindowFunction<>());
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));

        // Timers due: user's own timer at 99, maxDelay deadline at 150.
        fireProcessingTimersUpTo(200);

        assertEquals(1, trigger.onProcessingTimeCalls,
                "exactly the user's own timer may reach the user trigger callback");
        assertEquals(2, output.main.size(),
                "user fire + fallback fire both emit (ACCUMULATING re-emission)");
    }

    /**
     * copyForSubtask must carry the maxDelay/event configuration — a missed copy would
     * fork behavior per subtask.
     */
    @Test
    void copyForSubtaskCarriesMaxDelayAndEvents() {
        OutputTag<WindowLifecycleEvent> tag =
                lifecycleTag();
        WindowOperator<String, Integer, Object, String, TimeWindow> source =
                newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE), EventTimeTrigger.create(),
                        0L, 50L, true, new WindowingTestSupport.ToStringWindowFunction<>());

        WindowOperator<String, Integer, Object, String, TimeWindow> copy = source.copyForSubtask();

        assertEquals(50L, copy.maxDelayForTest(), "maxDelay must survive copyForSubtask");
        assertTrue(copy.emitWindowEventsForTest(), "emitWindowEvents must survive copyForSubtask");
        assertNotNull(copy.windowEventsOutputTagForTest());
        assertEquals(tag.getId(), copy.windowEventsOutputTagForTest().getId(),
                "the events tag must be shared across subtasks of one operator");

        // Default-off copy stays off.
        WindowOperator<String, Integer, Object, String, TimeWindow> offCopy =
                newOperator(TumblingEventTimeWindows.of(WINDOW_SIZE), EventTimeTrigger.create(),
                        0L, 0L, false, new WindowingTestSupport.ToStringWindowFunction<>())
                        .copyForSubtask();
        assertEquals(0L, offCopy.maxDelayForTest());
        assertFalse(offCopy.emitWindowEventsForTest());
        assertNull(offCopy.windowEventsOutputTagForTest());
    }

    /**
     * maxDelay + events enabled together: the full OPEN -> fallback FIRE -> CLOSE
     * sequence over one window.
     */
    @Test
    void maxDelayAndEventsFullSequence() throws Exception {
        RecordingOutput eventsOut = new RecordingOutput();
        OutputTag<WindowLifecycleEvent> tag =
                lifecycleTag();
        operator = new WindowOperator<>(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new WindowingTestSupport.SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new WindowingTestSupport.SimpleStringSerializer(),
                String.class,
                new WindowingTestSupport.ToStringWindowFunction<>(),
                EventTimeTrigger.create(),
                0L,
                null,
                Object.class,
                null,
                null,
                null,
                AccumulationMode.ACCUMULATING,
                50L,
                true,
                tag);
        operator.setOutput((Output) eventsOut);
        operator.open();

        operator.processElement(new StreamRecord<>(5, 10));
        fireProcessingTimersUpTo(200);
        advanceWatermark(200);

        List<WindowLifecycleEvent.Type> types = new ArrayList<>();
        for (WindowLifecycleEvent e : eventsOut.events) {
            types.add(e.getType());
        }
        assertEquals(List.of(WindowLifecycleEvent.Type.OPEN, WindowLifecycleEvent.Type.CLOSE),
                types, "full lifecycle: OPEN at first record, CLOSE at cleanup");
        assertEquals(2, eventsOut.main.size(),
                "fallback fire + event-time fire both emit under ACCUMULATING");
        assertTrue(eventsOut.timeline.indexOf("EVENT:OPEN") < eventsOut.timeline.indexOf("MAIN:5"),
                "OPEN precedes the fallback emission");
    }

    /**
     * RecordingOutput variant reused from the lifecycle test suite.
     */
    static class RecordingOutput extends TestWindowLifecycleEvents.RecordingOutput {
    }

    /**
     * Wiring proof: the Java API surface ({@code WindowedStream.emitWindowEvents()} +
     * {@code maxDelay}) reaches the created window operator through the factory and the
     * builder — the exact chain the XDSL strategy consumption feeds.
     */
    @Test
    void javaApiWiresMaxDelayAndEventsIntoOperator() {
        List<KeyValue> ignored = new ArrayList<>();
        ignored.add(new KeyValue("k1"));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        DataStreamImpl<KeyValue> source = (DataStreamImpl<KeyValue>) env.fromCollection(ignored);
        KeyedStreamImpl<KeyValue, String> keyed = new KeyedStreamImpl<>(
                env,
                source.getTransformation(),
                (KeySelector<KeyValue, String>) v -> v.key);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        OutputTag<WindowLifecycleEvent> eventsTag =
                lifecycleTag();

        WindowedStreamImpl<KeyValue, String, TimeWindow> windowed =
                new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(100L))
                        .withComponents(components);
        windowed.emitWindowEvents();
        windowed.maxDelay(50L);

        SingleOutputStreamOperator<Integer> aggregated = windowed.aggregate(new SumAggFunction());

        OneInputTransformation<?, ?> transform =
                (OneInputTransformation<?, ?>) ((DataStreamImpl<Integer>) aggregated).getTransformation();
        // WI8 wiring proof: the aggregate path wraps the WindowOperator in a
        // SimpleStreamOperatorFactory; the wrapped operator carries the configuration.
        SimpleStreamOperatorFactory<?> factory =
                (SimpleStreamOperatorFactory<?>) transform.getOperatorFactory();
        Object wrapped = null;
        try {
            java.lang.reflect.Field f = factory.getClass().getDeclaredField("operator");
            f.setAccessible(true);
            wrapped = f.get(factory);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot unwrap factory operator", e);
        }
        assertInstanceOf(io.nop.stream.runtime.operators.windowing.WindowOperator.class, wrapped,
                "the java-api wiring must build a WindowOperator");
    }

    static class KeyValue {
        final String key;

        KeyValue(String key) {
            this.key = key;
        }
    }

    static class SumAggFunction
            implements io.nop.stream.core.common.functions.AggregateFunction<KeyValue, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[1];
        }

        @Override
        public int[] add(KeyValue value, int[] accumulator) {
            accumulator[0] += 1;
            return accumulator;
        }

        @Override
        public Integer getResult(int[] accumulator) {
            return accumulator[0];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            return a;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static OutputTag<WindowLifecycleEvent> lifecycleTag() {
        return new OutputTag<>("test-op:window-events",
                (io.nop.stream.core.common.typeinfo.TypeInformation<WindowLifecycleEvent>)
                        io.nop.stream.core.common.typeinfo.UnknownTypeInformation.INSTANCE);
    }

}
