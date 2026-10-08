/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamJoinModel;
import io.nop.stream.flow.model.StreamJoinSpecModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8d: the parameterized join declaration surface. Build-time validation only —
 * the join runtime belongs to WI13's buildJoin, so every legal declaration walks
 * through validation and then hits the explicit NOT_IMPLEMENTED placeholder (the
 * correct no-silent-no-op form: a typed failure, never an empty body).
 */
public class TestParameterizedJoinModel {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String TWO_SOURCES =
            "<source id=\"s1\" bean=\"srcFn\"/>"
                    + "<source id=\"s2\" bean=\"srcFn\"/>";

    private StreamModel parseWithJoins(String transformsXml, String joinsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-join\" version=\"1\">"
                + joinsXml
                + "<transforms>" + transformsXml + "</transforms>"
                + edgesXml
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private static final String WINDOWED_JOIN_SPEC =
            "<joins><joinSpec joinId=\"j1\" joinType=\"LEFT\""
                    + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                    + " windowStrategyRef=\"ws1\" timeout=\"10s\"/></joins>"
                    + "<windowingStrategies>"
                    + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                    + "</windowingStrategies>";

    private static final String JOIN_TRANSFORM =
            "<join id=\"j\" joinRef=\"j1\"/>";
    // two edges from s1 and s2 into the join — the legal left/right shape
    private static final String JOIN_EDGES =
            "<edges>"
                    + "<edge id=\"e0\" from=\"s1\" to=\"j\"/>"
                    + "<edge id=\"e1\" from=\"s2\" to=\"j\"/>"
                    + "</edges>";

    private StreamModelDslBuilder builderOf(String transformsXml, String joinsXml, String edgesXml) {
        return StreamModelDslBuilder.of(
                parseWithJoins(transformsXml, joinsXml, edgesXml),
                new InMemoryBeanFunctionResolver());
    }

    @Test
    public void legalJoinDeclarationParsesAndRoundTrips() {
        StreamModel model = parseWithJoins(TWO_SOURCES + JOIN_TRANSFORM,
                WINDOWED_JOIN_SPEC, JOIN_EDGES);
        StreamJoinModel join = (StreamJoinModel) model.getTransforms().stream()
                .filter(t -> t instanceof StreamJoinModel).findFirst().orElseThrow();
        assertEquals("j1", join.getJoinRef());

        StreamJoinSpecModel spec = model.getJoinSpec("j1");
        assertEquals(io.nop.stream.core.model.JoinType.LEFT, spec.getJoinType());
        assertEquals("a", spec.getLeftKeyExprs());
        assertEquals("b", spec.getRightKeyExprs());
        assertEquals("ws1", spec.getWindowStrategyRef());
        assertEquals("10s", spec.getTimeout());
    }

    @Test
    public void unknownJoinRefFailsFast() {
        StreamModel model = parseWithJoins(TWO_SOURCES + "<join id=\"j\" joinRef=\"missing\"/>",
                WINDOWED_JOIN_SPEC, JOIN_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("missing"), () -> ex.getMessage());
    }

    @Test
    public void missingJoinTypeFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\""
                + " leftKeyExprs=\"a\" rightKeyExprs=\"b\"/></joins>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("joinType"), () -> ex.getMessage());
    }

