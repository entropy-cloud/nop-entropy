/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.execution.materialization.MaterializedElement;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the plan 368 Phase 3 CC-02 fix (materialization-replay
 * "stored gap" double delivery) plus its companion audits:
 * <ul>
 *   <li>CC-02 — the (T1 drain, T3 snapshot) gap records are delivered exactly
 *       once, in order, through the gap-closed replay attachment.</li>
 *   <li>AR-04 — control events (watermark / live barrier) drained from the
 *       residual queue are forwarded to the rebuilt gate, not dropped; stale
 *       barriers of the restored generation are dropped.</li>
 *   <li>REG-07/AR-05 — a second attachPendingReplay over an unconsumed segment
 *       fails fast instead of silently orphaning the pending segment.</li>
 * </ul>
 * The pre-fix failure of the core scenario is recorded in
 * {@code _tmp/r5-p3-prefix-repro.log} (gap record R2 delivered twice).
 */
class TestReplayGapClosure {

    private static final long CUT = 5L;

    private static String value(StreamElement element) {
        assertNotNull(element);
        return ((StreamRecord<?>) element).getValue().toString();
    }

    private static List<String> drainAll(InputChannel channel, int max) throws InterruptedException {
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            StreamElement e = channel.read(50, TimeUnit.MILLISECONDS);
            if (e == null) {
                break;
            }
            seen.add(value(e));
        }
        return seen;
    }

    private static CheckpointBarrier barrier(long id) {
        return new CheckpointBarrier(id, 0L, CheckpointType.CHECKPOINT);
    }

    // ==================== CC-02 seam (deterministic interleave) ====================

    /**
     * The audited seam, constructed by direct call in interleave order:
     * T1 residual drain removes R0; producer dual-writes R1/R2/R3 (all in the
     * store snapshot at T3); T3' gap drain removes the queue copy of the gap
     * record R2 — and a post-snapshot record R4 that is NOT in the snapshot.
     * The assembled segment must contain every record exactly once, in order:
     * snapshot segment first, then the kept post-snapshot record. The pre-cut
     * residual record R0 must not be re-delivered (already reflected in the
     * restored operator state).
     */
    @Test
    void seamInterleaveDeliversExactlyOnceInOrder() {
        StreamElement r0 = new StreamRecord<>("R0");
        StreamElement r1 = new StreamRecord<>("R1");
        StreamElement r2 = new StreamRecord<>("R2");
        StreamElement r3 = new StreamRecord<>("R3");
        StreamElement r4 = new StreamRecord<>("R4");

        // T1: the residual drain (pre-cut in-flight record).
        List<StreamElement> residual = Collections.singletonList(r0);
        // T3: the store snapshot (all post-cut store writes).
        List<MaterializedElement> snapshot = Arrays.asList(
                new MaterializedElement(r1, CUT),
                new MaterializedElement(r2, CUT),
                new MaterializedElement(r3, CUT));
        // T3': the gap drain — R2's queue copy (dual-written in the gap) plus
        // R4 enqueued after the snapshot.
        List<StreamElement> gap = Arrays.asList(r2, r4);

        List<StreamElement> segment =
                SupervisionLoop.assembleReplaySegment(snapshot, residual, gap, CUT);

        List<String> seen = new ArrayList<>();
        for (StreamElement e : segment) {
            seen.add(value(e));
        }
        assertEquals(Arrays.asList("R1", "R2", "R3", "R4"), seen,
                "each record exactly once, snapshot order first, gap survivor appended");
    }

    /**
     * AR-04 + barrier policy on the drained segments: watermarks survive (they
     * are never store-backed — dropping them would stall event-time progress
     * on the new gate); a stale barrier of the restored generation (id <= cut)
     * is dropped (it would start a spurious alignment in the rebuilt task's
     * fresh tracker); a live barrier of an in-flight post-restart checkpoint
     * (id > cut) is kept; gap data duplicates of snapshot content are dropped.
     */
    @Test
    void controlEventsSurviveAndStaleBarriersDrop() {
        StreamElement r1 = new StreamRecord<>("R1");
        StreamElement r1SameInstance = r1; // identity duplicate

        List<StreamElement> residual = Arrays.asList(
                new Watermark(100L),
                barrier(CUT),      // stale: belongs to the restored generation
                new StreamRecord<>("R0"));
        List<MaterializedElement> snapshot =
                Collections.singletonList(new MaterializedElement(r1, CUT));
        List<StreamElement> gap = Arrays.asList(
                new Watermark(200L),
                barrier(CUT + 2),  // live: in-flight checkpoint the rebuilt task must join
                r1SameInstance);   // identity duplicate of the snapshot record

        List<StreamElement> segment =
                SupervisionLoop.assembleReplaySegment(snapshot, residual, gap, CUT);

        List<Object> seen = new ArrayList<>();
        for (StreamElement e : segment) {
            if (e.isCheckpointBarrier()) {
                seen.add("barrier-" + ((CheckpointBarrier) e).getId());
            } else if (e.isWatermark()) {
                seen.add("watermark-" + ((Watermark) e).getTimestamp());
            } else {
                seen.add(value(e));
            }
        }
        assertEquals(Arrays.asList("R1", "watermark-100", "watermark-200", "barrier-7"), seen,
                "watermarks and the live barrier survive; stale barrier, residual record "
                        + "and identity gap duplicate are dropped");
    }

    // ==================== CC-02 composition (real partition + gate) ====================

    private InputGate oldGateOver(ResultPartition partition) {
        return new InputGate(Collections.singletonList(new InputChannel(partition)));
    }

    /**
     * End-to-end composition through the production rebuild path: pre-restart
     * content (data record + watermark) is delivered exactly once with the
     * watermark preserved, and live producer writes after the rebuild follow
     * in order. Pre-fix the watermark was dropped (AR-04).
     */
    @Test
    void rebuiltGateDeliversReplayThenControlsThenLive() throws Exception {
        InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-compose");
        ResultPartition partition = new ResultPartition(64);
        partition.setMaterializationPoint(point);
        partition.setCurrentMaterializationEpoch(CUT);

        partition.write(new StreamRecord<>("R1")); // dual write: store(CUT) + queue
        partition.write(new Watermark(100L));      // queue only

        InputGate newGate = SupervisionLoop.rebuildConsumerInputGateWithReplay(
                oldGateOver(partition), CUT, "consumer", 0);
        InputChannel channel = newGate.getChannels().get(0);

        // Replay segment first: the data record, then the forwarded residual
        // watermark (AR-04) — read sequentially, no discard-on-type.
        StreamElement first = channel.read(50, TimeUnit.MILLISECONDS);
        assertNotNull(first, "replay record must be delivered first");
        assertEquals("R1", value(first));
        StreamElement watermark = channel.read(50, TimeUnit.MILLISECONDS);
        assertNotNull(watermark, "the residual watermark must be forwarded to the new gate (AR-04)");
        assertEquals(100L, ((Watermark) watermark).getTimestamp());

        // The surviving producer continues writing to the same partition.
        partition.write(new StreamRecord<>("R2"));
        assertEquals(Collections.singletonList("R2"), drainAll(channel, 5),
                "live records follow the replay segment exactly once");
        assertNull(channel.read(20, TimeUnit.MILLISECONDS), "queue exhausted");
    }

    /**
     * Pre-cut residual records (in-flight at the restored checkpoint) must NOT
     * be re-delivered: they are already reflected in the restored operator
     * state. Post-cut store content is replayed once.
     */
    @Test
    void preCutResidualRecordIsNotRedelivered() throws Exception {
        InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-precut");
        ResultPartition partition = new ResultPartition(64);
        partition.setMaterializationPoint(point);
        partition.setCurrentMaterializationEpoch(3L);
        partition.write(new StreamRecord<>("PRE")); // epoch 3 < cut

        partition.setCurrentMaterializationEpoch(CUT);
        partition.write(new StreamRecord<>("POST")); // epoch 5 >= cut

        InputGate newGate = SupervisionLoop.rebuildConsumerInputGateWithReplay(
                oldGateOver(partition), CUT, "consumer", 0);

        assertEquals(Collections.singletonList("POST"), drainAll(newGate.getChannels().get(0), 5),
                "only the post-cut record is delivered; the pre-cut residual is not re-counted");
    }

    /**
     * No-checkpoint restart (cut = 0): full replay, and residual queue
     * duplicates of store content are dropped.
     */
    @Test
    void noCheckpointFullReplayDropsResidualDuplicates() throws Exception {
        InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-nockpt");
        ResultPartition partition = new ResultPartition(64);
        partition.setMaterializationPoint(point);
        partition.write(new StreamRecord<>("R1"));
        partition.write(new StreamRecord<>("R2"));

        InputGate newGate = SupervisionLoop.rebuildConsumerInputGateWithReplay(
                oldGateOver(partition), 0L, "consumer", 0);

        assertEquals(Arrays.asList("R1", "R2"), drainAll(newGate.getChannels().get(0), 5),
                "full replay, each record exactly once");
    }

    // ==================== REG-07 / AR-05: attach contract ====================

    /**
     * A second attachPendingReplay over an UNCONSUMED segment must fail fast —
     * the replace semantics would silently orphan the pending elements.
     */
    @Test
    void secondAttachOverPendingSegmentFailsFast() throws InterruptedException {
        ResultPartition partition = new ResultPartition(8);
        partition.attachPendingReplay(Collections.singletonList(new StreamRecord<>("p1")));

        assertThrows(StreamException.class,
                () -> partition.attachPendingReplay(Collections.singletonList(new StreamRecord<>("p2"))),
                "a second attach over a pending segment must fail fast (REG-07/AR-05)");

        // The original segment is intact.
        assertEquals("p1", value(partition.read(50, TimeUnit.MILLISECONDS)));

        // After the segment is exhausted, attaching again is legal (fresh
        // generation / new replay batch).
        assertDoesNotThrow(() -> partition.attachPendingReplay(
                Collections.singletonList(new StreamRecord<>("p3"))));
        assertEquals("p3", value(partition.read(50, TimeUnit.MILLISECONDS)));
    }
}
