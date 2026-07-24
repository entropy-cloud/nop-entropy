package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.operators.AbstractStreamOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointBarrierTrackerAbort {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    @Test
    void testTrackerNotifyCheckpointAbortedResetsState() throws Exception {
        List<AbstractStreamOperator<?>> operators = new ArrayList<>();
        DummyOperator op = new DummyOperator();
        operators.add(op);

        AtomicInteger ackCount = new AtomicInteger(0);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            ackCount.incrementAndGet();
        });

        op.setSnapshotCallback(snapshot -> tracker.acknowledgeOperator(0, snapshot));

        boolean triggered = tracker.triggerCheckpoint(42L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(triggered);
        assertEquals(42L, tracker.getCurrentCheckpointId());

        tracker.notifyCheckpointAborted(42L);

        assertEquals(-1L, tracker.getCurrentCheckpointId(),
                "Current checkpoint ID should be reset after abort");

        boolean retriggered = tracker.triggerCheckpoint(43L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(retriggered,
                "A new checkpoint should be triggerable after abort");
    }

    @Test
    void testOperatorNotifyCheckpointAbortedClearsSnapshot() throws Exception {
        DummyOperator op = new DummyOperator();

        OperatorSnapshotResult snapshot = op.snapshotState(new io.nop.stream.core.checkpoint.StateSnapshotContext(1L, 0L));
        assertNotNull(snapshot);
        op.setLastSnapshotResult(snapshot);
        assertNotNull(op.getLastSnapshotResult());

        op.notifyCheckpointAborted(1L);

        assertNull(op.getLastSnapshotResult(),
                "lastSnapshotResult should be cleared after abort");
    }

    @Test
    void testAbortDuringActiveCheckpointAllowsNextCheckpoint() throws Exception {
        List<AbstractStreamOperator<?>> operators = new ArrayList<>();
        DummyOperator op = new DummyOperator();
        operators.add(op);

        AtomicInteger ackCount = new AtomicInteger(0);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            ackCount.incrementAndGet();
        });

        op.setSnapshotCallback(snapshot -> tracker.acknowledgeOperator(0, snapshot));

        boolean triggered = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(triggered);

        tracker.notifyCheckpointAborted(1L);

        boolean nextTriggered = tracker.triggerCheckpoint(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(nextTriggered,
                "Next checkpoint should be accepted after abort");
        assertEquals(2L, tracker.getCurrentCheckpointId());
    }

    static class DummyOperator extends AbstractStreamOperator<Object> {
        void setLastSnapshotResult(OperatorSnapshotResult result) {
            this.lastSnapshotResult = result;
        }
    }
}
