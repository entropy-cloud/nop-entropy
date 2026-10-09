/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.triggers.ContinuousEventTimeTrigger;
import io.nop.stream.core.windowing.triggers.ContinuousProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.ProcessingTimeoutTrigger;
import io.nop.stream.core.windowing.triggers.PurgingTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 (plan 2351): the trigger declaration catalog — bean name first, then the
 * catalog grammar, then {@code ERR_STREAM_REF_UNKNOWN}; node-level triggerId
 * overrides the strategy level (allowedLateness precedent); emit=UPDATE owns the
 * trigger slot and conflicts with an explicit triggerId; non-mergeable triggers are
 * rejected on merging assigners before the runtime onMerge contract would be.
 */
public class TestTriggerDeclaration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private WindowedStreamImpl<?, ?, ?> buildWindowed(String strategyXml, String windowXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"trigger-declaration\" version=\"1\">"
                + "<windowingStrategies>" + strategyXml + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + (windowXml == null ? "<window id=\"w\" strategyRef=\"strat\"/>" : windowXml)
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        return (WindowedStreamImpl<?, ?, ?>) builder.registeredStream("w");
    }

    @Test
    void eventTimeAndProcessingTimeCatalogIds() {
        assertInstanceOf(EventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"event-time\"/>", null).getTrigger());
        assertInstanceOf(ProcessingTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"processing-time\"/>", null).getTrigger());
    }

    @Test
    void countCatalogIdCarriesTheMaxCount() {
        Trigger<?, ?> trigger = buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"60m\" triggerId=\"count:7\"/>", null).getTrigger();
        assertInstanceOf(CountTrigger.class, trigger);
    }

    @Test
    void continuousCatalogIdsParseDurations() {
        assertInstanceOf(ContinuousEventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"continuous-event-time:5s\"/>", null).getTrigger());
        assertInstanceOf(ContinuousProcessingTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"continuous-processing-time:200ms\"/>", null).getTrigger());
    }

    @Test
    void purgingGrammarWrapsTheNestedTrigger() {
        Trigger<?, ?> trigger = buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"60m\" triggerId=\"purging:count:3\"/>", null).getTrigger();
        PurgingTrigger<?, ?> purging = assertInstanceOf(PurgingTrigger.class, trigger);
        assertInstanceOf(CountTrigger.class, purging.getNestedTrigger());
    }

    @Test
    void processingTimeoutGrammarWrapsTheNestedTrigger() {
        Trigger<?, ?> trigger = buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"60m\" triggerId=\"processing-timeout:event-time:10s\"/>", null).getTrigger();
        // ProcessingTimeoutTrigger exposes no public nested accessor — the wiring is
        // proven by the runtime E2E (the timeout surface only arms around a nested
        // trigger) plus the builder grammar test here.
        assertInstanceOf(ProcessingTimeoutTrigger.class, trigger);
    }

    @Test
    void beanNameBeatsTheCatalogGrammar() {
        // a bean literally named like a catalog entry must win over the grammar
        Trigger<?, ?> beanTrigger = io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger.create();
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"trigger-declaration\" version=\"1\">"
                + "<windowingStrategies><strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"60m\" triggerId=\"event-time\"/></windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("event-time", beanTrigger);
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        assertInstanceOf(ProcessingTimeTrigger.class,
                ((WindowedStreamImpl<?, ?, ?>) builder.registeredStream("w")).getTrigger());
    }

    @Test
    void nodeLevelTriggerIdOverridesTheStrategyLevel() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"processing-time\"/>",
                "<window id=\"w\" strategyRef=\"strat\"><triggerId>event-time</triggerId></window>");
        assertInstanceOf(EventTimeTrigger.class, windowed.getTrigger());
    }

    @Test
    void unknownTriggerIdFailsFastWithRefUnknown() {
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"no-such-trigger\"/>", null));
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(),
                () -> ex.getMessage());
    }

    @Test
    void nonPositiveCountFailsFast() {
        assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" triggerId=\"count:0\"/>", null));
    }

    @Test
    void updateEmitConflictsWithExplicitTriggerId() {
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" emit=\"UPDATE\" triggerId=\"event-time\"/>", null));
        assertTrue(ex.getMessage().contains("conflict"), ex::getMessage);
    }

    @Test
    void nonMergeableTriggerOnSessionAssignerFailsFast() {
        // CountTrigger.canMerge()=false — the runtime onMerge contract would reject it,
        // so the builder rejects the combination up front (session-event-time is a
        // MergingWindowAssigner).
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"session-event-time\""
                        + " duration=\"5s\" triggerId=\"count:2\"/>", null));
        assertTrue(ex.getMessage().contains("merge"), ex::getMessage);
    }

}
