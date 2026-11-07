package io.nop.stream.flow.builder;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U2 (deferred-unlock, plan 2375): {@code <sqlTransform>} splice mechanics, pinned
 * against {@link FakeSqlStreamCompiler}'s canned fragment (entry source + one filter).
 * The real-compiler windowed-fragment coexistence case lives in the runtime module
 * (nop-stream-flow does not depend on nop-stream-sql).
 */
public class TestSqlTransformExpansion {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = startContainer("classpath:_vfs/nop/stream/test/test-sql-expansion.beans.xml",
                "sql-transform-expansion");
    }

    @AfterAll
    public static void destroy() {
        stopContainer(container);
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static IBeanContainerImplementor startContainer(String resource, String name) {
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(resource));
        IBeanContainerImplementor c = builder.build(name);
        c.start();
        BeanContainer.registerInstance(c);
        return c;
    }

    private static void stopContainer(IBeanContainerImplementor c) {
        if (c != null)
            c.stop();
        BeanContainer.registerInstance(null);
    }

    private static StreamModel parseModel(String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-sql-transform\" version=\"1\">"
                + "<transforms>" + transformsXml + "</transforms>"
                + "<edges>" + edgesXml + "</edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/schema/stream/stream.xdef");
        assertEquals(true, resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private static final String SQL_TRANSFORM =
            "<sqlTransform id=\"st\"><source>SELECT x FROM t WHERE x &gt; 1</source></sqlTransform>";

    /**
     * CRA 8.45 R4-05 W-07 reshape: the splice result used to be observed on the
     * caller-held model, which the builder consumed in place. Under the
     * zero-consumption contract the expansion product lives on the builder's
     * internal working copy (the caller model stays pristine), so the expansion is
     * now observed through the built pipeline's execution (output-level oracle).
     * The rename-id coverage is preserved indirectly by
     * {@link #fragmentIdCollisionFailsFast()} (the collision safety net only fires
     * because the fragment filter id is computed as {@code <vertex>_<id>}).
     */
    private static void runPipeline(StreamModel model, CollectingSinkFunction<String> sink) throws Exception {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("fakeSrcFn", new TestSourceFunction())
                .register("fakeSinkFn", sink);
        StreamModelDslBuilder.of(model, resolver).build()
                .execute("sql-transform-expansion-output");
    }

    @Test
    public void expansionSplicesFragmentAndRewiresEdges() throws Exception {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
        CollectingSinkFunction<String> sink = new CollectingSinkFunction<>();
        runPipeline(model, sink);

        // the spliced fragment (renamed filter chain, entry placeholder stripped)
        // assembled into a working pipeline and carried every record to the sink;
        // an unexpanded model could not even build (a raw <sqlTransform> vertex is
        // not a buildable transform type)
        assertEquals(TestSourceFunction.FIXED_DATA, sink.getCollected(),
                "the spliced fragment must carry every record through the rewired pipeline");
        // the caller-held model stays pristine: the declared vertex is still on it
        assertTrue(model.getTransforms().stream().anyMatch(t -> "st".equals(t.getId())),
                "build() must not strip the declared <sqlTransform> from the caller model");
    }

    @Test
    public void expansionSupportsFanOutToMultipleDownstreams() throws Exception {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out1\" bean=\"fakeSinkFn\"/>"
                        + "<sink id=\"out2\" bean=\"fakeSinkFn2\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out1\"/>"
                        + "<edge id=\"e2\" from=\"st\" to=\"out2\"/>");
        CollectingSinkFunction<String> sink1 = new CollectingSinkFunction<>();
        CollectingSinkFunction<String> sink2 = new CollectingSinkFunction<>();
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("fakeSrcFn", new TestSourceFunction())
                .register("fakeSinkFn", sink1)
                .register("fakeSinkFn2", sink2);
        // the canned fragment retargets both downstream edges to the fragment
        // terminal; the fan-out shape is proven by execution (both sinks collect)
        StreamModelDslBuilder.of(model, resolver).build().execute("sql-transform-fanout");

        assertEquals(TestSourceFunction.FIXED_DATA, sink1.getCollected(),
                "the fragment terminal feeds the first downstream edge");
        assertEquals(TestSourceFunction.FIXED_DATA, sink2.getCollected(),
                "the fragment terminal feeds every downstream edge");
    }

    /** Two fragments in a chain: each expands with its own vertex-id prefix. */
    @Test
    public void chainedSqlTransformsExpandWithDistinctPrefixes() throws Exception {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sqlTransform id=\"st2\"><source>SELECT y FROM t2</source></sqlTransform>"
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"st2\"/>"
                        + "<edge id=\"e2\" from=\"st2\" to=\"out\"/>");
        CollectingSinkFunction<String> sink = new CollectingSinkFunction<>();
        runPipeline(model, sink);

        // both fragments expanded (distinct prefixes) and chained into one working
        // pipeline; distinct-prefix collisions would typed-fail at build time
        assertEquals(TestSourceFunction.FIXED_DATA, sink.getCollected(),
                "two chained fragments must splice into a working pipeline");
    }

    @Test
    public void noInputEdgeFailsFast() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"out\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("no incoming edge"),
                () -> ex.getMessage());
    }

    @Test
    public void multiInputEdgesFailFast() {
        StreamModel model = parseModel(
                "<source id=\"src1\" bean=\"fakeSrcFn\"/>"
                        + "<source id=\"src2\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src1\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"src2\" to=\"st\"/>"
                        + "<edge id=\"e2\" from=\"st\" to=\"out\"/>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertTrue(ex.getMessage().contains("incoming edges"), () -> ex.getMessage());
    }

    @Test
    public void hashInputEdgeFailsFast() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\" partition=\"HASH\" keyExpr=\"event.k\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertTrue(ex.getMessage().contains("FORWARD"), () -> ex.getMessage());
    }

    @Test
    public void outputTagInputEdgeFailsFast() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\" outputTag=\"side\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertTrue(ex.getMessage().contains("outputTag"), () -> ex.getMessage());
    }

    /** The top-level <sql> generator keeps its only-content rule: a coexisting
     * <sqlTransform> counts as model content and is rejected by expandSqlModel. */
    @Test
    public void topLevelSqlCoexistenceFailsFast() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-coexist\" version=\"1\">"
                + "<sql sinkBean=\"fakeSinkFn\"><source>SELECT 1</source></sql>"
                + "<transforms>" + SQL_TRANSFORM + "</transforms>"
                + "<edges/>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertTrue(ex.getMessage().contains("only content"), () -> ex.getMessage());
    }

    @Test
    public void fragmentIdCollisionFailsFast() {
        // a pre-existing transform id that equals the renamed fragment filter id —
        // the id-collision safety net must typed-reject instead of silently merging
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<map id=\"st_fakeTFilter\"><source>return event;</source></map>"
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"st_fakeTFilter\"/>"
                        + "<edge id=\"e2\" from=\"st_fakeTFilter\" to=\"out\"/>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("id collision"), () -> ex.getMessage());
    }

    @Test
    public void noProviderFailsFastNamingDependency() {
        IBeanContainerImplementor empty = startContainer(
                "classpath:_vfs/nop/stream/test/test-empty.beans.xml", "sql-t-no-provider");
        try {
            StreamModel model = parseModel(
                    "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                            + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                    "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                            + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
            StreamException ex = assertThrows(StreamException.class,
                    () -> StreamModelDslBuilder.of(model).build());
            assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
            assertTrue(ex.getMessage().contains("nop-stream-sql"), () -> ex.getMessage());
        } finally {
            stopContainer(empty);
            BeanContainer.registerInstance(container);
        }
    }
}
