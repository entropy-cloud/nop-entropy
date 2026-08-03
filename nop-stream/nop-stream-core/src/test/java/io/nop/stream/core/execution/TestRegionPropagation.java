/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.jobgraph.region.RegionDecomposition;
import io.nop.stream.core.jobgraph.region.RegionId;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor plan 2: full-chain propagation + zero-regression tests.
 *
 * <p>Verifies the wiring chain
 * {@code JobGraph.decomposeRegions() → GraphExecutionPlan (carries decomposition)
 * → Subtask.getRegionId() → SubtaskTask.getRegionId()} is connected end-to-end
 * (wiring #23: the region ID is not just present on JobVertex-layer structures
 * but actually arrives at the SubtaskTask, where successor 3's supervision loop
 * will read it).
 *
 * <p>Also verifies zero regression: an existing job (no materialization markers)
 * decomposes into a single region and all existing build behavior is preserved.
 */
public class TestRegionPropagation {

    // ------------------------------------------------------------------
    // Full-chain propagation (wiring #23 + Anti-Hollow)
    // ------------------------------------------------------------------

    @Test
    public void regionIdPropagatesFromJobGraphToSubtask() {
        // A ==mat==> B : A in region-0, B in region-1
        JobGraph graph = twoRegionGraph();

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        // The plan carries the decomposition
        RegionDecomposition decomp = plan.getRegionDecomposition();
        assertNotNull(decomp, "plan built from a JobGraph must carry a region decomposition");
        assertEquals(2, decomp.getRegionCount());

        // Convenience accessor
        assertEquals(decomp.getRegionId("A"), plan.getRegionId("A"));
        assertEquals(decomp.getRegionId("B"), plan.getRegionId("B"));

        // The two vertices are in different regions
        assertNotEquals(plan.getRegionId("A"), plan.getRegionId("B"));

        // Subtask-level: each subtask reports its vertex's region (wiring #23)
        Subtask aSubtask = plan.getSubtasks("A").get(0);
        Subtask bSubtask = plan.getSubtasks("B").get(0);
        assertEquals(plan.getRegionId("A"), aSubtask.getRegionId(),
                "subtask A must carry its vertex's region ID");
        assertEquals(plan.getRegionId("B"), bSubtask.getRegionId(),
                "subtask B must carry its vertex's region ID");
    }

