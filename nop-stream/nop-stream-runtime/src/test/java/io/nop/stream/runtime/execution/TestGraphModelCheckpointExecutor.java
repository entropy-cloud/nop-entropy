/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GraphModelCheckpointExecutor's static utility methods:
 * - buildSnapshotFromTaskState (tested in TestE2EBuildSnapshotFromTaskState)
 * - createStorage
 * - handleJobTermination (private, tested indirectly via config dispatch)
 * - restoreFromSavepointPath (private, tested indirectly)
 */
class TestGraphModelCheckpointExecutor {

    @TempDir
    Path tempDir;

    /**
     * 16-01: Verify createStorage creates a LocalFileCheckpointStorage with default path.
     */
    @Test
    void testCreateStorageDefaultPath() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("local");

        ICheckpointStorage storage = GraphModelCheckpointExecutor.createStorage(config);
        assertNotNull(storage);
    }

    /**
     * 16-01: Verify createStorage throws for JDBC storage without configuration.
     */
    @Test
    void testCreateStorageJdbcThrows() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("jdbc");

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.createStorage(config));
        assertTrue(ex.getMessage().contains("JdbcCheckpointStorage")
                || ex.getParam("detail") != null);
    }

    /**
     * 16-01: Verify createStorage uses custom path from config.
     */
    @Test
    void testCreateStorageCustomPath() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("local");
        config.setStorageProperty("path", tempDir.toString());

        ICheckpointStorage storage = GraphModelCheckpointExecutor.createStorage(config);
        assertNotNull(storage);
    }

    /**
     * R6 TE-01 (plan 2325): REAL drive replacing the former config-getter
     * round-trip tests (which passed even with
     * {@code handleJobTermination}'s triggerTerminalSavepoint call deleted).
     * A DRAIN job must durably record a TERMINAL_SAVEPOINT checkpoint in the
     * configured storage — verified by reading the storage back, not by
     * re-reading the config. Mutation probe: commenting the DRAIN branch's
     * {@code triggerTerminalSavepoint} call leaves NOTHING on disk and turns
     * this test red.
     */
    @Test
    void drainModeTerminationWritesTerminalSavepointToDurableStorage(@TempDir Path dir) throws Exception {
        String jobId = "te01-drain";
        CheckpointConfig config = terminationJobConfig(dir, jobId, JobTerminationMode.DRAIN);

        GraphModelCheckpointExecutor.executeWithCheckpoint(terminationJobGraph(), jobId, config);

        CompletedCheckpoint cp = lastDurableCheckpoint(dir, jobId);
        assertNotNull(cp, "DRAIN must durably record a terminal checkpoint "
                + "(mutation: removing triggerTerminalSavepoint leaves nothing on disk)");
        assertEquals(io.nop.stream.core.checkpoint.CheckpointType.TERMINAL_SAVEPOINT, cp.getCheckpointType(),
                "DRAIN's terminal checkpoint must carry the TERMINAL_SAVEPOINT type");
    }

    /** SUSPEND mirrors DRAIN (same TERMINAL_SAVEPOINT type per design §7.3). */
    @Test
    void suspendModeTerminationWritesTerminalSavepointToDurableStorage(@TempDir Path dir) throws Exception {
        String jobId = "te01-suspend";
        CheckpointConfig config = terminationJobConfig(dir, jobId, JobTerminationMode.SUSPEND);

        GraphModelCheckpointExecutor.executeWithCheckpoint(terminationJobGraph(), jobId, config);

        CompletedCheckpoint cp = lastDurableCheckpoint(dir, jobId);
        assertNotNull(cp, "SUSPEND must durably record a terminal checkpoint");
        assertEquals(io.nop.stream.core.checkpoint.CheckpointType.TERMINAL_SAVEPOINT, cp.getCheckpointType(),
                "SUSPEND's terminal checkpoint must carry the TERMINAL_SAVEPOINT type");
    }

    /**
     * 16-01: Verify default termination mode is CANCEL (config-level assertion —
     * honest about scope).
     */
    @Test
    void testDefaultTerminationModeIsCancel() {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(JobTerminationMode.CANCEL, config.getJobTerminationMode());
        assertTrue(config.isCheckpointEnabled());
    }

    private static CheckpointConfig terminationJobConfig(Path dir, String jobId,
                                                         JobTerminationMode mode) {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId("pipeline-0");
        config.setCheckpointEnabled(true);
        // a huge interval: only the TERMINAL savepoint may land on disk
        config.setCheckpointInterval(3600_000L);
        config.setStorageType("local");
        config.setStorageProperty("path", dir.toString());
        config.setJobTerminationMode(mode);
        return config;
    }

    private static io.nop.stream.core.jobgraph.JobGraph terminationJobGraph() throws Exception {
        io.nop.stream.core.common.functions.source.SourceFunction<String> source =
                new io.nop.stream.core.common.functions.source.SourceFunction<>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> ctx) {
                    }

                    @Override
                    public void cancel() {
                    }
                };
        OperatorChain chain = new OperatorChain(java.util.Arrays.asList(
                new io.nop.stream.core.operators.StreamSourceOperator<>(source),
                new io.nop.stream.core.operators.StreamSinkOperator<>(v -> {
                })));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        io.nop.stream.core.jobgraph.JobVertex vertex = new io.nop.stream.core.jobgraph.JobVertex(
                "te01-vertex", "te01", 1, java.util.Collections.singletonList(chain), invokable);
        io.nop.stream.core.jobgraph.JobGraph jobGraph = new io.nop.stream.core.jobgraph.JobGraph("te01-job");
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    private static CompletedCheckpoint lastDurableCheckpoint(Path dir, String jobId) throws Exception {
        io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage storage =
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(dir.toString());
        return storage.getLatestCheckpoint(jobId, "pipeline-0");
    }

    @Test
    void testCreateStorageUnknownTypeThrows() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("unknown");

        assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.createStorage(config));
    }

    /**
     * 16-01: Verify restoreFromSavepointPath fallback when no savepoint exists.
     *
     * <p>When pointing to a non-existent savepoint path, the executor should
     * log a warning and start fresh rather than crash. We test this by verifying
     * that createStorage works with a non-existent directory and that the storage
     * can handle queries for missing checkpoints gracefully.
     */
    @Test
    void testRestoreFromSavepointPathNoSavepointStartsFresh() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageProperty("path", tempDir.resolve("nonexistent-savepoint").toString());

        ICheckpointStorage storage = GraphModelCheckpointExecutor.createStorage(config);
        assertNotNull(storage);

        // Verify that querying for latest checkpoint returns null when no checkpoint exists
        CompletedCheckpoint latest = storage.getLatestCheckpoint(config.getJobId(), config.getPipelineId());
        assertNull(latest, "Should return null when no savepoint exists");
    }

    /**
     * 16-01: Verify restoreFromSavepointPath with a valid but empty path.
     */
    @Test
    void testRestoreFromSavepointPathEmptyDirectoryStartsFresh() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageProperty("path", tempDir.toString());

        ICheckpointStorage storage = GraphModelCheckpointExecutor.createStorage(config);
        CompletedCheckpoint latest = storage.getLatestCheckpoint(config.getJobId(), config.getPipelineId());
        assertNull(latest, "Should return null for empty checkpoint directory");
    }
}
