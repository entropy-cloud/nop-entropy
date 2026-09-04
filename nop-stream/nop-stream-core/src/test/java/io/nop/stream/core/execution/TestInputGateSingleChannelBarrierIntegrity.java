/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.streamrecord.StreamElement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-5 / AR-6 regression proofs (plan 1326-2 Phase 2).
 *
 * <p>AR-5: the single-channel {@code InputGate.read()} path must apply the same
 * aborted-epoch barrier filtering and same-channel duplicate-barrier de-duplication
 * as the multi-channel path (Stage 45 written contract). Pre-fix, a dead epoch's
 * straggler barrier was still returned (snapshotted + forwarded downstream) and a
 * duplicate barrier fed AR-6's double-ACK window.
 *
 * <p>AR-6: {@code CheckpointBarrierTracker.acknowledgeOperator} must de-duplicate
 * ACKs PER OPERATOR — pre-fix, N-1 real ACKs + 1 duplicate completed the epoch
 * with a missing operator snapshot (silent exactly-once break).
 */
class TestInputGateSingleChannelBarrierIntegrity {

    private static final io.nop.stream.core.checkpoint.TaskLocation LOC =
            new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v0", 0);

    // ------------------------------------------------------------------
    // AR-5 component proofs (single-channel gate)
    // ------------------------------------------------------------------

