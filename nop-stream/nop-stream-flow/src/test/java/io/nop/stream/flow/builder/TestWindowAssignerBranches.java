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
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI26: the parameterized assigner branches for the new pseudo-table window kinds —
 * "sliding-event-time" (duration=size + slide) and "session-event-time"
 * (duration=gap). The slide<=size guard is a repository ruling (the engine's
 * validateSliding does not enforce it) enforced for the raw-DSL face here and in the
 * SQL compiler for the SQL face.
 */
public class TestWindowAssignerBranches {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private WindowedStreamImpl<?, ?, ?> buildWindowed(String strategyXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"assigner-branches\" version=\"1\">"
                + "<windowingStrategies>" + strategyXml + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertEquals(true, resource.exists());
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);
        return build(model, resolver(), true);
    }

    private InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        return resolver;
    }

    private WindowedStreamImpl<?, ?, ?> buildWindowedRaw(String strategyXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"assigner-branches\" version=\"1\">"
                + "<windowingStrategies>" + strategyXml + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        return build(model, resolver(), false);
    }

    private WindowedStreamImpl<?, ?, ?> build(StreamModel model,
                                              InMemoryBeanFunctionResolver resolver,
                                              boolean assertClean) {
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        if (assertClean)
            assertDoesNotThrow(builder::build);
        else
            builder.build();
        return assertInstanceOf(WindowedStreamImpl.class, builder.registeredStream("w"));
    }

    @Test
    void slidingEventTimeBranchAssemblesSlidingAssigner() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"sliding-event-time\""
                        + " duration=\"4s\" slide=\"2s\"/>");
        assertInstanceOf(SlidingEventTimeWindows.class, windowed.getWindowAssigner());
    }

    @Test
    void sessionEventTimeBranchAssemblesSessionAssigner() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"session-event-time\""
                        + " duration=\"5s\"/>");
        assertInstanceOf(EventTimeSessionWindows.class, windowed.getWindowAssigner());
    }

    @Test
    void slidingWithoutSlideFailsFast() {
        Exception ex = assertThrows(Exception.class, () -> buildWindowedRaw(
                "<strategy strategyId=\"strat\" windowFnId=\"sliding-event-time\""
                        + " duration=\"4s\"/>"));
        assertTrue(String.valueOf(ex.getMessage()).contains("slide")
                        || ex.getCause() != null
                                && String.valueOf(ex.getCause().getMessage()).contains("slide"),
                () -> "expected the slide gate, got: " + ex.getMessage());
    }

    @Test
    void slideLargerThanSizeFailsFastAtDslLayer() {
        Exception ex = assertThrows(Exception.class, () -> buildWindowedRaw(
                "<strategy strategyId=\"strat\" windowFnId=\"sliding-event-time\""
                        + " duration=\"4s\" slide=\"5s\"/>"));
        assertTrue(String.valueOf(ex.getMessage()).contains("exceed")
                        || ex.getCause() != null
                                && String.valueOf(ex.getCause().getMessage()).contains("exceed"),
                () -> "expected the slide<=size guard, got: " + ex.getMessage());
    }

    /**
     * WI1 (plan 2347) contract-ledger pin: a windowFnId outside the parameterized
     * ids, the legacy whitelist, and the bean resolver has no consumer — the
     * release work item is roadmap WI7 (global parameterization); until then the
     * ref-unknown gate must stay fail-fast.
     */
    @Test
    void unknownWindowFnIdFailsFast() {
        StreamException ex = assertThrows(StreamException.class, () -> buildWindowedRaw(
                "<strategy strategyId=\"strat\" windowFnId=\"no-such-window\"/>"));
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(),
                () -> "unknown windowFnId must fail with ERR_STREAM_REF_UNKNOWN: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no-such-window"), ex::getMessage);
    }
}
