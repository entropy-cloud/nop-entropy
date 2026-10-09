/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.TimeEvictor;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI6 (plan 2352): the evictor declaration catalog — bean name first, then the flat
 * grammar (count:N / time:<dur>), then {@code ERR_STREAM_REF_UNKNOWN}; node-level
 * evictorId overrides the strategy level (allowedLateness/triggerId precedent).
 */
public class TestEvictorDeclaration {

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
                + "name=\"evictor-declaration\" version=\"1\">"
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
    void countGrammarParsesTheMaxCount() {
        assertInstanceOf(CountEvictor.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" evictorId=\"count:2\"/>", null).getEvictor());
    }

    @Test
    void timeGrammarParsesTheDuration() {
        assertInstanceOf(TimeEvictor.class,
                buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" evictorId=\"time:5s\"/>", null).getEvictor());
    }

    @Test
    void beanNameBeatsTheCatalogGrammar() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"evictor-declaration\" version=\"1\">"
                + "<windowingStrategies><strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                + " duration=\"60m\" evictorId=\"count:2\"/></windowingStrategies>"
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
        // a bean literally named like a catalog entry must win over the grammar
        resolver.register("count:2", io.nop.stream.core.windowing.evictors.TimeEvictor.of(
                java.time.Duration.ofSeconds(1)));
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        assertInstanceOf(TimeEvictor.class,
                ((WindowedStreamImpl<?, ?, ?>) builder.registeredStream("w")).getEvictor());
    }

    @Test
    void nodeLevelEvictorIdOverridesTheStrategyLevel() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" evictorId=\"time:5s\"/>",
                "<window id=\"w\" strategyRef=\"strat\"><evictorId>count:3</evictorId></window>");
        assertInstanceOf(CountEvictor.class, windowed.getEvictor());
    }

    @Test
    void unknownEvictorIdFailsFastWithRefUnknown() {
        StreamException ex = assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" evictorId=\"no-such-evictor\"/>", null));
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(), ex::getMessage);
    }

    @Test
    void nonPositiveCountFailsFast() {
        assertThrows(StreamException.class,
                () -> buildWindowed("<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"60m\" evictorId=\"count:0\"/>", null));
    }
}
