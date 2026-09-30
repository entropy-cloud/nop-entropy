package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRE-FIX repro for plan 369 Phase 1 C2 (defect recorded in
 * ai-dev/analysis/nop-stream/09a-flink2.3-compare-checkpoint-fault-tolerance.md):
 * when a terminal/savepoint trigger collides with an in-flight periodic
 * checkpoint (maxConcurrent=1), the pre-fix call chain
 * {@code tryTriggerPendingCheckpoint → null → silently skip} drops the
 * terminal savepoint and the job "finishes" without its durable savepoint.
 *
 * <p>This test pins the MECHANISM the production callers hit pre-fix:
 * {@code tryTriggerPendingCheckpoint(TERMINAL_SAVEPOINT)} returns null while a
 * periodic checkpoint is in flight. Post-fix the callers use the new
 * {@code triggerCheckpointBounded} which retries within the checkpoint timeout.
 */
class ReproC2TerminalSavepointSkippedOnInFlightCollision {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 0);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(30000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter,
                new LocalFileCheckpointStorage(tempDir.toString()), config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void testTerminalSavepointTriggerReturnsNullWhilePeriodicInFlight() {
        // Periodic checkpoint in flight, never ACKed.
        PendingCheckpoint periodic = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(periodic, "Periodic checkpoint triggered");

        // THE BUG: the terminal-savepoint trigger collides with the in-flight
        // periodic checkpoint and returns null — the pre-fix production callers
        // (JobCoordinator.terminateWithTerminalSavepoint,
        // GraphModelCheckpointExecutor.triggerTerminalSavepoint) then silently
        // skip the terminal savepoint.
        PendingCheckpoint terminal = coordinator.tryTriggerPendingCheckpoint(CheckpointType.TERMINAL_SAVEPOINT);
        assertNull(terminal, "PRE-FIX BUG confirmed: terminal savepoint trigger silently skipped"
                + " (null) while a periodic checkpoint is in flight");
    }
}
