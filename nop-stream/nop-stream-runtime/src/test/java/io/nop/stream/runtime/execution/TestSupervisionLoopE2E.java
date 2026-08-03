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
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.jobgraph.region.RegionDecomposition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 3: end-to-end test for the supervision loop execution
 * model.
 *
 * <p>Verifies the full control-plane + data-plane call chain:
 * <ol>
 *   <li>Finite input → source emits and dual-writes to materialization point</li>
 *   <li>Single task failure injected into the consumer (sink)</li>
 *   <li>Supervision loop detects the FAILED task mid-execution</li>
 *   <li>Failed task's region is restarted (region-scoped restart)</li>
 *   <li>Restarted consumer replays materialized data (successor 1 replay)</li>
 *   <li>Exactly-once: all source elements appear exactly once in the sink output</li>
 * </ol>
 *
 * <p>This satisfies plan exit criteria:
 * <ul>
 *   <li><b>End-to-end verification (#22)</b>: from source operator through
 *       materialization, supervision loop, region restart, materialization
 *       replay, to sink output — the complete data-plane path.</li>
 *   <li><b>Wiring verification (#23)</b>: the supervision loop actually calls
 *       the region restart path, which actually calls activateMaterializationReplay,
 *       which actually injects data into the consumer's partition.</li>
 *   <li><b>No silent no-op (#24)</b>: the failure is explicitly handled
 *       (restart), not silently skipped.</li>
 *   <li><b>Anti-Hollow</b>: the full call chain is runtime-connected, not just
 *       types existing.</li>
 * </ul>
 */
class TestSupervisionLoopE2E {

    /**
     * E2E: finite input → materialization → inject single task failure →
     * supervision loop detects → region restart → materialization replay →
     * exactly-once.
     *
     * <p>Graph: Source(A) →[materialization edge]→ Sink(B)
     * <ul>
     *   <li>Region 0 = {A} (producer)</li>
     *   <li>Region 1 = {B} (consumer — pure sink, restartable)</li>
     * </ul>
     *
     * <p>B fails on the first attempt (before processing any data). The
     * supervision loop detects B FAILED, restarts region 1, and the new B
     * replays all materialized data from A's materialization point.
     * Exactly-once holds because:
     * <ul>
     *   <li>First attempt: B processed 0 elements (failed immediately).</li>
     *   <li>Restart attempt: B processed all 3 elements from materialization replay.</li>
     *   <li>Total: 3 elements, no loss, no duplication.</li>
     * </ul>
     */
    @Test
    void e2e_materialization_failureDetection_regionRestart_replay_exactlyOnce() throws Exception {
        // Shared state across operator copies (copyForSubtask shares the UDF).
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);
        AtomicInteger sinkInvocationCount = new AtomicInteger(0);

        // Source emits finite data: "a", "b", "c".
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("a");
                ctx.collect("b");
                ctx.collect("c");
            }

            @Override
            public void cancel() {
            }
        };

        // Sink with failure injection: throws on the FIRST invocation only.
        // copyForSubtask shares the same SinkFunction (and thus the same
        // AtomicBoolean), so the restarted sink sees hasFailedOnce=true and
        // succeeds.
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                sinkInvocationCount.incrementAndGet();
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new StreamException("Injected failure for supervision loop E2E test");
                }
                sinkResults.add(value);
            }
        };

        // Build the JobGraph: Source(A) →[materialization edge]→ Sink(B).
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("source-A", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-B", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-supervision-e2e");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("source-A", "sink-B", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        // Verify the graph decomposes into 2 regions (materialization boundary).
        RegionDecomposition decomposition = jobGraph.decomposeRegions();
        assertEquals(2, decomposition.getRegionCount(),
                "Materialization-enabled edge should split the graph into 2 regions");

        // Build the execution plan (attaches materialization points to enabled edges).
        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        // Build SubtaskTasks (same pattern as GraphModelCheckpointExecutor.buildTasks).
        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                List<OperatorChain> chainList = Collections.singletonList(chain);
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, chainList));
            }
        }

        // Run the supervision loop.
        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // Assertions: exactly-once verification.
        // The sink should have processed all 3 elements exactly once.
        // First attempt: 1 invocation (threw before adding to list) → 0 in sinkResults.
        // Restart attempt: 3 invocations (all succeeded) → 3 in sinkResults.
        assertEquals(4, sinkInvocationCount.get(),
                "Sink should be invoked 4 times total: 1 (first attempt, failed) + 3 (restart, succeeded)");
        assertEquals(3, sinkResults.size(),
                "Sink should have collected exactly 3 elements (no loss, no duplication)");
        assertEquals(List.of("a", "b", "c"), new ArrayList<>(sinkResults),
                "Sink output should match source input exactly (exactly-once)");

        // Verify the source task completed successfully.
        SubtaskTask sourceTask = tasks.get("source-A-0");
        assertNotNull(sourceTask);
        assertEquals(SubtaskTask.State.COMPLETED, sourceTask.getState(),
                "Source task should complete successfully");

        // Verify the restarted sink task completed successfully (the original
        // failed task was replaced in the tasks map by the supervision loop).
        SubtaskTask sinkTask = tasks.get("sink-B-0");
        assertNotNull(sinkTask);
        assertEquals(SubtaskTask.State.COMPLETED, sinkTask.getState(),
                "Restarted sink task should complete successfully");
    }

    /**
     * Zero-regression test: single-region graph (no materialization), a task
     * fails → supervision loop surfaces the failure immediately (equivalent
     * to the legacy awaitCompletion + checkTaskFailures path).
     *
     * <p>Uses a 2-vertex graph: Source(A) →[pipelined, no materialization]→
     * FailingSink(B). The graph is a single region. The sink throws on first
     * invocation. The supervision loop detects the FAILED sink and throws
     * ERR_STREAM_SUPERVISION_TASK_FAILED (single-region → no scoped restart).
     */
    @Test
    void zeroRegression_singleRegion_failureSurfacesImmediately() throws Exception {
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("trigger-failure");
            }

            @Override
            public void cancel() {
            }
        };

        // Sink that always throws.
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                throw new StreamException("Sink failure (zero-regression test)");
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("src-fail", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-fail", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-single-region-fail");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        // No materialization → single region.
        jobGraph.addEdge(new JobEdge("src-fail", "sink-fail", ResultPartitionType.PIPELINED));

        assertEquals(1, jobGraph.decomposeRegions().getRegionCount(),
                "No materialization edges → single region");

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex v = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain c = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, v, Collections.singletonList(c)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                    io.nop.stream.core.exceptions.StreamException.class,
                    () -> SupervisionLoop.run(execPlan, tasks, executor, jobGraph,
                            SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION, 50L));
            assertNotNull(ex.getErrorCode());
            assertTrue(ex.getErrorCode().contains("supervision"),
                    "Should surface a supervision error code, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }
    }

    /**
     * Zero-regression test: single-region graph, all tasks succeed →
     * supervision loop completes normally (equivalent to awaitCompletion
     + checkTaskFailures passing).
     */
    @Test
    void zeroRegression_singleRegion_allTasksSucceed() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("hello");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("ok-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("ok-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-ok");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("ok-src", "ok-sink", ResultPartitionType.PIPELINED));

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex v = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain c = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, v, Collections.singletonList(c)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION, 50L);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        assertEquals(List.of("hello"), results);
        for (SubtaskTask task : tasks.values()) {
            assertEquals(SubtaskTask.State.COMPLETED, task.getState(),
                    "All tasks should complete successfully");
        }
    }
}
