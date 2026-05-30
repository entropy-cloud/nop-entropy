package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestExactlyOnceCorrectnessFixes {

    @TempDir
    Path tempDir;

    @Test
    void testCommitFailureRetrySucceeds() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setMaxConcurrentCheckpoints(10);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                "job-1", "pipe-1", new CheckpointIDCounter(), storage, config);

        AtomicInteger commitAttempts = new AtomicInteger(0);
        AtomicInteger commitSuccesses = new AtomicInteger(0);

        CheckpointParticipant failingThenSuccess = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) throws Exception {
                return null;
            }

            @Override
            public void prepareCommit(long epochId) throws Exception {
            }

            @Override
            public void finishCommit(long epochId, boolean success) throws Exception {
                if (success) {
                    int attempt = commitAttempts.incrementAndGet();
                    if (attempt <= 1) {
                        throw new StreamException("Transient commit failure attempt " + attempt);
                    }
                    commitSuccesses.incrementAndGet();
                }
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
            }
        };

        coordinator.addParticipant(failingThenSuccess);

        TaskLocation loc = new TaskLocation("job-1", "pipe-1", "v1", 0);
        coordinator.registerTask(loc);

        PendingCheckpoint pending1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(loc, pending1.getCheckpointId(), new TaskStateSnapshot(loc));
        pending1.getCompletableFuture().get();

        PendingCheckpoint pending2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(loc, pending2.getCheckpointId(), new TaskStateSnapshot(loc));
        pending2.getCompletableFuture().get();

        assertTrue(commitSuccesses.get() >= 1,
                "commit should succeed after deferred retry, attempts=" + commitAttempts.get());

        coordinator.shutdown();
    }

    @Test
    void testFailedCommitsAreRetriedOnNextCheckpoint() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                "job-2", "pipe-2", new CheckpointIDCounter(), storage, config);

        AtomicInteger callCount = new AtomicInteger(0);
        List<Long> committedEpochs = Collections.synchronizedList(new ArrayList<>());

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) throws Exception {
                return null;
            }

            @Override
            public void prepareCommit(long epochId) throws Exception {
            }

            @Override
            public void finishCommit(long epochId, boolean success) throws Exception {
                if (success) {
                    int call = callCount.incrementAndGet();
                    if (call <= 1) {
                        throw new StreamException("First finishCommit call always fails");
                    }
                    committedEpochs.add(epochId);
                }
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
            }
        };

        coordinator.addParticipant(participant);

        TaskLocation loc = new TaskLocation("job-2", "pipe-2", "v1", 0);
        coordinator.registerTask(loc);

        PendingCheckpoint pending1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(loc, pending1.getCheckpointId(), new TaskStateSnapshot(loc));
        CompletedCheckpoint completed1 = (CompletedCheckpoint) pending1.getCompletableFuture().get();
        assertNotNull(completed1);

        assertTrue(committedEpochs.isEmpty(),
                "First epoch should fail and be deferred to retry cycle");

        PendingCheckpoint pending2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        coordinator.acknowledgeTask(loc, pending2.getCheckpointId(), new TaskStateSnapshot(loc));
        CompletedCheckpoint completed2 = (CompletedCheckpoint) pending2.getCompletableFuture().get();
        assertNotNull(completed2);

        assertTrue(committedEpochs.contains(pending1.getCheckpointId()),
                "Previously failed epoch should be retried on next checkpoint");

        coordinator.shutdown();
    }

    @Test
    void testSubsumingCommitCommitsAllPendingTransactions() throws Exception {
        List<Long> committedEpochs = Collections.synchronizedList(new ArrayList<>());
        TreeMap<Long, Object> pendingCommits = new TreeMap<>();

        TwoPhaseCommitSinkFunction<String> sink = new TwoPhaseCommitSinkFunction<String>() {
            private Map<Long, Object> pending = pendingCommits;

            @Override public void beginTransaction() {}
            @Override public void invoke(String value) {}
            @Override public void preCommit(long checkpointId) {
                pending.put(checkpointId, "tx-" + checkpointId);
            }
            @Override public void commit(long checkpointId) {
                committedEpochs.add(checkpointId);
            }
            @Override public void rollback() {}
            @Override public Map<Long, Object> getPendingCommits() { return pending; }
            @Override public void setPendingCommits(Map<Long, Object> p) { this.pending = p; }
        };

        sink.prepareCommit(1);
        sink.finishCommit(1, false);
        assertTrue(committedEpochs.isEmpty(), "Abort should not commit");
        assertEquals(1, pendingCommits.size());
        assertTrue(pendingCommits.containsKey(1L));

        sink.prepareCommit(2);
        sink.finishCommit(2, true);

        assertTrue(committedEpochs.contains(1L),
                "Subsuming commit should commit epoch 1 when epoch 2 succeeds");
        assertTrue(committedEpochs.contains(2L),
                "Epoch 2 should be committed");
        assertTrue(pendingCommits.isEmpty(),
                "All pending transactions should be cleared after subsuming commit");
    }

    @Test
    void testSubsumingCommitPartialOrdering() throws Exception {
        List<Long> committedEpochs = Collections.synchronizedList(new ArrayList<>());
        TreeMap<Long, Object> pendingCommits = new TreeMap<>();

        TwoPhaseCommitSinkFunction<String> sink = new TwoPhaseCommitSinkFunction<String>() {
            private Map<Long, Object> pending = pendingCommits;

            @Override public void beginTransaction() {}
            @Override public void invoke(String value) {}
            @Override public void preCommit(long checkpointId) {
                pending.put(checkpointId, "tx-" + checkpointId);
            }
            @Override public void commit(long checkpointId) {
                committedEpochs.add(checkpointId);
            }
            @Override public void rollback() {}
            @Override public Map<Long, Object> getPendingCommits() { return pending; }
            @Override public void setPendingCommits(Map<Long, Object> p) { this.pending = p; }
        };

        sink.prepareCommit(1);
        sink.finishCommit(1, false);

        sink.prepareCommit(2);
        sink.finishCommit(2, false);

        sink.prepareCommit(3);
        sink.finishCommit(3, true);

        assertEquals(List.of(1L, 2L, 3L), committedEpochs,
                "Commit(3) should subsume and commit all pending epochs <= 3");
        assertTrue(pendingCommits.isEmpty());
    }

    @Test
    void testRestoreFromEpochRollsbackPendingCommits() throws Exception {
        AtomicInteger rollbackCount = new AtomicInteger(0);
        TreeMap<Long, Object> pendingCommits = new TreeMap<>();
        pendingCommits.put(1L, "tx-1");
        pendingCommits.put(2L, "tx-2");

        TwoPhaseCommitSinkFunction<String> sink = new TwoPhaseCommitSinkFunction<String>() {
            private Map<Long, Object> pending = pendingCommits;

            @Override public void beginTransaction() {}
            @Override public void invoke(String value) {}
            @Override public void preCommit(long checkpointId) {}
            @Override public void commit(long checkpointId) {}
            @Override public void rollback() { rollbackCount.incrementAndGet(); }
            @Override public void recover(long checkpointId) {}
            @Override public Map<Long, Object> getPendingCommits() { return pending; }
            @Override public void setPendingCommits(Map<Long, Object> p) { this.pending = p; }
        };

        sink.restoreFromEpoch(3, null);

        assertEquals(2, rollbackCount.get(), "Should rollback each pending transaction");
        assertTrue(pendingCommits.isEmpty(), "Pending commits should be cleared");
    }

    @Test
    void testOperatorSnapshotResultMerge() {
        OperatorSnapshotResult result1 = new OperatorSnapshotResult();
        result1.putOperatorState("key-1", "value-1");
        result1.putKeyedState("keyed-1", "kvalue-1");

        OperatorSnapshotResult result2 = new OperatorSnapshotResult();
        result2.putOperatorState("key-2", "value-2");
        result2.putKeyedState("keyed-2", "kvalue-2");

        result1.merge(result2);

        assertEquals("value-1", result1.getOperatorState("key-1"));
        assertEquals("value-2", result1.getOperatorState("key-2"));
        assertEquals("kvalue-1", result1.getKeyedState("keyed-1"));
        assertEquals("kvalue-2", result1.getKeyedState("keyed-2"));
    }

    @Test
    void testOperatorSnapshotResultMergeNull() {
        OperatorSnapshotResult result = new OperatorSnapshotResult();
        result.putOperatorState("key-1", "value-1");
        result.merge(null);
        assertEquals(1, result.getOperatorStates().size());
    }

    @Test
    void testRestoreFailsOnTaskLocationMismatch() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        TaskLocation wrongLoc = new TaskLocation("job-x", "pipe-x", "v-wrong", 0);
        TaskStateSnapshot taskState = TaskStateSnapshot.builder(wrongLoc)
                .checkpointId(1L)
                .putOperatorState("op-1", "wrong-data")
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(wrongLoc, taskState);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();

        String path = storage.storeCheckPoint(checkpoint);
        assertNotNull(path);

        SourceFunction<String> source = new SourceFunction<>() {
            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("test");
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {});

        java.util.List<StreamOperator<?>> operators = java.util.Arrays.asList(sourceOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        java.util.List<OperatorChain> chains = java.util.Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("mismatch-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("1");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        StreamException ex = assertThrows(
                StreamException.class,
                () -> GraphModelCheckpointExecutor.executeWithSavepoint(
                        jobGraph, "Mismatch Test", config, tempDir.toString()));
        assertTrue(ex.getMessage().contains("v-wrong") || ex.getParam("detail").toString().contains("v-wrong"),
                "Exception should indicate task location mismatch, got: " + ex.getMessage());
    }
}
