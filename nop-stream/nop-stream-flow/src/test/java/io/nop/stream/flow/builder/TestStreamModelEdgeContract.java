/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.stream.Stream;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-XDSL-5 focused tests: every declared {@code <edge>} partition/keyExpr/flow-control
 * attribute must be consumed (HASH + keyExpr via {@code keyBy}) or rejected at build time
 * with an error that locates the declaration (edge id + attribute name). The six
 * attributes (partition/keyExpr/flowControlPolicy/queueCapacity/receiveWindow/packetSize)
 * must never be silently downgraded to FORWARD.
 */
public class TestStreamModelEdgeContract {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @ParameterizedTest
    @MethodSource("failFastCases")
    public void unsupportedEdgeDeclarationFailsFast(String edgeAttrs, String expectedErrorCode,
                                                   String expectedToken) {
        StreamModel model = parseInline("",
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sink id=\"out\" bean=\"sinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"out\" " + edgeAttrs + "/>");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolver()).build());
        assertEquals(expectedErrorCode, ex.getErrorCode().toString(),
                () -> "Unexpected error code for edge attrs [" + edgeAttrs + "]: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(expectedToken),
                () -> "Error should mention '" + expectedToken + "' for edge attrs ["
                        + edgeAttrs + "]: " + ex.getMessage());
    }

    static Stream<Arguments> failFastCases() {
        return Stream.of(
                // partition="HASH" without keyExpr
                Arguments.of("partition=\"HASH\"",
                        "nop.err.stream.edge-hash-key-expr-required", "e0"),
                // keyExpr declared on a non-HASH edge
                Arguments.of("partition=\"FORWARD\" keyExpr=\"event\"",
                        "nop.err.stream.edge-key-expr-without-hash", "e0"),
                // REBALANCE / BROADCAST — no DataStream operator in core
                Arguments.of("partition=\"REBALANCE\"",
                        "nop.err.stream.edge-partition-unsupported", "REBALANCE"),
                Arguments.of("partition=\"BROADCAST\"",
                        "nop.err.stream.edge-partition-unsupported", "BROADCAST"),
                // flow-control attributes — no consumer in core
                Arguments.of("flowControlPolicy=\"BLOCKING_QUEUE\"",
                        "nop.err.stream.edge-attr-unsupported", "flowControlPolicy"),
                Arguments.of("queueCapacity=\"10\"",
                        "nop.err.stream.edge-attr-unsupported", "queueCapacity"),
                Arguments.of("receiveWindow=\"5\"",
                        "nop.err.stream.edge-attr-unsupported", "receiveWindow"),
                Arguments.of("packetSize=\"2\"",
                        "nop.err.stream.edge-attr-unsupported", "packetSize"));
    }

    @Test
    public void hashWithKeyExprTargetingKeyByTransformIsRedundantAndFailsFast() {
        StreamModel model = parseInline("",
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<sink id=\"out\" bean=\"sinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"k\" partition=\"HASH\" keyExpr=\"event\"/>");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolver()).build());
        assertEquals("nop.err.stream.edge-hash-redundant", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("e0"), () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("k"), () -> ex.getMessage());
    }

    @Test
    public void hashWithKeyExprIntoWindowIsAppliedViaKeyBy() {
        // The edge's HASH partition must key the source stream before the window consumes
        // it — the window requires a KeyedStream upstream, which only the edge's keyBy can
        // provide here. This proves the partition attribute is consumed, not ignored.
        StreamModel model = parseInline(
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>",
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"w\" partition=\"HASH\" keyExpr=\"event\"/>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        assertDoesNotThrow(builder::build);
        Object windowed = builder.registeredStream("w");
        assertInstanceOf(WindowedStream.class, windowed,
                "<window> fed through a HASH+keyExpr edge must produce a WindowedStream");
    }

    @Test
    public void hashWithKeyExprIntoSinkBuilds() {
        StreamModel model = parseInline("",
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sink id=\"out\" bean=\"sinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"out\" partition=\"HASH\" keyExpr=\"event\"/>");

        assertDoesNotThrow(() -> StreamModelDslBuilder.of(model, resolver()).build());
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new TestSourceFunction());
        resolver.register("sinkFn", new io.nop.stream.flow.testing.CollectingSinkFunction<>());
        return resolver;
    }

    private StreamModel parseInline(String topLevelXml, String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-edge-contract\" version=\"1\">"
                + topLevelXml
                + "<transforms>" + transformsXml + "</transforms>"
                + "<edges>" + edgesXml + "</edges>"
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }
}
