/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.metrics;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.metrics.MicrometerStreamTaskMetrics;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (Phase 2 接线验证): runs a REAL local pipeline execution (source →
 * sink cross-task, periodic checkpoints) and asserts the metric registration
 * points are actually UPDATED on the execution path — not merely that the
 * meter types exist:
 * <ul>
 *   <li>engine layer: checkpoints.completed / checkpoint.duration incremented
 *       by the real completion path</li>
 *   <li>operator layer: records.in on the sink task (input-gate dispatch)</li>
 *   <li>io layer: records.consumed on the source task (SourceContext.collect)
 *       and records.emitted (cross-task writer emission)</li>
 * </ul>
 */
class TestObservabilityWiringE2E {

    @TempDir
    Path tempDir;

    @Test
    void testMetricsUpdatedOnRealExecutionPath() throws Exception {
        String jobId = "observability-wiring-e2e-" + System.nanoTime();
        String pipelineId = "1";
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());
        int recordCount = 12;

        // W-14 (CRA 8.41 R4-01, ruling E.2): the task meter surface is RELEASED on
        // task close (registry.remove + holder entry drop) — the "sequence stays,
        // reads NaN" contract is superseded. The wiring assertions therefore capture
        // the meters AS THEY ARE REGISTERED via onMeterAdded (a Counter keeps its
        // count after removal), assert the real-path updates on the captured
        // references, and additionally verify the release contract on the registry
        // after teardown.
        java.util.concurrent.ConcurrentMap<String, java.util.List<io.micrometer.core.instrument.Meter>> captured =
                new java.util.concurrent.ConcurrentHashMap<>();
        java.util.Set<String> taskMeterNames = java.util.Set.of(
                MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_CONSUMED,
                MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_EMITTED,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_RATIO,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_NANOS,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_ELAPSED_NANOS,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK,
                MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK_CHANNEL_LAG);
        StreamMetricsRegistries.registry().config().onMeterAdded(m -> {
            if (jobId.equals(m.getId().getTag("jobId")) && taskMeterNames.contains(m.getId().getName())) {
                captured.computeIfAbsent(m.getId().getName(), k -> java.util.Collections.synchronizedList(
                        new java.util.ArrayList<>())).add(m);
            }
        });

