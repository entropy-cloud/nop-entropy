/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.execution.task.TaskExecutor;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N2/F1/N1 regression (plan 366 Phase 2): region restart over a region with a
 * same-region INTERNAL (non-materialization) edge, and materialization replay
 * sets larger than the bounded queue capacity.
 *
 * <p>Pre-fix behavior:
 * <ul>
 *   <li>N2: an internal-edge channel was rebuilt with a FRESH empty partition
 *       while the rebuilt producer re-uses the OLD writer (old partition) — the
 *       edge was severed and the job hung forever;</li>
 *   <li>F1: a successfully COMPLETED producer task in the failed region was
 *       unconditionally resubmitted — the rebuilt path re-uses its OLD writer
 *       and the first write to the finished partition throws;</li>
 *   <li>N1: materialization replay pushed the replay set through blocking
 *       {@code queue.put} into the capacity-1024 queue before any consumer ran
 *       — a replay set &gt; capacity blocked the supervision thread forever.</li>
 * </ul>
 */
class TestRegionRestartInternalEdgeE2E {

    /**
     * Graph: Source → [internal edge, no mat] → FailingMap → [mat edge] → Sink.
     * Regions: {source, map} + {sink}. The map fails on its first record, but
     * only AFTER the source fully completed (latch) — so the restart must SKIP
     * the COMPLETED source (F1), reconnect the map's internal-edge input to the
     * OLD partition (residual r2/r3 + EOS), and carry over the map's OLD output
     * writer so the healthy sink keeps receiving.
     *
     * <p>Record r1 is consumed by the failed map attempt (thrown on) and has no
     * materialization store on the internal edge — the inherent in-flight loss
     * of a non-materialized edge without a checkpoint. r2/r3 are residual queue
     * content and must be delivered to the sink.
     */
    @Test
    @Timeout(60)
    void internalEdge_restartAfterProducerCompleted_skipsCompletedAndDrainsResidual() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch sourceDone = new CountDownLatch(1);
        AtomicBoolean mapFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("r1");
                ctx.collect("r2");
                ctx.collect("r3");
                sourceDone.countDown();
            }

            @Override
            public void cancel() {
            }
        };

        // Fails on the first invocation, but only after the source COMPLETED —
        // deterministic F1 setup (the completed source must be skipped, not
        // resubmitted into its finished partition).
        MapFunction<String, String> mapFn = v -> {
            sourceDone.await(5, TimeUnit.SECONDS);
            if (!mapFailedOnce.getAndSet(true)) {
                throw new StreamException("Injected first-map failure (internal edge E2E)");
            }
            return v;
        };

        SinkFunction<String> sinkFn = v -> sinkResults.add(v);

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSourceOperator<>(sourceFn))));
        StreamTaskInvokable mapInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamMap<>(mapFn))));
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSinkOperator<>(sinkFn))));

        JobVertex sourceVertex = new JobVertex("ie-src", "Source", 1,
                Collections.singletonList(sourceInvokable.getOperatorChain()), sourceInvokable);
        JobVertex mapVertex = new JobVertex("ie-map", "Map", 1,
                Collections.singletonList(mapInvokable.getOperatorChain()), mapInvokable);
        JobVertex sinkVertex = new JobVertex("ie-sink", "Sink", 1,
                Collections.singletonList(sinkInvokable.getOperatorChain()), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-internal-edge");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        // Internal edge: NOT materialization-enabled → same-region internal edge.
        jobGraph.addEdge(new JobEdge("ie-src", "ie-map", ResultPartitionType.PIPELINED));
        JobEdge matEdge = new JobEdge("ie-map", "ie-sink", ResultPartitionType.PIPELINED);
        matEdge.setMaterializationEnabled(true);
        jobGraph.addEdge(matEdge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "only the materialization edge cuts regions; source→map stays internal");

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.task.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }
        SubtaskTask sourceTaskBefore = tasks.get("ie-src-0");

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // F1: the COMPLETED source was skipped, not resubmitted (same instance).
        assertSame(sourceTaskBefore, tasks.get("ie-src-0"),
                "COMPLETED source must not be resubmitted (its finished partition cannot be "
                        + "written again; the first write would throw ERR_STREAM_INVALID_STATE)");
        assertEquals(SubtaskTask.State.COMPLETED, tasks.get("ie-src-0").getState());

        // Rebuilt map drains the residual (r2, r3) then EOS and completes.
        assertEquals(SubtaskTask.State.COMPLETED, tasks.get("ie-map-0").getState(),
                "rebuilt map must observe residual + EOS on the reused internal-edge partition");

        // The rebuilt map carried over its OLD output writer: the healthy sink
        // received the residual records.
        assertEquals(2, sinkResults.size(),
                "sink must receive exactly the residual records r2/r3 (r1 was consumed by the "
                        + "failed attempt — inherent in-flight loss of a non-materialized edge "
                        + "without checkpoint): " + sinkResults);
        assertTrue(sinkResults.contains("r2") && sinkResults.contains("r3"),
                "residual records delivered: " + sinkResults);
    }

    /**
     * Zero-regression for the mat+finished quadrant: a finished producer's
     * materialization edge restart reuses the FINISHED partition (residual
     * drained — it duplicates the replay set), attaches the full replay, and
     * the consumer observes replay then EOS. Each record lands exactly once in
     * the sink (the failed first attempt threw before recording).
     */
    @Test
    @Timeout(60)
    void materializedEdge_producerFinished_replayThenEosExactlyOnce() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean sinkFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("m1");
                ctx.collect("m2");
                ctx.collect("m3");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = v -> {
            if (!sinkFailedOnce.getAndSet(true)) {
                throw new StreamException("Injected first-consume failure (mat+finished E2E)");
            }
            sinkResults.add(v);
        };

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSourceOperator<>(sourceFn))));
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSinkOperator<>(sinkFn))));

        JobVertex sourceVertex = new JobVertex("mf-src", "Source", 1,
                Collections.singletonList(sourceInvokable.getOperatorChain()), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("mf-sink", "Sink", 1,
                Collections.singletonList(sinkInvokable.getOperatorChain()), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-mat-finished");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        JobEdge matEdge = new JobEdge("mf-src", "mf-sink", ResultPartitionType.PIPELINED);
        matEdge.setMaterializationEnabled(true);
        jobGraph.addEdge(matEdge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount());

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.task.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        assertEquals(3, sinkResults.size(),
                "replay (epoch 0) re-delivers all records exactly once after the drained residual: "
                        + sinkResults);
        assertTrue(sinkResults.contains("m1") && sinkResults.contains("m2") && sinkResults.contains("m3"),
                "all records present: " + sinkResults);
        assertEquals(SubtaskTask.State.COMPLETED, tasks.get("mf-sink-0").getState(),
                "restarted sink must see replay then EOS on the reused finished partition");
    }

    /**
     * Core N1 regression: the materialization replay set (1500 records, no
     * checkpoint → epoch-0 full replay) exceeds the default queue capacity
     * (1024). Pre-fix the supervision thread blocked forever on
     * {@code injectFront}'s {@code queue.put} (no consumer running yet) — this
     * test hung until timeout. Post-fix the replay attaches as pending and the
     * consumer drains it lazily.
     */
    @Test
    @Timeout(60)
    void materializedEdge_replaySetLargerThanQueueCapacity_doesNotBlockRestart() throws Exception {
        int total = 1500;
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean sinkFailedOnce = new AtomicBoolean(false);
        Map<String, Boolean> seen = new ConcurrentHashMap<>();

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= total; i++) {
                    ctx.collect("s" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = v -> {
            if (!sinkFailedOnce.getAndSet(true)) {
                throw new StreamException("Injected first-consume failure (large replay E2E)");
            }
            seen.put(v, Boolean.TRUE);
            sinkResults.add(v);
        };

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSourceOperator<>(sourceFn))));
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSinkOperator<>(sinkFn))));

        JobVertex sourceVertex = new JobVertex("lr-src", "Source", 1,
                Collections.singletonList(sourceInvokable.getOperatorChain()), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("lr-sink", "Sink", 1,
                Collections.singletonList(sinkInvokable.getOperatorChain()), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-large-replay");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        JobEdge matEdge = new JobEdge("lr-src", "lr-sink", ResultPartitionType.PIPELINED);
        matEdge.setMaterializationEnabled(true);
        jobGraph.addEdge(matEdge);

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (io.nop.stream.core.execution.task.Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // At-least-once assertion: every record is delivered, and the consumer
        // reaches EOS on the reused finished partition. Records written in the
        // tiny window between residual-drain and the replay snapshot are
        // delivered once from the queue and once from the replay (pre-existing
        // replay-window semantics of the drain+replay design, registered as
        // plan 366 follow-up) — duplicates are tolerated on this edge, zero
        // deliveries are not.
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (String v : sinkResults) {
            counts.merge(v, 1, Integer::sum);
        }
        List<String> missing = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            if (!counts.containsKey("s" + i)) {
                missing.add("s" + i);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(sinkResults.size() >= total,
                "at-least-once: every record delivered (missing=" + missing + ", total=" + sinkResults.size() + ")");
        org.junit.jupiter.api.Assertions.assertTrue(missing.isEmpty(),
                "every record present at least once; missing=" + missing);
        assertEquals(SubtaskTask.State.COMPLETED, tasks.get("lr-sink-0").getState(),
                "sink must reach EOS after draining the >capacity pending replay");
    }
}
