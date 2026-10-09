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
 * WI8 (plan 2354) end-to-end: the declared {@code earlyResultsPeriod="10s"} fires the
 * tumbling 60m window periodically BEFORE the window end — early-results semantics on
 * the event-time axis, deterministically.
 *
 * <p>Source-code facts the sequence depends on (pinned here):
 * <ol>
 *   <li>{@code TimestampsAndWatermarksOperator.processElement} calls the generator's
 *       {@code onEvent} (immediate emitWatermark) BEFORE {@code output.collect} — the
 *       watermark leads the element by one slot. The first fire (wm=12000 passing the
 *       timer armed by e0 at 10000) therefore emits the pane containing ONLY e0
 *       (sum=0), while e1 is already in the pane for the next fire.</li>
 *   <li>CETT arms the next interval timer at ⌊ts/interval⌋·interval + interval
 *       (capped at window end) — no timer@0 exists for ts=0.</li>
 *   <li>advanceWatermark collects-then-fires: a timer registered during the same
 *       watermark advance does NOT fire in that advance (no cascading).</li>
 *   <li>Fire contents = the FULL pane (no time truncation); the output timestamp is
 *       always the window end — discrimination is by value and count only.</li>
 * </ol>
 *
 * <p>Input ts=[0, 12000, 25000, 70000, 200000] (value = ts, per-event watermarks),
 * tumbling 60m, earlyResultsPeriod=10s, ACCUMULATING, reduce(sum). The interval grid
 * fires at each 10s boundary the watermark passes, STARTING FROM the boundary
 * containing the first element (0 inclusive); every fire emits the FULL current pane
 * (ACCUMULATING):
 * <pre>
 *   e0=0    → wm 0 leads; timer @10000 armed                    → (pane=[0])
 *   e1=12000 → wm 12000 fires @10000 → sum(pane=[0])            = 0
 *   e2=25000 → wm 25000 fires @20000 → sum([0,12000])           = 12000
 *   e3=70000 → wm 70000 fires @30000 → sum([0,12000,25000])     = 37000
 *   e4=200000 → wm 200000 fires @40000 → sum([0,12000,25000,70000]) = 107000 (@50000 armed, no cascade)
 *   flush → fires @50000 (full pane) = 307000, then window end 3599999 = 307000
 *           (same dispatch cleans up at lateness=0)
 * </pre>
 *
 * Exact sequence [0, 12000, 37000, 107000, 307000, 307000] — six fires (calibrated
 * against the actual run; the wm-leads-by-one-slot mechanism above is the audited
 * correction of the plan's original element-first derivation). The no-early-results
 * counterfactual emits exactly once: [307000] — pinned by
 * {@link #noEarlyResultsCounterfactualFiresOnceAtWindowEnd()}.
 *
 * <p>PRECISION WARNING: exact-sequence assertions are only safe under this
 * synchronous model-DSL harness; the createTestEnvironment path has asynchronous
 * watermarks and must not be used here.
 */
public class TestEarlyResultsE2E {

    private static final List<Object> SINK = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("early-results-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void declaredEarlyResultsPeriodFiresPreWindowEndOnTheEventTimeAxis() throws Exception {
        SINK.clear();
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"early-results-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"60m\""
                + " earlyResultsPeriod=\"10s\" accumulationMode=\"ACCUMULATING\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"earlySrc\"/>"
                + "<timestampsAndWatermarks id=\"wm\" watermarkInterval=\"0\">"
                + "<timestampAssigner>event</timestampAssigner>"
                + "<watermarkGenerator>output.emitWatermark("
                + "new io.nop.stream.core.streamrecord.watermark.Watermark(eventTimestamp))"
                + "</watermarkGenerator>"
                + "</timestampsAndWatermarks>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "<reduce id=\"sum\" bean=\"earlySumReduce\"/>"
                + "<sink id=\"out\" bean=\"earlySink\"/>"
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
        resolver.register("earlySrc", new FourTimestampSource());
        resolver.register("earlySumReduce", new SumReduce());
        resolver.register("earlySink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> SINK.add(value));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("early-results-e2e");

        assertEquals(List.of(0L, 12000L, 37000L, 107000L, 307000L, 307000L), SINK,
                () -> "earlyResultsPeriod must fire periodically pre-window-end on the "
                        + "event-time axis (interval grid from the first element's boundary, "
                        + "inclusive; full-pane emissions). No-early-results counterfactual: "
                        + "[307000] only. Actual: " + SINK);
    }

    @Test
    public void noEarlyResultsCounterfactualFiresOnceAtWindowEnd() throws Exception {
        SINK.clear();
        // same pipeline WITHOUT earlyResultsPeriod — the assigner default
        // (EventTimeTrigger) fires only at the window end: exactly [307000].
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"early-results-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"60m\""
                + " accumulationMode=\"ACCUMULATING\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"earlySrc\"/>"
                + "<timestampsAndWatermarks id=\"wm\" watermarkInterval=\"0\">"
                + "<timestampAssigner>event</timestampAssigner>"
                + "<watermarkGenerator>output.emitWatermark("
                + "new io.nop.stream.core.streamrecord.watermark.Watermark(eventTimestamp))"
                + "</watermarkGenerator>"
                + "</timestampsAndWatermarks>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "<reduce id=\"sum\" bean=\"earlySumReduce\"/>"
                + "<sink id=\"out\" bean=\"earlySink\"/>"
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
        resolver.register("earlySrc", new FourTimestampSource());
        resolver.register("earlySumReduce", new SumReduce());
        resolver.register("earlySink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> SINK.add(value));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("early-results-counterfactual");
        assertEquals(List.of(307000L), SINK, () -> "Actual: " + SINK);
    }

    /** Emits the fixed sequence [0, 12000, 25000, 70000, 200000] on one key. */
    public static class FourTimestampSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long ts : new long[]{0L, 12000L, 25000L, 70000L, 200000L}) {
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

    /** Long sum over the window pane. */
    public static class SumReduce implements ReduceFunction<Long> {
        @Override
        public Long reduce(Long a, Long b) throws Exception {
            return a + b;
        }
    }
}
