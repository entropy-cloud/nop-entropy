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
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Stage 51 — Delta customization (x:extends + _delta/default/) execution
 * correctness and delta-unique assertion tests.
 *
 * <p>This is the <b>Anti-Hollow / Silent No-Op</b> gate for Delta: each delta
 * case asserts a property that <b>only exists in the delta file</b> (a
 * transform id introduced by the delta, and/or an output that only the
 * delta-merged pipeline can produce). This proves the delta was actually
 * applied by the XDSL merge machinery, rather than the base being silently
 * loaded and the test passing for the wrong reason.
 *
 * <p>Two delta mechanisms are covered:
 * <ul>
 *   <li><b>x:extends (explicit base path)</b> — {@code test-delta-extends.stream.xml}
 *       extends {@code test-delta-base.stream.xml} by path, adding a
 *       {@code <filter>} transform.</li>
 *   <li><b>_delta/default/ (layered, auto-activated)</b> — the delta at
 *       {@code _delta/default/nop/stream/test/test-delta-layered.stream.xml}
 *       uses {@code x:extends="super"} to add a {@code <map>} transform.</li>
 * </ul>
 *
 * <p>Each execution uses a fresh {@link InMemoryBeanFunctionResolver} with
 * fresh {@link TestSourceFunction} / {@link CollectingSinkFunction} instances,
 * because the execution framework cancels source functions on shutdown
 * (setting their {@code running} flag to false). Sharing a singleton source
 * across multiple {@code execute()} calls would silently emit no data after
 * the first execution.
 */
public class TestStreamModelDeltaExtends {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ----------------------------------------------------------------
    // x:extends (explicit base path) — transform-level delta
    // ----------------------------------------------------------------

    @Test
    public void xExtendsDeltaAddsFilterTransformNotPresentInBase() {
        StreamModel base = parseStreamXml("/nop/stream/test/test-delta-base.stream.xml");
        StreamModel delta = parseStreamXml("/nop/stream/test/test-delta-extends.stream.xml");

        // delta-unique assertion: "deltaFilter" exists ONLY in the delta-merged model
        assertNull(base.getTransform("deltaFilter"),
                "Base must not contain the delta-introduced transform 'deltaFilter'");
        assertNotNull(delta.getTransform("deltaFilter"),
                "Delta-merged model must contain the delta-introduced transform 'deltaFilter'");

        assertEquals(3, base.getTransforms().size(), "Base should have src/upper/out");
        assertEquals(4, delta.getTransforms().size(),
                "Delta-merged should have src/upper/deltaFilter/out");
    }

    @Test
    public void xExtendsDeltaRedirectsEdgeToUpperFilterOut() {
        StreamModel base = parseStreamXml("/nop/stream/test/test-delta-base.stream.xml");
        StreamModel delta = parseStreamXml("/nop/stream/test/test-delta-extends.stream.xml");

        assertNull(base.getEdge("e2"), "Base must not have edge e2");
        assertNotNull(delta.getEdge("e2"), "Delta-merged must have new edge e2");
        assertEquals("deltaFilter", delta.getEdge("e1").getTo(),
                "Delta must redirect e1 to deltaFilter");
        assertEquals("deltaFilter", delta.getEdge("e2").getFrom());
        assertEquals("out", delta.getEdge("e2").getTo());
    }

    @Test
    public void xExtendsDeltaProducesDifferentSinkOutput() throws Exception {
        // Execute base: src -> upper(toUpperCase) -> sink => ["A","B","C"]
        CollectingSinkFunction<String> baseSink = new CollectingSinkFunction<>();
        StreamModel baseModel = parseStreamXml("/nop/stream/test/test-delta-base.stream.xml");
        buildAndExecute(baseModel, baseSink);
        assertEquals(Arrays.asList("A", "B", "C"), baseSink.getCollected(),
                "Base pipeline should uppercase all three elements");

        // Execute delta: src -> upper(toUpperCase) -> deltaFilter(drop B) -> sink => ["A","C"]
        CollectingSinkFunction<String> deltaSink = new CollectingSinkFunction<>();
        StreamModel deltaModel = parseStreamXml("/nop/stream/test/test-delta-extends.stream.xml");
        buildAndExecute(deltaModel, deltaSink);
        assertEquals(Arrays.asList("A", "C"), deltaSink.getCollected(),
                "Delta pipeline should drop 'B' via the delta-added filter");
    }

    // ----------------------------------------------------------------
    // _delta/default/ (layered, auto-activated) — transform-level delta
    // ----------------------------------------------------------------

    @Test
    public void layeredDefaultDeltaAddsMapTransform() {
        StreamModel merged = parseStreamXml("/nop/stream/test/test-delta-layered.stream.xml");

        // delta-unique assertion: "deltaLayerMap" exists ONLY because the
        // _delta/default/ layer added it. If the base were silently loaded
        // (delta not applied), this transform would be absent.
        assertNotNull(merged.getTransform("deltaLayerMap"),
                "Layered delta must add the 'deltaLayerMap' transform");
    }

    @Test
    public void layeredDefaultDeltaProducesUppercaseOutput() throws Exception {
        // Merged pipeline: src -> deltaLayerMap(toUpperCase) -> out
        // Without delta: src -> out (passthrough, lowercase ["a","b","c"])
        // With delta: uppercase ["A","B","C"] — only possible if map is applied
        CollectingSinkFunction<String> sink = new CollectingSinkFunction<>();
        StreamModel merged = parseStreamXml("/nop/stream/test/test-delta-layered.stream.xml");
        buildAndExecute(merged, sink);

        List<String> output = sink.getCollected();
        assertEquals(Arrays.asList("A", "B", "C"), output,
                "Layered delta must apply the map (uppercase). Lowercase output "
                        + "would mean the _delta/default/ layer was NOT applied.");
        assertFalse(output.contains("a"),
                "Lowercase 'a' in output proves the delta map was bypassed");
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    /**
     * Build and execute the given flow model with fresh source/sink function
     * instances registered in an {@link InMemoryBeanFunctionResolver}. The
     * sink instance is supplied by the caller so it can be inspected after
     * execution.
     */
    private static void buildAndExecute(StreamModel model,
                                        CollectingSinkFunction<String> sink) throws Exception {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("testSourceFunction", new TestSourceFunction());
        resolver.register("collectingSinkFunction", sink);

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("delta-test");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
