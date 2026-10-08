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
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
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
 * WI38 (plan 03, FU-8 fix / D10 release): the accumulationMode declared on the
 * windowing strategy must be consumed by buildWindow — previously the declaration
 * never left the builder and every model-built window ran the operator's
 * ACCUMULATING default. D10 (2026-10-08) released ACCUMULATING;
 * ACCUMULATING_AND_RETRACTING stays rejected at build time (spec-only, double-gated
 * with the runtime open() check).
 *
 * <p>Covers rings 1→2 of the wiring chain (strategy model → WindowedStreamImpl);
 * the runtime module pins rings 3→5 (factory → builder → operator field) for all
 * four operator variants.
 */
public class TestWindowAccumulationModeDeclaration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private StreamModel parseModel(String strategyXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"accumulation-mode-declaration\" version=\"1\">"
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
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        return resolver;
    }

    private WindowedStreamImpl<?, ?, ?> buildWindowed(String strategyXml) {
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(parseModel(strategyXml), resolver());
        assertDoesNotThrow(builder::build, "the strategy declaration must build cleanly");
        return assertInstanceOf(WindowedStreamImpl.class, builder.registeredStream("w"),
                "<window> must produce a WindowedStream");
    }

    @Test
    void discardingDeclarationReachesWindowedStream() {
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" accumulationMode=\"DISCARDING\"/>");
        assertEquals(AccumulationMode.DISCARDING, windowed.getAccumulationMode(),
                "the declared DISCARDING must be consumed by buildWindow (FU-8)");
    }

    @Test
    void accumulatingDeclarationBuildsAndReachesWindowedStream() {
        // D10 (2026-10-08): ACCUMULATING is released — this used to fail fast at build.
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" accumulationMode=\"ACCUMULATING\"/>");
        assertEquals(AccumulationMode.ACCUMULATING, windowed.getAccumulationMode(),
                "the declared ACCUMULATING must be consumed by buildWindow");
    }

    @Test
    void missingDeclarationNormalizesToXdefDefaultDiscarding() {
        // FU-8: the xdef default (DISCARDING) previously never reached the operator —
        // the built window silently ran ACCUMULATING. The declaration default must not
        // depend on parser injection behavior.
        WindowedStreamImpl<?, ?, ?> windowed = buildWindowed(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\" duration=\"1s\"/>");
        assertEquals(AccumulationMode.DISCARDING, windowed.getAccumulationMode(),
                "a strategy without the attribute must run the xdef default DISCARDING");
    }

    @Test
    void accumulatingAndRetractingStaysRejectedAtBuildTime() {
        StreamModel model = parseModel(
                "<strategy strategyId=\"strat\" windowFnId=\"tumbling-event-time\""
                        + " duration=\"1s\" accumulationMode=\"ACCUMULATING_AND_RETRACTING\"/>");
        Exception ex = assertThrows(Exception.class,
                () -> StreamModelDslBuilder.of(model, resolver()).build(),
                "ACCUMULATING_AND_RETRACTING is spec-only and must keep failing at build");
        assertTrue(String.valueOf(ex.getMessage()).contains("unsupported")
                        || ex.getCause() != null
                                && String.valueOf(ex.getCause().getMessage()).contains("unsupported"),
                () -> "expected the accumulationMode gate, got: " + ex.getMessage());
    }
}
