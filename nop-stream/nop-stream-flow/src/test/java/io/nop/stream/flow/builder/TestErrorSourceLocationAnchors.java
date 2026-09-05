package io.nop.stream.flow.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;

import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 29 (Phase 3): every build-time error family carries the failing model
 * element's source location (file:line). Verified three ways:
 * <ol>
 *   <li><b>XDSL fixtures</b> — the anchor's file and line match the fixture's
 *       actual element position (layer-2 edge error + FL-1 source-config error);</li>
 *   <li><b>Programmatic models with a manually set location</b> — representative
 *       per-family coverage (the anchor equals the model object's location);</li>
 *   <li><b>Location-less synthetic models</b> — no fake anchor is produced
 *       (getErrorLocation() stays null; nothing invents a file:line).</li>
 * </ol>
 */
public class TestErrorSourceLocationAnchors {

    private static final String EDGE_FIXTURE = "/nop/stream/test/test-loc-bad-edge.stream.xml";
    private static final String SOURCE_CONFIG_FIXTURE =
            "/nop/stream/test/test-loc-bad-source-config.stream.xml";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }

    private static StreamException buildFailsFast(StreamModel model) {
        return assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("sinkBean", new CollectingSinkFunction<>())).build());
    }

    // ------------------------------------------------------------------
    // XDSL fixtures: file:line matches the actual element position
    // ------------------------------------------------------------------

    @Test
    public void edgeRefUnknownCarriesEdgeElementLocation() {
        StreamModel model = parseStreamXml(EDGE_FIXTURE);
        StreamException ex = buildFailsFast(model);

        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode());
        SourceLocation loc = ex.getErrorLocation();
        assertNotNull(loc, "edge endpoint error must carry the edge element's location");
        assertTrue(loc.getPath().endsWith("test-loc-bad-edge.stream.xml"),
                "anchor must point at the fixture file: " + loc.getPath());
        assertEquals(13, loc.getLine(), "anchor line must match the <edge> element's line");
    }

    @Test
    public void sourceConfigFailFastCarriesSourceElementLocation() {
        StreamModel model = parseStreamXml(SOURCE_CONFIG_FIXTURE);
        StreamException ex = buildFailsFast(model);

        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("params"), () -> ex.getMessage());
        SourceLocation loc = ex.getErrorLocation();
        assertNotNull(loc, "FL-1 source-config error must carry the source element's location");
        assertTrue(loc.getPath().endsWith("test-loc-bad-source-config.stream.xml"));
        assertEquals(9, loc.getLine(), "anchor line must match the <source> element's line");
    }

    // ------------------------------------------------------------------
    // Programmatic models with manual locations: representative per-family coverage
    // ------------------------------------------------------------------

    private static final SourceLocation LOC =
            SourceLocation.fromLine("/virtual/test-loc.stream.xml", 42);

    private static StreamModel baseModel() {
        StreamModel model = new StreamModel();
        model.setName("loc-test");
        model.setVersion(1L);
        return model;
    }

    private static void assertAnchorAt42(StreamException ex, String family) {
        SourceLocation loc = ex.getErrorLocation();
        assertNotNull(loc, family + " error must carry the model element's location");
        assertEquals(LOC.getPath(), loc.getPath(), family);
        assertEquals(42, loc.getLine(), family);
    }

    @Test
    public void cycleFamilyCarriesModelLocation() {
        StreamModel model = baseModel();
        model.setLocation(LOC);
        StreamSourceModel src = new StreamSourceModel();
        src.setId("cycSrc");
        src.setBean("srcBean");
        io.nop.stream.flow.model.StreamMapModel map = new io.nop.stream.flow.model.StreamMapModel();
        map.setId("cycMap");
        map.setBean("mapBean");
        model.setTransforms(Arrays.asList(src, map));
        model.setEdges(Arrays.asList(edge("e0", "cycSrc", "cycMap"), edge("e1", "cycMap", "cycSrc")));
        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.cyclic-job-graph", ex.getErrorCode());
        SourceLocation loc = ex.getErrorLocation();
        assertNotNull(loc, "cycle error must carry the model's location");
        assertEquals(42, loc.getLine());
    }

    @Test
    public void requiredBodyFamilyCarriesLocation() {
        StreamModel model = baseModel();
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");
        io.nop.stream.flow.model.StreamMapModel map = new io.nop.stream.flow.model.StreamMapModel();
        map.setId("m");
        map.setLocation(LOC);
        StreamSinkModel out = new StreamSinkModel();
        out.setId("out");
        out.setBean("sinkBean");
        model.setTransforms(Arrays.asList(src, map, out));
        model.setEdges(Arrays.asList(edge("e0", "src", "m"), edge("e1", "m", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.required-body", ex.getErrorCode());
        assertAnchorAt42(ex, "required-body");
    }

    @Test
    public void hashEdgeMatrixFamiliesCarryEdgeLocation() {
        // HASH without keyExpr
        StreamModel model = baseModel();
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");
        StreamSinkModel out = new StreamSinkModel();
        out.setId("out");
        out.setBean("sinkBean");
        model.setTransforms(Arrays.asList(src, out));
        StreamEdgeModel e = edge("e0", "src", "out");
        e.setPartition(io.nop.stream.core.execution.plan.PartitionPolicy.HASH);
        e.setLocation(LOC);
        model.setEdges(Arrays.asList(e));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.edge-hash-key-expr-required", ex.getErrorCode());
        assertAnchorAt42(ex, "edge-hash-key-expr-required");
    }

    @Test
    public void upstreamTypeFamilyCarriesLocation() {
        // window without keyed upstream: buildWindow rejects with the window
        // element's location
        StreamModel model = baseModel();
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");
        io.nop.stream.flow.model.StreamWindowModel win = new io.nop.stream.flow.model.StreamWindowModel();
        win.setId("w");
        win.setStrategyRef("missing");
        win.setLocation(LOC);
        StreamSinkModel out = new StreamSinkModel();
        out.setId("out");
        out.setBean("sinkBean");
        model.setTransforms(Arrays.asList(src, win, out));
        model.setEdges(Arrays.asList(edge("e0", "src", "w"), edge("e1", "w", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.upstream-type", ex.getErrorCode());
        assertAnchorAt42(ex, "window upstream-type");
    }

    @Test
    public void beanNotFoundFamilyCarriesLocation() {
        StreamModel model = baseModel();
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("no-such-bean");
        src.setLocation(LOC);
        StreamSinkModel out = new StreamSinkModel();
        out.setId("out");
        out.setBean("sinkBean");
        model.setTransforms(Arrays.asList(src, out));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()
                        .register("sinkBean", new CollectingSinkFunction<>())).build());
        assertEquals("nop.err.stream.bean-not-found", ex.getErrorCode());
        assertAnchorAt42(ex, "bean-not-found");
    }

    // ------------------------------------------------------------------
    // Location-less synthetic models: no fake anchor
    // ------------------------------------------------------------------

    @Test
    public void syntheticModelWithoutLocationProducesNoFakeAnchor() {
        StreamModel model = baseModel();
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcBean");
        // programmatic model: no setLocation anywhere
        io.nop.stream.flow.model.StreamParamModel p = new io.nop.stream.flow.model.StreamParamModel();
        p.setName("topic");
        src.setParams(Arrays.asList(p));
        StreamSinkModel out = new StreamSinkModel();
        out.setId("out");
        out.setBean("sinkBean");
        model.setTransforms(Arrays.asList(src, out));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode());
        assertNull(ex.getErrorLocation(),
                "a location-less synthetic model must NOT produce a fake file:line anchor");
        assertNull(NopException.class.cast(ex).getErrorLocation());
    }

    private static StreamEdgeModel edge(String id, String from, String to) {
        StreamEdgeModel e = new StreamEdgeModel();
        e.setId(id);
        e.setFrom(from);
        e.setTo(to);
        return e;
    }
}
