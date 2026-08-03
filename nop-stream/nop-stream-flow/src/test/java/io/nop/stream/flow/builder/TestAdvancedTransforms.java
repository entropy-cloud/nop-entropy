/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Arrays;
import java.util.List;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.FirstMatchPatternProcessFunction;
import io.nop.stream.flow.testing.IdentityProcessFunction;
import io.nop.stream.flow.testing.SumAggregateFunction;
import io.nop.stream.flow.testing.SumReduceFunction;
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
 * Phase 2 focused tests: verify that {@link StreamModelDslBuilder} correctly dispatches
 * and wires each advanced transform type declared in {@code stream.xdef}.
 *
 * <p>Each test builds a minimal pipeline (via inline {@code .stream.xml} parsed through
 * the real XDSL parser) and asserts on the registered stream type produced by the
 * builder. Transforms that require the {@code nop-stream-runtime}
 * {@code IWindowOperatorFactory} (window→aggregate, window→reduce) are asserted to
 * dispatch correctly by checking that the expected {@link StreamException}
 * ("nop-stream-runtime on classpath") is thrown — proving the builder reached the right
 * branch, with the runtime gap already documented in the plan's Deferred section.
 */
public class TestAdvancedTransforms {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ----------------------------------------------------------------
    // window
    // ----------------------------------------------------------------

    @Test
    public void windowTransformProducesWindowedStreamInRegistry() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\" triggerId=\"t\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();

