/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph.region;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor plan 2: correctness tests for the region decomposition
 * algorithm ({@link RegionDecomposer}).
 *
 * <p>Verifies the connected-component cut rule (materialization-enabled edges
 * are region boundaries; non-materialization edges connect within a region)
 * across the four required topologies:
 * <ul>
 *   <li>single region (no materialization marker) — zero regression;</li>
 *   <li>double region (one materialization marker on a linear chain);</li>
 *   <li>diamond (multi-input convergence with a cut);</li>
 *   <li>multi-source convergence.</li>
 * </ul>
 *
 * <p>Also verifies the No-Silent-No-Op (#24) fail-fast on unclassifiable edges
 * (BLOCKING partition type).
 */
public class TestRegionDecomposer {

    // ------------------------------------------------------------------
    // Single region — zero regression (no materialization marker)
    // ------------------------------------------------------------------

    @Test
    public void linearChainWithNoMaterializationIsSingleRegion() {
        // A -> B -> C, no materialization markers → 1 region {A,B,C}
        JobGraph graph = linearChain("A", "B", "C");
        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(1, decomp.getRegionCount());
        Region region = decomp.getRegions().get(0);
        assertEquals(3, region.getVertexCount());
        assertTrue(region.contains("A"));
        assertTrue(region.contains("B"));
        assertTrue(region.contains("C"));

        // Every vertex maps to the same region
        RegionId rid = region.getId();
        assertEquals(rid, decomp.getRegionId("A"));
        assertEquals(rid, decomp.getRegionId("B"));
        assertEquals(rid, decomp.getRegionId("C"));
    }

    // ------------------------------------------------------------------
    // Double region — one materialization marker on a linear chain
    // ------------------------------------------------------------------

    @Test
    public void linearChainWithOneMaterializationMarkerIsTwoRegions() {
        // A -> B ==mat==> C : A,B in region-0, C in region-1
        JobGraph graph = new JobGraph("two-region-linear");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));

        JobEdge ab = edge("A", "B");
        graph.addEdge(ab);

        JobEdge bc = edge("B", "C");
        bc.setMaterializationEnabled(true);
        graph.addEdge(bc);

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(2, decomp.getRegionCount());

        Region r0 = decomp.getRegions().get(0);
        Region r1 = decomp.getRegions().get(1);
        assertNotEquals(r0.getId(), r1.getId());

        // A and B share a region; C is in a different region
        assertEquals(r0.getId(), decomp.getRegionId("A"));
        assertEquals(r0.getId(), decomp.getRegionId("B"));
        assertEquals(r1.getId(), decomp.getRegionId("C"));

        assertTrue(r0.contains("A"));
        assertTrue(r0.contains("B"));
        assertFalse(r0.contains("C"));
        assertTrue(r1.contains("C"));
    }

    @Test
    public void linearChainWithTwoMarkersIsThreeRegions() {
        // A ==mat==> B ==mat==> C : each vertex its own region
        JobGraph graph = new JobGraph("three-region-linear");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));

        JobEdge ab = edge("A", "B");
        ab.setMaterializationEnabled(true);
        graph.addEdge(ab);

        JobEdge bc = edge("B", "C");
        bc.setMaterializationEnabled(true);
        graph.addEdge(bc);

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(3, decomp.getRegionCount());
        assertNotEquals(decomp.getRegionId("A"), decomp.getRegionId("B"));
        assertNotEquals(decomp.getRegionId("B"), decomp.getRegionId("C"));
        assertNotEquals(decomp.getRegionId("A"), decomp.getRegionId("C"));
    }

    // ------------------------------------------------------------------
    // Diamond — multi-input convergence with a region cut
    // ------------------------------------------------------------------

    @Test
    public void diamondWithCutSeparatesSourceAndSinkRegions() {
        // Diamond: A -> B, A -> C, B -> D, C -> D
        //   Mark B->D as materialization. Then {A,B,C} in one region,
        //   {D} in a separate region (both B and C are cut from D... but
        //   C->D is not a cut, so C and D would be in the same region).
        //
        //   Let's reconsider: if only B->D is a cut, then:
        //     A,B,C connected via A->B, A->C (no cut)
        //     C->D (no cut) → C and D in same region
        //     B->D (cut) → B and D in different regions
        //   This means {A,B,C,D} would all be one region because C-D connects them.
        //   Wait: union(A,B), union(A,C) → {A,B,C}. union(C,D) → {A,B,C,D}.
        //   The cut on B->D doesn't matter because D is already in the same set as B via C.
        //
        //   For a proper diamond split, we need BOTH B->D and C->D to be cuts:
        //     {A,B,C} in region-0, {D} in region-1.
        JobGraph graph = new JobGraph("diamond-cut");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addVertex(vertex("D"));

        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("A", "C"));

        JobEdge bd = edge("B", "D");
        bd.setMaterializationEnabled(true);
        graph.addEdge(bd);

        JobEdge cd = edge("C", "D");
        cd.setMaterializationEnabled(true);
        graph.addEdge(cd);

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(2, decomp.getRegionCount());

        // {A,B,C} share a region; {D} is alone
        RegionId abcRegion = decomp.getRegionId("A");
        assertEquals(abcRegion, decomp.getRegionId("B"));
        assertEquals(abcRegion, decomp.getRegionId("C"));
        assertNotEquals(abcRegion, decomp.getRegionId("D"));
    }

    @Test
    public void diamondWithNoCutIsSingleRegion() {
        // Plain diamond with no materialization → 1 region (all connected)
        JobGraph graph = new JobGraph("diamond-nocut");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addVertex(vertex("D"));

        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("A", "C"));
        graph.addEdge(edge("B", "D"));
        graph.addEdge(edge("C", "D"));

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(1, decomp.getRegionCount());
        Region region = decomp.getRegions().get(0);
        assertEquals(4, region.getVertexCount());
    }

    // ------------------------------------------------------------------
    // Multi-source convergence
    // ------------------------------------------------------------------

    @Test
    public void multiSourceConvergenceWithCutSeparatesSourcesFromSink() {
        // Two independent sources A, B both feed into C via materialization edges,
        // then C -> D (no cut).
        // A ==mat==> C, B ==mat==> C, C -> D
        // Regions: {A}, {B}, {C,D}
        JobGraph graph = new JobGraph("multi-source");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addVertex(vertex("D"));

        JobEdge ac = edge("A", "C");
        ac.setMaterializationEnabled(true);
        graph.addEdge(ac);

        JobEdge bc = edge("B", "C");
        bc.setMaterializationEnabled(true);
        graph.addEdge(bc);

        graph.addEdge(edge("C", "D"));

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(3, decomp.getRegionCount());

        // A alone, B alone, {C,D} together
        assertNotEquals(decomp.getRegionId("A"), decomp.getRegionId("C"));
        assertNotEquals(decomp.getRegionId("B"), decomp.getRegionId("C"));
        assertNotEquals(decomp.getRegionId("A"), decomp.getRegionId("B"));
        assertEquals(decomp.getRegionId("C"), decomp.getRegionId("D"));
    }

    @Test
    public void multiSourceConvergenceNoCutIsSingleRegion() {
        // A -> C, B -> C, C -> D, no cuts → single region
        JobGraph graph = new JobGraph("multi-source-nocut");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addVertex(vertex("D"));

        graph.addEdge(edge("A", "C"));
        graph.addEdge(edge("B", "C"));
        graph.addEdge(edge("C", "D"));

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(1, decomp.getRegionCount());
        assertEquals(4, decomp.getRegions().get(0).getVertexCount());
    }

    // ------------------------------------------------------------------
    // Disconnected vertices (no edges) → each its own region
    // ------------------------------------------------------------------

    @Test
    public void disconnectedVerticesAreSeparateRegions() {
        // Three vertices, no edges → three singleton regions
        JobGraph graph = new JobGraph("disconnected");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(3, decomp.getRegionCount());
        assertNotEquals(decomp.getRegionId("A"), decomp.getRegionId("B"));
        assertNotEquals(decomp.getRegionId("B"), decomp.getRegionId("C"));
    }

    @Test
    public void singleVertexIsSingleRegion() {
        JobGraph graph = new JobGraph("single");
        graph.addVertex(vertex("A"));

        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertEquals(1, decomp.getRegionCount());
        assertEquals(1, decomp.getRegions().get(0).getVertexCount());
        assertNotNull(decomp.getRegionId("A"));
    }

    // ------------------------------------------------------------------
    // No-Silent-No-Op (#24): unclassifiable edge fails fast
    // ------------------------------------------------------------------

    @Test
    public void blockingEdgeFailsFast() {
        // A BLOCKING partition-type edge is never produced by determinePartitionType.
        // Encountering one fails fast rather than silently classifying it.
        JobGraph graph = new JobGraph("blocking-edge");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        JobEdge blocking = new JobEdge("A", "B", ResultPartitionType.BLOCKING);
        graph.addEdge(blocking);

        StreamException ex = assertThrows(StreamException.class, () -> RegionDecomposer.decompose(graph));
        // Verify the fail-fast error code is the expected one (No-Silent-No-Op #24)
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE.getErrorCode(),
                ex.getErrorCode());
        // The exception carries a human-readable description mentioning BLOCKING
        String desc = String.valueOf(ex);
        assertTrue(desc.contains("BLOCKING"),
                "error description should mention BLOCKING; was: " + desc);
    }

    // ------------------------------------------------------------------
    // Null/empty guards
    // ------------------------------------------------------------------

    @Test
    public void nullGraphFailsFast() {
        assertThrows(StreamException.class, () -> RegionDecomposer.decompose(null));
    }

    @Test
    public void emptyGraphFailsFastForDirectCaller() {
        // RegionDecomposer.decompose() fails fast on an empty graph — direct
        // callers must supply a graph with at least one vertex. (GraphExecutionPlan
        // guards this case internally for backward compatibility with empty graphs.)
        JobGraph graph = new JobGraph("empty");
        assertThrows(StreamException.class, () -> RegionDecomposer.decompose(graph));
    }

    // ------------------------------------------------------------------
    // Region ID stability + lookup consistency
    // ------------------------------------------------------------------

    @Test
    public void regionIdsAreStableAcrossDecompositionOfSameGraph() {
        JobGraph g1 = linearChain("A", "B", "C");
        JobGraph g2 = linearChain("A", "B", "C");

        RegionDecomposition d1 = RegionDecomposer.decompose(g1);
        RegionDecomposition d2 = RegionDecomposer.decompose(g2);

        assertEquals(d1.getRegionId("A"), d2.getRegionId("A"));
        assertEquals(d1.getRegionId("B"), d2.getRegionId("B"));
        assertEquals(d1.getRegionId("C"), d2.getRegionId("C"));
    }

    @Test
    public void unknownVertexReturnsNullRegionId() {
        JobGraph graph = linearChain("A", "B");
        RegionDecomposition decomp = RegionDecomposer.decompose(graph);
        assertNull(decomp.getRegionId("Z"));
    }

    @Test
    public void jobGraphConvenienceMethodMatchesDecomposer() {
        JobGraph graph = new JobGraph("convenience");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        JobEdge e = edge("A", "B");
        e.setMaterializationEnabled(true);
        graph.addEdge(e);

        RegionDecomposition viaMethod = graph.decomposeRegions();
        RegionDecomposition viaDecomposer = RegionDecomposer.decompose(graph);

        assertEquals(viaMethod.getRegionCount(), viaDecomposer.getRegionCount());
        assertEquals(viaMethod.getRegionId("A"), viaDecomposer.getRegionId("A"));
        assertEquals(viaMethod.getRegionId("B"), viaDecomposer.getRegionId("B"));
    }

    // ------------------------------------------------------------------
    // Immutability of decomposition result
    // ------------------------------------------------------------------

    @Test
    public void decompositionResultIsImmutable() {
        JobGraph graph = linearChain("A", "B", "C");
        RegionDecomposition decomp = RegionDecomposer.decompose(graph);

        assertThrows(UnsupportedOperationException.class, () -> decomp.getRegions().add(
                new Region(new RegionId("x"), Collections.singleton("Y"))));
        assertThrows(UnsupportedOperationException.class, () -> decomp.getVertexToRegion().put("Z", new RegionId("z")));
    }

    // --- helpers -------------------------------------------------------

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (io.nop.stream.core.jobgraph.Invokable<Void>) () -> {});
    }

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobEdge edge(String from, String to) {
        return new JobEdge(from, to, ResultPartitionType.PIPELINED);
    }

    private static JobGraph linearChain(String... ids) {
        JobGraph graph = new JobGraph("chain-" + String.join("-", ids));
        for (String id : ids) {
            graph.addVertex(vertex(id));
        }
        for (int i = 0; i < ids.length - 1; i++) {
            graph.addEdge(edge(ids[i], ids[i + 1]));
        }
        return graph;
    }

    @io.nop.stream.core.operators.Shareable
    private static class StubOperator implements StreamOperator<Object> {
        @Override
        public void open() throws Exception {
        }

        @Override
        public void finish() throws Exception {
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {
        }

        @Override
        public void setKeyContextElement1(StreamRecord<?> record) throws Exception {
        }

        @Override
        public void setKeyContextElement2(StreamRecord<?> record) throws Exception {
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
        }

        @Override
        public void setCurrentKey(Object key) {
        }

        @Override
        public Object getCurrentKey() {
            return null;
        }
    }
}
