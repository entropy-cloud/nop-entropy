package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
     * Builds a two-vertex topology: source task → sink task (cross-task via
     * ResultPartition/InputGate). The source emits records slowly so at least one
     * checkpoint can be triggered mid-run and its barrier can flow through the data path.
     */
    private JobGraph buildSourceToSinkGraph(List<Integer> sinkResults, int recordCount, long emitDelayMs) {
        SourceFunction<Integer> sourceFn = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= recordCount; i++) {
                    ctx.collect(i);
                    Thread.sleep(emitDelayMs);
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
                sinkResults.add(value);
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

        // Source emits 15 records with 100ms delay (~1.5s); checkpoint interval 300ms
        // gives ~4-5 trigger opportunities, at least one of which must complete.
        int recordCount = 15;
        JobGraph jobGraph = buildSourceToSinkGraph(sinkResults, recordCount, 100L);

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
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CompletedCheckpoint completed = storage.getLatestCheckpoint(jobId, pipelineId);
        assertNotNull(completed, "At least one checkpoint must complete (no hang) for " + guarantee);
        assertTrue(completed.getCheckpointId() >= 0, "Completed checkpoint must have a valid non-negative id");

        storage.deleteAllCheckpoints(jobId);
    }
}
