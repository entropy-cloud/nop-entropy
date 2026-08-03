/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 1: wiring verification for the materialization point
 * mechanism (option B). Verifies the runtime data-flow chain
 * {@code ResultPartition.write → materialization store → InputChannel.replay}
 * is connected end-to-end, plus the build-time marker propagation
 * {@code JobEdge.materializationEnabled → ResultPartition.materializationPoint}.
 *
 * <p>Satisfies plan exit criteria:
 * <ul>
 *   <li>wiring #23 — ResultPartition.write on a materialization-enabled edge
 *       actually invokes the materialization store (assertable);</li>
 *   <li>wiring #23 — InputChannel.replay actually hits the materialization store
 *       (assertable);</li>
 *   <li>Anti-Hollow check — ResultPartition→store→InputChannel replay call chain
 *       is runtime-connected (not just types existing);</li>
 *   <li>default partition type unchanged — marker is opt-in, default-off edges
 *       show zero-regression by-reference behavior.</li>
 * </ul>
 */
public class TestMaterializationWiring {

    // ------------------------------------------------------------------
    // ResultPartition dual-write bypass (wiring #23)
    // ------------------------------------------------------------------

    @Test
    public void resultPartitionDualWritesToMaterializationStoreWithEpoch() throws Exception {
        ResultPartition partition = new ResultPartition();
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-dual");
        partition.setMaterializationPoint(point);

        assertTrue(partition.isMaterializationEnabled());
        assertSame(point, partition.getMaterializationPoint());

        // epoch 0 writes
        partition.setCurrentMaterializationEpoch(0L);
        partition.write(new StreamRecord<>("a"));
        partition.write(new StreamRecord<>("b"));

        // advance producer epoch, write more
        partition.setCurrentMaterializationEpoch(5L);
        partition.write(new StreamRecord<>("c"));

        // The materialization store received all three elements, tagged with the
        // producer's current epoch at each write (wiring #23: bypass write
        // actually happened and is assertable).
        List<MaterializedElement> all = point.replayAll();
        assertEquals(3, all.size());
        assertEquals("a", all.get(0).getElement().<String>asRecord().getValue());
        assertEquals(0L, all.get(0).getEpoch());
        assertEquals("b", all.get(1).getElement().<String>asRecord().getValue());
        assertEquals(0L, all.get(1).getEpoch());
        assertEquals("c", all.get(2).getElement().<String>asRecord().getValue());
        assertEquals(5L, all.get(2).getEpoch());
        assertEquals(5L, point.getLastEpoch());

        // The main queue also received all three (dual-write, not bypass-only).
        assertEquals(3, partition.size());
    }

    @Test
    public void resultPartitionWithoutMaterializationPointFollowsLegacyPath() throws Exception {
        // default: no materialization point attached → by-reference only (zero regression)
        ResultPartition partition = new ResultPartition();
        assertFalse(partition.isMaterializationEnabled());
        assertNull(partition.getMaterializationPoint());

        partition.write(new StreamRecord<>("legacy"));
        assertEquals(1, partition.size());
        StreamElement read = partition.read();
        assertEquals("legacy", read.<String>asRecord().getValue());
    }

    @Test
    public void detachingMaterializationPointDisablesDualWrite() throws Exception {
        ResultPartition partition = new ResultPartition();
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-detach");
        partition.setMaterializationPoint(point);
        partition.write(new StreamRecord<>("with-bypass"));
        assertEquals(1, point.size());

        // detach → back to legacy by-reference path
        partition.setMaterializationPoint(null);
        assertFalse(partition.isMaterializationEnabled());
        partition.write(new StreamRecord<>("no-bypass"));
        // store was not touched by the second write
        assertEquals(1, point.size());
        assertEquals(2, partition.size());
    }

    // ------------------------------------------------------------------
    // InputChannel replay path (wiring #23)
    // ------------------------------------------------------------------

    @Test
    public void inputChannelReplayReadsFromMaterializationStore() throws Exception {
        ResultPartition partition = new ResultPartition();
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-replay");
        partition.setMaterializationPoint(point);

        partition.setCurrentMaterializationEpoch(0L);
        partition.write(new StreamRecord<>("a"));
        partition.setCurrentMaterializationEpoch(1L);
        partition.write(new StreamRecord<>("b"));
        partition.setCurrentMaterializationEpoch(2L);
        partition.write(new StreamRecord<>("c"));

        InputChannel channel = new InputChannel(partition);
        assertTrue(channel.hasMaterializationPoint());
        assertSame(point, channel.getMaterializationPoint());

        // replayMaterialized(fromEpoch) returns elements with epoch >= fromEpoch
        // directly from the materialization store (wiring #23: replay hits the store).
        List<MaterializedElement> from1 = channel.replayMaterialized(1L);
        assertEquals(2, from1.size());
        assertEquals("b", from1.get(0).getElement().<String>asRecord().getValue());
        assertEquals(1L, from1.get(0).getEpoch());
        assertEquals("c", from1.get(1).getElement().<String>asRecord().getValue());

        // replayMaterialized does not disturb the in-flight main queue
        assertEquals(3, partition.size());
    }

