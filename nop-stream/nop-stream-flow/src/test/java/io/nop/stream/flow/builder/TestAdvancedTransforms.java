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
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.FirstMatchPatternProcessFunction;
import io.nop.stream.flow.testing.IdentityProcessFunction;
import io.nop.stream.flow.testing.SumAggregateFunction;
import io.nop.stream.flow.testing.SumReduceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * ("window operator factory ships in core (WI7)") is thrown — proving the builder reached the right
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
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
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
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.upstream-type", ex.getErrorCode().toString());
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
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("aggFn", new SumAggregateFunction());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        // WI7 (plan 2417): the window operator factory now ships in core, so the
        // aggregate branch (windowed.aggregate(fn)) dispatches and builds successfully.
        // This proves dispatch + wiring through the windowed aggregate path end-to-end.
        assertNotNull(builder.build(),
                "windowed aggregate dispatch should build now that the factory ships in core");
    }

    @Test
    public void aggregateWithoutBeanOrRefFailsFast() {
        // WI8c: exactly-one adjudication replaces the former bean-required error —
        // neither bean nor aggregatorRef is now ERR_STREAM_INVALID_ARG. The
        // upstream WindowedStream check runs first, so the keyBy+window chain is
        // required for this test to reach the exactly-one branch.
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("exactly one of bean or aggregatorRef"),
                () -> "Expected exactly-one error, got: " + ex.getMessage());
    }

    @Test
    public void aggregateWithBothBeanAndRefFailsFast() {
        // WI8c: bean and aggregatorRef together are rejected — no silent precedence.
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\" bean=\"aggFn\" aggregatorRef=\"a1\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>"
                        + "<aggregators>"
                        + "<aggregator aggregatorId=\"a1\" fnId=\"sum\" expr=\"amount\"/>"
                        + "</aggregators>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("exactly one of bean or aggregatorRef"),
                () -> "Expected exactly-one error, got: " + ex.getMessage());
    }

    @Test
    public void aggregateRefWithoutProviderFailsFastNamingDependency() {
        // WI8c no-provider branch: this module's test classpath has nop-stream-sql
        // absent, so the BeanContainer has no IAggregatorFunctionResolver provider —
        // a legal aggregatorRef declaration must fail fast naming the dependency,
        // never silently fall back.
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"a1\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>"
                        + "<aggregators>"
                        + "<aggregator aggregatorId=\"a1\" fnId=\"sum\" expr=\"amount\"/>"
                        + "</aggregators>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("nop-stream-sql"),
                () -> "Expected no-provider error naming nop-stream-sql, got: " + ex.getMessage());
    }

    @Test
    public void aggregateUnknownAggregatorRefFailsFast() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"global-strat\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"missing\"/>",
                "<windowingStrategies>"
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>"
                        + "<aggregators>"
                        + "<aggregator aggregatorId=\"a1\" fnId=\"sum\" expr=\"amount\"/>"
                        + "</aggregators>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("missing"),
                () -> "Expected unknown-ref error naming the id, got: " + ex.getMessage());
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
                        + "<strategy strategyId=\"global-strat\" windowFnId=\"global\"/>"
                        + "</windowingStrategies>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("intSrcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("reduceFn", new SumReduceFunction());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        // WI7 (plan 2417): factory ships in core — windowed reduce dispatch builds.
        assertNotNull(builder.build(),
                "windowed reduce dispatch should build now that the factory ships in core");
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

    /**
     * R5-AR-02: the &lt;custom&gt;&lt;source&gt; xpl body is xdef-declared and parsed
     * into the model but has no execution consumer (the operator bean is resolved
     * purely from customType). The builder must fail fast instead of silently
     * dropping the declared transformation logic.
     */
    @Test
    public void customWithSourceBodyFailsFastInsteadOfSilentDrop() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<custom id=\"cu\" customType=\"myOp\">"
                        + "<source>return event;</source>"
                        + "</custom>",
                "");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("myOp", new StreamMap<>((MapFunction<String, String>) String::toUpperCase));

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build,
                "<custom><source> must be rejected loudly, never silently dropped");
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString(),
                () -> "expected ERR_STREAM_NOT_IMPLEMENTED, got: " + ex.getMessage());
        String detail = String.valueOf(ex.getParam("detail"));
        assertTrue(detail.contains("source"),
                () -> "error must name the unconsumed <source> element, got: " + detail);
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
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.upstream-type", ex.getErrorCode().toString());
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
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("doesNotExist"),
                () -> "Expected unknown-pattern-ref error, got: " + ex.getMessage());
    }

    // ----------------------------------------------------------------
    // union — WI6: real multi-input merge over the declared edges
    // ----------------------------------------------------------------

    @Test
    public void unionTransformProducesUnionStreamInRegistry() {
        // explicit edges — parseInline's edgeChain auto-chains sequentially and cannot
        // express the fan-in shape a union needs
        StreamModel model = parseWithEdges(
                "<source id=\"src1\" bean=\"srcFn\"/>"
                        + "<source id=\"src2\" bean=\"srcFn\"/>"
                        + "<union id=\"u\"/>",
                "<edges>"
                        + "<edge id=\"ue0\" from=\"src1\" to=\"u\" partition=\"FORWARD\"/>"
                        + "<edge id=\"ue1\" from=\"src2\" to=\"u\" partition=\"FORWARD\"/>"
                        + "</edges>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();

        Object union = builder.registeredStream("u");
        assertInstanceOf(io.nop.stream.core.datastream.DataStream.class, union,
                "<union> must produce a DataStream");
        Object transform = ((io.nop.stream.core.datastream.DataStreamImpl<?>) union).getTransformation();
        assertInstanceOf(io.nop.stream.core.transformation.UnionTransformation.class, transform,
                "union stream must wrap a UnionTransformation");
        assertEquals(2, ((io.nop.stream.core.transformation.UnionTransformation<?>) transform)
                        .getUnionInputs().size(),
                "union must merge both declared upstream edges");
    }

    @Test
    public void unionWithoutUpstreamFailsFast() {
        StreamModel model = parseInline("<union id=\"u\"/>", "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("union"),
                () -> "Expected union in error, got: " + ex.getMessage());
    }

    @Test
    public void unionRejectsKeyedUpstream() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<union id=\"u\"/>",
                "");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.upstream-type", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("KeyedStream"),
                () -> "Expected KeyedStream rejection, got: " + ex.getMessage());
    }

    @Test
    public void hashEdgeIntoUnionFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src1\" bean=\"srcFn\"/>"
                        + "<source id=\"src2\" bean=\"srcFn\"/>"
                        + "<union id=\"u\"/>",
                "<edges>"
                        + "<edge id=\"ue0\" from=\"src1\" to=\"u\" partition=\"HASH\" keyExpr=\"event\"/>"
                        + "<edge id=\"ue1\" from=\"src2\" to=\"u\" partition=\"FORWARD\"/>"
                        + "</edges>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamException ex = assertThrows(StreamException.class,
                builder::build);
        assertEquals("nop.err.stream.edge-hash-redundant", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("keyBy"),
                () -> "Expected keyBy-after-union guidance, got: " + ex.getMessage());
    }

    @Test
    public void sideOutputBuildsTapAndStampsConsumerEdgeTag() {
        // WI8 Phase 2 (plan 2332): <sideOutput> no longer fails fast — it builds a
        // pass-through tap vertex, and the declared <edge outputTag> stamps the tag on
        // the consumer's incoming edge (StreamEdge.outputTag → JobEdge.outputTagId) so
        // the deployment layer routes the tag channel and registers the target's
        // side-output consumer.
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>"
                        + "<map id=\"m2\"><source>event</source></map>"
                        + "<sink id=\"mainOut\" bean=\"mainSink\"/>"
                        + "<sink id=\"tagOut\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"so\"/>"
                        + "<edge id=\"e1\" from=\"so\" to=\"m2\" outputTag=\"late\"/>"
                        + "<edge id=\"e2\" from=\"m2\" to=\"tagOut\"/>"
                        + "<edge id=\"e3\" from=\"so\" to=\"mainOut\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("mainSink", new CollectingSinkFunction<String>());
        resolver.register("tagSink", new CollectingSinkFunction<String>());

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamExecutionEnvironment env = assertDoesNotThrow(builder::build,
                () -> "a paired <sideOutput>/<edge outputTag> declaration must build");

        io.nop.stream.core.jobgraph.JobGraph jobGraph = env.buildJobGraph("side-output-wiring");
        io.nop.stream.core.jobgraph.JobEdge tagEdge = jobGraph.getEdges().stream()
                .filter(e -> e.getOutputTagId() != null)
                .findFirst().orElse(null);
        assertTrue(tagEdge != null,
                () -> "the JobGraph must carry the tag edge, edges=" + jobGraph.getEdges());
        assertEquals("late", tagEdge.getOutputTagId(),
                "the tag edge must carry the declared outputTag id");
    }

    /**
     * validateDag pairing path 1: a declared sideOutput tag with NO consuming tag edge
     * fails fast with ERR_STREAM_INVALID_ARG (the former NOT_IMPLEMENTED gap is now a
     * declaration-typo diagnosis).
     */
    @Test
    public void sideOutputWithoutConsumerEdgeFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>"
                        + "<map id=\"m2\"><source>event</source></map>"
                        + "<sink id=\"out\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"so\"/>"
                        + "<edge id=\"e1\" from=\"m2\" to=\"out\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("tagSink", new CollectingSinkFunction<String>());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("no consuming tag edge"),
                () -> "expected the no-consumer pairing error, got: " + ex.getMessage());
    }

    /**
     * validateDag pairing path 2: a tag edge consuming an UNDECLARED tag (from is not
     * the declaring sideOutput node) fails fast with ERR_STREAM_REF_UNKNOWN.
     */
    @Test
    public void tagEdgeConsumingUndeclaredTagFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"m1\"><source>event</source></map>"
                        + "<map id=\"m2\"><source>event</source></map>"
                        + "<sink id=\"out\" bean=\"tagSink\"/>"
                        + "<sink id=\"tagOut\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"m1\"/>"
                        + "<edge id=\"e1\" from=\"m1\" to=\"m2\" outputTag=\"late\"/>"
                        + "<edge id=\"e2\" from=\"m2\" to=\"tagOut\"/>"
                        + "<edge id=\"e3\" from=\"m1\" to=\"out\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("tagSink", new CollectingSinkFunction<String>());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("does not declare a <sideOutput> node"),
                () -> "expected the undeclared-tag error, got: " + ex.getMessage());
    }

    /**
     * validateDag pairing path 3: the same tag consumed by multiple edges fails fast —
     * the runtime tag registration is replace-by-id semantics, so a second consumer
     * would silently take over the first.
     */
    @Test
    public void sameTagConsumedByMultipleEdgesFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>"
                        + "<map id=\"m2\"><source>event</source></map>"
                        + "<map id=\"m3\"><source>event</source></map>"
                        + "<sink id=\"tagOut2\" bean=\"tagSink\"/>"
                        + "<sink id=\"tagOut3\" bean=\"tagSink\"/>"
                        + "<sink id=\"mainOut\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"so\"/>"
                        + "<edge id=\"e1\" from=\"so\" to=\"m2\" outputTag=\"late\"/>"
                        + "<edge id=\"e2\" from=\"m2\" to=\"tagOut2\"/>"
                        + "<edge id=\"e3\" from=\"so\" to=\"m3\" outputTag=\"late\"/>"
                        + "<edge id=\"e4\" from=\"m3\" to=\"tagOut3\"/>"
                        + "<edge id=\"e5\" from=\"so\" to=\"mainOut\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("tagSink", new CollectingSinkFunction<String>());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("consumed by multiple"),
                () -> "expected the multi-consumer pairing error, got: " + ex.getMessage());
    }

    /**
     * validateDag pairing path 4: a tag edge declaring partition="HASH" (+ keyExpr)
     * fails fast — a tag edge follows the producer's partitioning.
     */
    @Test
    public void tagEdgeWithHashPartitionFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>"
                        + "<map id=\"m2\"><source>event</source></map>"
                        + "<sink id=\"tagOut\" bean=\"tagSink\"/>"
                        + "<sink id=\"mainOut\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"so\"/>"
                        + "<edge id=\"e1\" from=\"so\" to=\"m2\" outputTag=\"late\""
                        + " partition=\"HASH\" keyExpr=\"event\"/>"
                        + "<edge id=\"e2\" from=\"m2\" to=\"tagOut\"/>"
                        + "<edge id=\"e3\" from=\"so\" to=\"mainOut\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("tagSink", new CollectingSinkFunction<String>());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.edge-attr-unsupported", ex.getErrorCode().toString(),
                () -> "expected ERR_STREAM_EDGE_ATTR_UNSUPPORTED, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("partition"),
                () -> "error must name the partition attribute, got: " + ex.getMessage());
    }

    /**
     * A tag edge into a sink is rejected: the supported tag consumption surface is
     * single-input one-input transforms; sinks consume side outputs via the Java API
     * getSideOutput(tag) view.
     */
    @Test
    public void tagEdgeIntoSinkFailsFast() {
        StreamModel model = parseWithEdges(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<sideOutput id=\"so\" tag=\"late\"/>"
                        + "<sink id=\"tagOut\" bean=\"tagSink\"/>"
                        + "<sink id=\"mainOut\" bean=\"tagSink\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"src\" to=\"so\"/>"
                        + "<edge id=\"e1\" from=\"so\" to=\"tagOut\" outputTag=\"late\"/>"
                        + "<edge id=\"e2\" from=\"so\" to=\"mainOut\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = resolver();
        resolver.register("tagSink", new CollectingSinkFunction<String>());
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("single-input transform"),
                () -> "expected the unsupported-target error, got: " + ex.getMessage());
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
     * Like {@link #parseInline} but with caller-declared edges instead of the
     * sequential auto-chain — union topologies need the fan-in shape.
     */
    private StreamModel parseWithEdges(String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-advanced\" version=\"1\">"
                + "<transforms>" + transformsXml + "</transforms>"
                + edgesXml
                + "</stream>";

        XNode node = XNode.parse(xml);
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
