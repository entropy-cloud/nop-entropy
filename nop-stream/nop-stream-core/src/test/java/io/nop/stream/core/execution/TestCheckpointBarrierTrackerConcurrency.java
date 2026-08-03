package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.operators.AbstractStreamOperator;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointBarrierTrackerConcurrency {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    @Test
    void testOverlappingTriggerAcceptedAndAckedIndependently() throws Exception {
        // Stage 45: overlapping triggers are now ACCEPTED (multi-epoch). Each epoch
        // owns independent ACK state; ACKs for epoch 1 do not pollute epoch 2 and
        // vice-versa. This replaces the legacy single-in-flight rejection.
        List<AbstractStreamOperator<?>> operators = createMockOperators(3);
        Map<Long, TaskStateSnapshot> completed = new ConcurrentHashMap<>();
        AtomicInteger callbackCount = new AtomicInteger(0);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            completed.put(snapshot.getCheckpointId(), snapshot);
            callbackCount.incrementAndGet();
        });

        setSnapshotCallbacks(operators, tracker);

        boolean first = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(first, "First trigger should be accepted");

        boolean second = tracker.triggerCheckpoint(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(second, "Stage 45: overlapping trigger must be accepted (multi-epoch)");

        assertEquals(2, tracker.getInFlightCheckpointIds().size(),
                "Both epochs must be in-flight simultaneously");

        // ACK epoch 1 fully (3 operators) using results tagged with checkpointId=1.
        for (int i = 0; i < operators.size(); i++) {
            tracker.acknowledgeOperator(i, taggedResult(1L));
        }
        // ACK epoch 2 fully using results tagged with checkpointId=2.
        for (int i = 0; i < operators.size(); i++) {
            tracker.acknowledgeOperator(i, taggedResult(2L));
        }

        assertEquals(2, callbackCount.get(), "Both epochs must fire their completion callback");
        assertNotNull(completed.get(1L), "Epoch 1 snapshot delivered");
        assertNotNull(completed.get(2L), "Epoch 2 snapshot delivered");
        assertEquals(1L, completed.get(1L).getCheckpointId(), "Epoch 1 snapshot carries id 1");
        assertEquals(2L, completed.get(2L).getCheckpointId(), "Epoch 2 snapshot carries id 2");
        assertFalse(tracker.hasInFlightCheckpoints(), "No epochs remain in-flight after both ACKed");
    }

    @Test
    void testOverlappingTriggerDuplicateIdRejected() throws Exception {
        // Duplicate trigger for the SAME in-flight id is still rejected (idempotency).
        List<AbstractStreamOperator<?>> operators = createMockOperators(2);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {});

        setSnapshotCallbacks(operators, tracker);

        assertTrue(tracker.triggerCheckpoint(1L, 0L, CheckpointType.CHECKPOINT));
        assertFalse(tracker.triggerCheckpoint(1L, 0L, CheckpointType.CHECKPOINT),
                "Duplicate trigger for the same in-flight id must be rejected");
        assertEquals(1, tracker.getInFlightCheckpointIds().size());
    }

    @Test
    void testEpochAbortDoesNotAffectOtherInFlightEpoch() throws Exception {
        // Stage 45 epoch-precise abort: aborting epoch 1 while epoch 2 is in-flight
        // must NOT disturb epoch 2's ACK tracking.
        List<AbstractStreamOperator<?>> operators = createMockOperators(2);
        Map<Long, TaskStateSnapshot> completed = new ConcurrentHashMap<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            completed.put(snapshot.getCheckpointId(), snapshot);
        });
        setSnapshotCallbacks(operators, tracker);

        assertTrue(tracker.triggerCheckpoint(1L, 0L, CheckpointType.CHECKPOINT));
        assertTrue(tracker.triggerCheckpoint(2L, 0L, CheckpointType.CHECKPOINT));

        // Abort epoch 1 mid-flight.
        tracker.notifyCheckpointAborted(1L);
        assertFalse(tracker.hasInFlightCheckpoints() && tracker.getInFlightCheckpointIds().contains(1L),
                "Epoch 1 must be removed after abort");

        // Epoch 2 still in-flight and completable.
        assertTrue(tracker.getInFlightCheckpointIds().contains(2L), "Epoch 2 must remain in-flight");
        for (int i = 0; i < operators.size(); i++) {
            tracker.acknowledgeOperator(i, taggedResult(2L));
        }
        assertNotNull(completed.get(2L), "Epoch 2 must still complete after epoch 1 aborted");
        assertNull(completed.get(1L), "Epoch 1 must not deliver a snapshot (aborted)");
    }

    @Test
    void testRejectedBarrierDoesNotLeaveDirtyState() throws Exception {
        // Stage 45: with multi-epoch, "rejected" only happens for duplicate ids.
        // Verify a duplicate-id rejection leaves prior in-flight state intact and
        // the prior epoch still completes normally.
        List<AbstractStreamOperator<?>> operators = createMockOperators(2);
        AtomicInteger callbackCount = new AtomicInteger(0);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            callbackCount.incrementAndGet();
        });

        setSnapshotCallbacks(operators, tracker);

        boolean first = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(first, "First trigger should succeed");

        boolean dup = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertFalse(dup, "Duplicate-id trigger should be rejected");

        assertEquals(1L, tracker.getCurrentCheckpointId(), "Checkpoint ID should remain at 1 after duplicate rejection");

        for (int i = 0; i < operators.size(); i++) {
            tracker.acknowledgeOperator(i, taggedResult(1L));
        }

        assertEquals(1, callbackCount.get(), "Callback should fire for checkpoint 1");

        boolean third = tracker.triggerCheckpoint(3L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(third, "Third trigger after completion of first should succeed");
        assertEquals(3L, tracker.getCurrentCheckpointId(), "Checkpoint ID should be 3 after successful trigger");
    }

    @Test
    void testConcurrentAckCallbackCalledExactlyOnce() throws Exception {
        int operatorCount = 10;
        List<AbstractStreamOperator<?>> operators = createMockOperators(operatorCount);
        AtomicInteger callbackCount = new AtomicInteger(0);
        CountDownLatch callbackLatch = new CountDownLatch(1);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            callbackCount.incrementAndGet();
            callbackLatch.countDown();
        });

        setSnapshotCallbacks(operators, tracker);

        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(operatorCount);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < operatorCount; i++) {
            final int opIndex = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    tracker.acknowledgeOperator(opIndex, new OperatorSnapshotResult());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertTrue(callbackLatch.await(5, TimeUnit.SECONDS), "Completion callback should have been called");
        assertEquals(1, callbackCount.get(), "Completion callback must be called exactly once");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @RepeatedTest(10)
    void testConcurrentAckRepeated() throws Exception {
        testConcurrentAckCallbackCalledExactlyOnce();
    }

    @Test
    void testExtraAckIsSafelyIgnored() throws Exception {
        // Previously a known issue (operatorsToAck going negative → callback re-fired on
        // extra ACK). After source-path serialization (mailbox) + middle/sink sync trigger,
        // the `operatorsToAck.get() <= 0` guard in acknowledgeOperator safely ignores the
        // extra ACK: the counter stays at 0 and the completion callback is NOT re-fired.
        // This is now a strict positive assertion (was previously a soft `>= 1`).
        List<AbstractStreamOperator<?>> operators = createMockOperators(3);
        AtomicInteger callbackCount = new AtomicInteger(0);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> {
            callbackCount.incrementAndGet();
        });

        setSnapshotCallbacks(operators, tracker);

        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());
        tracker.acknowledgeOperator(1, new OperatorSnapshotResult());
        tracker.acknowledgeOperator(2, new OperatorSnapshotResult());

        assertEquals(1, callbackCount.get(), "completion callback fires exactly once after all operators ACK");

        // Extra/duplicate ACK for operator 0: must be safely ignored, not re-fire the callback.
        tracker.acknowledgeOperator(0, new OperatorSnapshotResult());

        assertEquals(1, callbackCount.get(),
                "extra ACK must NOT re-fire the completion callback (operatorsToAck guard holds)");
    }

    private List<AbstractStreamOperator<?>> createMockOperators(int count) {
        List<AbstractStreamOperator<?>> operators = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            operators.add(new AbstractStreamOperator<Object>() {
                private static final long serialVersionUID = 1L;
            });
        }
        return operators;
    }

    private void setSnapshotCallbacks(List<AbstractStreamOperator<?>> operators, CheckpointBarrierTracker tracker) {
        for (int i = 0; i < operators.size(); i++) {
            final int opIndex = i;
            operators.get(i).setSnapshotCallback(snapshot ->
                    tracker.acknowledgeOperator(opIndex, snapshot));
        }
    }

    /**
     * Builds an OperatorSnapshotResult tagged with the given checkpoint id, so the
     * tracker routes the ACK to the correct epoch (Stage 45 multi-epoch routing).
     */
    private static OperatorSnapshotResult taggedResult(long checkpointId) {
        OperatorSnapshotResult r = new OperatorSnapshotResult();
        r.setCheckpointId(checkpointId);
        return r;
    }
}