    @Test
    public void inputChannelActivateReplayInjectsMaterializedDataAheadOfQueue() throws Exception {
        // Scenario: producer wrote a,b,c (dual-write); consumer "restarts" on a
        // fresh empty partition that shares the SAME materialization point (the
        // point is independently addressable and outlives the partition). On
        // recovery, activateMaterializationReplay(fromEpoch) injects the
        // materialized data at the front of the consumer's buffer so subsequent
        // read() returns the replayed elements first.
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-shared");
        ResultPartition producerPartition = new ResultPartition();
        producerPartition.setMaterializationPoint(point);
        producerPartition.setCurrentMaterializationEpoch(10L);
        producerPartition.write(new StreamRecord<>("a"));
        producerPartition.setCurrentMaterializationEpoch(11L);
        producerPartition.write(new StreamRecord<>("b"));

        // consumer-side: fresh partition wired to the same materialization point
        ResultPartition consumerPartition = new ResultPartition();
        consumerPartition.setMaterializationPoint(point);
        // simulate some live data already in the consumer queue (e.g. post-replay upstream)
        consumerPartition.write(new StreamRecord<>("live-after"));

        InputChannel channel = new InputChannel(consumerPartition);
        int injected = channel.activateMaterializationReplay(10L);
        assertEquals(2, injected);

        // Replayed elements come first (injectFront preserves order), then live data.
        assertEquals("a", channel.read().<String>asRecord().getValue());
        assertEquals("b", channel.read().<String>asRecord().getValue());
        assertEquals("live-after", channel.read().<String>asRecord().getValue());
    }

    @Test
    public void replayOnChannelWithoutMaterializationPointFailsFast() {
        // No-Silent-No-Op: replay on a non-materialized edge throws rather than
        // silently returning empty.
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        assertFalse(channel.hasMaterializationPoint());

        StreamException ex1 = assertThrows(StreamException.class, () -> channel.replayMaterialized(0L));
        assertEquals(ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED.getErrorCode(), ex1.getErrorCode());

        StreamException ex2 = assertThrows(StreamException.class, () -> channel.activateMaterializationReplay(0L));
        assertEquals(ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED.getErrorCode(), ex2.getErrorCode());
    }

    @Test
    public void activateReplayWithNoMatchingEpochIsZeroInjectNoOp() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p-empty-match");
        ResultPartition partition = new ResultPartition();
        partition.setMaterializationPoint(point);
        partition.setCurrentMaterializationEpoch(0L);
        partition.write(new StreamRecord<>("a"));

        InputChannel channel = new InputChannel(partition);
        // fromEpoch beyond anything stored → 0 elements injected (legitimate empty,
        // not a silent skip of an unimplemented branch).
        int injected = channel.activateMaterializationReplay(99L);
        assertEquals(0, injected);
    }

    // ------------------------------------------------------------------
    // Build-time wiring: JobEdge.materializationEnabled → ResultPartition
    // ------------------------------------------------------------------

    @Test
    public void graphExecutionPlanAttachesMaterializationPointToEnabledEdgePartitions() {
        JobGraph graph = new JobGraph("mat-wiring");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));

        JobEdge edge = edge("A", "B");
        edge.setMaterializationEnabled(true);
        graph.addEdge(edge);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        // The source vertex A's RecordWriter holds the partitions of edge A->B.
        // With materializationEnabled=true, every partition in the matrix must
        // carry an attached materialization point.
        Subtask aSubtask = plan.getSubtasks("A").get(0);
        RecordWriter<Object> writer = aSubtask.getInvokable().getOutputWriter();
        assertNotNull(writer, "source vertex A should have a RecordWriter");
        ResultPartition[] partitions = writer.getPartitions();
        assertEquals(1, partitions.length);
        assertTrue(partitions[0].isMaterializationEnabled(),
                "partition on a materialization-enabled edge must have a point attached");
        assertNotNull(partitions[0].getMaterializationPoint());
        assertNotNull(partitions[0].getMaterializationPoint().getPointId());
    }

    @Test
    public void graphExecutionPlanDoesNotAttachMaterializationPointByDefault() {
        // Zero-regression: default edge (marker off) produces partitions with no
        // materialization point.
        JobGraph graph = new JobGraph("mat-default");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B")); // marker left at default false

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph,
                null, false, 0L, new BufferPool(64));

        Subtask aSubtask = plan.getSubtasks("A").get(0);
        RecordWriter<Object> writer = aSubtask.getInvokable().getOutputWriter();
        ResultPartition[] partitions = writer.getPartitions();
        assertEquals(1, partitions.length);
        assertFalse(partitions[0].isMaterializationEnabled(),
                "default edge must NOT attach a materialization point");
        assertNull(partitions[0].getMaterializationPoint());
    }

    @Test
    public void jobEdgeMaterializationMarkerIsOffByDefaultAndSettable() {
        JobEdge edge = new JobEdge("A", "B", ResultPartitionType.PIPELINED);
        assertFalse(edge.isMaterializationEnabled());

        edge.setMaterializationEnabled(true);
        assertTrue(edge.isMaterializationEnabled());

        edge.setMaterializationEnabled(false);
        assertFalse(edge.isMaterializationEnabled());

        // marker does not change partition type (option B: additive metadata)
        assertEquals(ResultPartitionType.PIPELINED, edge.getPartitionType());
    }

    // --- helpers -------------------------------------------------------

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
