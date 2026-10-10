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
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private static List<String> transformIds(StreamModel model) {
        List<String> ids = new ArrayList<>();
        for (StreamTransformModel t : model.getTransforms())
            ids.add(t.getId());
        return ids;
    }

    private static List<String> edgeStrings(StreamModel model) {
        List<String> out = new ArrayList<>();
        for (StreamEdgeModel e : model.getEdges())
            out.add(e.getFrom() + "->" + e.getTo());
        return out;
    }

    @Test
    public void expansionSplicesFragmentAndRewiresEdges() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out\"/>");
        StreamModelDslBuilder.of(model).build();

        // the sqlTransform vertex and the entry placeholder are gone; the fragment
        // chain (renamed with the vertex prefix) is spliced in their place
        assertEquals(List.of("src", "st_fakeTFilter", "out"), transformIds(model),
                () -> "transforms: " + transformIds(model));
        assertEquals(List.of("src->st_fakeTFilter", "st_fakeTFilter->out"),
                edgeStrings(model), () -> "edges: " + edgeStrings(model));
    }

    @Test
    public void expansionSupportsFanOutToMultipleDownstreams() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sink id=\"out1\" bean=\"fakeSinkFn\"/>"
                        + "<sink id=\"out2\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"out1\"/>"
                        + "<edge id=\"e2\" from=\"st\" to=\"out2\"/>");
        StreamModelDslBuilder.of(model).build();

        assertEquals(List.of("src->st_fakeTFilter", "st_fakeTFilter->out1",
                        "st_fakeTFilter->out2"),
                edgeStrings(model), "the fragment terminal feeds every downstream edge");
    }

    /** Two fragments in a chain: each expands with its own vertex-id prefix. */
    @Test
    public void chainedSqlTransformsExpandWithDistinctPrefixes() {
        StreamModel model = parseModel(
                "<source id=\"src\" bean=\"fakeSrcFn\"/>" + SQL_TRANSFORM
                        + "<sqlTransform id=\"st2\"><source>SELECT y FROM t2</source></sqlTransform>"
                        + "<sink id=\"out\" bean=\"fakeSinkFn\"/>",
                "<edge id=\"e0\" from=\"src\" to=\"st\"/>"
                        + "<edge id=\"e1\" from=\"st\" to=\"st2\"/>"
                        + "<edge id=\"e2\" from=\"st2\" to=\"out\"/>");
        StreamModelDslBuilder.of(model).build();

        assertEquals(List.of("src", "st_fakeTFilter", "st2_fakeTFilter", "out"),
                transformIds(model), () -> "transforms: " + transformIds(model));
        assertEquals(List.of("src->st_fakeTFilter", "st_fakeTFilter->st2_fakeTFilter",
                        "st2_fakeTFilter->out"),
                edgeStrings(model), () -> "edges: " + edgeStrings(model));
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
