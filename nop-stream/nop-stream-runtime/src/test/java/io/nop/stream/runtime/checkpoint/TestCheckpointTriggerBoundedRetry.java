package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 369 Phase 1 C2: bounded-retry trigger for terminal/savepoint
 * checkpoints ({@link CheckpointCoordinator#triggerCheckpointBounded}).
 *
 * <p>Pre-fix repro (silent skip on in-flight collision) recorded in
 * {@code _tmp/r6-p1-c2-repro.log} via
 * {@code ReproC2TerminalSavepointSkippedOnInFlightCollision}: the raw
 * {@code tryTriggerPendingCheckpoint} returned null while a periodic
 * checkpoint was in flight and the production callers silently skipped the
 * terminal savepoint.
 */
class TestCheckpointTriggerBoundedRetry {

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

    /**
     * In-flight collision → bounded retry: the terminal savepoint trigger
     * waits for the in-flight periodic checkpoint to complete (ACKed from a
     * helper thread) and then succeeds.
     */
    @Test
    void testInFlightCollisionRetriesUntilPeriodicCompletes() throws Exception {
        PendingCheckpoint periodic = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(periodic);

        // Complete the in-flight periodic checkpoint after a delay, from a
        // helper thread — simulating the periodic pipeline finishing while the
        // terminal trigger is retrying.
        CountDownLatch acked = new CountDownLatch(1);
        Thread acker = new Thread(() -> {
            try {
                Thread.sleep(300L);
                coordinator.acknowledgeTask(LOC_1, periodic.getCheckpointId(),
                        TaskStateSnapshot.builder(LOC_1).checkpointId(periodic.getCheckpointId())
                                .putOperatorState("op", "p1").build());
                coordinator.acknowledgeTask(LOC_2, periodic.getCheckpointId(),
                        TaskStateSnapshot.builder(LOC_2).checkpointId(periodic.getCheckpointId())
                                .putOperatorState("op", "p2").build());
            } catch (Exception e) {
                // test failure surfaced by the assertion below
            } finally {
                acked.countDown();
            }
        });
        acker.start();

        long start = System.currentTimeMillis();
        PendingCheckpoint terminal = coordinator.triggerCheckpointBounded(
                CheckpointType.TERMINAL_SAVEPOINT, 10_000L);
        acked.await(5, TimeUnit.SECONDS);

        assertNotNull(terminal, "Terminal savepoint must be triggered after the in-flight "
                + "checkpoint clears (bounded retry)");
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, terminal.getCheckpointType());
        assertTrue(System.currentTimeMillis() - start >= 250L,
                "The trigger actually retried while the periodic checkpoint was in flight");
        acker.join(5000L);
    }

    /**
     * NO_TASKS_TO_ACK → short-circuit success (null return). With the C3
     * participant contraction an empty ACK set on a terminal trigger means
     * every task is terminal — treating that as a failure would turn a normal
     * shutdown into a job failure (the C2×C3 interaction).
     */
    @Test
    void testNoTasksToAckShortCircuitsAsSuccess() throws Exception {
        coordinator.setTasksToAcknowledge(java.util.Collections.emptyList());
        long start = System.currentTimeMillis();
        PendingCheckpoint terminal = coordinator.triggerCheckpointBounded(
                CheckpointType.TERMINAL_SAVEPOINT, 5000L);
        assertNull(terminal, "NO_TASKS_TO_ACK short-circuits as success (null)");
        assertTrue(System.currentTimeMillis() - start < 1000L, "Short-circuit is immediate");
    }

    /**
     * Retry budget exhausted → loud typed failure. The in-flight periodic
     * checkpoint is never ACKed, so the back-pressure rejection never clears
     * and the bounded trigger fails with ERR_STREAM_CHECKPOINT_FAILED.
     */
    @Test
    void testRetryBudgetExhaustedFailsLoud() throws Exception {
        PendingCheckpoint periodic = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(periodic);
        // Never ACK the periodic checkpoint.

        long start = System.currentTimeMillis();
        try {
            coordinator.triggerCheckpointBounded(CheckpointType.TERMINAL_SAVEPOINT, 600L);
            fail("Expected ERR_STREAM_CHECKPOINT_FAILED when the retry budget is exhausted");
        } catch (StreamException e) {
            assertEquals("nop.err.stream.checkpoint-failed", e.getErrorCode().toString(),
                    "Budget exhaustion must fail with the typed checkpoint-failed error");
            assertTrue(String.valueOf(e.toString()).contains("TERMINAL_SAVEPOINT"),
                    "The failure message must name the blocked checkpoint type");
        }
        assertTrue(System.currentTimeMillis() - start >= 550L,
                "The trigger retried until the budget was exhausted");
    }
}
