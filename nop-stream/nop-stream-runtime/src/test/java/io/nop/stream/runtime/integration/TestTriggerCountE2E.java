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
 * WI5 (plan 2351) end-to-end: the declared {@code triggerId="count:2"} on a tumbling
 * event-time window reaches the window operator and fires BEFORE the window end —
 * the emission sequence is the discriminative proof (a type assertion cannot
 * distinguish a wired trigger from the default EventTimeTrigger, which would emit
 * nothing without watermarks here).
 *
 * <p>Pipeline: source [1,2,3,4] (one key) → tumbling 60m window,
 * {@code triggerId="count:2"}, {@code accumulationMode="ACCUMULATING"} → reduce(sum)
 * → collecting sink. ACCUMULATING fires emit the FULL window contents, so the exact
 * sequence is [3, 10]: after the 2nd record sum(1,2)=3, after the 4th
 * sum(1,2,3,4)=10. A DISCARDING window would emit [3, 7] (contents clear after each fire,
 * so the second fire sums the fresh [3, 4]); the default trigger would emit
 * nothing (no watermarks) — all three wrong shapes fail the assertion.
 */
public class TestTriggerCountE2E {

    private static final List<Object> SINK = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("trigger-count-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void countTriggerFiresMidWindowWithAccumulatingContents() throws Exception {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"trigger-count-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"60m\""
                        + " triggerId=\"count:2\" accumulationMode=\"ACCUMULATING\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"trigCountSource\"/>"
                + "<timestampsAndWatermarks id=\"wm\" watermarkInterval=\"0\">"
                + "<timestampAssigner>event</timestampAssigner>"
                + "<watermarkGenerator>output.emitWatermark("
                + "new io.nop.stream.core.streamrecord.watermark.Watermark(eventTimestamp))"
                + "</watermarkGenerator>"
                + "</timestampsAndWatermarks>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "<reduce id=\"sum\" bean=\"trigSumReduce\"/>"
                + "<sink id=\"out\" bean=\"trigCountSink\"/>"
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
        resolver.register("trigCountSource", new FourRecordSource());
        resolver.register("trigSumReduce", new SumReduce());
        resolver.register("trigCountSink", (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> SINK.add(value));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("trigger-count-e2e");

        assertEquals(List.of(3L, 10L), SINK,
                "count:2 + ACCUMULATING must fire mid-window with full contents: "
                        + "expected emissions [3, 10] (a default trigger emits nothing, "
                        + "DISCARDING emits [3, 4])");
    }

    /**
     * Emits the fixed sequence [1, 2, 3, 4] on one key (the keyBy constant folds
     * every record into the same window instance).
     */
    public static class FourRecordSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long i = 1; i <= 4 && running; i++) {
                ctx.collect(i);
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
