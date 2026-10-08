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
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.StreamEmitMode;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EmitUpdateTrigger;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
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
 * WI37 (D11 release): the two-tier emit declaration is consumed by buildWindow —
 * {@code emit="update"} assembles an {@link EmitUpdateTrigger} and unconditionally
 * forces ACCUMULATING (the xdef default back-fill makes an explicit DISCARDING
 * indistinguishable from the omitted declaration); {@code close}/absent keeps the
 * legacy default trigger and the declared accumulation mode. The triggerId gates
 * (D11: no free-form trigger surface) stay fail-fast.
 *
 * <p>Declared surface: uppercase enum constants {@code UPDATE|CLOSE} (F-1: the xdef
 * enum dict matches constant names case-sensitively — same convention as
 * accumulationMode; the lowercase form recorded by WI0a is superseded).
 *
 * <p>Covers the DSL-side consumption ring; the runtime operator behavior for the
 * update tier is pinned by the sql-side E2E (real execution) and the core trigger
 * unit tests.
 */
public class TestWindowEmitDeclaration {

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
                + "name=\"emit-declaration\" version=\"1\">"
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
        assertEquals(true, resource.exists(), "stream.xdef must be on the test classpath");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);

        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        assertDoesNotThrow(builder::build, "the strategy declaration must build cleanly");
        return assertInstanceOf(WindowedStreamImpl.class, builder.registeredStream("w"),
                "<window> must produce a WindowedStream");
    }

    @Test
    void emitUpdateAssemblesUpdateTriggerAndForcesAccumulating() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" emit=\"UPDATE\"/>");
        assertInstanceOf(EmitUpdateTrigger.class, windowed.getTrigger(),
                "emit=update must assemble the per-element firing trigger");
        assertEquals(AccumulationMode.ACCUMULATING, windowed.getAccumulationMode(),
                "emit=update unconditionally forces ACCUMULATING");
    }

    @Test
    void emitUpdateOverridesExplicitDiscardingDeclaration() {
        // the xdef default back-fill makes an explicit DISCARDING indistinguishable
        // from the omitted declaration — update overrides rather than conflicts
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" accumulationMode=\"DISCARDING\" emit=\"UPDATE\"/>");
        assertInstanceOf(EmitUpdateTrigger.class, windowed.getTrigger());
        assertEquals(AccumulationMode.ACCUMULATING, windowed.getAccumulationMode(),
                "update forces ACCUMULATING even over an explicit DISCARDING declaration");
    }

    @Test
    void emitCloseKeepsLegacyBehavior() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" emit=\"CLOSE\" accumulationMode=\"ACCUMULATING\"/>");
        Trigger<?, ?> trigger = windowed.getTrigger();
        assertTrue(!(trigger instanceof EmitUpdateTrigger),
                "emit=close must keep the assigner's default trigger");
        assertInstanceOf(EventTimeTrigger.class, trigger);
        assertEquals(AccumulationMode.ACCUMULATING, windowed.getAccumulationMode(),
                "emit=close keeps the declared accumulation mode");
    }

    @Test
    void emitAbsentDefaultsToCloseBehavior() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\"/>");
        assertInstanceOf(EventTimeTrigger.class, windowed.getTrigger());
        assertEquals(AccumulationMode.DISCARDING, windowed.getAccumulationMode());
    }

    @Test
    void declaredSurfaceIsUppercaseEnumConstants() {
        // F-1 (WI37 execution finding): the xdef enum dict validates against the enum
        // constant names — the declared surface is UPDATE|CLOSE (same convention as
        // accumulationMode="DISCARDING"); the WI0a-recorded lowercase form is superseded
        assertThrows(Exception.class, () -> buildWindowed(
                        "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                                + " duration=\"1s\" emit=\"update\"/>"),
                "lowercase emit is outside the declared surface (xdef dict is case-sensitive)");
        WindowedStreamImpl<?, ?, ?> close = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" emit=\"CLOSE\"/>");
        assertInstanceOf(EventTimeTrigger.class, close.getTrigger());
    }

    @Test
    void triggerIdOnWindowNodeStillFailsFast() {
        // D11 boundary: the emit tier is a closed enum, not a free-form trigger surface
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"emit-declaration\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"1s\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"><triggerId>t</triggerId></window>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        Exception ex = assertThrows(Exception.class,
                () -> StreamModelDslBuilder.of(model, resolver).build(),
                "the window-node triggerId gate must stay fail-fast");
        assertTrue(String.valueOf(ex.getMessage()).contains("unsupported")
                        || ex.getCause() != null
                                && String.valueOf(ex.getCause().getMessage()).contains("unsupported"),
                () -> "expected the triggerId gate, got: " + ex.getMessage());
    }

    @Test
    void unknownEmitValueFailsAtParseOrBuild() {
        String strategyXml = "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"1s\" emit=\"final\"/>";
        // xdef enum resolution rejects unknown values during model parse (typed)
        assertThrows(Exception.class, () -> buildWindowed(strategyXml),
                "unknown emit values must fail fast, never be silently ignored");
    }

    @Test
    void windowAssignerTypeSafeViaInterface() {
        // smoke: the windowed stream keeps its assigner after emit wiring
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" emit=\"UPDATE\"/>");
        assertInstanceOf(WindowAssigner.class, windowed.getWindowAssigner());
    }
}
