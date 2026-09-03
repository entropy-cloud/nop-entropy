package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.execution.task.TaskExecutor;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-02 wiring verification (Rule #23): after a region-scoped restart, the
 * rebuilt task must be indistinguishable from an initially-registered task
 * w.r.t. the checkpoint pipeline:
 * <ul>
 *   <li>the barrier-injection list contains the REBUILT invokable and no longer
 *       contains the OLD (superseded) invokable (replace semantics — an append
 *       would leave a phantom old invokable receiving barriers forever);</li>
 *   <li>the rebuilt invokable has a non-null {@code CheckpointBarrierTracker}
 *       (which installs non-null operator snapshot callbacks — the ACK path is
 *       exercised behaviorally by {@link TestSupervisionLoopCheckpointReconnectE2E});</li>
 *   <li>the rebuilt chain's operators are registered as coordinator listeners
 *       (observable via the listener count growth pattern: the rebuilt task
 *       re-registers its chain after the old chain's de-registration);</li>
 *   <li>the configured state backend is re-provisioned on the rebuilt operator
 *       chain (deep-copied chains carry no state backend).</li>
 * </ul>
 */
class TestSupervisionLoopCheckpointRewireWiring {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "sl-rewire-wiring";
    private static final String PIPELINE_ID = "1";

    @Test
    void testRebuiltTaskIsRewiredIntoCheckpointPipeline() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= 20; i++) {
                    ctx.collect("s" + i);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new ParticipatingSinkFunction(sinkResults, hasFailedOnce);

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("rw-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("rw-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-sl-rewire-wiring");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("rw-src", "rw-sink", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setStorageProperty("path", tempDir.toString());

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, JOB_ID, PIPELINE_ID);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, config);

        // Mirror of the executor's initial registration: wire every task into the
        // checkpoint pipeline and build the barrier-injection list. NOTE: the
        // execution plan deep-copies each chain, so the invokables the execPlan
        // runs are NOT the ones constructed above — capture them from the plan.
        CopyOnWriteArrayList<StreamTaskInvokable> allInvokables = new CopyOnWriteArrayList<>();
        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        StreamTaskInvokable[] originalSinkInvokable = new StreamTaskInvokable[1];
        StreamTaskInvokable[] originalSourceInvokable = new StreamTaskInvokable[1];
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                StreamTaskInvokable invokable = subtask.getInvokable();
                allInvokables.add(invokable);
                GraphModelCheckpointExecutor.wireTaskCheckpointPipeline(
                        coordinator, checkpointPlan, config, invokable, subtask.getTaskLocation());
                if ("rw-sink".equals(vertexId) && subtask.getTaskIndex() == 0) {
                    originalSinkInvokable[0] = invokable;
                }
                if ("rw-src".equals(vertexId) && subtask.getTaskIndex() == 0) {
                    originalSourceInvokable[0] = invokable;
                }
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex,
                        Collections.singletonList(invokable.getOperatorChain())));
            }
        }
        assertNotNull(originalSinkInvokable[0].getBarrierTracker(),
                "Initially-registered sink must have a barrier tracker");
        assertEquals(1L, countParticipantRegistrations(coordinator, sinkFn),
                "Initially-registered sink UDF must be registered as participant exactly once");

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                    allInvokables, config,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        SubtaskTask rebuiltSinkTask = tasks.get("rw-sink-0");
        assertNotNull(rebuiltSinkTask);
        StreamTaskInvokable rebuiltInvokable = rebuiltSinkTask.getSubtask().getInvokable();
        StreamTaskInvokable oldSinkInvokable = originalSinkInvokable[0];

        // (a) the rebuilt task must be a NEW invokable instance
        assertNotSame(oldSinkInvokable, rebuiltInvokable,
                "Region restart must rebuild a fresh invokable");

        // (b) wiring: tracker attached (snapshot callbacks non-null via setupSnapshotCallbacks)
        assertNotNull(rebuiltInvokable.getBarrierTracker(),
                "Rebuilt invokable must have a CheckpointBarrierTracker wired (P0-02)");

        // (c) replace semantics: rebuilt invokable in the injection list, old one removed
        assertTrue(allInvokables.contains(rebuiltInvokable),
                "Barrier-injection list must contain the rebuilt invokable");
        assertFalse(allInvokables.contains(oldSinkInvokable),
                "Barrier-injection list must no longer contain the OLD invokable (replace, not append)");
        assertTrue(allInvokables.contains(originalSourceInvokable[0]),
                "Surviving (non-restarted) task's invokable must remain in the list");

        // (c2) participant/CheckpointListener re-registration: the rebuilt chain's
        // UDF must be registered exactly ONCE after the restart — the rewire first
        // de-registers the superseded chain's registration, then re-adds. Without
        // the de-registration, repeated restarts would accumulate duplicate
        // registrations of the shared UDF (stale listener/participant growth).
        assertEquals(1L, countParticipantRegistrations(coordinator, sinkFn),
                "Sink UDF participant registration must not accumulate across restart "
                        + "(unwire old chain + wire new chain)");
        coordinator.shutdown();
        assertEquals(0L, countParticipantRegistrations(coordinator, sinkFn),
                "Coordinator shutdown must clear participant registrations");

        // (d) state backend re-provisioned on the rebuilt chain: deep-copied operators
        // carry no state backend; the rewire must provision one (config null → Memory).
        for (io.nop.stream.core.operators.StreamOperator<?> op :
                rebuiltInvokable.getOperatorChain().getOperators()) {
            if (op instanceof io.nop.stream.core.operators.AbstractStreamOperator) {
                assertNotNull(((io.nop.stream.core.operators.AbstractStreamOperator<?>) op).getStateBackend(),
                        "Rebuilt chain operators must have a provisioned state backend (P0-02 rewire)");
            }
        }

        // (e) all records processed exactly once (restart + replay correctness intact)
        assertEquals(20, sinkResults.size(),
                "All 20 records must be processed after restart: " + sinkResults);
    }

    /**
     * Sink UDF that also implements {@code CheckpointParticipant} so the test can
     * observe coordinator participant-registration accumulation across restarts.
     * The instance is SHARED between the old and rebuilt chains (deepCopy keeps
     * user functions shared), so the registration count must stay exactly 1 —
     * unwire(old chain) + wire(new chain) must not leave duplicates.
     */
    private static final class ParticipatingSinkFunction
            implements SinkFunction<String>, io.nop.stream.core.checkpoint.participant.CheckpointParticipant {
        private static final long serialVersionUID = 1L;

        private final List<String> sinkResults;
        private final AtomicBoolean hasFailedOnce;

        ParticipatingSinkFunction(List<String> sinkResults, AtomicBoolean hasFailedOnce) {
            this.sinkResults = sinkResults;
            this.hasFailedOnce = hasFailedOnce;
        }

        @Override
        public void consume(String value) {
            if (!hasFailedOnce.getAndSet(true)) {
                throw new io.nop.stream.core.exceptions.StreamException("Injected first-consume failure (rewire wiring)");
            }
            sinkResults.add(value);
        }

        @Override
        public io.nop.stream.core.checkpoint.TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
        }

        @Override
        public void restoreFromEpoch(long epochId, io.nop.stream.core.checkpoint.TaskStateSnapshot state) {
        }
    }

    private static long countParticipantRegistrations(CheckpointCoordinator coordinator, Object udf) {
        return coordinator.getParticipants().stream()
                .filter(p -> p == udf)
                .count();
    }
}