    @Test
    public void regionIdPropagatesFromSubtaskToSubtaskTask() {
        // The successor-3-facing contract: SubtaskTask.getRegionId() works.
        JobGraph graph = twoRegionGraph();

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        // Build SubtaskTasks the same way GraphModelCheckpointExecutor.buildTasks does
        for (String vertexId : plan.getSortedVertexIds()) {
            JobVertex vertex = plan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : plan.getSubtasks(vertexId)) {
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                List<OperatorChain> chainList = Collections.singletonList(chain);
                SubtaskTask task = new SubtaskTask(subtask, vertex, chainList);

                // The task reports the same region ID as the subtask (wiring #23:
                // the region ID actually arrives at the SubtaskTask layer, not just
                // the JobVertex/Subtask layers).
                assertEquals(subtask.getRegionId(), task.getRegionId(),
                        "SubtaskTask must expose the same region ID as its underlying Subtask");
                assertNotNull(task.getRegionId(),
                        "SubtaskTask region ID must be non-null for plans built via build()");
            }
        }
    }

    @Test
    public void parallelSubtasksShareTheSameRegionId() {
        // Parallelism > 1: all parallel subtasks of the same vertex share its region.
        JobGraph graph = new JobGraph("parallel");
        graph.addVertex(new JobVertex("A", "A", 3,
                Collections.singletonList(testChain()), (Invokable<Void>) () -> {}));
        graph.addVertex(new JobVertex("B", "B", 2,
                Collections.singletonList(testChain()), (Invokable<Void>) () -> {}));
        graph.addEdge(edge("A", "B"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        // Single region (no materialization marker)
        assertEquals(1, plan.getRegionDecomposition().getRegionCount());
        RegionId aRegion = plan.getRegionId("A");

        // All 3 A-subtasks share the same region ID
        List<Subtask> aSubtasks = plan.getSubtasks("A");
        assertEquals(3, aSubtasks.size());
        for (Subtask s : aSubtasks) {
            assertEquals(aRegion, s.getRegionId());
        }
        // All 2 B-subtasks share the same region ID (same region as A)
        List<Subtask> bSubtasks = plan.getSubtasks("B");
        assertEquals(2, bSubtasks.size());
        for (Subtask s : bSubtasks) {
            assertEquals(aRegion, s.getRegionId());
        }
    }

    // ------------------------------------------------------------------
    // Zero regression (existing jobs = single region, behavior unchanged)
    // ------------------------------------------------------------------

    @Test
    public void existingJobWithNoMaterializationMarkerIsSingleRegion() {
        // A -> B -> C, no markers → 1 region, every subtask in that region
        JobGraph graph = new JobGraph("legacy");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("B", "C"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        RegionDecomposition decomp = plan.getRegionDecomposition();
        assertNotNull(decomp);
        assertEquals(1, decomp.getRegionCount(),
                "a job with no materialization markers must decompose into exactly one region");

        // All vertices share one region
        RegionId single = decomp.getRegions().get(0).getId();
        assertEquals(single, plan.getRegionId("A"));
        assertEquals(single, plan.getRegionId("B"));
        assertEquals(single, plan.getRegionId("C"));

        // Subtasks also share the region
        for (String vertexId : plan.getSortedVertexIds()) {
            for (Subtask s : plan.getSubtasks(vertexId)) {
                assertEquals(single, s.getRegionId());
            }
        }
    }

    @Test
    public void existingJobBuildBehaviorUnchanged() {
        // The region decomposition is additive: existing build outputs (sorted
        // vertices, subtasks, invokables, buffer pool) are unaffected.
        JobGraph graph = new JobGraph("legacy-behavior");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        // Existing accessors still work
        assertEquals(2, plan.getSortedVertexIds().size());
        assertEquals(2, plan.getExecutionVertices().size());
        assertEquals(1, plan.getSubtasks("A").size());
        assertEquals(1, plan.getSubtasks("B").size());
        assertNotNull(plan.getBufferPool());
        assertNotNull(plan.getInvokables().get("A"));
        assertNotNull(plan.getInvokables().get("B"));
    }

    @Test
    public void createBuiltPlanHasNullDecomposition() {
        // GraphExecutionPlan.create() (runtime builders without a JobGraph) has
        // no decomposition — regionId is null, which is acceptable for that path.
        GraphExecutionPlan plan = GraphExecutionPlan.create(
                Collections.singletonList("A"),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap());

        assertNull(plan.getRegionDecomposition(),
                "create()-built plan has no region decomposition");
        assertNull(plan.getRegionId("A"),
                "create()-built plan returns null for any regionId lookup");
    }

    @Test
    public void emptyJobGraphBuildsGracefullyWithoutDecomposition() {
        // Backward compat: an empty JobGraph (zero vertices) is a valid input to
        // GraphExecutionPlan.build() — the plan has no vertices, so no decomposition
        // is attached (null). This preserves the pre-existing behavior.
        JobGraph empty = new JobGraph("empty");
        GraphExecutionPlan plan = GraphExecutionPlan.build(empty);

        assertNull(plan.getRegionDecomposition(),
                "an empty graph produces no decomposition");
        assertTrue(plan.getSortedVertexIds().isEmpty());
        assertTrue(plan.getSubtasks().isEmpty());
    }

    @Test
    public void multiRegionGraphSubtasksReportCorrectRegions() {
        // A ==mat==> B ==mat==> C : three regions
        JobGraph graph = new JobGraph("three-region");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));

        JobEdge ab = edge("A", "B");
        ab.setMaterializationEnabled(true);
        graph.addEdge(ab);

        JobEdge bc = edge("B", "C");
        bc.setMaterializationEnabled(true);
        graph.addEdge(bc);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        assertEquals(3, plan.getRegionDecomposition().getRegionCount());

        // Each vertex's subtask reports a distinct region
        RegionId rA = plan.getSubtasks("A").get(0).getRegionId();
        RegionId rB = plan.getSubtasks("B").get(0).getRegionId();
        RegionId rC = plan.getSubtasks("C").get(0).getRegionId();
        assertNotEquals(rA, rB);
        assertNotEquals(rB, rC);
        assertNotEquals(rA, rC);
    }

    // --- helpers -------------------------------------------------------

    private static JobGraph twoRegionGraph() {
        JobGraph graph = new JobGraph("two-region");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        JobEdge e = edge("A", "B");
        e.setMaterializationEnabled(true);
        graph.addEdge(e);
        return graph;
    }

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {});
    }

    private static JobEdge edge(String from, String to) {
        return new JobEdge(from, to, ResultPartitionType.PIPELINED);
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
