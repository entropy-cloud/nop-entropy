package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C3 — LOCAL E2E (embedded form, per review F-7): a bounded
 * job where one branch (source1→sink1) completes EARLY while the other
 * (source2→sink2) still runs. Pre-fix, the completed sink1 stayed in the
 * checkpoint participant set and every periodic checkpoint after its
 * completion waited for its ACK until the full checkpoint timeout and aborted
 * (the bounded-job timeout-abort loop from
 * {@code ai-dev/analysis/nop-stream/09a-...md} C3).
 *
 * <p>Post-fix: the supervision loop contracts terminal tasks out of the
 * participant set as they complete (state inherited from the latest completed
 * checkpoint), so periodic checkpoints triggered after partial completion
 * complete normally. The completed checkpoint still carries the FULL
 * task-state set (the contracted sink's state is inherited), and the CANCEL
 * final checkpoint at job end short-circuits on the empty ACK set (the C2×C3
 * interaction) instead of hanging or failing.
 */
class TestE2EBoundedJobCheckpointAfterPartialCompletion {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "bounded-c3-e2e";
    private static final String PIPELINE_ID = "1";

    /**
     * Harness linger signal (plan 2510): highest durable epoch id observed by the
     * slow branch's CheckpointListener callback + abort count + heartbeat bound.
     * The executed source is the graph-template instance (StreamSourceOperator
     * shares the UDF for plain functions), so statics are visible to the test.
     */
    private static final java.util.concurrent.atomic.AtomicLong PARK_MAX_COMPLETED_ID =
            new java.util.concurrent.atomic.AtomicLong(-1);
    private static final java.util.concurrent.atomic.AtomicLong PARK_ABORTS =
            new java.util.concurrent.atomic.AtomicLong();
    /** Linger bound: max heartbeats (100µs service interval each ⇒ ~5s wall). */
    private static final long PARK_MAX_HEARTBEATS = 50_000L;

    private LocalFileCheckpointStorage storage;

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) {
            storage.deleteAllCheckpoints(JOB_ID);
        }
    }

    /**
     * Listener variant of the slow-branch source: emits the values, then lingers —
     * draining checkpoint control mails via heartbeat records (the no-op sink
     * ignores them) — until at least 6 periodic epochs have completed durably
     * (ids 0..5, i.e. latest id ≥ 5: the exact quantity the post-execute
     * assertion pins), the deterministic form of the old fixed emit pacing.
     * Observes epoch completions via the coordinator's UDF listener notification.
     */
    private static final class FiveEpochLingerSource
            implements SourceFunction<Integer>, io.nop.stream.core.common.state.CheckpointListener {
        private static final long serialVersionUID = 1L;

        private final int count;

        FiveEpochLingerSource(int count) {
            this.count = count;
        }

        @Override
        public void run(SourceContext<Integer> ctx) {
            PARK_MAX_COMPLETED_ID.set(-1);
            PARK_ABORTS.set(0);
            for (int i = 1; i <= count; i++) {
                ctx.collect(i);
            }
            for (long i = 0; i < PARK_MAX_HEARTBEATS
                    && PARK_MAX_COMPLETED_ID.get() < 5 && PARK_ABORTS.get() < 3; i++) {
                ctx.collect(0);
                java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
            }
        }

        @Override
        public void cancel() {
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            PARK_MAX_COMPLETED_ID.accumulateAndGet(checkpointId, Math::max);
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
            PARK_ABORTS.incrementAndGet();
        }
    }

    private JobVertex sourceVertex(String id, int count, boolean lingerForFiveEpochs) {
        SourceFunction<Integer> fn = lingerForFiveEpochs
                ? new FiveEpochLingerSource(count)
                : new SourceFunction<Integer>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void run(SourceContext<Integer> ctx) {
                        for (int i = 1; i <= count; i++) {
                            ctx.collect(i);
                        }
                    }

                    @Override
                    public void cancel() {
                    }
                };
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(fn);
        OperatorChain chain = new OperatorChain(Collections.singletonList(sourceOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        return new JobVertex(id, id, 1, Collections.singletonList(chain), invokable);
    }

    private JobVertex sinkVertex(String id) {
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
            }
        });
        OperatorChain chain = new OperatorChain(Collections.singletonList(sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        return new JobVertex(id, id, 1, Collections.singletonList(chain), invokable);
    }

    @Test
    void testPeriodicCheckpointsCompleteAfterPartialCompletion() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());

        JobGraph jobGraph = new JobGraph(JOB_ID);
        // Fast branch: source1 emits 5 records and finishes immediately — sink1
        // completes early, creating the partial-completion state.
        jobGraph.addVertex(sourceVertex("source-1", 5, false));
        jobGraph.addVertex(sinkVertex("sink-1"));
        jobGraph.addEdge(new JobEdge("source-1", "sink-1", ResultPartitionType.PIPELINED));
        // Slow branch: source2 emits 10 records then lingers (drain-servicing
        // heartbeats) until the 6th periodic epoch is durable — keeps the job alive
        // while periodic checkpoints are triggered and complete.
        jobGraph.addVertex(sourceVertex("source-2", 10, true));
        jobGraph.addVertex(sinkVertex("sink-2"));
        jobGraph.addEdge(new JobEdge("source-2", "sink-2", ResultPartitionType.PIPELINED));

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setStorageProperty("path", tempDir.toString());
        // Periodic checkpoints every 100ms; a pre-fix epoch aborts after 500ms.
        config.setCheckpointInterval(100L);
        config.setCheckpointTimeout(500L);
        config.setMinPause(0L);

        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, JOB_ID, config);

        // Post-fix: multiple periodic checkpoints completed during the ~1.3s
        // window while only the slow branch was still running (pre-fix every
        // epoch after sink-1 completed aborted at the 500ms timeout, so no
        // checkpoint beyond the first window could ever complete).
        CompletedCheckpoint latest = storage.getLatestCheckpoint(JOB_ID, PIPELINE_ID);
        assertNotNull(latest, "periodic checkpoints must have completed after partial completion");
        assertTrue(latest.getCheckpointId() >= 5,
                "several epochs must have completed (pre-fix the abort loop prevented this): "
                        + latest.getCheckpointId());

        // The completed checkpoint carries the FULL task-state set: the
        // contracted sink-1's state is inherited from its last participating
        // epoch (restore semantics unchanged).
        for (String vertexId : new String[]{"source-1", "sink-1", "source-2", "sink-2"}) {
            TaskLocation loc = findLocation(latest, vertexId);
            assertNotNull(loc, "location family for " + vertexId + " must be present");
            assertNotNull(latest.getTaskState(loc),
                    "the completed checkpoint must carry the state of " + vertexId
                            + " (inherited after its task completed)");
        }
    }

    private TaskLocation findLocation(CompletedCheckpoint checkpoint, String vertexId) {
        for (TaskLocation loc : checkpoint.getTaskStates().keySet()) {
            if (loc.getVertexId().equals(vertexId)) {
                return loc;
            }
        }
        return null;
    }
}
