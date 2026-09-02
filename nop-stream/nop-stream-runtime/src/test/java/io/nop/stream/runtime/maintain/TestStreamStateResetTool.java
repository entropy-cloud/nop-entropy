/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-10): state reset tool — explicit refusal semantics and the
 * reset-then-replay-from-start e2e demonstration.
 */
class TestStreamStateResetTool {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "reset-e2e-job";

    private JobGraph buildReplayableGraph(List<Integer> sinkResults, int recordCount) {
        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= recordCount; i++) {
                    ctx.collect(i);
                    Thread.sleep(20);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                sinkResults.add(value);
            }
        });
        OperatorChain sc = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain kc = new OperatorChain(Collections.singletonList(sinkOp));
        JobGraph g = new JobGraph(JOB_ID);
        g.addVertex(new JobVertex("source-1", "Source", 1, Collections.singletonList(sc),
                new StreamTaskInvokable(sc)));
        g.addVertex(new JobVertex("sink-2", "Sink", 1, Collections.singletonList(kc),
                new StreamTaskInvokable(kc)));
        g.addEdge(new JobEdge("source-1", "sink-2", ResultPartitionType.PIPELINED));
        return g;
    }

    private List<Integer> runJob() throws Exception {
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());
        JobGraph graph = buildReplayableGraph(sinkResults, 10);
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(100);
        config.setCheckpointTimeout(5000);
        config.setProcessingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE);
        config.setStorageProperty("path", tempDir.toString());
        GraphModelCheckpointExecutor.executeWithCheckpoint(graph, JOB_ID, config);
        return sinkResults;
    }

    @Test
    void resetThenReplayFromStart() throws Exception {
        // run 1: full output + durable state on disk
        List<Integer> run1 = runJob();
        assertEquals(10, run1.size(), "run 1 processes all records");
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        assertNotNull(storage.getLatestCheckpoint(JOB_ID, "1"),
                "durable checkpoint state exists after run 1");
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)), "job state directory exists");

        // reset: clears durable checkpoints + source cursors
        StreamStateResetTool.ResetResult result =
                StreamStateResetTool.reset(JOB_ID, tempDir.toString(), true, null);
        assertEquals(JOB_ID, result.getJobId());
        assertEquals(tempDir.resolve(JOB_ID), result.getDeletedPath());
        assertTrue(result.getDeletedCheckpoints() >= 1,
                "at least one checkpoint artifact counted: " + result.getDeletedCheckpoints());
        assertFalse(Files.exists(tempDir.resolve(JOB_ID)), "state directory gone after reset");

        // run 2 (same jobId, fresh state): replays from the start — full output again
        List<Integer> run2 = runJob();
        assertEquals(10, run2.size(), "after reset the job replays from the start: " + run2);
        for (int i = 1; i <= 10; i++) {
            assertTrue(run2.contains(i));
        }
    }

    @Test
    void refusesNonReplayableSource() throws Exception {
        runJob(); // ensure state dir exists
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StreamStateResetTool.reset(JOB_ID, tempDir.toString(), false, null));
        assertTrue(ex.getMessage().contains("NON-REPLAYABLE"),
                "refusal explains the non-replayable reason: " + ex.getMessage());
        // state untouched (refused, not best-effort-cleared)
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)));
    }

    @Test
    void refusesMissingStateDirectory() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StreamStateResetTool.reset("typo-job", tempDir.toString(), true, null));
        assertTrue(ex.getMessage().contains("no local state directory"),
                "wrong path refuses instead of silently succeeding: " + ex.getMessage());
    }

    @Test
    void refusesBlankArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> StreamStateResetTool.reset(" ", tempDir.toString(), true, null));
        assertThrows(IllegalArgumentException.class,
                () -> StreamStateResetTool.reset("j", " ", true, null));
    }

    @Test
    void refusesActiveCoordinator() throws Exception {
        runJob();
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerCoordinator(JOB_ID, "coordinator-live", 42L);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StreamStateResetTool.reset(JOB_ID, tempDir.toString(), true, registry));
        assertTrue(ex.getMessage().contains("active coordinator"),
                "running job refuses reset: " + ex.getMessage());
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)), "state untouched after refusal");
    }
}
