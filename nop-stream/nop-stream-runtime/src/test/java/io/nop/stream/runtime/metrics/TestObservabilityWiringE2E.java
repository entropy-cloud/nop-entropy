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

        JobGraph jobGraph = buildSourceToSinkGraph(jobId, sinkResults, recordCount, 60L);

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
        // (metrics are per-task — vertex-tagged; summing across the job's tasks)
        Double consumed = sumCounters(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_CONSUMED, jobId);
        Double emitted = sumCounters(MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_EMITTED, jobId);
        Double recordsIn = sumCounters(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN, jobId);

        assertNotNull(consumed, "io records.consumed meter must be registered on the LOCAL path");
        assertEquals((double) recordCount, consumed, 0.0,
                "source-side consumption must equal the emitted record count");
        assertNotNull(emitted, "io records.emitted meter must be registered on the LOCAL path");
        assertEquals((double) recordCount, emitted, 0.0,
                "cross-task emissions must equal the emitted record count");
        assertNotNull(recordsIn, "operator records.in meter must be registered on the LOCAL path");
        assertEquals((double) recordCount, recordsIn, 0.0,
                "sink-task input-gate dispatch must equal the record count");
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
                                            int recordCount, long emitDelayMs) {
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

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "sink-2", ResultPartitionType.PIPELINED));
        return jobGraph;
    }
}