        Object windowed = builder.registeredStream("w");
        assertInstanceOf(WindowedStream.class, windowed,
                "<window> on a KeyedStream upstream must produce a WindowedStream");
    }

    @Test
    public void windowRejectsNonKeyedUpstream() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\" triggerId=\"t\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                builder::build);
        assertTrue(ex.getMessage().contains("KeyedStream"),
                () -> "Expected KeyedStream requirement error, got: " + ex.getMessage());
    }

    // ----------------------------------------------------------------
    // aggregate (needs window operator factory → StreamException dispatch proof)
    // ----------------------------------------------------------------

    @Test
    public void aggregateDispatchesToWindowedAggregatePath() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\" bean=\"aggFn\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\" triggerId=\"t\"/>"
                        + "</windowingStrategies>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("aggFn", new SumAggregateFunction());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        // The builder reaches the aggregate branch (windowed.aggregate(fn)); the runtime
        // throws StreamException because no IWindowOperatorFactory is on the test classpath.
        // This proves dispatch + wiring up to the runtime boundary.
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertTrue(ex.getMessage().contains("nop-stream-runtime"),
                () -> "Expected runtime-factory gap exception, got: " + ex.getMessage());
    }

    @Test
    public void aggregateWithoutBeanFailsFast() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\" triggerId=\"t\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                builder::build);
        assertTrue(ex.getMessage().contains("bean"),
                () -> "Expected bean requirement error, got: " + ex.getMessage());
    }

    // ----------------------------------------------------------------
    // reduce (KeyedStream — executable; WindowedStream — runtime gap)
    // ----------------------------------------------------------------

    @Test
    public void reduceOnKeyedStreamProducesSingleOutputStreamOperator() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"intSrcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<reduce id=\"r\" bean=\"reduceFn\"/>",
                "");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("intSrcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("reduceFn", new SumReduceFunction());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();

        Object reduced = builder.registeredStream("r");
        assertInstanceOf(SingleOutputStreamOperator.class, reduced,
                "<reduce> on a KeyedStream must produce a SingleOutputStreamOperator");
    }

    @Test
    public void reduceOnWindowedStreamDispatchesToWindowedReducePath() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"intSrcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<reduce id=\"r\" bean=\"reduceFn\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\" triggerId=\"t\"/>"
                        + "</windowingStrategies>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("intSrcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("reduceFn", new SumReduceFunction());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertTrue(ex.getMessage().contains("nop-stream-runtime"),
                () -> "Expected runtime-factory gap exception, got: " + ex.getMessage());
    }

    @Test
    public void reduceWithInlineXplCompilesAndDispatches() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<reduce id=\"r\"><source>return a;</source></reduce>",
                "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();
        assertInstanceOf(SingleOutputStreamOperator.class, builder.registeredStream("r"));
    }

    // ----------------------------------------------------------------
    // process
    // ----------------------------------------------------------------

    @Test
    public void processOnDataStreamProducesSingleOutputStreamOperator() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<process id=\"p\" bean=\"procFn\"/>",
                "");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("procFn", new IdentityProcessFunction<>());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        assertInstanceOf(SingleOutputStreamOperator.class, builder.registeredStream("p"));
    }

    // ----------------------------------------------------------------
    // timestampsAndWatermarks
    // ----------------------------------------------------------------

    @Test
    public void timestampsAndWatermarksProducesSingleOutputStreamOperator() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<timestampsAndWatermarks id=\"ts\">"
                        + "<timestampAssigner>return 0L;</timestampAssigner>"
                        + "</timestampsAndWatermarks>",
                "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();
        Object out = builder.registeredStream("ts");
        assertInstanceOf(SingleOutputStreamOperator.class, out);
    }

    // ----------------------------------------------------------------
    // custom
    // ----------------------------------------------------------------

    @Test
    public void customTransformProducesSingleOutputStreamOperator() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<custom id=\"cu\" customType=\"myOp\"/>",
                "");

        InMemoryBeanFunctionResolver resolver = resolver();
        // StreamMap is a concrete OneInputStreamOperator; use it as the "custom operator".
        resolver.register("myOp", new StreamMap<>((MapFunction<String, String>) String::toUpperCase));

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        builder.build();
        assertInstanceOf(SingleOutputStreamOperator.class, builder.registeredStream("cu"));
    }

    @Test
    public void customWithoutCustomTypeRejectedByXdef() {
        // customType is mandatory at the xdef level (customType="!xml-name"), so a
        // missing attribute is rejected by the parser before the builder is invoked.
        assertThrows(Exception.class, () -> parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<custom id=\"cu\"/>", ""));
    }

    // ----------------------------------------------------------------
    // CEP
    // ----------------------------------------------------------------

    @Test
    public void cepTransformBuildsPatternAndDispatches() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<cep id=\"c\" patternRef=\"p1\" bean=\"cepFn\"/>",
                "<patterns>"
                        + "<pattern name=\"p1\" start=\"first\">"
                        + "<single name=\"first\"><where>return true;</where></single>"
                        + "</pattern>"
                        + "</patterns>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("cepFn", new FirstMatchPatternProcessFunction<>());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        // The builder resolves the pattern, builds it via CepPatternBuilder, calls
        // CEP.pattern + patternStream.process. This should succeed at the builder level.
        assertDoesNotThrow(builder::build);
        assertInstanceOf(SingleOutputStreamOperator.class, builder.registeredStream("c"));
    }

    @Test
    public void cepRejectsNonKeyedUpstream() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<cep id=\"c\" patternRef=\"p1\" bean=\"cepFn\"/>",
                "<patterns>"
                        + "<pattern name=\"p1\" start=\"first\">"
                        + "<single name=\"first\"><where>return true;</where></single>"
                        + "</pattern>"
                        + "</patterns>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("cepFn", new FirstMatchPatternProcessFunction<>());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                builder::build);
        assertTrue(ex.getMessage().contains("KeyedStream"),
                () -> "Expected KeyedStream requirement error, got: " + ex.getMessage());
    }

    @Test
    public void cepWithUnknownPatternRefFailsFast() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<cep id=\"c\" patternRef=\"doesNotExist\" bean=\"cepFn\"/>",
                "");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("cepFn", new FirstMatchPatternProcessFunction<>());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                builder::build);
        assertTrue(ex.getMessage().contains("doesNotExist"),
                () -> "Expected unknown-pattern-ref error, got: " + ex.getMessage());
    }

    // ----------------------------------------------------------------
    // union / sideOutput — runtime API gap fail-fast (from AdvancedTransforms)
    // ----------------------------------------------------------------

    @Test
    public void unionTransformThrowsWithRuntimeGapMessage() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<union id=\"u\"/>",
                "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, builder::build);
        assertTrue(ex.getMessage().contains("union"),
                () -> "Expected union in error, got: " + ex.getMessage());
    }

    @Test
    public void sideOutputTransformThrowsWithRuntimeGapMessage() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>",
                "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, builder::build);
        assertTrue(ex.getMessage().contains("sideOutput"),
                () -> "Expected sideOutput in error, got: " + ex.getMessage());
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    /**
     * Build the standard test resolver pre-registered with {@code srcFn}
     * (the fixed-data TestSourceFunction emitting ["a","b","c"]).
     */
    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        return resolver;
    }

    private StreamModel parseInline(String transformsXml, String topLevelXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-advanced\" version=\"1\">"
                + topLevelXml
                + "<transforms>" + transformsXml + "</transforms>"
                + edgeChain(transformsXml)
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    /**
     * Auto-generate FORWARD edges so the transforms form a linear chain in declaration
     * order (e.g. src→k→w). Each transform id is connected to the next one parsed from
     * the snippet.
     */
    private static String edgeChain(String transformsXml) {
        List<String> ids = java.util.stream.Stream.of(transformsXml.split("(?=<[a-zA-Z])"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    int idStart = s.indexOf("id=\"");
                    if (idStart < 0) {
                        return null;
                    }
                    int idEnd = s.indexOf("\"", idStart + 4);
                    return s.substring(idStart + 4, idEnd);
                })
                .collect(java.util.stream.Collectors.toList());

        StringBuilder edges = new StringBuilder("<edges>");
        int edgeIdx = 0;
        for (int i = 0; i + 1 < ids.size(); i++) {
            if (ids.get(i) == null || ids.get(i + 1) == null) {
                continue;
            }
            edges.append("<edge id=\"e").append(edgeIdx++).append("\" from=\"")
                    .append(ids.get(i)).append("\" to=\"").append(ids.get(i + 1))
                    .append("\" partition=\"FORWARD\"/>");
        }
        edges.append("</edges>");
        return edges.toString();
    }
}
