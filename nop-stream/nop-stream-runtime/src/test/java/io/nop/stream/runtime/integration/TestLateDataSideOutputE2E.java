/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2356) end-to-end: the declared {@code <window lateData="late-tag">}
 * reaches the window operator and routes SKIPPED LATE records onto the tag channel —
 * the data-plane proof of the declarative wiring (the structural pairing checks live
 * in flow's {@code TestWi10LateDataDeclaration}; nop-stream-flow cannot run a real
 * window pipeline — no runtime factory on its classpath).
 *
 * <p>Pipeline: source [10, 120, 50] (ts = value) → timestampsAndWatermarks
 * (per-event watermark ts-10, the bounded-out-of-orderness-10 shape) → keyBy(const)
 * → tumbling 100ms window, {@code lateData="late-tag"} → reduce(sum) → main sink;
 * tag channel: {@code <edge from="sum" to="lateMap" outputTag="late-tag">} →
 * lateMap → late sink. Note the tag edge starts at the HOST vertex (the reduce that
 * owns the window operator) — the window itself is a virtual build point.
 *
 * <p>Timeline (watermark leads the element by one slot — onEvent emits before
 * collect): e0 ts=10 brings wm to 0 and lands in [0,100); e1 ts=120 brings wm to 110
 * which fires [0,100) (sum=10) and cleans it up (allowedLateness=0 default), then
 * lands in [100,200); e2 ts=50 finds its window gone — a skipped late record
 * (50 + 0 &lt;= 110) — and must land on the lateData tag, NOT in the main
 * aggregation. Expected: main=[10, 120] (on-time fire + end-of-input flush of the
 * second window), late=[50]; a wiring gap shows up as late=[] (record counted as
 * dropped) or the late record leaking into the main path.
 */
public class TestLateDataSideOutputE2E {

    private static final List<Object> MAIN_SINK = new ArrayList<>();
    private static final List<Object> LATE_SINK = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("late-data-side-output-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void declaredLateDataRoutesSkippedRecordToTagChannel() throws Exception {
        MAIN_SINK.clear();
        LATE_SINK.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"late-data-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"100ms\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"lateDataSource\"/>"
                + "<timestampsAndWatermarks id=\"wm\" watermarkInterval=\"0\">"
                + "<timestampAssigner>event</timestampAssigner>"
                + "<watermarkGenerator>output.emitWatermark("
                + "new io.nop.stream.core.streamrecord.watermark.Watermark(eventTimestamp - 10))"
                + "</watermarkGenerator>"
                + "</timestampsAndWatermarks>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\" lateData=\"late-tag\"/>"
                + "<reduce id=\"sum\" bean=\"lateDataSumReduce\"/>"
                + "<map id=\"lateMap\"><source>event</source></map>"
                + "<sink id=\"mainSink\" bean=\"lateDataMainSink\"/>"
                + "<sink id=\"lateSink\" bean=\"lateDataLateSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"wm\"/>"
                + "<edge id=\"e1\" from=\"wm\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"sum\"/>"
                + "<edge id=\"e4\" from=\"sum\" to=\"mainSink\"/>"
                + "<edge id=\"e5\" from=\"sum\" to=\"lateMap\" outputTag=\"late-tag\"/>"
                + "<edge id=\"e6\" from=\"lateMap\" to=\"lateSink\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("lateDataSource", new ThreeRecordSource());
        resolver.register("lateDataSumReduce", new SumReduce());
        resolver.register("lateDataMainSink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> MAIN_SINK.add(value));
        resolver.register("lateDataLateSink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> LATE_SINK.add(value));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("late-data-side-output-e2e");

        // Main path: the on-time fire of [0,100) with sum(10)=10, plus the
        // end-of-input flush of the [100,200) window that holds e1 (ts=120). The late
        // record (ts=50) must NOT appear in either — its window was already fired and
        // cleaned before it arrived (counterfactual half of the assertion).
        assertEquals(List.of(10L, 120L), MAIN_SINK,
                "main path must carry the on-time [0,100) fire (10) and the end-of-input"
                        + " flush of [100,200) (120); the late record (ts=50) must not join"
                        + " any window result");
        // Tag channel: the skipped late record crosses the declared lateData tag edge
        // into the downstream transform.
        assertEquals(List.of(50L), LATE_SINK,
                "the skipped late record (ts=50, its window already fired and cleaned)"
                        + " must be routed onto the declared lateData tag channel");
        assertTrue(!MAIN_SINK.contains(50L), "late record must not appear on the main path");
    }

    /**
     * Emits ts=[10, 120, 50] on one key — the third record is late after the second
     * one pushes the watermark past the first window's cleanup point.
     */
    public static class ThreeRecordSource
            implements SourceFunction<Long> {
        private boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long ts : new long[]{10L, 120L, 50L}) {
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

    /** Long sum over the window contents. */
    public static class SumReduce implements ReduceFunction<Long> {
        @Override
        public Long reduce(Long a, Long b) throws Exception {
            return a + b;
        }
    }
}
