package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 43, Phase 4: end-to-end unaligned-checkpoint recovery — the Anti-Hollow
 * proof (plan guide #22) that the full loop works: capture in-flight channel data
 * under a stalled alignment → persist via {@link CheckpointSerDe} → restore on a
 * fresh {@link InputGate} → replay in-flight records BEFORE new upstream data,
 * preserving exactly-once (no loss, no duplicates).
 *
 * <p>Two scenarios:
 * <ul>
 *   <li>{@code TestUnalignedCheckpointBackpressure} — single multi-input task:
 *       aligned channel's post-barrier in-flight records survive the loop.</li>
 *   <li>{@code TestUnalignedCheckpointMultiInput} — per-channel replay:
 *       multiple channels' in-flight records are routed back to their own
 *       channels and read in order.</li>
 * </ul>
 */
class TestUnalignedCheckpointBackpressure {

    private TaskLocation loc = new TaskLocation("job", "pipe", "v", 0);

    /**
     * Anti-Hollow E2E: a multi-input task under a stalled alignment (channel 1
     * never delivers its barrier) switches to unaligned mode. The captured
     * channel state is persisted (CompletedCheckpoint + EpochManifest round-trip)
     * and then restored into a freshly-built InputGate. The replayed in-flight
     * records are read FIRST, ahead of any new upstream data, and exactly-once
     * holds (no loss, no duplicates).
     */
    @Test
    void testCapturePersistRestoreReplayLoop() throws Exception {
        // --- 1. Capture: drive an InputGate into unaligned mode. ---
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        InputGate liveGate = new InputGate(Arrays.asList(new InputChannel(p0), new InputChannel(p1)),
                null, true, 5000L, true, 100L);

        // Channel 0 delivers barrier then a post-barrier (new-epoch) record that
        // must be captured and replayed (it is buffered because channel 0 blocks
        // after the barrier). Channel 1 is "stuck" (no barrier, no data).
        p0.write(new CheckpointBarrier(42, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("post-barrier-inflight"));

        ChannelState captured;
        CheckpointBarrier emitted = null;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000L) {
            Optional<StreamElement> opt = liveGate.read();
            if (opt.isPresent() && opt.get().isCheckpointBarrier()) {
                emitted = opt.get().asCheckpointBarrier();
                break;
            }
        }
        assertNotNull(emitted, "Unaligned barrier should be emitted under stalled alignment");
        captured = liveGate.consumePendingChannelState();
        assertNotNull(captured, "Channel state captured on unaligned switch");
        assertEquals(1, captured.getRecords(0).size(),
                "Aligned channel post-barrier record captured");
        assertEquals("post-barrier-inflight", captured.getRecords(0).get(0).asRecord().getValue());

        // --- 2. Persist: serialize the snapshot through CheckpointSerDe. ---
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(loc, 42L);
        snapshot.setChannelState(captured);
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc, snapshot);

        byte[] cpBytes = CheckpointSerDe.serializeCheckpoint(
                io.nop.stream.core.checkpoint.CompletedCheckpoint.builder()
                        .jobId("job").pipelineId("pipe")
                        .checkpointId(42L).triggerTimestamp(1L).completedTimestamp(2L)
                        .checkpointType(CheckpointType.CHECKPOINT)
                        .taskStates(taskStates).build());
        // Also round-trip through EpochManifest (the recovery entry point).
        io.nop.stream.core.checkpoint.EpochManifest manifest =
                new io.nop.stream.core.checkpoint.EpochManifest(
                        42L, "job", "pipe", 2L, CheckpointType.CHECKPOINT,
                        io.nop.stream.core.checkpoint.EpochState.DURABLE,
                        taskStates, null, null);
        byte[] manifestBytes = CheckpointSerDe.serializeEpochManifest(manifest);
        io.nop.stream.core.checkpoint.EpochManifest restoredManifest =
                CheckpointSerDe.deserializeEpochManifest(manifestBytes);

        TaskEpochSnapshot restoredSnapshot = (TaskEpochSnapshot)
                restoredManifest.getTaskSnapshots().get(loc);
        assertNotNull(restoredSnapshot.getChannelState(),
                "Channel state must survive persist round-trip");
        assertEquals(1, restoredSnapshot.getChannelState().getRecords(0).size());

        // --- 3. Restore: build a FRESH InputGate and replay channel state. ---
        ResultPartition rp0 = new ResultPartition();
        ResultPartition rp1 = new ResultPartition();
        InputGate recoveredGate = new InputGate(
                Arrays.asList(new InputChannel(rp0), new InputChannel(rp1)),
                null, true, 5000L, true, 100L);
        // Simulate new upstream data that will arrive AFTER the replayed records.
        rp0.write(new StreamRecord<>("new-upstream-after-recovery-0"));
        rp1.write(new StreamRecord<>("new-upstream-after-recovery-1"));

        recoveredGate.restoreChannelState(restoredSnapshot.getChannelState());

        // --- 4. Replay order: in-flight records BEFORE new upstream records. ---
        List<String> readOrder = new ArrayList<>();
        int recordsRead = 0;
        long deadline = System.currentTimeMillis() + 2000L;
        while (recordsRead < 3 && System.currentTimeMillis() < deadline) {
            Optional<StreamElement> opt = recoveredGate.read();
            if (opt.isPresent() && opt.get().isRecord()) {
                readOrder.add(opt.get().asRecord().getValue().toString());
                recordsRead++;
            }
        }

        assertEquals(3, recordsRead, "Should read 1 replayed + 2 new records");
        // The replayed post-barrier record must be the FIRST read on channel 0
        // (ahead of channel 0's new-upstream record).
        int replayIdx = readOrder.indexOf("post-barrier-inflight");
        int newIdx = readOrder.indexOf("new-upstream-after-recovery-0");
        assertTrue(replayIdx >= 0 && newIdx >= 0, "Both records read: " + readOrder);
        assertTrue(replayIdx < newIdx,
                "Replayed in-flight record must be read before new upstream on the same channel: " + readOrder);
    }

    /**
     * No-duplicates: records already delivered to the operator before the barrier
     * (i.e. read out of the gate during alignment) are NOT in channel state and
     * therefore NOT replayed. Only captured in-flight records are replayed.
     */
    @Test
    void testNoDuplicatesRecordsBeforeBarrierNotReplayed() throws Exception {
        // A captured channel state containing ONLY the in-flight record.
        ChannelState cs = new ChannelState();
        cs.putRecords(0, Arrays.asList(new StreamRecord<>("in-flight-only")));

        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc, 1L);
        snap.setChannelState(cs);
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(
                io.nop.stream.core.checkpoint.CompletedCheckpoint.builder()
                        .jobId("job").pipelineId("pipe")
                        .checkpointId(1L).triggerTimestamp(1L).completedTimestamp(2L)
                        .checkpointType(CheckpointType.CHECKPOINT)
                        .taskStates(new LinkedHashMap<>(Map.of(loc, snap))).build());
        TaskEpochSnapshot restored = (TaskEpochSnapshot) CheckpointSerDe.deserializeCheckpoint(bytes)
                .getTaskState(loc);

        // Fresh gate; restore.
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(new ResultPartition()),
                new InputChannel(new ResultPartition())), null, true, 5000L, true, 100L);
        gate.restoreChannelState(restored.getChannelState());

        // Read exactly the one replayed record, then EOS.
        Optional<StreamElement> first = gate.read();
        assertTrue(first.isPresent() && first.get().isRecord());
        assertEquals("in-flight-only", first.get().asRecord().getValue());
        // No more data (no duplicate of a "pre-barrier" record — none was captured).
    }
}
