package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-11 closure: proves the {@link CheckpointBarrierTracker} honors an
 * {@link OperatorSnapshotResult#hasError()} ACK by routing it to the
 * {@link CheckpointFailureListener} channel and refusing to deliver the snapshot
 * to the success channel. The previous implementation silently treated a
 * failed ACK as success and corrupted checkpoint state.
 */
class TestCheckpointBarrierTrackerErrorPropagation {

    private static final TaskLocation LOC = new TaskLocation("job", "p", "v", 0);

    @Test
    void testSnapshotErrorRoutesToAbortCallbackAndDoesNotDeliverSuccess() throws Exception {
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());
        operators.add(new MockOp());

        AtomicReference<TaskStateSnapshot> successSnapshot = new AtomicReference<>();
        AtomicReference<Long> abortCheckpointId = new AtomicReference<>();
        AtomicReference<Exception> abortError = new AtomicReference<>();
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC, operators, Collections.emptyList(),
                successSnapshot::set,
                (CheckpointFailureListener) (checkpointId, error) -> {
                    abortCheckpointId.set(checkpointId);
                    abortError.set(error);
                });

        assertTrue(tracker.triggerCheckpoint(7L, 0L, CheckpointType.CHECKPOINT));

        // First operator reports an error in its snapshot.
        OperatorSnapshotResult failed = new OperatorSnapshotResult();
        Exception cause = new IllegalStateException("boom");
        failed.setError(cause);
        tracker.acknowledgeOperator(0, failed);

        // Second operator sends a normal empty ACK — must NOT be delivered as success.
        tracker.acknowledgeOperator(1, new OperatorSnapshotResult());

        assertNull(successSnapshot.get(),
                "Success callback must NOT fire when an operator snapshot failed");
        assertEquals(7L, abortCheckpointId.get(),
                "Abort callback must receive the failed checkpoint id");
        assertSame(cause, abortError.get(),
                "Abort callback must receive the snapshot error cause");
        assertEquals(-1L, tracker.getCurrentCheckpointId(),
                "Tracker state reset after snapshot failure (mirrors notifyCheckpointAborted)");
    }

    @Test
    void testNoAbortCallbackStillRefusesToDeliverFailedSnapshot() throws Exception {
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());

        AtomicReference<TaskStateSnapshot> successSnapshot = new AtomicReference<>();
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC, operators, successSnapshot::set);

        assertTrue(tracker.triggerCheckpoint(9L, 0L, CheckpointType.CHECKPOINT));

        OperatorSnapshotResult failed = new OperatorSnapshotResult();
        failed.setError(new IllegalStateException("no callback wired"));
        tracker.acknowledgeOperator(0, failed);

        assertNull(successSnapshot.get(),
                "Even without an abort callback the tracker must refuse to deliver a failed snapshot");
        assertEquals(-1L, tracker.getCurrentCheckpointId());
    }

    @Test
    void testSuccessfulSnapshotsContinueToRouteToSuccessCallback() throws Exception {
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());

        AtomicReference<TaskStateSnapshot> successSnapshot = new AtomicReference<>();
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC, operators, Collections.emptyList(), successSnapshot::set,
                (CheckpointFailureListener) (id, e) -> fail("abort callback must not fire on success"));

        assertTrue(tracker.triggerCheckpoint(11L, 0L, CheckpointType.CHECKPOINT));
        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());

        assertNotNull(successSnapshot.get(),
                "Success callback must fire when all operators ACK without error");
        assertEquals(11L, successSnapshot.get().getCheckpointId());
    }

    static class MockOp extends AbstractStreamOperator<String> {
    }
}
