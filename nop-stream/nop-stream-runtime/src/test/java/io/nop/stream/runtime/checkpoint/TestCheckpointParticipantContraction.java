package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C3: checkpoint participant-set contraction for terminal
 * tasks + state inheritance.
 *
 * <p>Pre-fix repro (the bounded-job timeout-abort loop) recorded in
 * {@code _tmp/r6-p1-c3-repro.log} via
 * {@code ReproC3CompletedTaskStallsCheckpoints}: a task that reached COMPLETED
 * stayed in the ACK set, so every subsequent checkpoint waited for its ACK
 * until the full checkpoint timeout and aborted.
 */
class TestCheckpointParticipantContraction {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 0);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(30000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    private void ackBoth(long cpId, String tag) throws Exception {
        coordinator.acknowledgeTask(LOC_1, cpId,
                TaskStateSnapshot.builder(LOC_1).checkpointId(cpId)
                        .putOperatorState("op", tag + "-v1").build());
        coordinator.acknowledgeTask(LOC_2, cpId,
                TaskStateSnapshot.builder(LOC_2).checkpointId(cpId)
                        .putOperatorState("op", tag + "-v2").build());
    }

    /**
     * After a task is contracted (markTaskCompleted), the next checkpoint's
     * participant set excludes it, the epoch completes on the live task's ACK
     * alone, and the completed checkpoint INHERITS the contracted task's state
     * from the latest completed checkpoint (full task-state set preserved).
     */
    @Test
    void testCompletedTaskContractedAndStateInherited() throws Exception {
        // Epoch 1: both tasks participate.
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        assertEquals(2, p1.getNumberOfNotAcknowledgedTasks());
        ackBoth(p1.getCheckpointId(), "e1");
        CompletedCheckpoint c1 = p1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(c1);

        // Task v2 reaches its terminal COMPLETED state and is contracted out.
        coordinator.markTaskCompleted(LOC_2);

        // Epoch 2: only the live task must ACK.
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2);
        assertEquals(1, p2.getNumberOfNotAcknowledgedTasks(),
                "The completed task must be contracted out of the participant set");
        assertEquals(1, p2.getNumberOfAcknowledgedTasks(),
                "The completed task's state must be pre-acknowledged (inherited)");

        coordinator.acknowledgeTask(LOC_1, p2.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_1).checkpointId(p2.getCheckpointId())
                        .putOperatorState("op", "e2-v1").build());
        CompletedCheckpoint c2 = p2.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(c2, "The epoch completes on the live task's ACK alone");

        // Inheritance: the new epoch carries the contracted task's state from
        // the latest completed checkpoint (epoch 1).
        TaskStateSnapshot inherited = c2.getTaskState(LOC_2);
        assertNotNull(inherited, "The completed task's state must be inherited into the new epoch");
        assertEquals("e1-v2", inherited.getOperatorState("op"),
                "The inherited snapshot is the contracted task's last ACKed snapshot");
        assertEquals("e2-v1", c2.getTaskState(LOC_1).getOperatorState("op"),
                "The live task's snapshot is its own epoch-2 snapshot");
    }

    /**
     * When every task is terminal the ACK set is empty. The PERIODIC trigger is
     * rejected NO_TASKS_TO_ACK (null — the driver skips the tick, no synthetic
     * empty-epoch spam), while an EXPLICIT terminal action assembles its epoch
     * ENTIRELY from the inherited terminal states and completes it inline:
     * zero waiting (no live participant can hang it), zero failure (the normal
     * shutdown never becomes a job fail), and the durable artifact the action
     * promises (a real CompletedCheckpoint carrying the FULL task-state set)
     * still exists — a DRAIN must not bypass its terminal savepoint (§2.9)
     * even when every task already finished. This is the C2×C3 lifecycle
     * closure for bounded jobs.
     */
    @Test
    void testAllTasksTerminalAssemblesEpochFromInheritedStates() throws Exception {
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        ackBoth(p1.getCheckpointId(), "e1");
        p1.getCompletableFuture().get(5, TimeUnit.SECONDS);

        coordinator.markTaskCompleted(LOC_1);
        coordinator.markTaskCompleted(LOC_2);

        // Periodic tick after full drain: NO_TASKS_TO_ACK (null).
        assertNull(coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT),
                "The periodic trigger reports NO_TASKS_TO_ACK after full drain");

        // Explicit terminal action: the epoch is assembled from the inherited
        // terminal states and completes inline (immediate, no live ACKs).
        // 时间确定化（plan 2513）：注入与 ManualClock 共线的手动调度器——虚拟时间
        // 从未推进，超时任务不可能被触发；future 仍然完成即结构性证明「无需等待」
        // （替代墙钟 elapsed < 2000 阈值断言，禁止形态第 4 类）。
        io.nop.commons.test.time.ManualClock manualClock = new io.nop.commons.test.time.ManualClock(1_000_000L);
        io.nop.api.core.time.CoreMetrics.registerClock(manualClock);
        CompletedCheckpoint completed;
        try {
            coordinator.setTimeoutSchedulerForTest(new io.nop.commons.test.time.ManualScheduler(manualClock));
            PendingCheckpoint terminal = coordinator.triggerCheckpointBounded(
                    CheckpointType.COMPLETED_POINT_TYPE, 5000L);
            assertNotNull(terminal, "The explicit terminal trigger still produces its epoch");
            completed = terminal.getCompletableFuture().get(5, TimeUnit.SECONDS);
            assertNotNull(completed, "The all-terminal epoch completes (no hanging, no job fail)");
        } finally {
            io.nop.api.core.time.CoreMetrics.registerClock(io.nop.api.core.time.CoreMetrics.defaultClock());
        }
        assertEquals("e1-v1", completed.getTaskState(LOC_1).getOperatorState("op"),
                "The epoch carries the inherited state of terminal task v1");
        assertEquals("e1-v2", completed.getTaskState(LOC_2).getOperatorState("op"),
                "The epoch carries the inherited state of terminal task v2");
        assertNotNull(storage.getLatestCheckpoint("j", "p"),
                "The assembled epoch is published durably (the savepoint/manifest promise)");
    }

    /**
     * An unknown (never-registered) location cannot inject a phantom
     * contraction: markTaskCompleted ignores it.
     */
    @Test
    void testUnknownLocationIgnored() {
        TaskLocation unknown = new TaskLocation("j", "p", "ghost", 0);
        coordinator.markTaskCompleted(unknown);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(2, pending.getNumberOfNotAcknowledgedTasks(),
                "The participant set is unchanged by an unknown location");
    }

    /**
     * The completed tombstones are generation-scoped: a (re-)assignment via
     * setTasksToAcknowledge clears them, matching the DISTRIBUTED
     * completedSubtaskKeys tombstone lifecycle on fencing rotation.
     */
    @Test
    void testReassignmentClearsCompletedGeneration() throws Exception {
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        ackBoth(p1.getCheckpointId(), "e1");
        p1.getCompletableFuture().get(5, TimeUnit.SECONDS);

        coordinator.markTaskCompleted(LOC_2);

        // New generation reassignment (DISTRIBUTED recovery path).
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2);
        assertEquals(2, p2.getNumberOfNotAcknowledgedTasks(),
                "After a generation reassignment every subtask is a participant again");
        assertEquals(0, p2.getNumberOfAcknowledgedTasks(),
                "No inherited state after a generation reset (terminal state not re-reported yet)");
    }

    /**
     * Pre-fix mechanism pin: WITHOUT contraction a completed task stalls the
     * epoch until the checkpoint timeout aborts it. Uses a short timeout to
     * keep the repro fast; this is the unit-scale form of the bounded-job
     * 600s abort loop from the comparison report.
     */
    @Test
    void testUncontractedCompletedTaskStallsEpochUntilTimeout() throws Exception {
        // 时间确定化（plan 2513）：超时触发器用与 ManualClock 共享虚拟时间线的
        // ManualScheduler 注入（plan 2512/2513 式附加 seam，默认路径不变）；
        // 「等满超时才中止」的契约由精确虚拟时刻的 (a)+(b) 结构性证明，
        // 不再用墙钟 elapsed 阈值断言。
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        ManualScheduler timeoutScheduler = new ManualScheduler(clock);
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(400L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        CheckpointCoordinator stall = new CheckpointCoordinator("j2", "p",
                new CheckpointIDCounter(), storage, config);
        stall.setTimeoutSchedulerForTest(timeoutScheduler);
        stall.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        try {
            PendingCheckpoint p1 = stall.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(p1);
            // Only the live task ACKs; the completed task never does.
            stall.acknowledgeTask(LOC_1, p1.getCheckpointId(),
                    TaskStateSnapshot.builder(LOC_1).checkpointId(p1.getCheckpointId())
                            .putOperatorState("op", "v1").build());

            // (a) Not yet due: the timeout task is scheduled (structural, count == 1)
            // but 399 virtual ms short of its 400ms deadline — the epoch future must
            // still be pending. This proves the coordinator does NOT abort early.
            assertEquals(1, timeoutScheduler.pendingTaskCount(),
                    "The timeout task is scheduled for the epoch");
            timeoutScheduler.advance(399L);
            assertFalse(p1.getCompletableFuture().isDone(),
                    "Before the timeout deadline the epoch must still be pending (no early abort)");
            assertEquals(1, timeoutScheduler.pendingTaskCount(),
                    "The timeout task has not fired before its deadline");

            // (b) At the deadline the timeout task fires and aborts the epoch.
            timeoutScheduler.advance(1L);
            java.util.concurrent.ExecutionException blocked = assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> p1.getCompletableFuture().get(5, TimeUnit.SECONDS),
                    "The epoch can only end in the timeout abort (the pre-fix defect mechanism)");
            assertTrue(String.valueOf(blocked.getCause()).contains("Timeout")
                            || String.valueOf(blocked.getCause()).contains("abort"),
                    "The abort is the timeout abort: " + blocked.getCause());
        } finally {
            stall.shutdown();
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }
}
