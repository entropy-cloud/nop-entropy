package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-02 end-to-end proof: a region-scoped restart (consumer region) must
 * re-wire the rebuilt task into the checkpoint pipeline so that post-restart
 * checkpoints full-ACK and complete instead of timing out and aborting the job.
 *
 * <p>Graph: Source(A) →[materialization edge]→ FailingSink(B).
 * Region 0 = {A} (producer), Region 1 = {B} (consumer, fails once on first
 * consume, restarted by the supervision loop).
 *
 * <p>Timing contract (deterministic):
 * <ul>
 *   <li>checkpointInterval=300ms, checkpointTimeout=1500ms — the first trigger
 *       fires AFTER the restart completes (~100-150ms), so no checkpoint is
 *       in-flight across the restart boundary and the RED failure mode (post-
 *       restart checkpoints never ACK → timeout abort) is the only visible
 *       outcome when the re-wire is missing.</li>
 *   <li>slow source (~2.4s) — the job is still running at trigger+timeout so the
 *       abort path is reachable in the RED state (a bounded fast source would
 *       end the job before the timeout fires and vacuously pass).</li>
 * </ul>
 *
 * <p>Green assertion = observable full-ACK evidence: the job completes without
 * exception AND the shared storage holds multiple completed checkpoints
 * (manifests) — at least one checkpoint must have completed after the restart.
 */
class TestSupervisionLoopCheckpointReconnectE2E {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "sl-checkpoint-reconnect";

    @Test
    void testRegionRestartRewiresCheckpointPipeline_checkpointsCompleteAfterRestart() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);

        // Slow source: ~2.4s total runtime, outliving trigger(300ms)+timeout(1500ms)
        // so the RED abort path is reachable before the job ends.
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= 60; i++) {
                    ctx.collect("s" + i);
                    try {
                        Thread.sleep(40);
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

        // Sink fails once on the FIRST consume (before recording), succeeds after.
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new io.nop.stream.core.exceptions.StreamException("Injected first-consume failure (checkpoint reconnect E2E)");
                }
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        io.nop.stream.core.execution.task.StreamTaskInvokable sourceInvokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(sourceChain);
        io.nop.stream.core.execution.task.StreamTaskInvokable sinkInvokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("slck-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("slck-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-sl-checkpoint-reconnect");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("slck-src", "slck-sink", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "Materialization edge should split into 2 regions");

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(300L);
        config.setCheckpointTimeout(1500L);
        config.setMinPause(100L);
        config.setStorageProperty("path", tempDir.toString());

        // Public entry point (Rule #22): the supervision-loop/checkpoint wiring is
        // internal (private static), so the E2E must go through
        // GraphModelCheckpointExecutor.executeWithCheckpoint.
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "test-sl-checkpoint-reconnect", config);

        // The job must NOT have been aborted (no ERR_STREAM_CHECKPOINT_ABORTED),
        // and every record must have been processed exactly once (the failing
        // first consume is replayed by the materialization replay).
        assertEquals(60, sinkResults.size(),
                "All 60 records must be processed exactly once after restart. "
                        + "Results: " + sinkResults);

        // Observable full-ACK evidence: at least 2 completed checkpoints stored
        // (≥1 of them after the restart — post-restart checkpoints full-ACKed).
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        List<EpochManifest> manifests = storage.loadRetainedEpochManifests(
                JOB_ID, "1", Integer.MAX_VALUE);
        assertTrue(manifests.size() >= 2,
                "Multiple checkpoints must complete across the restart (full-ACK wiring evidence). "
                        + "Manifest epochs: " + manifests.stream()
                        .map(EpochManifest::getEpochId)
                        .collect(java.util.stream.Collectors.toList()));

        storage.deleteAllCheckpoints(JOB_ID);
    }
}
