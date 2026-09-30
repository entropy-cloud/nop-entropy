package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.testsupport.TestAwait;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C3 (review F-6 core test): incremental RocksDB segment
 * reference-count MIGRATION on state inheritance.
 *
 * <p>When a terminal task is contracted out of the participant set, its state
 * snapshot is inherited from the latest completed checkpoint. The inherited
 * snapshot's SST handles must be re-registered under the NEW epoch (via the
 * coordinator's incremental persist path — SharedStateRegistry CLAIM
 * semantics) so that when the OLD checkpoint is retention-pruned its
 * unregister only drops its own reference and the segments the new epoch
 * depends on stay materialized. Raw reference sharing would let the retention
 * prune delete a segment the new checkpoint's restore needs.
 */
class TestCheckpointInheritedSegmentRefCount {

    @TempDir
    Path tmp;

    private LocalFileCheckpointStorage storage;
    private LocalFileSegmentStore segmentStore;
    private CheckpointCoordinator coordinator;
    private SharedStateRegistry registry;

    private static final TaskLocation LOC_A = new TaskLocation("job-mig", "pipe", "va", 0);
    private static final TaskLocation LOC_B = new TaskLocation("job-mig", "pipe", "vb", 0);

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tmp.resolve("cp").toString());
        segmentStore = new LocalFileSegmentStore(tmp.resolve("ss"));
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(2);
        config.setMinPause(0L);
        coordinator = new CheckpointCoordinator("job-mig", "pipe",
                new CheckpointIDCounter(), storage, config);
        coordinator.setIncrementalCheckpointEnabled(true);
        coordinator.setSegmentStore(segmentStore);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_A, LOC_B));
        registry = coordinator.getSharedStateRegistry();
        assertNotNull(registry);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    /** Real RocksDB incremental keyed-state snapshot carrying SST handles. */
    private StateSnapshot rocksIncrementalSnapshot(String dbSub) throws Exception {
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tmp.resolve(dbSub).toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tmp.resolve(dbSub + "-ckp").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(7L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(13L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();
        return snapshot;
    }

    private TaskStateSnapshot incrementalTaskState(TaskLocation loc, long cpId,
                                                   StateSnapshot keyed) {
        TaskStateSnapshot ts = new TaskStateSnapshot(loc, cpId);
        ts.putKeyedState("keyed-state", keyed);
        return ts;
    }

    private Set<String> hashesOf(TaskStateSnapshot snapshot) {
        Set<String> hashes = new HashSet<>();
        Object keyed = snapshot.getKeyedStates().get("keyed-state");
        IncrementalSnapshotResult result = extract(keyed);
        assertNotNull(result, "the snapshot must carry an incremental result");
        for (io.nop.stream.core.checkpoint.incremental.SharedStateHandle h : result.getSstHandles()) {
            hashes.add(h.getStateObjectId());
        }
        assertFalse(hashes.isEmpty(), "the incremental result must carry SST handles");
        return hashes;
    }

    private IncrementalSnapshotResult extract(Object value) {
        if (value instanceof IncrementalSnapshotResult) {
            return (IncrementalSnapshotResult) value;
        }
        if (value instanceof StateSnapshot) {
            Object marker = ((StateSnapshot) value).getStateData()
                    .get(IncrementalSnapshotResult.MARKER_KEY);
            if (marker instanceof IncrementalSnapshotResult) {
                return (IncrementalSnapshotResult) marker;
            }
        }
        return null;
    }

    /**
     * The migration scenario: epoch 1 has both tasks (vb carries incremental
     * SST handles); vb is then contracted; epochs 2 and 3 inherit vb's state.
     * When retention prunes epoch 1, its unregister must NOT delete the
     * segments — the new epochs hold migrated references, so the segments
     * remain materialized and the inherited handles stay restorable.
     */
    @Test
    void testInheritedSstSegmentsSurviveRetentionPruneOfOldCheckpoint() throws Exception {
        // Epoch 1: both tasks ACK; vb with incremental RocksDB state. The hash
        // set is extracted from the SAME snapshot object that is acked — two
        // separate RocksDB snapshots never share SST content hashes.
        StateSnapshot vbKeyed = rocksIncrementalSnapshot("db1");
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        Set<String> hashes = hashesOf(
                incrementalTaskState(LOC_B, p1.getCheckpointId(), vbKeyed));
        coordinator.acknowledgeTask(LOC_A, p1.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_A).checkpointId(p1.getCheckpointId())
                        .putOperatorState("op", "e1-va").build());
        coordinator.acknowledgeTask(LOC_B, p1.getCheckpointId(),
                incrementalTaskState(LOC_B, p1.getCheckpointId(), vbKeyed));
        CompletedCheckpoint c1 = p1.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertNotNull(c1);

        for (String hash : hashes) {
            assertEquals(1, registry.getReferenceCount(hash),
                    "epoch 1 holds exactly one reference per handle");
            assertTrue(segmentStore.segmentExists(hash),
                    "segment materialized for epoch 1");
        }

        // vb reaches terminal state and is contracted out.
        coordinator.markTaskCompleted(LOC_B);

        // Epoch 2: only va ACKs; vb's state is inherited.
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2);
        assertEquals(1, p2.getNumberOfNotAcknowledgedTasks());
        coordinator.acknowledgeTask(LOC_A, p2.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_A).checkpointId(p2.getCheckpointId())
                        .putOperatorState("op", "e2-va").build());
        CompletedCheckpoint c2 = p2.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertNotNull(c2);
        // The inherited snapshot carries the same SST handles → the new epoch's
        // persist re-registered them (ref migration to epoch 2).
        for (String hash : hashes) {
            assertTrue(registry.getReferenceCount(hash) >= 2,
                    "the inherited handles must be re-registered under the new epoch (got "
                            + registry.getReferenceCount(hash) + " for " + hash + ")");
        }

        // Epoch 3: retention (maxRetained=2) prunes epoch 1. vb's state is
        // inherited again from epoch 2.
        PendingCheckpoint p3 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p3);
        coordinator.acknowledgeTask(LOC_A, p3.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_A).checkpointId(p3.getCheckpointId())
                        .putOperatorState("op", "e3-va").build());
        CompletedCheckpoint c3 = p3.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertNotNull(c3);

        // Wait for the async retention run to prune epoch 1 and release its
        // references. The migrated references (epochs 2/3) keep the count >= 2
        // and the segment files materialized.
        TestAwait.until("retention pruned epoch 1 and migrated refs survive", () -> {
            for (String hash : hashes) {
                int count = registry.getReferenceCount(hash);
                if (count < 2 || !segmentStore.segmentExists(hash)) {
                    return false;
                }
            }
            return true;
        }, 30_000L);

        // The pruned epoch's manifest is gone / superseded; the NEW epoch's
        // manifest carries the inherited vb state with restorable handles.
        EpochManifest manifest = storage.loadLatestEpochManifest("job-mig", "pipe");
        assertNotNull(manifest);
        assertTrue(manifest.getEpochId() >= c3.getCheckpointId());
        TaskStateSnapshot inheritedB = manifest.getTaskSnapshots().get(LOC_B);
        assertNotNull(inheritedB, "the new epoch's manifest still carries the inherited task state");
        Set<String> manifestHashes = new HashSet<>();
        IncrementalSnapshotResult inheritedResult =
                extract(inheritedB.getKeyedStates().get("keyed-state"));
        if (inheritedResult != null) {
            for (io.nop.stream.core.checkpoint.incremental.SharedStateHandle h
                    : inheritedResult.getSstHandles()) {
                manifestHashes.add(h.getStateObjectId());
            }
        }
        for (String hash : hashes) {
            if (manifestHashes.contains(hash)) {
                assertTrue(segmentStore.segmentExists(hash),
                        "inherited handle " + hash + " must resolve to a materialized segment"
                                + " after the old checkpoint was pruned");
            }
        }
    }

    /**
     * Non-incremental (JSON) inheritance: the snapshot reference is shared
     * directly (adjudicated as acceptable in plan 369 C3) — the inherited
     * state is readable from the new epoch.
     */
    @Test
    void testNonIncrementalInheritanceSharesSnapshotReference() throws Exception {
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        TaskStateSnapshot bState = TaskStateSnapshot.builder(LOC_B)
                .checkpointId(p1.getCheckpointId()).putOperatorState("op", "e1-vb").build();
        coordinator.acknowledgeTask(LOC_A, p1.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_A).checkpointId(p1.getCheckpointId())
                        .putOperatorState("op", "e1-va").build());
        coordinator.acknowledgeTask(LOC_B, p1.getCheckpointId(), bState);
        p1.getCompletableFuture().get(30, TimeUnit.SECONDS);

        coordinator.markTaskCompleted(LOC_B);

        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2);
        coordinator.acknowledgeTask(LOC_A, p2.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_A).checkpointId(p2.getCheckpointId())
                        .putOperatorState("op", "e2-va").build());
        CompletedCheckpoint c2 = p2.getCompletableFuture().get(30, TimeUnit.SECONDS);
        assertNotNull(c2);
        assertEquals("e1-vb", c2.getTaskState(LOC_B).getOperatorState("op"),
                "non-incremental inherited state is readable from the new epoch");
    }
}
