/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 45 (multi-epoch) Phase 4 end-to-end proof: drives the full
 * trigger → ACK → complete path with {@code maxConcurrentCheckpoints=3} so that
 * three epochs are simultaneously in-flight at the task-side tracker, and proves
 * that aborting one epoch does not kill the others (design §2.8.1 D3).
 *
 * <p>Goes through the **real coordinator path** (tryTriggerPendingCheckpoint →
 * acknowledgeTask → completePendingCheckpoint / abortPendingCheckpoint) AND the
 * real {@link CheckpointBarrierTracker} per-epoch ACK path
 * (triggerCheckpoint → acknowledgeOperator tagged with checkpointId). This is the
 * plan guide #22 end-to-end verification and #23 wiring verification: the
 * tracker's multi-epoch ACK path is actually exercised, not just its type system.
 */
class TestMultiEpochCheckpointE2E {

    private static final TaskLocation LOC = new TaskLocation("j", "p", "v0", 0);

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;
    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(5_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(3)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter, storage, config);
        coordinator.registerTask(LOC);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    /**
     * E2E: trigger 3 epochs (all accepted because maxConcurrentCheckpoints=3),
     * hold them simultaneously in-flight at the tracker, then ACK each epoch
     * independently. Each epoch completes via its own completePendingCheckpoint;
     * task states do not cross-contaminate.
     */
    @Test
    void testThreeEpochsInFlightCompleteIndependently() throws Exception {
        Map<Long, TaskStateSnapshot> delivered = new ConcurrentHashMap<>();
        List<AbstractStreamOperator<?>> operators = mockOperators(3);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            delivered.put(snapshot.getCheckpointId(), snapshot);
            coordinator.acknowledgeTask(LOC, snapshot.getCheckpointId(), snapshot);
        });
        wireCallbacks(operators, tracker);

        // Trigger 3 epochs at the coordinator (maxConcurrentCheckpoints=3 allows all).
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint p3 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1, "Epoch 1 must be triggerable");
        assertNotNull(p2, "Epoch 2 must be triggerable (maxConcurrent=3)");
        assertNotNull(p3, "Epoch 3 must be triggerable (maxConcurrent=3)");

        long cp1 = p1.getCheckpointId();
        long cp2 = p2.getCheckpointId();
        long cp3 = p3.getCheckpointId();
        assertNotEquals(cp1, cp2);
        assertNotEquals(cp2, cp3);

        // Register all three at the task-side tracker BEFORE acking any — they must
        // coexist as simultaneously in-flight (the core Stage 45 capability).
        assertTrue(tracker.triggerCheckpoint(cp1, 0L, CheckpointType.CHECKPOINT));
        assertTrue(tracker.triggerCheckpoint(cp2, 0L, CheckpointType.CHECKPOINT));
        assertTrue(tracker.triggerCheckpoint(cp3, 0L, CheckpointType.CHECKPOINT));
        assertEquals(3, tracker.getInFlightCheckpointIds().size(),
                "All three epochs must be in-flight at the tracker simultaneously");

        // ACK each epoch independently (results tagged with checkpointId for routing).
        ackEpoch(tracker, operators, cp1);
        ackEpoch(tracker, operators, cp2);
        ackEpoch(tracker, operators, cp3);

        CompletedCheckpoint c1 = p1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        CompletedCheckpoint c2 = p2.getCompletableFuture().get(5, TimeUnit.SECONDS);
        CompletedCheckpoint c3 = p3.getCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(cp1, c1.getCheckpointId());
        assertEquals(cp2, c2.getCheckpointId());
        assertEquals(cp3, c3.getCheckpointId());

        // task states did not cross-contaminate
        assertEquals(cp1, delivered.get(cp1).getCheckpointId());
        assertEquals(cp2, delivered.get(cp2).getCheckpointId());
        assertEquals(cp3, delivered.get(cp3).getCheckpointId());
        assertFalse(tracker.hasInFlightCheckpoints(), "Tracker empty after all epochs ACKed");
    }

    /**
     * Abort precision (design §2.8.1 D3): with three epochs in-flight, aborting the
     * middle epoch must NOT prevent the other two from completing. The full wiring
     * is exercised: {@code coordinator.abortPendingCheckpoint} fires the registered
     * abort handler, which performs the per-epoch tracker cleanup
     * ({@code notifyCheckpointAborted(N)} — exactly what the updated
     * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler} does). Epochs
     * 1 and 3 remain ACKable after epoch 2 is aborted. This is plan guide #23
     * (wiring) + #22 (E2E).
     */
    @Test
    void testAbortMiddleEpochOthersStillComplete() throws Exception {
        Map<Long, TaskStateSnapshot> delivered = new ConcurrentHashMap<>();
        List<AbstractStreamOperator<?>> operators = mockOperators(3);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            delivered.put(snapshot.getCheckpointId(), snapshot);
            coordinator.acknowledgeTask(LOC, snapshot.getCheckpointId(), snapshot);
        });
        wireCallbacks(operators, tracker);

        // Wire the coordinator abort handler to mirror the local executor's
        // epoch-precise contract: per-epoch tracker cleanup only (the real handler
        // also calls inputGate.abortBarrierAlignment(N) and conditionally cancels
        // the task — proven separately in TestInputGateMultiEpochBarrier).
        coordinator.setAbortHandler(tracker::notifyCheckpointAborted);

        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        PendingCheckpoint p3 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
        long cp1 = p1.getCheckpointId();
        long cp2 = p2.getCheckpointId();
        long cp3 = p3.getCheckpointId();

        tracker.triggerCheckpoint(cp1, 0L, CheckpointType.CHECKPOINT);
        tracker.triggerCheckpoint(cp2, 0L, CheckpointType.CHECKPOINT);
        tracker.triggerCheckpoint(cp3, 0L, CheckpointType.CHECKPOINT);

        // Abort the middle epoch via the real coordinator path → fires the abort
        // handler → per-epoch tracker cleanup. Epochs 1 and 3 are undisturbed.
        coordinator.abortPendingCheckpoint(p2, "Stage 45 precision test: abort middle epoch");

        assertEquals(PendingCheckpoint.Status.ABORTED, p2.getStatus().get(),
                "Aborted epoch must reach ABORTED status");
        assertNull(coordinator.getPendingCheckpoint(cp2),
                "Aborted epoch must be removed from the coordinator's pending map");
        assertFalse(tracker.getInFlightCheckpointIds().contains(cp2),
                "Epoch 2 removed from tracker by the abort handler");
        assertTrue(tracker.getInFlightCheckpointIds().contains(cp1)
                        && tracker.getInFlightCheckpointIds().contains(cp3),
                "Epochs 1 and 3 must remain in-flight after middle abort");

        ackEpoch(tracker, operators, cp1);
        ackEpoch(tracker, operators, cp3);

        CompletedCheckpoint c1 = p1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        CompletedCheckpoint c3 = p3.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(cp1, c1.getCheckpointId(), "Epoch 1 (unaborted) must complete");
        assertEquals(cp3, c3.getCheckpointId(), "Epoch 3 (unaborted) must complete");

        assertNotNull(delivered.get(cp1), "Epoch 1 snapshot delivered");
        assertNotNull(delivered.get(cp3), "Epoch 3 snapshot delivered");
        assertNull(delivered.get(cp2), "Aborted epoch 2 must NOT deliver a snapshot");
    }

    private void ackEpoch(CheckpointBarrierTracker tracker, List<AbstractStreamOperator<?>> operators, long cpId) {
        for (int i = 0; i < operators.size(); i++) {
            OperatorSnapshotResult r = new OperatorSnapshotResult();
            r.setCheckpointId(cpId);
            tracker.acknowledgeOperator(i, r);
        }
    }

    private List<AbstractStreamOperator<?>> mockOperators(int count) {
        List<AbstractStreamOperator<?>> ops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ops.add(new AbstractStreamOperator<Object>() {
                private static final long serialVersionUID = 1L;
            });
        }
        return ops;
    }

    private void wireCallbacks(List<AbstractStreamOperator<?>> operators, CheckpointBarrierTracker tracker) {
        for (int i = 0; i < operators.size(); i++) {
            final int opIndex = i;
            operators.get(i).setSnapshotCallback(snapshot ->
                    tracker.acknowledgeOperator(opIndex, snapshot));
        }
    }
}
