/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 constraint 2 regression: parallel edges between the same vertex pair are legal
 * topology when the target is a union vertex — the historical per-vertex-pair JobEdge
 * dedup silently collapsed them (self-union received half its declared input). The
 * fix keeps one JobEdge per declared StreamEdge into a union vertex, with one
 * independent partition matrix per JobEdge (identity-keyed).
 */
public class TestJobGraphParallelEdges {

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {
                });
    }

    @io.nop.stream.core.operators.Shareable
    private static class StubOperator implements StreamOperator<Object> {
        @Override public void open() throws Exception {}
        @Override public void finish() throws Exception {}
        @Override public void close() throws Exception {}
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}
        @Override public void setKeyContextElement1(StreamRecord<?> record) throws Exception {}
        @Override public void setKeyContextElement2(StreamRecord<?> record) throws Exception {}
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception {}
        @Override public void setCurrentKey(Object key) {}
        @Override public Object getCurrentKey() { return null; }
    }

    @Test
    public void testParallelEdgesIntoUnionVertexSurviveAsSeparateJobEdges() {
        JobGraph graph = new JobGraph("self-union-parallel-edges");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("U"));
        // Two declared StreamEdges from A into union vertex U (self-union)
        graph.addEdge(new JobEdge("A", "U", ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("A", "U", ResultPartitionType.PIPELINED));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        // Source has a fan-out of two writers — one per declared edge
        StreamTaskInvokable source = plan.getInvokables().get("A");
        assertNotNull(source, "source invokable must exist");
        List<RecordWriter<Object>> writers = new ArrayList<>(source.getFanOutWriters());
        assertEquals(2, writers.size(),
                "self-union source must fan out one writer per declared edge (constraint 2 fixed)");

        // Union vertex reads two channels — one per declared edge
        StreamTaskInvokable union = plan.getInvokables().get("U");
        assertNotNull(union, "union invokable must exist");
        InputGate gate = union.getInputGate();
        assertNotNull(gate, "union vertex must have an input gate");
        List<InputChannel> channels = gate.getChannels();
        assertEquals(2, channels.size(), "union vertex must see one channel per declared edge");

        // The two writers must write DISTINCT partitions (independent matrices —
        // identity keying, not equals-collapsed onto one matrix)
        assertNotSame(writers.get(0).getPartitions()[0], writers.get(1).getPartitions()[0],
                "parallel edges must not share partition instances (independent matrices)");
        assertNotSame(channels.get(0), channels.get(1),
                "union channels must be distinct instances");
    }

    @Test
    public void testSingleEdgeTopologyKeepsSingleWriter() {
        // Zero-regression guard: a plain A->B vertex pair still gets exactly one
        // JobEdge / writer / channel.
        JobGraph graph = new JobGraph("single-edge");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(new JobEdge("A", "B", ResultPartitionType.PIPELINED));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        StreamTaskInvokable source = plan.getInvokables().get("A");
        List<RecordWriter<Object>> writers = source.getFanOutWriters();
        assertTrue(writers == null || writers.isEmpty(),
                "single outgoing edge stays on the legacy single-writer path");
        assertNotNull(source.getOutputWriter(), "single-edge writer must exist");

        StreamTaskInvokable target = plan.getInvokables().get("B");
        assertEquals(1, target.getInputGate().getChannels().size(),
                "single-edge target must have exactly one channel");
    }
}
