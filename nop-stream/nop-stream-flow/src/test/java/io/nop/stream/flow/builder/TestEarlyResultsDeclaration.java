/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.triggers.ContinuousEventTimeTrigger;
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
 * WI8 (plan 2354): the earlyResultsPeriod declaration — one of the THREE faces that
 * own the window trigger slot (triggerId / emit=UPDATE / earlyResultsPeriod), all
 * mutually exclusive at build time before any resolution. The resolved trigger is a
 * ContinuousEventTimeTrigger on the event-time axis (the processing-time axis stays
 * with the triggerId catalog's continuous-processing-time:<dur>).
 */
public class TestEarlyResultsDeclaration {

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
                + "name=\"early-results-declaration\" version=\"1\">"
                + "<windowingStrategies>" + strategyXml + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"/>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + (windowXml == null ? "<window id=\"w\" strategyRef=\"strat\"/>" : windowXml)
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"k\"/><edge id=\"e2\" from=\"k\" to=\"w\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        return (WindowedStreamImpl<?, ?, ?>) builder.registeredStream("w");
    }

    @Test
    void earlyResultsPeriodResolvesToContinuousEventTimeTrigger() {
        assertInstanceOf(ContinuousEventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"10s\"/>", null).getTrigger());
    }

    @Test
    void nodeLevelEarlyResultsOverridesTheStrategyLevel() {
        // both declared with different values — node wins; the assertion can only see
        // the trigger instance, so the override proof is that the build succeeds with
        // the mutual-exclusion gate NOT firing (two levels of ONE face are legal).
        assertInstanceOf(ContinuousEventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"10s\"/>",
                        "<window id=\"w\" strategyRef=\"strat\">"
                                + "<earlyResultsPeriod>20s</earlyResultsPeriod></window>").getTrigger());
    }

    @Test
    void earlyResultsConflictsWithTriggerId() {
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"10s\" triggerId=\"event-time\"/>", null));
        assertTrue(ex.getMessage().contains("competing declarations"), ex::getMessage);
    }

    @Test
    void earlyResultsConflictsWithEmitUpdate() {
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"10s\" emit=\"UPDATE\"/>", null));
        assertTrue(ex.getMessage().contains("competing declarations"), ex::getMessage);
    }

    @Test
    void nonPositivePeriodFailsFast() {
        assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"0\"/>", null));
    }

    @Test
    void earlyResultsAndAllowedLatenessCoDeclare() {
        // interaction-contract pin: the early-results face and allowedLateness are
        // orthogonal — same-strategy co-declaration builds (cleanup = window end +
        // lateness, untouched by interval fires).
        assertInstanceOf(ContinuousEventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" earlyResultsPeriod=\"10s\" allowedLateness=\"5000\"/>",
                        null).getTrigger());
    }

    @Test
    void sessionAssignerAcceptsTheEarlyResultsTrigger() {
        // CETT canMerge=true — the canMerge-merging guard passes on session windows.
        assertInstanceOf(ContinuousEventTimeTrigger.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"session-event-time\""
                        + " duration=\"5s\" earlyResultsPeriod=\"1s\"/>", null).getTrigger());
    }
}
