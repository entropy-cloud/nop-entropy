/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.45 R4-05 (plan 2538) W-07 / 1.3-C1-005: {@code StreamModelDslBuilder.build()}
 * must not mutate (consume) the caller-held parsed {@link StreamModel}. Pre-fix the
 * model is consumed in place: {@code expandSqlModel} replaces the compiled content
 * wholesale (six setters + {@code setSql(null)}) and {@code expandSqlTransformVertex}
 * replaces the transforms/edges lists and rewrites existing edge objects
 * ({@code e.setFrom(exit)}) — a second build (or any other consumer reusing the parsed
 * model under the platform's model-cache conventions) runs on consumed residual state.
 *
 * <p>Driver 红⑦ (F-4 pin shape — residual-state leg is the MAIN leg): after ONE build
 * the input model's {@code <sql>} declaration, transforms list, edges list and edge
 * from/to values must all be unchanged (object identity asserted). Pre-fix the
 * assertions fail deterministically on a single build = red. The double-build
 * equivalence legs are the post-fix calibration legs (each build re-expands from
 * pristine input).
 */
public class TestDslBuilderBuildZeroConsumptionR405 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-sql-expansion.beans.xml"));
        container = builder.build("r405-build-contract");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static StreamModel parseModel(String transformsXml, String edgesXml, String extra) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"r405-build-contract\" version=\"1\">"
                + (transformsXml == null ? "" : "<transforms>" + transformsXml + "</transforms>")
                + (edgesXml == null ? "" : "<edges>" + edgesXml + "</edges>")
                + (extra == null ? "" : extra)
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    /**
     * F-4 residual-state MAIN leg (top-level {@code <sql>} face): one build must
     * leave the input model pristine.
     */
    @Test
    public void sqlExpansionDoesNotMutateTheInputModel() {
        StreamModel model = parseModel(null, null,
                "<sql sinkBean=\"fakeSinkFn\"><source>ANY SQL TEXT</source></sql>");
        Object sqlBefore = model.getSql();
        assertNotNull(sqlBefore, "model must declare a <sql> element pre-build");
        List<?> transformsBefore = model.getTransforms();
        List<?> edgesBefore = model.getEdges();
        assertTrue(model.getSql() == sqlBefore);

        assertNotNull(StreamModelDslBuilder.of(model).build(), "build must succeed");

        // the <sql> declaration is NOT consumed
        assertSame(sqlBefore, model.getSql(),
                "build() must not consume the <sql> declaration (setSql(null) pre-fix)");
        // transforms/edges lists: object identity preserved — no wholesale replacement
        assertSame(transformsBefore, model.getTransforms(),
                "build() must not replace the caller-held transforms list");
        assertSame(edgesBefore, model.getEdges(),
                "build() must not replace the caller-held edges list");
    }

    /**
     * F-4 residual-state leg ({@code <sqlTransform>} splice face): the fragment
     * expansion must not rewrite the caller-held edges (from/to values) nor remove
     * the declared vertex from the caller's transforms list.
     */
    @Test
    public void sqlTransformSpliceDoesNotMutateTheInputModel() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>"
                        + "<sqlTransform id=\"st\"><source>SELECT x FROM t</source></sqlTransform>"
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>",
                null);
        List<?> transformsBefore = model.getTransforms();
        List<?> edgesBefore = model.getEdges();
        StreamEdgeModel edge1 = (StreamEdgeModel) edgesBefore.get(1);

        assertNotNull(StreamModelDslBuilder.of(model).build(), "build must succeed");

        assertSame(transformsBefore, model.getTransforms(),
                "build() must not replace the caller-held transforms list");
        assertTrue(model.getTransforms().stream().anyMatch(t -> "st".equals(t.getId())),
                "the declared <sqlTransform> vertex must stay on the caller-held model");
        assertSame(edgesBefore, model.getEdges(),
                "build() must not replace the caller-held edges list");
        assertSame(edge1, model.getEdges().get(1),
                "the caller-held edge objects must be preserved");
        assertEquals("st", edge1.getFrom(),
                "edge from value must not be rewritten to the fragment terminal");
        assertEquals("out", edge1.getTo(), "edge to value must not be rewritten");
    }

    /**
     * Post-fix calibration leg: double build on the same builder produces equivalent
     * envs (each build re-expands from pristine input). Observed at the output level:
     * both executions emit the same record sequence. The source resets itself on
     * every run so the pipeline (not instance reuse) decides the output.
     */
    @Test
    public void doubleBuildOnSameBuilderProducesEquivalentEnvs() throws Exception {
        StreamModel model = parseModel(null, null,
                "<sql sinkBean=\"fakeSinkFn\"><source>ANY SQL TEXT</source></sql>");
        CollectingSinkFunction<String> sink = new CollectingSinkFunction<>();
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("fakeSrcFn", new RepeatableTestSource())
                .register("fakeSinkFn", sink);
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);

        builder.build().execute("r405-double-build-1");
        List<String> firstRun = sink.getCollected();
        sink.clear();
        builder.build().execute("r405-double-build-2");
        List<String> secondRun = sink.getCollected();

        assertEquals(firstRun, secondRun,
                "two builds from the same pristine model must produce equivalent pipelines");
        assertEquals(io.nop.stream.flow.testing.TestSourceFunction.FIXED_DATA, firstRun,
                "the expanded fake-SQL pipeline must emit the declared sequence");
    }

    /** A source that re-arms itself on every run (env teardown cancels after execute). */
    public static class RepeatableTestSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<String> {
        private volatile boolean running;

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> ctx) {
            running = true;
            for (String element : io.nop.stream.flow.testing.TestSourceFunction.FIXED_DATA) {
                if (!running) {
                    break;
                }
                ctx.collect(element);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * Calibration leg: two builders sharing one parsed model each build cleanly —
     * the first build must not leave consumed state that poisons the second builder.
     */
    @Test
    public void twoBuildersSharingOneModelBothBuildCleanly() {
        StreamModel model = parseModel(null, null,
                "<sql sinkBean=\"fakeSinkFn\"><source>ANY SQL TEXT</source></sql>");
        Object sqlBefore = model.getSql();
        assertNotNull(StreamModelDslBuilder.of(model).build(), "first builder must build");
        assertSame(sqlBefore, model.getSql(),
                "first build must leave the model pristine for the second builder");
        assertNotNull(StreamModelDslBuilder.of(model).build(),
                "second builder on the SAME model must build (no consumed residual state)");
        assertSame(sqlBefore, model.getSql(), "both builds must leave the model pristine");
    }
}
