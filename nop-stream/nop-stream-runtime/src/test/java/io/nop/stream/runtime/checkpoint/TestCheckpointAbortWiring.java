package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *
 * <p>时间确定化（plan 2510）：源函数的模拟阻塞全部改为 latch 阻塞（由 cancel /
 * 中断解除），不再使用 sleep 定节奏；「不会永久挂起」由独立线程 + future.get 的
 * 挂死保护上限证明（正常终止远小于上限，触及即判失败），不再断言墙钟耗时阈值。
 * checkpoint 超时本身仍是生产 timeoutScheduler 的真实调度语义（该 seam 未开放注入，
 * 虚拟时钟不可达——按 plan 2510 gated 面口径具名保留）。
 */
class TestCheckpointAbortWiring {

    @TempDir
    Path tempDir;

    /** 挂死保护上限：正常在 checkpointTimeout+interval 内终止，触及上限即判失败。 */
    private static final long EXECUTE_HANG_PROTECTION_MS = 60_000L;

    /**
     * Stuck-source body: parks on a latch until cancelled (or the abort interrupts it).
     * The latch await doubles as hang protection — if the abort wiring regresses and
     * never cancels, the source returns after the bound and the job completes
     * normally, which fails the assertThrows below deterministically.
     */
    private static SourceFunction<Integer> stuckSource() {
        final CountDownLatch stopped = new CountDownLatch(1);
        return new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                stopped.await(EXECUTE_HANG_PROTECTION_MS, TimeUnit.MILLISECONDS);
            }

            @Override
            public void cancel() {
                stopped.countDown();
            }
        };
    }

    /** Runs the checkpointed job on a helper thread with hang-protection bound. */
    private static void executeJobWithHangProtection(JobGraph jobGraph, String jobId,
                                                     CheckpointConfig config,
                                                     Class<? extends Exception> expectedTopLevel) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "abort-wiring-execute-" + jobId);
            t.setDaemon(true);
            return t;
        });
        try {
            Future<?> future = exec.submit(
                    () -> GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config));
            // The abort wiring must terminate the job (throw) instead of hanging forever:
            // the get() bound is pure hang protection — normal termination is seconds.
            Exception jobFailure;
            try {
                future.get(EXECUTE_HANG_PROTECTION_MS, TimeUnit.MILLISECONDS);
                jobFailure = null;
            } catch (ExecutionException e) {
                jobFailure = e;
            } catch (TimeoutException e) {
                throw new AssertionError(
                        "executeWithCheckpoint must terminate via the checkpoint abort (not hang)", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while awaiting the job failure", e);
            }
            assertNotNull(jobFailure,
                    "executeWithCheckpoint must terminate via the checkpoint abort (not hang)");
            assertTrue(expectedTopLevel.isInstance(jobFailure.getCause()),
                    "job failure must surface as " + expectedTopLevel.getSimpleName()
                            + ", got: " + jobFailure.getCause());
        } finally {
            exec.shutdownNow();
        }
    }

    /**
     * Multi-input topology: two sources → one sink. One source is stuck (blocks until
     * cancelled). Checkpoint times out → abort → tasks cancelled → job fails.
     */
    @Test
    void testStuckChannelAbortTerminatesJob() {
        long checkpointTimeout = 2000L;
        long checkpointInterval = 500L;

        // Source A: normal, emits a burst of data and finishes — its records reach the
        // sink early; the abort scenario is driven entirely by the stuck source B.
        SourceFunction<Integer> normalSourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 1000; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        // Source B: stuck, blocks until the abort's cancel/interrupt releases it.
        SourceFunction<Integer> stuckSourceFn = stuckSource();

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

        // Wiring verification: the exception proves abort handler was called
        // (checkAbortMarker only throws if abortMarked==true, which is only set by the
        // abort handler). Termination (not hanging forever) is proven by the bounded
        // future.get — hang protection only, no elapsed-threshold assertion.
        executeJobWithHangProtection(jobGraph, "test-stuck-channel-abort", config, StreamException.class);

        // Cleanup
        cleanupStorage("test-stuck-abort");
    }

    /**
     * Single stuck source (simpler topology) — validates abort wiring without multi-input.
     * Source blocks until cancelled → checkpoint timeout → abort → cancel → job fails.
     */
    @Test
    void testSingleStuckSourceAbortTerminatesJob() {
        long checkpointTimeout = 1500L;
        long checkpointInterval = 300L;

        SourceFunction<Integer> stuckSourceFn = stuckSource();

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

        executeJobWithHangProtection(jobGraph, "test-single-stuck-abort", config, Exception.class);

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
