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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 4 Phase 2: producer-region restart (drainable producer
 * regions no longer hit {@code ERR_STREAM_REGION_RESTART_UNSUPPORTED}).
 *
 * <p>Verifies that a region containing producer vertices (with outgoing
 * cross-region materialization edges) can be cancelled + rebuilt + restarted.
 * The rebuilt producer reuses the output writer and continues writing to the
 * same partition, so the surviving consumer reads the post-restart data
 * seamlessly.
 */
class TestSupervisionLoopProducerRegionRestart {

    /**
     * Producer-region restart: the source task (in region-0, which has an
     * outgoing cross-region materialization edge) fails on the first attempt.
     * The supervision loop restarts region-0 (no ERR_STREAM_REGION_RESTART_UNSUPPORTED).
     * The restarted source emits data; the surviving sink (in region-1) reads it.
     *
     * <p>Graph: Source(A) →[materialization edge]→ Sink(B)
     * <ul>
     *   <li>Region 0 = {A} (producer — has outgoing cross-region edge)</li>
     *   <li>Region 1 = {B} (consumer — pure sink)</li>
     * </ul>
     */
    @Test
    void producerRegionRestart_doesNotThrowUnsupported_restartedProducerFeedsConsumer() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean sourceHasFailed = new AtomicBoolean(false);
        AtomicInteger sourceInvocations = new AtomicInteger(0);

        // Source fails on the FIRST run (before emitting), succeeds on restart.
        // copyForSubtask shares the SourceFunction (same AtomicBoolean), so the
        // restarted source sees sourceHasFailed=true and emits data.
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                sourceInvocations.incrementAndGet();
                if (!sourceHasFailed.getAndSet(true)) {
                    throw new StreamException("Injected source first-attempt failure");
                }
                ctx.collect("restart-1");
                ctx.collect("restart-2");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("prod-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("prod-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-producer-region-restart");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("prod-src", "prod-sink", ResultPartitionType.PIPELINED);
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
            // No checkpoint context — the source starts fresh on restart (correct
            // for this test: first attempt fails before emitting, restart emits).
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // The source should have been invoked twice: first (failed) + restart (succeeded).
        assertEquals(2, sourceInvocations.get(),
                "Source should run twice: first-attempt (failed) + restart (succeeded)");

        // The sink should have received the restart-emitted data (from the
        // restarted producer writing to the reused output partition).
        assertEquals(List.of("restart-1", "restart-2"), new ArrayList<>(sinkResults),
                "Surviving sink should read data from the restarted producer");

        // Both tasks should complete successfully.
        SubtaskTask sourceTask = tasks.get("prod-src-0");
        assertNotNull(sourceTask);
        assertEquals(SubtaskTask.State.COMPLETED, sourceTask.getState(),
                "Restarted source task should complete successfully");

        SubtaskTask sinkTask = tasks.get("prod-sink-0");
        assertNotNull(sinkTask);
        assertEquals(SubtaskTask.State.COMPLETED, sinkTask.getState(),
                "Sink task should complete successfully");
    }
}