    @Test
    public void missingKeyExprsFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"INNER\""
                + " leftKeyExprs=\"a\"/></joins>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("leftKeyExprs and rightKeyExprs"), () -> ex.getMessage());
    }

    @Test
    public void keyCountMismatchFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"INNER\""
                + " leftKeyExprs=\"a,b\" rightKeyExprs=\"c\"/></joins>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("arity"), () -> ex.getMessage());
    }

    @Test
    public void singleUpstreamJoinFailsFast() {
        // join requires exactly two upstream edges (left/right)
        StreamModel model = parseWithJoins(TWO_SOURCES + JOIN_TRANSFORM,
                WINDOWED_JOIN_SPEC,
                "<edges><edge id=\"e0\" from=\"s1\" to=\"j\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("exactly two"), () -> ex.getMessage());
    }

    @Test
    public void hashEdgeIntoJoinFailsFast() {
        StreamModel model = parseWithJoins(TWO_SOURCES + JOIN_TRANSFORM,
                WINDOWED_JOIN_SPEC,
                "<edges>"
                        + "<edge id=\"e0\" from=\"s1\" to=\"j\" partition=\"HASH\" keyExpr=\"a\"/>"
                        + "<edge id=\"e1\" from=\"s2\" to=\"j\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.edge-hash-redundant", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("join"), () -> ex.getMessage());
    }

    @Test
    public void unknownWindowStrategyRefFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"INNER\""
                + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                + " windowStrategyRef=\"nope\"/></joins>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("nope"), () -> ex.getMessage());
    }

    @Test
    public void fullWindowJoinFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"FULL\""
                + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                + " windowStrategyRef=\"ws1\"/></joins>"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                + "</windowingStrategies>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("INNER/LEFT"), () -> ex.getMessage());
    }

    @Test
    public void timeoutWithoutWindowStrategyFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"INNER\""
                + " leftKeyExprs=\"a\" rightKeyExprs=\"b\" timeout=\"10s\"/></joins>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("windowStrategyRef"), () -> ex.getMessage());
    }

    @Test
    public void illegalTimeoutFormatFailsFast() {
        String spec = "<joins><joinSpec joinId=\"j1\" joinType=\"INNER\""
                + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                + " windowStrategyRef=\"ws1\" timeout=\"bogus\"/></joins>"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                + "</windowingStrategies>";
        StreamException ex = assertThrows(StreamException.class,
                () -> builderOf(TWO_SOURCES + JOIN_TRANSFORM, spec, JOIN_EDGES).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
    }

    @Test
    public void selfJoinTopologyWalksValidationToRealJoinBuild() {
        // SELF-JOIN shape (roadmap WI8d completion criterion): ONE join element fed
        // by two edges from the SAME source — the exactly-two-upstream check's
        // positive case. WI13 landed the join runtime: the declared topology now
        // walks validation into a REAL join build (tag → union → keyBy →
        // EquiJoinOperator) instead of the former NOT_IMPLEMENTED placeholder.
        StreamModel model = parseWithJoins(
                "<source id=\"s1\" bean=\"srcFn\"/>" + JOIN_TRANSFORM,
                WINDOWED_JOIN_SPEC,
                "<edges>"
                        + "<edge id=\"e0\" from=\"s1\" to=\"j\"/>"
                        + "<edge id=\"e1\" from=\"s1\" to=\"j\"/>"
                        + "</edges>");

        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        assertNotNull(env, "self-join topology assembles through the real buildJoin");
    }

    @Test
    public void sourceBeanResolutionNotReachedBeforeValidation() {
        // sanity: pure declaration tests fail at validation before bean resolution —
        // an unknown source bean with an INVALID join spec still fails on the join
        // (no bean container needed for these tests)
        StreamModel model = parseWithJoins(
                "<source id=\"s1\" bean=\"noSuchBean\"/>" + "<join id=\"j\" joinRef=\"nope\"/>",
                WINDOWED_JOIN_SPEC, JOIN_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString());
        assertInstanceOf(StreamJoinModel.class,
                model.getTransforms().stream().filter(t -> t instanceof StreamJoinModel).findFirst().orElseThrow());
    }

    // ---- WI27: per-side asymmetric timeout ----

    @Test
    public void leftTimeoutOnlyRoundTripsAndDefaultsOtherSideToZero() {
        StreamModel model = parseWithJoins(
                TWO_SOURCES + "<join id=\"j\" joinRef=\"wj\"/>",
                "<joins><joinSpec joinId=\"wj\" joinType=\"INNER\""
                        + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                        + " windowStrategyRef=\"ws1\" leftTimeout=\"20s\"/></joins>"
                        + "<windowingStrategies>"
                        + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                        + "</windowingStrategies>",
                JOIN_EDGES);
        StreamJoinSpecModel spec = model.getJoinSpec("wj");
        assertEquals("20s", spec.getLeftTimeout());
        assertNull(spec.getRightTimeout());
        assertNull(spec.getTimeout());
        // legal declaration builds through the real builder (defaults applied in buildJoin)
        StreamModelDslBuilder.of(model, resolverWithSrc()).build();
    }

    @Test
    public void bothSideTimeoutsRoundTrip() throws Exception {
        StreamModel model = parseWithJoins(
                TWO_SOURCES + "<join id=\"j\" joinRef=\"wj\"/>",
                "<joins><joinSpec joinId=\"wj\" joinType=\"LEFT\""
                        + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                        + " windowStrategyRef=\"ws1\" leftTimeout=\"20s\" rightTimeout=\"5s\"/></joins>"
                        + "<windowingStrategies>"
                        + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                        + "</windowingStrategies>",
                JOIN_EDGES);
        StreamJoinSpecModel spec = model.getJoinSpec("wj");
        assertEquals("20s", spec.getLeftTimeout());
        assertEquals("5s", spec.getRightTimeout());
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolverWithSrc()).build();
        // WI27 换序盲区封堵：EOS-only 水位下 env 发射集对非对称不判别——直接断言
        // buildJoin 4 参构造把 leftTimeout/rightTimeout 传入算子的正确槽位
        io.nop.stream.core.operators.join.EquiJoinOperator<?, ?> op = findJoinOperator(env);
        assertEquals(20000L, readLong(op, "leftTimeout"), "leftTimeout slot");
        assertEquals(5000L, readLong(op, "rightTimeout"), "rightTimeout slot");
    }

    private static io.nop.stream.core.operators.join.EquiJoinOperator<?, ?> findJoinOperator(
            StreamExecutionEnvironment env) throws Exception {
        java.lang.reflect.Field tf = StreamExecutionEnvironment.class
                .getDeclaredField("transformations");
        tf.setAccessible(true);
        for (Object t : (java.util.List<?>) tf.get(env)) {
            io.nop.stream.core.transformation.Transformation<?> tr =
                    (io.nop.stream.core.transformation.Transformation<?>) t;
            if (tr.getName() != null && tr.getName().startsWith("Join:")) {
                io.nop.stream.core.operators.StreamOperatorFactory<?> factory =
                        ((io.nop.stream.core.transformation.OneInputTransformation<?, ?>) tr)
                                .getOperatorFactory();
                if (factory instanceof io.nop.stream.core.operators.SimpleStreamOperatorFactory) {
                    io.nop.stream.core.operators.StreamOperator<?> op =
                            ((io.nop.stream.core.operators.SimpleStreamOperatorFactory<?>) factory)
                                    .getRawOperator();
                    if (op instanceof io.nop.stream.core.operators.join.EquiJoinOperator) {
                        return (io.nop.stream.core.operators.join.EquiJoinOperator<?, ?>) op;
                    }
                }
            }
        }
        throw new AssertionError("join operator not found in built transformations");
    }

    private static long readLong(Object target, String field) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.getLong(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new AssertionError("no field " + field);
    }

    @Test
    public void symmetricTimeoutWithPerSideFailsFast() {
        StreamModel model = parseWithJoins(
                TWO_SOURCES + "<join id=\"j\" joinRef=\"wj\"/>",
                "<joins><joinSpec joinId=\"wj\" joinType=\"INNER\""
                        + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                        + " windowStrategyRef=\"ws1\" timeout=\"10s\" leftTimeout=\"20s\"/></joins>"
                        + "<windowingStrategies>"
                        + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
                        + "</windowingStrategies>",
                JOIN_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolverWithSrc()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("not both"),
                () -> "expected the mutual-exclusion gate, got: " + ex.getMessage());
    }

    @Test
    public void sideTimeoutWithoutWindowStrategyRefFailsFast() {
        StreamModel model = parseWithJoins(
                TWO_SOURCES + "<join id=\"j\" joinRef=\"wj\"/>",
                "<joins><joinSpec joinId=\"wj\" joinType=\"INNER\""
                        + " leftKeyExprs=\"a\" rightKeyExprs=\"b\""
                        + " leftTimeout=\"20s\"/></joins>",
                JOIN_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolverWithSrc()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("windowStrategyRef"));
    }

    private InMemoryBeanFunctionResolver resolverWithSrc() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        return resolver;
    }
}
