/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-ST-07: an EXPLICIT savepoint restore whose three-tier lookup chain
 * (keyed latest-checkpoint, direct file load, parent-directory keyed lookup)
 * misses entirely must fail loudly — never silently start a stateful job
 * from empty state. Only the absence of a configured savepoint path keeps
 * the fresh-start behavior.
 */
class TestExplicitSavepointMissFailFast {

    @TempDir
    Path tempDir;

    private JobGraph newJobGraph(String jobId) {
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {
        });

        OperatorChain chain = new OperatorChain(Arrays.<StreamOperator<?>>asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, Collections.singletonList(chain), invokable);
        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    private CheckpointConfig newConfig(String jobId, String savepointDir) {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600000L);
        config.setStorageProperty("path", savepointDir);
        return config;
    }

    @Test
    void testExplicitSavepointAllTiersMissFailsInsteadOfFreshStart() throws Exception {
        // Empty directory: keyed lookup, direct file load and parent-directory
        // lookup all miss — the explicit restore must be rejected.
        Path missDir = tempDir.resolve("miss-dir");
        java.nio.file.Files.createDirectories(missDir);

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.executeWithSavepoint(
                        newJobGraph("savepoint-miss-job"), "savepoint-miss",
                        newConfig("savepoint-miss-job", missDir.toString()),
                        missDir.toString()));

        assertEquals(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(), ex.getErrorCode(),
                "all-tier savepoint miss must fail with the restore-failed error code");
        String detail = String.valueOf(ex.getParam("detail"));
        assertTrue(detail.contains(missDir.toString()),
                "error must list the requested savepoint path, got: " + detail);
        assertTrue(detail.contains("jobId=savepoint-miss-job"),
                "error must list the keyed lookup coordinates, got: " + detail);
        assertTrue(detail.contains("(1)") && detail.contains("(2)") && detail.contains("(3)"),
                "error must enumerate all three lookup tiers, got: " + detail);
    }

    @Test
    void testExplicitSavepointNonexistentPathAlsoFails() throws Exception {
        Path nonexistent = tempDir.resolve("does-not-exist");

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.executeWithSavepoint(
                        newJobGraph("savepoint-missing-path-job"), "savepoint-missing-path",
                        newConfig("savepoint-missing-path-job", nonexistent.toString()),
                        nonexistent.toString()));

        assertEquals(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(), ex.getErrorCode());
        String detail = String.valueOf(ex.getParam("detail"));
        assertTrue(detail.contains(nonexistent.toString()),
                "error must list the requested savepoint path, got: " + detail);
    }
}
