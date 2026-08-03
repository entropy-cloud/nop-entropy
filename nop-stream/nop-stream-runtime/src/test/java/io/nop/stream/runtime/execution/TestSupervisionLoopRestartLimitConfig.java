/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 5 (per-region restart limit configurability): verifies that
 * the per-region restart budget configured on {@link CheckpointConfig} flows
 * through to {@link SupervisionLoop} and observably governs when
 * {@code ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED} is thrown.
 *
 * <p>This test simulates the production wiring path
 * ({@code CheckpointConfig.getMaxRestartsPerRegion()} → {@code submitAndRun} →
 * {@code SupervisionLoop.run} full-parameter signature) by reading the budget
 * from a {@link CheckpointConfig} instance and passing it to
 * {@link SupervisionLoop#run}, exactly as
 * {@code GraphModelCheckpointExecutor.submitAndRun} does. Combined with the
 * compile-time enforcement that {@code submitAndRun}'s signature now takes the
 * budget as a parameter, this covers the full config → SupervisionLoop wiring.
 *
 * <p><b>Anti-Hollow</b>: the custom value must observably change SupervisionLoop's
 * exhaustion threshold (not merely compile). An always-failing sink is restarted
 * exactly {@code maxRestarts} times before exhaustion, so
 * {@code consume-invocations == maxRestarts + 1}.
 */
class TestSupervisionLoopRestartLimitConfig {

    /**
     * Builds a 2-region graph Source(A) →[materialization edge]→ FailingSink(B),
     * where the sink throws on every consume. Returns the shared invocation
     * counter (wrapped in the sink UDF via {@code copyForSubtask} sharing).
     *
     * <p>The materialization-enabled edge splits the graph into:
     * <ul>
     *   <li>Region 0 = {source-A} (producer)</li>
     *   <li>Region 1 = {sink-B} (consumer — restartable)</li>
     * </ul>
     */
    private GraphSetup buildAlwaysFailingGraph() {
        AtomicInteger sinkConsumeCount = new AtomicInteger(0);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("trigger");
            }

            @Override
            public void cancel() {
            }
        };

        // Sink throws on EVERY consume — never recovers. Each (re)started sink
        // task instance reads the materialized element and fails on consume.
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                sinkConsumeCount.incrementAndGet();
                throw new StreamException("Injected always-failure for restart-limit test");
            }
        };

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

        JobGraph jobGraph = new JobGraph("test-restart-limit");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("source-A", "sink-B", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "Materialization-enabled edge should split the graph into 2 regions");

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

        return new GraphSetup(jobGraph, execPlan, tasks, sinkConsumeCount);
    }

    /**
     * Custom maxRestartsPerRegion=1 (read from {@link CheckpointConfig}, simulating
     * the production wiring): the always-failing sink is restarted exactly once,
     * then on the 2nd failure the budget is exhausted and
     * {@code ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED} is thrown.
     *
     * <p>Observable proof: consume-invocations == 2 (1 original + 1 restart).
     */
    @Test
    void customLimit_configValue_reachesSupervisionLoop() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setMaxRestartsPerRegion(1);
        assertEquals(1, config.getMaxRestartsPerRegion());

        GraphSetup setup = buildAlwaysFailingGraph();
        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class, () ->
                    SupervisionLoop.run(setup.execPlan, setup.tasks, executor, setup.jobGraph,
                            null, null,
                            config.getMaxRestartsPerRegion(),
                            SupervisionLoop.DEFAULT_POLL_INTERVAL_MS));
            assertNotNull(ex.getErrorCode());
            assertTrue(ex.getErrorCode().contains("supervision-restart-exhausted"),
                    "Should surface restart-exhausted error code, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            setup.execPlan.closeBufferPool();
        }

        // maxRestarts=1 → exactly 2 consume invocations (1 original + 1 restart).
        assertEquals(2, setup.sinkConsumeCount.get(),
                "maxRestartsPerRegion=1 should allow exactly 1 restart (2 total consume invocations), got: "
                        + setup.sinkConsumeCount.get());
    }

    /**
     * Custom maxRestartsPerRegion=0 (disable scoped restart entirely): the first
     * region failure surfaces immediately with NO restart attempted.
     *
     * <p>Observable proof: consume-invocations == 1 (original only, no restart).
     */
    @Test
    void zeroLimit_disablesScopedRestart() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setMaxRestartsPerRegion(0);

        GraphSetup setup = buildAlwaysFailingGraph();
        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class, () ->
                    SupervisionLoop.run(setup.execPlan, setup.tasks, executor, setup.jobGraph,
                            null, null,
                            config.getMaxRestartsPerRegion(),
                            SupervisionLoop.DEFAULT_POLL_INTERVAL_MS));
            assertTrue(ex.getErrorCode().contains("supervision-restart-exhausted"),
                    "Should surface restart-exhausted error code, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            setup.execPlan.closeBufferPool();
        }

        // maxRestarts=0 → no restart, exactly 1 consume invocation.
        assertEquals(1, setup.sinkConsumeCount.get(),
                "maxRestartsPerRegion=0 should allow no restart (1 consume invocation), got: "
                        + setup.sinkConsumeCount.get());
    }

    /**
     * Zero-regression: default maxRestartsPerRegion=3 (the production default,
     * read from {@link CheckpointConfig}) preserves the successor-3 behavior —
     * the always-failing sink is restarted 3 times before exhaustion.
     *
     * <p>Observable proof: consume-invocations == 4 (1 original + 3 restarts).
     */
    @Test
    void defaultLimit_behaviorUnchanged() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(CheckpointConfig.DEFAULT_MAX_RESTARTS_PER_REGION, config.getMaxRestartsPerRegion());

        GraphSetup setup = buildAlwaysFailingGraph();
        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class, () ->
                    SupervisionLoop.run(setup.execPlan, setup.tasks, executor, setup.jobGraph,
                            null, null,
                            config.getMaxRestartsPerRegion(),
                            SupervisionLoop.DEFAULT_POLL_INTERVAL_MS));
            assertTrue(ex.getErrorCode().contains("supervision-restart-exhausted"),
                    "Should surface restart-exhausted error code, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            setup.execPlan.closeBufferPool();
        }

        // default=3 → exactly 4 consume invocations (1 original + 3 restarts).
        assertEquals(4, setup.sinkConsumeCount.get(),
                "Default maxRestartsPerRegion=3 should allow 3 restarts (4 total consume invocations), got: "
                        + setup.sinkConsumeCount.get());
    }

    /** Bundles graph setup artifacts for a single test invocation. */
    private static final class GraphSetup {
        final JobGraph jobGraph;
        final GraphExecutionPlan execPlan;
        final Map<String, SubtaskTask> tasks;
        final AtomicInteger sinkConsumeCount;

        GraphSetup(JobGraph jobGraph, GraphExecutionPlan execPlan,
                   Map<String, SubtaskTask> tasks, AtomicInteger sinkConsumeCount) {
            this.jobGraph = jobGraph;
            this.execPlan = execPlan;
            this.tasks = tasks;
            this.sinkConsumeCount = sinkConsumeCount;
        }
    }
}
