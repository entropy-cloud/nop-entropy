/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Processing-time window production-drive E2E (plan `2026-08-13-0132-1` Phase 2, Rules #22/#23):
 *
 * <ul>
 *   <li>a {@code TumblingProcessingTimeWindows} + default-trigger job running through the FULL
 *       production path (env.execute → GraphExecutionPlan → StreamTaskInvokable → driver →
 *       mailbox) actually FIRES windows and emits to the sink — no mock time advancement;</li>
 *   <li>an invokable-level production assembly (direct StreamTaskInvokable.invoke, no mocks)
 *       verifies the cleanup contract at the state level: after all windows fired, the
 *       WindowOperator's {@code internalTimerService} holds no processing-time timers and the
 *       {@code window-contents} map state is empty for every created window — the unbounded
 *       growth path is closed.</li>
 * </ul>
 *
 * <p>Timing design (recorded in the plan): the slow source emits with a cadence whose element
 * timestamps never sit within a few ms of a window boundary (41ms cadence vs 300ms windows —
 * coprime), and the last emission is followed by a quiescent sleep so the final window's fire
 * mail is drained before the job closes. Window counts are asserted; exact per-window sums are
 * not (boundary jitter only shifts which bucket an element lands in, never the total).
 */
public class TestProcessingTimeWindowProductionE2E {

    private static final long WINDOW_SIZE = 300L;
    private static final long EMIT_INTERVAL_MS = 41L;
    private static final int ELEMENT_COUNT = 40;
    private static final long TRAILING_SLEEP_MS = 500L;

    static final class IntEvent {
        final String key;
        final int value;

        IntEvent(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Slow source: emits {@code count} events at {@code intervalMs} cadence, then sleeps
     * {@code trailingSleepMs} before returning so the last window's fire mail is drained by
     * the task thread before the job closes.
     */
    private static SourceFunction<IntEvent> slowSource(int count, long intervalMs, long trailingSleepMs) {
        return new SourceFunction<IntEvent>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<IntEvent> ctx) throws Exception {
                for (int i = 0; i < count; i++) {
                    ctx.collect(new IntEvent("key1", 1));
                    Thread.sleep(intervalMs);
                }
                Thread.sleep(trailingSleepMs);
            }

            @Override
            public void cancel() {
            }
        };
    }

    private static class SumAggregateFunction implements AggregateFunction<IntEvent, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[]{0};
        }

        @Override
        public int[] add(IntEvent value, int[] accumulator) {
            accumulator[0] += value.value;
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

    /**
     * End-to-end (Rule #22): production path from source → keyBy → PT window → aggregate →
     * sink. The driver thread delivers fire mails to the window task's mailbox; the task
     * thread drains them and fires the windows. Assertions: every emitted element is counted
     * exactly once across the fired windows, and at least the first windows fired (the source
     * outlives them).
     */
    @Test
    void testProcessingTimeWindowsFireThroughProductionDriver() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        KeyedStream<IntEvent, String> keyed = env
                .addSource(slowSource(ELEMENT_COUNT, EMIT_INTERVAL_MS, TRAILING_SLEEP_MS), "slow-source")
                .keyBy((KeySelector<IntEvent, String>) e -> e.key);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        new io.nop.stream.core.datastream.WindowedStreamImpl<>(keyed,
                TumblingProcessingTimeWindows.of(WINDOW_SIZE))
                .withComponents(components)
                .aggregate(new SumAggregateFunction())
                .sink((SinkFunction<Integer>) results::add);

        env.execute("pt-window-production-e2e");

        assertFalse(results.isEmpty(), "Processing-time windows fired through the production driver");
        int total = 0;
        for (Integer r : results) {
            assertTrue(r > 0, "Every fired window has contents: " + results);
            total += r;
        }
        assertEquals(ELEMENT_COUNT, total,
                "Every emitted element is counted exactly once across all fired windows");
        assertTrue(results.size() >= 4, "Multiple windows fired: " + results);
    }

    /**
     * Cleanup verification (invokable-level production assembly, no mocks): a
     * WindowOperator driven by the real driver via StreamTaskInvokable.invoke(). After all
     * windows fired and were cleaned:
     *
     * <ul>
     *   <li>{@code internalTimerService.numProcessingTimeTimers() == 0} — every PT timer
     *       (fire + cleanup) was fired and removed, no unbounded timer growth;</li>
     *   <li>{@code window-contents} map state empty for every created window namespace —
     *       window contents were cleared on cleanup.</li>
     * </ul>
     */
    @Test
    void testProcessingTimeWindowCleanupClearsState() throws Exception {
        long t0 = System.currentTimeMillis();
        TestOutput<String> output = new TestOutput<>();

        WindowOperator<String, IntEvent, Object, String, TimeWindow> windowOp =
                new WindowOperator<String, IntEvent, Object, String, TimeWindow>(
                        TumblingProcessingTimeWindows.of(WINDOW_SIZE),
                        new SimpleTimeWindowSerializer(),
                        (KeySelector<IntEvent, String>) e -> e.key,
                        new SimpleStringSerializer(),
                        String.class,
                        new ObjectToStringWindowFunction(),
                        ProcessingTimeTrigger.create(),
                        0L,
                        null);
        windowOp.setOutput((Output) output);

        StreamSourceOperator<IntEvent> sourceOp =
                new StreamSourceOperator<>(slowSource(ELEMENT_COUNT, EMIT_INTERVAL_MS, TRAILING_SLEEP_MS));

        List<KeySelector<?, ?>> keySelectors = new ArrayList<>();
        keySelectors.add(null);
        keySelectors.add((KeySelector<IntEvent, String>) e -> e.key);

        OperatorChain chain = new OperatorChain(
                Arrays.asList((io.nop.stream.core.operators.StreamOperator<?>) sourceOp, windowOp),
                keySelectors);

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        invokable.invoke();

        assertFalse(output.isEmpty(), "Windows fired through the invokable-level production path");

        // Cleanup evidence 1: no processing-time timers remain (all fire + cleanup timers fired).
        assertEquals(0, windowOp.internalTimerService.numProcessingTimeTimers(),
                "All processing-time timers (fire + cleanup) were fired and removed");

        // Cleanup evidence 2: window-contents map state empty for every created window.
        IKeyedStateBackend<String> backend = windowOp.getKeyedStateBackend();
        MapState<String, Object> contents = backend.getMapState(
                new MapStateDescriptor<>("window-contents", String.class, Object.class));

        long firstBucket = (t0 / WINDOW_SIZE) * WINDOW_SIZE;
        // Candidate bucket starts cover the real first bucket (t0 falls inside one of the
        // first two candidates) plus all buckets the 40 elements over ~1.6s can reach.
        for (long start = firstBucket; start <= firstBucket + 6 * WINDOW_SIZE; start += WINDOW_SIZE) {
            backend.setCurrentKey("key1");
            backend.setCurrentNamespace("TW:" + start + "," + (start + WINDOW_SIZE));
            assertTrue(contents.isEmpty(),
                    "Window contents cleared after cleanup for window [" + start + "," + (start + WINDOW_SIZE) + ")");
        }
    }

    /**
     * No-silent-skip (Rule #24): a processing-time WindowOperator opened without a wired
     * ProcessingTimeService (no task, no driver) must warn explicitly instead of silently
     * producing zero output. The production task wiring always injects the service before
     * open(); this pins the explicit-warning fallback for non-task usage.
     */
    @Test
    void testProcessingTimeWindowWithoutDriverWarnsExplicitly() throws Exception {
        ch.qos.logback.classic.Logger windowLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WindowOperator.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        windowLogger.addAppender(appender);
        try {
            WindowOperator<String, IntEvent, Object, String, TimeWindow> windowOp =
                    new WindowOperator<String, IntEvent, Object, String, TimeWindow>(
                            TumblingProcessingTimeWindows.of(WINDOW_SIZE),
                            new SimpleTimeWindowSerializer(),
                            (KeySelector<IntEvent, String>) e -> e.key,
                            new SimpleStringSerializer(),
                            String.class,
                            new ObjectToStringWindowFunction(),
                            ProcessingTimeTrigger.create(),
                            0L,
                            null);
            windowOp.setOutput((Output) new TestOutput<>());
            windowOp.open();
            windowOp.close();

            long warns = appender.list.stream()
                    .filter(ev -> ev.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("will never fire"))
                    .count();
            assertEquals(1, warns,
                    "Opening a PT window without a ProcessingTimeService warns explicitly "
                            + "(no silent zero output). Events: " + appender.list);
        } finally {
            windowLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private static class ObjectToStringWindowFunction
            implements io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction<
            Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window,
                            io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction.InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window,
                          io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction.InternalWindowContext context) {
        }
    }

    static class SimpleTimeWindowSerializer implements io.nop.stream.core.common.typeutils.TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer implements io.nop.stream.core.common.typeutils.TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
