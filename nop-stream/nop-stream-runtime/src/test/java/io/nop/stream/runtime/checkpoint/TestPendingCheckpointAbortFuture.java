package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ST-01 regression: {@code PendingCheckpoint.abort} used to CAS
 * RUNNING&rarr;ABORTED internally, but {@code CheckpointCoordinator.abortPendingCheckpoint}
 * already performs that transition before delegating — so the inner CAS never
 * fired and the future stayed incomplete forever (savepoint/DRAIN/SUSPEND
 * waiters blocked for the full checkpointTimeout, then saw a misleading
 * TimeoutException instead of {@code ERR_STREAM_CHECKPOINT_ABORTED}).
 *
 * <p>The fix mirrors {@code forceFail}'s read-judge-complete pattern: the
 * future completes exceptionally with the abort reason regardless of which
 * side performed the status transition, idempotently, and never overwrites an
 * already-completed (normal) future.
 */
class TestPendingCheckpointAbortFuture {

    private Set<TaskLocation> tasks;

    @BeforeEach
    void setUp() {
        tasks = new HashSet<>();
        tasks.add(new TaskLocation("job-1", "pipe-1", "v1", 0));
        tasks.add(new TaskLocation("job-1", "pipe-1", "v1", 1));
    }

    private PendingCheckpoint newPending() {
        return new PendingCheckpoint("job-1", "pipe-1", 1L,
                System.currentTimeMillis(), CheckpointType.CHECKPOINT, tasks);
    }

    /**
     * The production interleaving: the coordinator CASes RUNNING&rarr;ABORTED
     * (e.g. timeout scheduler / RPC abortCheckpoint) and then calls
     * {@code pending.abort(reason)}. The future must surface the abort reason
     * instead of never completing.
     */
    @Test
    void abortAfterCoordinatorPreCas_completesFutureWithAbortReason() throws Exception {
        PendingCheckpoint pc = newPending();
        // Coordinator-side transition (CheckpointCoordinator.abortPendingCheckpoint:900)
        assertTrue(pc.getStatus().compareAndSet(PendingCheckpoint.Status.RUNNING,
                PendingCheckpoint.Status.ABORTED));

        pc.abort("checkpoint timed out");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> pc.getCompletableFuture().get(1, TimeUnit.SECONDS),
                "Waiters must be released by the abort, not hang until checkpointTimeout");
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof StreamException,
                "cause must be the typed abort error, got " + cause);
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED.getErrorCode(),
                ((StreamException) cause).getErrorCode());
        assertEquals("checkpoint timed out", ((StreamException) cause).getParam(ARG_REASON));
    }

    /** Direct abort (status still RUNNING — the CAS happens inside abort). */
    @Test
    void directAbort_completesFutureWithAbortReason() {
        PendingCheckpoint pc = newPending();
        pc.abort("coordinator shutdown");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> pc.getCompletableFuture().get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof StreamException);
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED.getErrorCode(),
                ((StreamException) ex.getCause()).getErrorCode());
        assertEquals("coordinator shutdown", ((StreamException) ex.getCause()).getParam(ARG_REASON));
    }

    /** Re-entrant abort (timeout + RPC abort racing) must not throw or corrupt the cause. */
    @Test
    void abortIsIdempotent_secondAbortDoesNotThrow() {
        PendingCheckpoint pc = newPending();
        pc.abort("first abort");
        pc.abort("second abort");

        assertEquals(PendingCheckpoint.Status.ABORTED, pc.getStatus().get());
        assertTrue(pc.isDisposed());
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> pc.getCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED.getErrorCode(),
                ((StreamException) ex.getCause()).getErrorCode());
    }

    /** A future already completed normally (all ACKs + forceComplete) is never overwritten. */
    @Test
    void abortAfterNormalComplete_doesNotOverwriteFuture() throws Exception {
        PendingCheckpoint pc = newPending();
        for (TaskLocation loc : tasks) {
            pc.acknowledgeTask(loc, TaskStateSnapshot.empty(loc));
        }
        assertTrue(pc.forceComplete());
        CompletedCheckpoint completed = pc.getCompletableFuture().get(1, TimeUnit.SECONDS);
        assertNotNull(completed);

        // Late abort after the normal completion: must not replace the result.
        pc.abort("late abort");

        CompletedCheckpoint reread = pc.getCompletableFuture().get(1, TimeUnit.SECONDS);
        assertSame(completed, reread, "abort must not overwrite a normally completed future");
    }
}
