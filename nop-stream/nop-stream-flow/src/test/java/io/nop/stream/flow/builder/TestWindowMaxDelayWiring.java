/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.SumAggregateFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * WI8 (plan 2332) XDSL wiring: the {@code <windowingStrategies>/<strategy maxDelay>}
 * declaration is consumed by {@code AdvancedTransforms.buildWindow} and flows into the
 * {@code WindowedStream} API (from where the runtime factory carries it into the window
 * operator). The operator-level behavior is proven in nop-stream-runtime
 * ({@code TestWindowMaxDelay}); this module proves the declaration-side consumption.
 */
public class TestWindowMaxDelayWiring {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private StreamModel parseInline(String transformsXml, String topLevelXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-maxdelay\" version=\"1\">"
                + topLevelXml
                + "<transforms>" + transformsXml + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"k\" partition=\"FORWARD\"/>"
                + "<edge id=\"e1\" from=\"k\" to=\"w\" partition=\"FORWARD\"/>"
                + "</edges>"
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertEquals(true, resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        return resolver;
    }

    /**
     * A declared strategy maxDelay reaches the WindowedStream the window node produces —
     * the hand-off point to the window operator.
     */
    @Test
    public void maxDelayDeclarationFlowsIntoWindowedStream() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"slow-strat\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"slow-strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" maxDelay=\"500\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        assertDoesNotThrow(builder::build, "a maxDelay declaration must build cleanly");

        Object windowed = builder.registeredStream("w");
        WindowedStreamImpl<?, ?, ?> windowedImpl =
                assertInstanceOf(WindowedStreamImpl.class, windowed,
                        "<window> must produce a WindowedStream");
        assertEquals(500L, windowedImpl.getMaxDelay(),
                "the strategy maxDelay declaration must be consumed by buildWindow");
        assertFalse(windowedImpl.isEmitWindowEvents(),
                "the XDSL emitWindowEvents declaration is out of scope (WI8 Deferred m1) — default off");
    }

    /** Without a maxDelay declaration the fallback stays disabled (default-off gate). */
    @Test
    public void missingMaxDelayDeclarationDefaultsToDisabled() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"plain-strat\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"plain-strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();

        WindowedStream<?, ?, ?> windowed = (WindowedStream<?, ?, ?>) builder.registeredStream("w");
        assertEquals(0L, ((WindowedStreamImpl<?, ?, ?>) windowed).getMaxDelay(),
                "no declaration means the maxDelay fallback stays disabled");
    }

    /**
     * maxDelay=0 declared explicitly is the disabled value — builds cleanly, operator
     * receives 0.
     */
    @Test
    public void explicitZeroMaxDelayStaysDisabled() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"zero-strat\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"zero-strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" maxDelay=\"0\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();

        WindowedStreamImpl<?, ?, ?> windowedImpl =
                (WindowedStreamImpl<?, ?, ?>) builder.registeredStream("w");
        assertEquals(0L, windowedImpl.getMaxDelay());
    }
}
