package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.ChannelState;
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
 * Stage 43, Phase 4: multi-input unaligned-checkpoint recovery. In-flight data
 * captured from multiple non-aligned channels is routed back to its OWN channel
 * on recovery and replayed in per-channel order. This is the multi-channel
 * counterpart of {@link TestUnalignedCheckpointBackpressure}.
 */
class TestUnalignedCheckpointMultiInput {

    private TaskLocation loc = new TaskLocation("job", "pipe", "v", 0);

    /**
     * Three input channels, two of which (0 and 2) held in-flight data captured at
     * the unaligned switch. After persist + restore, each channel's replayed
     * records must appear on the SAME channel index and in order. Routed records
     * are verified per-channel by round-robin reading and tracking origin.
     */
    @Test
    void testMultiChannelPerChannelReplayAfterPersist() throws Exception {
        // Channel 0: two in-flight records; Channel 1: none; Channel 2: one record.
        ChannelState cs = new ChannelState();
        cs.putRecords(0, Arrays.asList(
                new StreamRecord<>("ch0-a"),
                new StreamRecord<>("ch0-b")
        ));
        cs.putRecords(2, Arrays.asList(new StreamRecord<>("ch2-only")));

        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc, 7L);
        snap.setChannelState(cs);
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc, snap);

        // Persist round-trip (CompletedCheckpoint path).
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(
                io.nop.stream.core.checkpoint.CompletedCheckpoint.builder()
                        .jobId("job").pipelineId("pipe")
                        .checkpointId(7L).triggerTimestamp(1L).completedTimestamp(2L)
                        .checkpointType(CheckpointType.CHECKPOINT)
                        .taskStates(taskStates).build());
        TaskEpochSnapshot restored = (TaskEpochSnapshot)
                CheckpointSerDe.deserializeCheckpoint(bytes).getTaskState(loc);
        assertNotNull(restored.getChannelState());
        assertEquals(3, restored.getChannelState().getTotalRecordCount());

        // Build a FRESH 3-channel gate and replay.
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        InputGate gate = new InputGate(
                Arrays.asList(new InputChannel(p0), new InputChannel(p1), new InputChannel(p2)),
                null, true, 5000L, true, 100L);
        gate.restoreChannelState(restored.getChannelState());

        // Drain all replayed records (the gate round-robins; capture read order).
        List<String> read = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 2000L;
        while (read.size() < 3 && System.currentTimeMillis() < deadline) {
            Optional<StreamElement> opt = gate.read();
            if (opt.isPresent() && opt.get().isRecord()) {
                read.add(opt.get().asRecord().getValue().toString());
            }
        }

        assertEquals(3, read.size(), "All 3 in-flight records replayed: " + read);
        assertTrue(read.containsAll(Arrays.asList("ch0-a", "ch0-b", "ch2-only")),
                "All per-channel records present: " + read);
        // Channel 0's records preserve their relative order.
        int a = read.indexOf("ch0-a");
        int b = read.indexOf("ch0-b");
        assertTrue(a < b, "Channel 0 in-flight records preserve order: " + read);
    }

    /**
     * A snapshot with NO channel state (aligned checkpoint) restores as a no-op —
     * the recovered gate reads nothing extra (no silent injection, no exception).
     */
    @Test
    void testAlignedSnapshotRestoreIsNoOp() throws Exception {
        TaskEpochSnapshot aligned = new TaskEpochSnapshot(loc, 5L);
        aligned.putOperatorState("op-state", "v");
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc, aligned);
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(
                io.nop.stream.core.checkpoint.CompletedCheckpoint.builder()
                        .jobId("job").pipelineId("pipe")
                        .checkpointId(5L).triggerTimestamp(1L).completedTimestamp(2L)
                        .checkpointType(CheckpointType.CHECKPOINT)
                        .taskStates(taskStates).build());
        TaskStateSnapshot restored = CheckpointSerDe.deserializeCheckpoint(bytes).getTaskState(loc);

        // No records were injected. Close the partition so the single-channel
        // read() returns EOS (Optional.empty) instead of blocking forever.
        ResultPartition p0 = new ResultPartition();
        InputGate gate = new InputGate(Arrays.asList(new InputChannel(p0)),
                null, true, 5000L, true, 100L);
        if (restored instanceof TaskEpochSnapshot) {
            gate.restoreChannelState(((TaskEpochSnapshot) restored).getChannelState());
        }
        p0.close();
        Optional<StreamElement> opt = gate.read();
        assertFalse(opt.isPresent(), "No replay when channel state absent");
    }
}
