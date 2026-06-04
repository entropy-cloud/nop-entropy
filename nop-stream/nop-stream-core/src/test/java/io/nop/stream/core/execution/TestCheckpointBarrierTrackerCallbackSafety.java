package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointBarrierTrackerCallbackSafety {

    @Test
    void testCallbackReceivesSnapshotAfterAllAcks() throws Exception {
        AtomicReference<TaskStateSnapshot> captured = new AtomicReference<>();
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());
        operators.add(new MockOp());

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                new TaskLocation("j", "p", "v", 0), operators, captured::set);

        tracker.triggerCheckpoint(1, 0, CheckpointType.CHECKPOINT);

        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());
        assertNull(captured.get());

        tracker.acknowledgeOperator(1, new OperatorSnapshotResult());
        assertNotNull(captured.get());
        assertEquals(1, captured.get().getCheckpointId());
    }

    @Test
    void testDuplicateAckIgnored() throws Exception {
        AtomicReference<TaskStateSnapshot> captured = new AtomicReference<>();
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                new TaskLocation("j", "p", "v", 0), operators, captured::set);

        tracker.triggerCheckpoint(1, 0, CheckpointType.CHECKPOINT);
        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());
        assertNotNull(captured.get());

        TaskStateSnapshot first = captured.get();
        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());
        assertSame(first, captured.get());
    }

    @Test
    void testCallbackDoesNotHoldLockDuringInvocation() throws Exception {
        AtomicReference<TaskStateSnapshot> captured = new AtomicReference<>();
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new MockOp());

        final CheckpointBarrierTracker[] holder = new CheckpointBarrierTracker[1];
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                new TaskLocation("j", "p", "v", 0), operators, snap -> {
            captured.set(snap);
            try {
                holder[0].triggerCheckpoint(2, 0, CheckpointType.CHECKPOINT);
            } catch (Exception e) {
                throw new StreamException(ARG_DETAIL, e).param(ARG_DETAIL, "triggerCheckpoint in callback");
            }
        });
        holder[0] = tracker;

        tracker.triggerCheckpoint(1, 0, CheckpointType.CHECKPOINT);
        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());

        assertNotNull(captured.get());
        assertEquals(1, captured.get().getCheckpointId());
    }

    private static class MockOp extends AbstractStreamOperator<String> {
    }
}