    /**
     * A straggler barrier for an aborted epoch must be discarded by the
     * single-channel path (same contract as the multi-channel path): not returned
     * to the operator (no spurious snapshot / no downstream forwarding), and a
     * later live epoch still emits normally.
     */
    @Test
    void testAbortedEpochStragglerBarrierDiscardedOnSingleChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(p0), null);

        // epoch 5 is aborted via the control channel BEFORE its straggler barrier
        // meanders through the data plane
        gate.abortBarrierAlignment(5);

        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT)); // dead-epoch straggler
        p0.write(new CheckpointBarrier(6, 0, CheckpointType.CHECKPOINT)); // next live epoch
        p0.write(new io.nop.stream.core.streamrecord.StreamRecord<>("tail"));
        p0.close();

        List<Long> emitted = drainBarrierIds(gate);
        assertEquals(List.of(6L), emitted,
                "the aborted epoch's straggler barrier must be discarded on the single-channel "
                        + "path; only the live epoch 6 may reach the operator");
    }

    /**
     * A duplicate barrier (same id, same channel) must be processed exactly once.
     */
    @Test
    void testDuplicateBarrierDeduplicatedOnSingleChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(p0), null);

        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT)); // duplicate
        p0.write(new CheckpointBarrier(8, 0, CheckpointType.CHECKPOINT));
        p0.close();

        List<Long> emitted = drainBarrierIds(gate);
        assertEquals(List.of(7L, 8L), emitted,
                "duplicate barrier 7 must be de-duplicated on the single-channel path "
                        + "(delivered exactly once); epoch 8 still emits");
    }

    // ------------------------------------------------------------------
    // AR-6 component proof (tracker per-operator ACK dedup)
    // ------------------------------------------------------------------

    /**
     * N-1 real ACKs + 1 duplicate ACK must NOT complete the epoch: the tracker
     * counts DISTINCT operators only. The epoch stays in-flight until the real
     * Nth operator ACK arrives.
     */
    @Test
    void testDuplicateOperatorAckDoesNotCompleteEpoch() throws Exception {
        Map<Long, TaskStateSnapshot> delivered = new ConcurrentHashMap<>();
        List<AbstractStreamOperator<?>> operators = mockOperators(3);
        CheckpointBarrierTracker tracker =
                new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> delivered.put(snapshot.getCheckpointId(), snapshot));

        assertTrue(tracker.triggerCheckpoint(1, 0, CheckpointType.CHECKPOINT));

        tracker.acknowledgeOperator(0, taggedResult(1, "op0"));
        tracker.acknowledgeOperator(1, taggedResult(1, "op1"));
        // duplicate ACK from operator 1 (at-least-once redelivery / double callback)
        tracker.acknowledgeOperator(1, taggedResult(1, "op1-dup"));

        assertNull(delivered.get(1L),
                "N-1 real ACKs (op0, op1) + 1 duplicate (op1 again) must NOT complete the "
                        + "epoch — the shared counter must not be double-decremented");
        assertTrue(tracker.hasInFlightCheckpoints(),
                "epoch must remain in-flight until the real 3rd operator ACKs");

        tracker.acknowledgeOperator(2, taggedResult(1, "op2"));

        TaskStateSnapshot completed = delivered.get(1L);
        assertEquals(1L, completed.getCheckpointId(),
                "the real Nth ACK completes the epoch");
        assertFalse(tracker.hasInFlightCheckpoints());
    }

    // ------------------------------------------------------------------
    // AR-5 → AR-6 chain proof: duplicate barrier channel → double ACK
    // ------------------------------------------------------------------

    /**
     * Chain reproduction: a duplicate barrier crossing the single-channel gate
     * drives a second {@code processBarrier} for the same operator (the production
     * path is {@code AbstractStreamOperator.processBarrier} → snapshot callback →
     * {@code acknowledgeOperator}). Pre-fix this fed the tracker one duplicate ACK
     * that substituted for a real operator's snapshot. With AR-5 fixed the gate
     * never delivers the duplicate; with AR-6 fixed the tracker refuses one even
     * if it arrives by another channel.
     */
    @Test
    void testDuplicateBarrierCannotCompleteEpochWithMissingOperatorSnapshot() throws Exception {
        // two operators chain: op0 sits behind the (single-channel) input gate,
        // op1 is chained downstream and ACKs once per epoch trigger
        Map<Long, TaskStateSnapshot> delivered = new ConcurrentHashMap<>();
        List<AbstractStreamOperator<?>> operators = mockOperators(2);
        CheckpointBarrierTracker tracker =
                new CheckpointBarrierTracker(LOC, new ArrayList<>(operators), snapshot -> delivered.put(snapshot.getCheckpointId(), snapshot));
        // mirror StreamTaskInvokable.setupSnapshotCallbacks wiring (keep direct
        // references — the operator has no callback getter)
        List<Consumer<OperatorSnapshotResult>> callbacks = new ArrayList<>();
        for (int i = 0; i < operators.size(); i++) {
            final int opIndex = i;
            Consumer<OperatorSnapshotResult> callback = snapshot -> tracker.acknowledgeOperator(opIndex, snapshot);
            operators.get(i).setSnapshotCallback(callback);
            callbacks.add(callback);
        }

        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(p0), null);

        assertTrue(tracker.triggerCheckpoint(7, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT));
        p0.write(new CheckpointBarrier(7, 0, CheckpointType.CHECKPOINT)); // duplicate
        p0.close();

        AtomicInteger op0BarrierReceipts = new AtomicInteger();
        // task loop: every barrier the gate delivers reaches op0 (processBarrier →
        // snapshot → ACK). op1 ACKs once per epoch (chained behind op0's barrier
        // forwarding — modelled as: ACK on first receipt only).
        boolean op1Acked = false;
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) {
                if (gate.isAllFinished()) {
                    break;
                }
                continue;
            }
            if (e.get().isCheckpointBarrier()) {
                long id = e.get().asCheckpointBarrier().getId();
                OperatorSnapshotResult result = taggedResult(id, "op0");
                callbacks.get(0).accept(result); // op0 ACK per receipt
                op0BarrierReceipts.incrementAndGet();
                if (!op1Acked) {
                    callbacks.get(1).accept(taggedResult(id, "op1"));
                    op1Acked = true;
                }
            }
        }

        assertEquals(1, op0BarrierReceipts.get(),
                "AR-5: the single-channel gate must deliver the duplicate barrier exactly "
                        + "once (pre-fix: 2 receipts)");
        TaskStateSnapshot completed = delivered.get(7L);
        assertEquals(1, delivered.size(), "epoch 7 completes exactly once");
        assertTrue(completed.getOperatorStates().containsKey("operator-0-op0")
                        && completed.getOperatorStates().containsKey("operator-1-op1"),
                "the completed snapshot must contain BOTH operators' states — a duplicate "
                        + "ACK must never substitute for a real operator's snapshot");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static List<Long> drainBarrierIds(InputGate gate) throws InterruptedException {
        List<Long> ids = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) {
                if (gate.isAllFinished()) {
                    break;
                }
                continue;
            }
            if (e.get().isCheckpointBarrier()) {
                ids.add(e.get().asCheckpointBarrier().getId());
            }
        }
        return ids;
    }

    private static OperatorSnapshotResult taggedResult(long cpId, String marker) {
        OperatorSnapshotResult r = new OperatorSnapshotResult();
        r.setCheckpointId(cpId);
        r.putOperatorState(marker, marker);
        return r;
    }

    private static List<AbstractStreamOperator<?>> mockOperators(int count) {
        List<AbstractStreamOperator<?>> ops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ops.add(new AbstractStreamOperator<Object>() {
                private static final long serialVersionUID = 1L;
            });
        }
        return ops;
    }
}
