/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED;
import static org.junit.jupiter.api.Assertions.*;

class TestSavepointEndToEnd {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "1", "v1", 0);

    @TempDir
    Path tempDir;

    @Test
    void testGraphModelSavepointTriggerWritesFile() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= 3; i++) {
                    ctx.collect("item-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(results::add);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("savepoint-trigger-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        String savepointPath = GraphModelCheckpointExecutor.triggerSavepoint(jobGraph, config, tempDir.toString());
        assertNotNull(savepointPath);

        assertTrue(Files.exists(Path.of(savepointPath)));
        assertEquals(Arrays.asList("item-1", "item-2", "item-3"), results);
    }

    @Test
    void testGraphModelExecuteWithSavepointRestoresState() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(1L)
                .putOperatorState("operator-1", "restored-data")
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(LOC_0, taskState);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        String savepointPath = storage.storeCheckPoint(checkpoint);
        assertNotNull(savepointPath);

        AtomicInteger restoredCount = new AtomicInteger(0);
        AtomicReference<Object> restoredState = new AtomicReference<>();

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("post-restore");
            }

            @Override
            public void cancel() {
            }
        };

        AbstractStreamOperator<String> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
                super.restoreState(snapshotResult);
                if (snapshotResult != null && !snapshotResult.getOperatorStates().isEmpty()) {
                    restoredCount.incrementAndGet();
                    restoredState.set(snapshotResult.getOperatorStates().values().iterator().next());
                }
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>((value) -> {});

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, restoredOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("savepoint-recovery-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("1");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        io.nop.stream.core.environment.StreamExecutionResult result =
                GraphModelCheckpointExecutor.executeWithSavepoint(
                        jobGraph, "Savepoint Recovery Test", config, tempDir.toString());
        assertNotNull(result);

        assertEquals(1, restoredCount.get());
        assertEquals("restored-data", restoredState.get());

        storage.deleteAllCheckpoints("1");
    }

    /**
     * G29 (anti-hollow): proves the {@code restoreFromEpoch} call site no longer hardcodes
     * {@code epochId=0}. The capturing operator records the epoch that actually reaches
     * {@link CheckpointParticipant#restoreFromEpoch}; it must equal the durable savepoint id
     * staged here (deliberately non-zero), covering the full path:
     * {@code executeWithSavepoint -> restoreFromSavepointPath -> restoreTaskStatesFromCheckpoint
     * -> restoreTaskStatesFromSource -> restoreOperatorsFromState -> restoreFromEpoch}.
     */
    @Test
    void testRestoreFromEpochReceivesRealEpochIdNotZero() throws Exception {
        long stagedEpochId = 9L;
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(stagedEpochId)
                .putOperatorState("payload", "restore-from-savepoint")
                .build();
        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(LOC_0, taskState);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(stagedEpochId)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        String savepointPath = storage.storeCheckPoint(checkpoint);
        assertNotNull(savepointPath);

        EpochCapturingOperator participant = new EpochCapturingOperator();

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
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>((value) -> {});

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, participant, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("epoch-propagation-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("1");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        GraphModelCheckpointExecutor.executeWithSavepoint(
                jobGraph, "Epoch Propagation Test", config, tempDir.toString());

        assertEquals(stagedEpochId, participant.lastEpoch.get(),
                "restoreFromEpoch must receive the real durable epochId, not the hardcoded 0");
        assertTrue(participant.restoreFromEpochCount.get() >= 1,
                "restoreFromEpoch must be invoked at least once during restore");

        storage.deleteAllCheckpoints("1");
    }

    /**
     * Fail-fast coverage (existing behavior, G29 item): when a staged savepoint is missing the
     * TaskLocation the execution plan expects, the restore state-lookup lambda must throw
     * {@link StreamException} rather than silently skipping the task. This asserts the existing
     * fail-fast path at {@code GraphModelCheckpointExecutor} (state-lookup lambda), not new code.
     */
    @Test
    void testRestoreFailsFastOnMissingTaskState() throws Exception {
        long stagedEpochId = 5L;
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        TaskLocation wrongLoc = new TaskLocation("1", "1", "nonexistent-vertex", 0);
        TaskStateSnapshot taskState = TaskStateSnapshot.builder(wrongLoc)
                .checkpointId(stagedEpochId)
                .putOperatorState("payload", "data")
                .build();
        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(wrongLoc, taskState);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(stagedEpochId)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        String savepointPath = storage.storeCheckPoint(checkpoint);
        assertNotNull(savepointPath);

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
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>((value) -> {});

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("failfast-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("1");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        StreamException ex = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.executeWithSavepoint(
                        jobGraph, "Fail-Fast Test", config, tempDir.toString()));
        assertEquals(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED.getErrorCode(), ex.getErrorCode(),
                "Missing task state must fail fast with RESTORE_FAILED, not be silently skipped");

        storage.deleteAllCheckpoints("1");
    }

    /**
     * An operator that is simultaneously an {@link AbstractStreamOperator} and a
     * {@link CheckpointParticipant}, so that
     * {@code GraphModelCheckpointExecutor.restoreOperatorsFromState} invokes
     * {@link CheckpointParticipant#restoreFromEpoch} directly on it with the real epochId.
     * Records the epochId and per-subtask state it observes during restore.
     */
    static final class EpochCapturingOperator extends AbstractStreamOperator<String>
            implements CheckpointParticipant {
        private static final long serialVersionUID = 1L;

        final AtomicLong lastEpoch = new AtomicLong(Long.MIN_VALUE);
        final Map<Integer, Object> restoredPayloadByTaskIndex = new ConcurrentHashMap<>();
        final AtomicInteger restoreFromEpochCount = new AtomicInteger(0);

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return TaskStateSnapshot.builder(new TaskLocation("x", "x", "x", 0)).build();
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            lastEpoch.set(epochId);
            restoreFromEpochCount.incrementAndGet();
            if (state != null && state.getTaskLocation() != null) {
                restoredPayloadByTaskIndex.put(
                        state.getTaskLocation().getTaskIndex(),
                        state.getOperatorState("payload"));
            }
        }
    }
}
