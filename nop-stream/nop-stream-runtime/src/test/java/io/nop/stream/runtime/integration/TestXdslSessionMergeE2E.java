/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI7 (plan 2353) end-to-end: the XDSL-declared {@code session-event-time} window
 * merges intersecting sessions and interacts with allowedLateness — through the
 * declaration path only (no Java window API).
 *
 * <p>Session semantics: window = [ts, ts+gap); intersecting windows merge (cover),
 * the merged window end = last merged element ts + gap, and the fire timer is
 * re-registered on each merge (49 → 79 → 104 for the inputs below). The bounded
 * source flush emits Watermark.MAX_WATERMARK, so the trailing record's own window
 * fires at flush.
 *
 * <p>Test 1 (merge discrimination): input ts=[0,30,55,200] → ONE merged window
 * [0,105) fires 85 when wm 200 passes timer 104 (85 before 200 proves the timer was
 * re-registered by the merges, not flushed), then flush fires the tail window 200.
 * A broken merge would emit [0,30,55,200] (four fires). The 85-before-200 order is
 * consistent with the merge re-registering the fire timer; the timer-re-registration
 * invariant itself is pinned by the operator-level suite.
 *
 * <p>Test 2 (lateness pin): allowedLateness=10000 (>96ms — the merge guard
 * ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK requires mergeResult.maxTimestamp +
 * lateness > currentWatermark). Input ts=[0,30,55,200,40] — the LATE record 40 must
 * come AFTER 200 (otherwise it is an ordinary merge and the pin silently tests
 * nothing). The late record merges into the still-in-flight window [0,105) and the
 * EventTimeTrigger fires IMMEDIATELY (maxTimestamp 104 ≤ wm 200) with the FULL
 * contents → 125 (skipPurgeForLateness: even DISCARDING would emit 125). Then flush
 * fires the tail window 200 → [85, 125, 200].
 */
public class TestXdslSessionMergeE2E {

    private static final List<Object> SINK = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("xdsl-session-merge-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static long run(String strategyExtra, long[] timestamps) throws Exception {
        SINK.clear();
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"session-merge-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"session-event-time\" duration=\"50ms\""
                + " accumulationMode=\"ACCUMULATING\"" + strategyExtra + "/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"sessionMergeSource\"/>"
                + "<timestampsAndWatermarks id=\"wm\" watermarkInterval=\"0\">"
                + "<timestampAssigner>event</timestampAssigner>"
                + "<watermarkGenerator>output.emitWatermark("
                + "new io.nop.stream.core.streamrecord.watermark.Watermark(eventTimestamp))"
                + "</watermarkGenerator>"
                + "</timestampsAndWatermarks>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "<reduce id=\"sum\" bean=\"sessionSumReduce\"/>"
                + "<sink id=\"out\" bean=\"sessionMergeSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"wm\"/>"
                + "<edge id=\"e1\" from=\"wm\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"sum\"/>"
                + "<edge id=\"e4\" from=\"sum\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("sessionMergeSource", new TimestampListSource(timestamps));
        resolver.register("sessionSumReduce", new SumReduce());
        resolver.register("sessionMergeSink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> SINK.add(value));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("xdsl-session-merge-e2e");

        long sum = 0;
        for (Object v : SINK) {
            sum += (Long) v;
        }
        return sum;
    }

    @Test
    public void intersectingSessionsMergeIntoOneWindow() throws Exception {
        long total = run("", new long[]{0, 30, 55, 200});
        // merged window [0,105) fires 85 (0+30+55) at wm 200; flush fires tail 200.
        // A broken merge counterfactually emits [0,30,55,200] (sum 285, four fires).
        assertEquals(285L, total, () -> "unexpected emission set: " + SINK);
        assertEquals(List.of(85L, 200L), SINK,
                () -> "session merge must produce ONE window emission [85] before the flush "
                        + "tail [200]; four emissions would mean the merge is broken");
    }

    @Test
    public void lateRecordWithinAllowedLatenessMergesAndRefiresFullContents() throws Exception {
        long total = run(" allowedLateness=\"10000\"", new long[]{0, 30, 55, 200, 40});
        // 85 (wm 200 fires merged window) → late 40 merges into the in-flight window
        // and EventTimeTrigger refires the FULL contents immediately → 125
        // (skipPurgeForLateness: DISCARDING would also emit 125) → flush tail 200.
        // Emitting only 40, or dropping 40, are both wrong shapes.
        assertEquals(410L, total, () -> "unexpected emission set: " + SINK);
        assertEquals(List.of(85L, 125L, 200L), SINK,
                () -> "the late record must merge into the in-flight window and trigger an "
                        + "immediate full-contents refire (125)");
    }

    /** Emits the fixed timestamp sequence on one key (the value IS the event time). */
    public static class TimestampListSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private final long[] timestamps;
        private boolean running = true;

        public TimestampListSource(long[] timestamps) {
            this.timestamps = timestamps;
        }

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long ts : timestamps) {
                if (!running) {
                    return;
                }
                ctx.collect(ts);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Long sum over the (merged) session window pane. */
    public static class SumReduce implements ReduceFunction<Long> {
        @Override
        public Long reduce(Long a, Long b) throws Exception {
            return a + b;
        }
    }
}
