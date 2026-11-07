package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end multi-task checkpoint tests for the mailbox control plane (Plan:
 * 2026-07-25-1500-1, Phase 2 exit criterion). Validates the full chain:
 * SOURCE trigger-checkpoint mail (drained at collect() on the source task thread)
 * → barrier emitted cross-task via ResultPartition
 * → middle/sink in-band barrier received via InputGate
 * → snapshot + ACK
 * → coordinator completes the checkpoint (no hang).
 *
 * <p>One test per processing guarantee:
 * <ul>
 *   <li>{@link #testE2ECheckpointAligned()} — STRICT_EXACTLY_ONCE (barrier alignment).</li>
 *   <li>{@link #testE2ECheckpointAtLeastOnce()} — AT_LEAST_ONCE (no alignment).</li>
 * </ul>
 *
 * <p>The cross-task priming invariant is exercised implicitly: the sink task's ack count
 * is primed synchronously by the injector thread BEFORE the in-band barrier arrives via
 * the data flow, so the checkpoint completes instead of hanging.
 */
class TestMailboxE2ECheckpoint {

    @TempDir
    Path tempDir;

    /**
     * Linger bound: max heartbeat records (100µs service interval each ⇒ ~10s wall),
     * hang protection only — normal runs exit as soon as the first checkpoint is
     * durable in the run's storage.
     */
    private static final long LINGER_MAX_HEARTBEATS = 100_000L;

    /**
     * Builds a two-vertex topology: source task → sink task (cross-task via
     * ResultPartition/InputGate). The source emits all records, then lingers —
     * draining checkpoint control mails via heartbeat records that the sink
     * filters out — until at least one checkpoint has completed durably in the
     * run's storage (deterministic condition, plan 2510; no fixed emit pacing),
     * so a checkpoint is guaranteed to be triggered mid-run and its barrier
     * flows through the data path before EOS.
     */
    private JobGraph buildSourceToSinkGraph(List<Integer> sinkResults, int recordCount,
                                            LocalFileCheckpointStorage storage, String jobId) {
        SourceFunction<Integer> sourceFn = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= recordCount; i++) {
                    ctx.collect(i);
                }
                // Linger: heartbeat records (0, filtered out by the sink) keep the
                // task thread servicing the control mailbox (checkpoint trigger
                // mails are drained at the collect() emission point) while the
                // storage-visible completion count provides the deterministic exit
                // (probed every 16 heartbeats to amortize the directory scan).
                for (long i = 0; i < LINGER_MAX_HEARTBEATS; i++) {
                    ctx.collect(0);
                    java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
                    if ((i & 0xF) == 0 && storage.getCheckpointCount(jobId) > 0) {
                        return;
                    }
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                if (value > 0) {
                    sinkResults.add(value);
                }
            }
        });

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInv = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInv = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("source-1", "Source", 1,
                Collections.singletonList(sourceChain), sourceInv);
        JobVertex sinkVertex = new JobVertex("sink-2", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInv);

        JobGraph jobGraph = new JobGraph("mailbox-e2e");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "sink-2", ResultPartitionType.PIPELINED));
        return jobGraph;
    }

    /**
     * Aligned (STRICT_EXACTLY_ONCE) end-to-end: single-input sink aligns immediately.
     */
    @Test
    void testE2ECheckpointAligned() throws Exception {
        runE2ECheckpoint(ProcessingGuarantee.STRICT_EXACTLY_ONCE, "mailbox-e2e-aligned");
    }

    /**
     * AT_LEAST_ONCE end-to-end: single-input sink does not block on barrier.
     */
    @Test
    void testE2ECheckpointAtLeastOnce() throws Exception {
        runE2ECheckpoint(ProcessingGuarantee.AT_LEAST_ONCE, "mailbox-e2e-alo");
    }

    private void runE2ECheckpoint(ProcessingGuarantee guarantee, String jobId) throws Exception {
        String pipelineId = "1";
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());

        // Source emits 15 records then lingers (drain-servicing heartbeats) until at
        // least one checkpoint is durable in the run's storage — the checkpoint
        // trigger at the 300ms interval is therefore guaranteed to fire mid-run.
        int recordCount = 15;
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        JobGraph jobGraph = buildSourceToSinkGraph(sinkResults, recordCount, storage, jobId);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(300);
        config.setCheckpointTimeout(10000);
        config.setProcessingGuarantee(guarantee);
        config.setStorageProperty("path", tempDir.toString());

        // Execute: must complete without hanging (cross-task priming invariant holds).
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config);

        // All records must have flowed through the cross-task data path.
        assertEquals(recordCount, sinkResults.size(),
                "All " + recordCount + " records must reach the sink. Got: " + sinkResults);
        for (int i = 1; i <= recordCount; i++) {
            assertTrue(sinkResults.contains(i), "Record " + i + " missing from sink output");
        }

        // At least one completed checkpoint must be durable in storage, proving the
        // SOURCE-trigger-via-mail → cross-task barrier → sink snapshot → ACK chain works.
        // NOTE: the framework's CheckpointIDCounter is 0-based (first checkpoint id = 0),
        // so we only assert a valid non-negative id here. The assertNotNull above is the
        // real "at least one checkpoint completed" gate.
        CompletedCheckpoint completed = storage.getLatestCheckpoint(jobId, pipelineId);
        assertNotNull(completed, "At least one checkpoint must complete (no hang) for " + guarantee);
        assertTrue(completed.getCheckpointId() >= 0, "Completed checkpoint must have a valid non-negative id");

        storage.deleteAllCheckpoints(jobId);
    }
}