        // 时间确定化（plan 2510，merge 保留）：不再用 60ms/条 pacing 把运行拉过真实周期
        // checkpoint——运行终止路径的 final checkpoint（CANCEL 模式 triggerFinalCheckpoint）
        // 确定性完成并持久化，满足 checkpoints.completed >= 1 断言，无真实时间依赖。
        JobGraph jobGraph = buildSourceToSinkGraph(jobId, sinkResults, recordCount);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(200);
        config.setCheckpointTimeout(10000);
        config.setProcessingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE);
        config.setStorageProperty("path", tempDir.toString());

        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config);

        // sanity: full data path executed
        assertEquals(recordCount, sinkResults.size(), "all records must reach the sink");

        // durable checkpoint must exist (completion path really ran)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        assertNotNull(storage.getLatestCheckpoint(jobId, pipelineId),
                "at least one checkpoint must complete");
        storage.deleteAllCheckpoints(jobId);

        // ---- engine layer: real completion path incremented the meters ----
        Double completed = counter(EngineMetrics.METRIC_CHECKPOINTS_COMPLETED, jobId);
        assertNotNull(completed, "engine checkpoints.completed meter must be registered");
        assertTrue(completed >= 1.0,
                "engine checkpoints.completed must be incremented by the real path, got " + completed);

        // ---- operator/io layers: LOCAL-path injection produced real updates ----
        // (metrics are per-task — vertex-tagged; summed from the captured meters:
        // the registration points fired on the real path, and the counter values
        // survived the W-14 teardown removal)
        assertEquals((double) recordCount,
                capturedCount(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_CONSUMED, captured),
                "io records.consumed must be registered by the LOCAL path and count every source record");
        assertEquals((double) recordCount,
                capturedCount(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_EMITTED, captured),
                "io records.emitted must be registered by the LOCAL path and count every cross-task emission");
        assertEquals((double) recordCount,
                capturedCount(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN, captured),
                "operator records.in must be registered by the LOCAL path and count every sink dispatch");

        // ---- WI16 (plan 2337): the observability surfaces are registered by
        // the real execution path (Anti-Hollow — not island registrations) ----
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO, captured);
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO, captured);
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_RATIO, captured);
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_NANOS, captured);
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_ELAPSED_NANOS, captured);
        // the sink task has an input gate → the watermark gauges must be bound too
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK, captured);
        assertCaptured(MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK_CHANNEL_LAG, captured);

        // ---- W-14 release contract on the real path: after LOCAL teardown the
        // whole task meter surface (every captured name) is REMOVED from the
        // registry — the scrape sequence disappears (ruling E.2; owner doc synced).
        for (String name : captured.keySet()) {
            assertTrue(registryMetersForJob(name, jobId) == 0,
                    "W-14 release must remove the task meter surface on teardown: " + name);
        }
    }

    /** Sum of the counters captured by the onMeterAdded listener (all tasks of the job). */
    private static double capturedCount(String name,
                                        java.util.concurrent.ConcurrentMap<String, java.util.List<io.micrometer.core.instrument.Meter>> captured) {
        java.util.List<io.micrometer.core.instrument.Meter> meters = captured.get(name);
        assertNotNull(meters, "task meter must be registered by the real execution path: " + name);
        double total = 0.0;
        for (io.micrometer.core.instrument.Meter m : meters) {
            total += ((io.micrometer.core.instrument.Counter) m).count();
        }
        return total;
    }

    /** Asserts the onMeterAdded listener captured a meter of this name for the job. */
    private static void assertCaptured(String name,
                                       java.util.concurrent.ConcurrentMap<String, java.util.List<io.micrometer.core.instrument.Meter>> captured) {
        assertNotNull(captured.get(name),
                "WI16 meter must be registered on the LOCAL path: " + name);
    }

    /** Number of registry meters with this name carrying the job's taskId tag. */
    private static int registryMetersForJob(String name, String jobId) {
        int[] n = {0};
        StreamMetricsRegistries.registry().find(name).meters().forEach(m -> {
            if (jobId.equals(m.getId().getTag("jobId"))) {
                n[0]++;
            }
        });
        return n[0];
    }

    /** Asserts at least one meter with the name (and jobId tag) exists in the registry. */
    private static void assertGaugePresent(String name, String jobId) {
        boolean[] found = {false};
        StreamMetricsRegistries.registry().find(name).meters().forEach(m -> {
            if (jobId.equals(m.getId().getTag("jobId"))) {
                found[0] = true;
            }
        });
        assertTrue(found[0], "WI16 meter must be registered on the LOCAL path: " + name);
    }

    private static Double counter(String name, String jobId) {
        io.micrometer.core.instrument.Counter counter = StreamMetricsRegistries.registry()
                .find(name).tag("jobId", jobId).counter();
        return counter == null ? null : counter.count();
    }

    /** Sums all per-task (vertex-tagged) counters of one metric name within a job. */
    private static Double sumCounters(String name, String jobId) {
        double[] total = {0.0};
        boolean[] found = {false};
        StreamMetricsRegistries.registry().getMeters().forEach(m -> {
            if (m.getId().getName().equals(name)
                    && jobId.equals(m.getId().getTag("jobId"))) {
                found[0] = true;
                m.measure().forEach(mv -> {
                    if (mv.getStatistic() == io.micrometer.core.instrument.Statistic.COUNT) {
                        total[0] += mv.getValue();
                    }
                });
            }
        });
        return found[0] ? total[0] : null;
    }

    private JobGraph buildSourceToSinkGraph(String jobId, List<Integer> sinkResults,
                                            int recordCount) {
        SourceFunction<Integer> sourceFn = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= recordCount; i++) {
                    ctx.collect(i);
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

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "sink-2", ResultPartitionType.PIPELINED));
        return jobGraph;
    }
}
