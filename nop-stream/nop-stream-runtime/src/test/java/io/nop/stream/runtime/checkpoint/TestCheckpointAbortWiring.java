package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test for Phase 1 of plan 172: local path Coordinator abort → task cancel wiring.
 *
 * <p>Validates that when a checkpoint times out due to a stuck channel (source that
 * never finishes), the abort handler:
 * <ol>
 *   <li>Fires after checkpointTimeout</li>
 *   <li>Cancels all tasks (interrupts blocked threads)</li>
 *   <li>Causes executeWithCheckpoint to throw (not hang forever)</li>
 * </ol>
 */
class TestCheckpointAbortWiring {

    @TempDir
    Path tempDir;

    /**
     * Multi-input topology: two sources → one sink. One source is stuck (blocks forever).
     * Checkpoint times out → abort → tasks cancelled → job fails.
     */
    @Test
    void testStuckChannelAbortTerminatesJob() {
        long checkpointTimeout = 2000L;
        long checkpointInterval = 500L;

        // Source A: slow but normal, emits data over time
        SourceFunction<Integer> normalSourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 1000; i++) {
                    ctx.collect(i);
                    Thread.sleep(200);
                }
            }

            @Override
            public void cancel() {
            }
        };

        // Source B: stuck, blocks forever until interrupted
        SourceFunction<Integer> stuckSourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                while (running) {
                    Thread.sleep(1000);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };

        // Sink: consumes data
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
            }
        });

        StreamSourceOperator<Integer> sourceAOp = new StreamSourceOperator<>(normalSourceFn);
        StreamSourceOperator<Integer> sourceBOp = new StreamSourceOperator<>(stuckSourceFn);

        OperatorChain sourceAChain = new OperatorChain(Collections.singletonList(sourceAOp));
        OperatorChain sourceBChain = new OperatorChain(Collections.singletonList(sourceBOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceAInv = new StreamTaskInvokable(sourceAChain);
        StreamTaskInvokable sourceBInv = new StreamTaskInvokable(sourceBChain);
        StreamTaskInvokable sinkInv = new StreamTaskInvokable(sinkChain);

        JobVertex sourceAVertex = new JobVertex("source-a", "SourceA", 1,
                Collections.singletonList(sourceAChain), sourceAInv);
        JobVertex sourceBVertex = new JobVertex("source-b", "SourceB", 1,
                Collections.singletonList(sourceBChain), sourceBInv);
        JobVertex sinkVertex = new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInv);

        JobGraph jobGraph = new JobGraph("test-stuck-channel-abort");
        jobGraph.addVertex(sourceAVertex);
        jobGraph.addVertex(sourceBVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-a", "sink", ResultPartitionType.PIPELINED));
        jobGraph.addEdge(new JobEdge("source-b", "sink", ResultPartitionType.PIPELINED));

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("test-stuck-abort");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(checkpointInterval);
        config.setCheckpointTimeout(checkpointTimeout);
        config.setStorageProperty("path", tempDir.toString());

        long startTime = System.currentTimeMillis();

        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "test-stuck-channel-abort", config));

        long elapsed = System.currentTimeMillis() - startTime;

        // Wiring verification: the exception proves abort handler was called
        // (checkAbortMarker only throws if abortMarked==true, which is only set by the abort handler)
        assertNotNull(thrown);

        // Should complete within checkpointTimeout + grace (not hang forever)
        long grace = 15_000L;
        assertTrue(elapsed < checkpointInterval + checkpointTimeout + grace,
                "Job should terminate within interval + timeout + " + grace + "ms grace, took " + elapsed + "ms");

        // Cleanup
        cleanupStorage("test-stuck-abort");
    }

    /**
     * Single stuck source (simpler topology) — validates abort wiring without multi-input.
     * Source blocks forever → checkpoint timeout → abort → cancel → job fails.
     */
    @Test
    void testSingleStuckSourceAbortTerminatesJob() {
        long checkpointTimeout = 1500L;
        long checkpointInterval = 300L;

        SourceFunction<Integer> stuckSourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                while (running) {
                    Thread.sleep(5000);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };

        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
            }
        });

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(stuckSourceFn);

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "StuckChain", 1,
                Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph("test-single-stuck-abort");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("test-single-stuck-abort");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(checkpointInterval);
        config.setCheckpointTimeout(checkpointTimeout);
        config.setStorageProperty("path", tempDir.toString());

        long startTime = System.currentTimeMillis();

        assertThrows(Exception.class, () ->
                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "test-single-stuck-abort", config));

        long elapsed = System.currentTimeMillis() - startTime;
        long grace = 15_000L;
        assertTrue(elapsed < checkpointInterval + checkpointTimeout + grace,
                "Job should terminate within interval + timeout + grace, took " + elapsed + "ms");

        cleanupStorage("test-single-stuck-abort");
    }

    private void cleanupStorage(String jobId) {
        try {
            io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage storage =
                    new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(tempDir.toString());
            storage.deleteAllCheckpoints(jobId);
        } catch (Exception e) {
            // ignore cleanup errors
        }
    }
}
