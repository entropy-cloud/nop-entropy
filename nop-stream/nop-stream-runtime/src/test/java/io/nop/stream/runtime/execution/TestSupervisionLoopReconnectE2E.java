/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 4 Phase 3: consumer reconnect-to-live-queue + end-to-end
 * exactly-once verification.
 *
 * <p>Verifies that when a consumer-only region is restarted while the producer
 * is still running (infinite source), the restarted consumer:
 * <ol>
 *   <li>Replays materialized data (post-checkpoint or full replay);</li>
 *   <li>Reconnects to the LIVE producer partition;</li>
 *   <li>Continues consuming new data without blocking;</li>
 *   <li>Processes the complete dataset with no loss.</li>
 * </ol>
 */
class TestSupervisionLoopReconnectE2E {

    /**
     * Consumer-only region restart with a SLOW source (simulates infinite source):
     * the source emits records with delays so it's still running when the consumer
     * restarts. After restart, the consumer replays materialized data AND reads
     * live data from the still-running producer (reconnect-to-live-queue).
     *
     * <p>Graph: Source(A) →[materialization edge]→ FailingSink(B)
     * <ul>
     *   <li>Region 0 = {A} (producer — slow source, still running at restart time)</li>
     *   <li>Region 1 = {B} (consumer — fails on first consume, restarted)</li>
     * </ul>
     *
     * <p>The source emits "s1".."s6" with ~80ms between each. The consumer fails
     * on the first consume. The supervision loop restarts the consumer (~200ms
     * later). By then the source has emitted a few records but hasn't finished.
     * The restarted consumer drains stale queue data, replays materialized data
     * (epoch 0 — no checkpoint, full replay), then reads live data as the source
     * continues emitting, and finally sees EOS when the source completes.
     */
    @Test
    void reconnectToLiveQueue_consumerRestartsWhileProducerRunning_readsReplayThenLive() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);
        CountDownLatch sourceFinished = new CountDownLatch(1);

        // Slow source: emits 6 records with delays. This keeps the source running
        // long enough for the consumer to fail + restart while the partition is
        // still open (not finished → reconnect-to-live-queue path is exercised).
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= 6; i++) {
                    ctx.collect("s" + i);
                    try {
                        Thread.sleep(80); // slow emission
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

        // Sink fails on the FIRST consume (throws before recording), succeeds
        // afterward. This means the first-attempt record is NOT in sinkResults
        // (exactly-once: no duplicate in the final output).
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new StreamException("Injected first-consume failure (reconnect E2E)");
                }
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("reconnect-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("reconnect-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-reconnect");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("reconnect-src", "reconnect-sink", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "Materialization edge should split into 2 regions");

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            // No checkpoint → full replay (epoch 0). The reconnect mechanism
            // drains stale queue data + injectFronts replay data + the consumer
            // continues reading live data from the still-running source.
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // The restarted consumer should have processed ALL 6 records:
        // - Records emitted before the restart are replayed (from materialization
        //   store, epoch 0 = full replay since no checkpoint).
        // - Records emitted after the restart are read live (reconnect-to-live-queue).
        // - The first-attempt record was NOT recorded (threw before recording).
        assertEquals(6, sinkResults.size(),
                "Restarted consumer should process all 6 records (replay + live). "
                        + "Results: " + sinkResults);

        // Verify all records are present (order: replay first, then live).
        // The source emits s1..s6 in order. The materialization store has them
        // in order. Replay preserves order. Live data continues in order.
        for (int i = 1; i <= 6; i++) {
            assertTrue(sinkResults.contains("s" + i),
                    "Record s" + i + " should be in sink results: " + sinkResults);
        }

        // Both tasks should complete successfully.
        SubtaskTask sourceTask = tasks.get("reconnect-src-0");
        assertNotNull(sourceTask);
        assertEquals(SubtaskTask.State.COMPLETED, sourceTask.getState());

        SubtaskTask sinkTask = tasks.get("reconnect-sink-0");
        assertNotNull(sinkTask);
        assertEquals(SubtaskTask.State.COMPLETED, sinkTask.getState(),
                "Restarted sink should complete successfully after reconnect-to-live-queue");
    }

    /**
     * Zero-regression: no materialization marker → single region. Existing
     * behavior (supervision loop surfaces failure immediately) is unchanged.
     */
    @Test
    void zeroRegression_noMaterialization_singleRegionBehavior() throws Exception {
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("x");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                throw new StreamException("Sink failure (reconnect zero-regression)");
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("zr-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("zr-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-reconnect-zr");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        // NO materialization → single region → no scoped restart possible.
        jobGraph.addEdge(new JobEdge("zr-src", "zr-sink", ResultPartitionType.PIPELINED));

        assertEquals(1, jobGraph.decomposeRegions().getRegionCount());

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class, () ->
                    SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                            SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION, 50L));
            assertNotNull(ex.getErrorCode());
            assertTrue(ex.getErrorCode().contains("supervision"),
                    "Single-region failure should surface a supervision error, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }
    }
}
