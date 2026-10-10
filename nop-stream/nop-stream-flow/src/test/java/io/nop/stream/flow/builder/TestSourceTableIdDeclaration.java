/*
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.source.RateLimitedSourceFunction;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U4 (deferred-unlock, plan 2377): the {@code <source tableId>} declaration must land
 * on the registered {@link SourceTransformation} — the carrier the graph generator
 * reads to stamp (INSERT, tableId) at the source emission points. Absent/blank stays
 * null (undeclared), and the declaration coexists with the U3 rate-limit attributes.
 */
public class TestSourceTableIdDeclaration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static StreamModelDslBuilder buildModel(String sourceAttrs) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"table-id-declaration\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcFn\"" + sourceAttrs + "/>"
                + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("sinkFn", new io.nop.stream.flow.testing.CollectingSinkFunction());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        return builder;
    }

    private static SourceTransformation<?> sourceTransformation(StreamModelDslBuilder builder) {
        Object stream = builder.registeredStream("src");
        assertTrue(stream instanceof DataStreamImpl,
                () -> "the source must register a DataStream, got: "
                        + (stream == null ? "null" : stream.getClass().getName()));
        return (SourceTransformation<?>) ((DataStreamImpl<?>) stream).getTransformation();
    }

    @Test
    public void declaredTableIdReachesSourceTransformation() {
        SourceTransformation<?> transformation = sourceTransformation(
                buildModel(" tableId=\"db.orders\""));
        assertEquals("db.orders", transformation.getDeclaredTableId(),
                "the <source tableId> declaration must ride the registered SourceTransformation");
    }

    @Test
    public void absentTableIdStaysNull() {
        SourceTransformation<?> transformation = sourceTransformation(buildModel(""));
        assertNull(transformation.getDeclaredTableId(),
                "no declaration = undeclared: the historic insert-only default");
    }

    @Test
    public void tableIdCoexistsWithRateLimitDeclaration() {
        SourceTransformation<?> transformation = sourceTransformation(
                buildModel(" tableId=\"db.a\" rateLimitRowsPerSecond=\"100\""));
        assertEquals("db.a", transformation.getDeclaredTableId(),
                "tableId and rateLimit are independent declaration faces");
        assertTrue(transformation.getSourceFunction() instanceof RateLimitedSourceFunction,
                "the rate-limit wrapper must still be applied on the same source");
    }
}
