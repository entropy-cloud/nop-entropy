package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 43, Phase 2: {@link CheckpointSerDe} must serialize/deserialize
 * {@link ChannelState} within {@link TaskEpochSnapshot}, and existing aligned
 * snapshots without channel state must still deserialize (backward compatible).
 *
 * <p>Both the {@code CompletedCheckpoint} path ({@link CheckpointSerDe#serializeCheckpoint})
 * and the {@code EpochManifest} path ({@link CheckpointSerDe#serializeEpochManifest})
 * are exercised, since both share {@link CheckpointSerDe#serializeTaskStateSnapshot}.
 */
class TestCheckpointSerDeChannelState {

    private TaskLocation loc(String v) {
        return new TaskLocation("job-1", "pipe-1", v, 0);
    }

    private TaskEpochSnapshot snapshotWithChannelState() {
        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc("v1"), 5L);
        snap.putOperatorState("operator-0-state", "value");
        ChannelState cs = new ChannelState();
        cs.putRecords(0, java.util.Arrays.asList(
                new StreamRecord<>("in-flight-a"),
                new StreamRecord<>("in-flight-b")
        ));
        cs.putRecords(1, java.util.Arrays.asList(new StreamRecord<>(123)));
        snap.setChannelState(cs);
        return snap;
    }

    private TaskEpochSnapshot snapshotWithoutChannelState() {
        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc("v1"), 5L);
        snap.putOperatorState("operator-0-state", "value");
        return snap;
    }

    @Test
    void testCompletedCheckpointRoundTripsChannelState() {
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc("v1"), snapshotWithChannelState());
        taskStates.put(loc("v2"), snapshotWithoutChannelState());

        CompletedCheckpoint original = CompletedCheckpoint.builder()
                .jobId("job-1").pipelineId("pipe-1")
                .checkpointId(5L).triggerTimestamp(100L).completedTimestamp(200L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates).build();

        byte[] bytes = CheckpointSerDe.serializeCheckpoint(original);
        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(bytes);

        assertNotNull(restored);
        TaskStateSnapshot v1 = restored.getTaskState(loc("v1"));
        assertNotNull(v1);
        assertTrue(v1 instanceof TaskEpochSnapshot, "Should deserialize as TaskEpochSnapshot");
        TaskEpochSnapshot v1epoch = (TaskEpochSnapshot) v1;
        assertNotNull(v1epoch.getChannelState(), "Channel state must round-trip");
        assertEquals(3, v1epoch.getChannelState().getTotalRecordCount());
        assertEquals(2, v1epoch.getChannelState().getRecords(0).size());
        assertEquals("in-flight-a", v1epoch.getChannelState().getRecords(0).get(0).asRecord().getValue());

        // v2 has no channel state — backward compatible. It may deserialize as a
        // plain TaskStateSnapshot (no key-group, no channel state) or a
        // TaskEpochSnapshot with null channel state; either way it must NOT
        // expose channel state.
        TaskStateSnapshot v2 = restored.getTaskState(loc("v2"));
        assertNotNull(v2);
        if (v2 instanceof TaskEpochSnapshot) {
            assertNull(((TaskEpochSnapshot) v2).getChannelState(),
                    "Aligned snapshot must keep null channel state");
        }
        // operator state preserved regardless of snapshot subtype.
        assertEquals("value", v2.getOperatorState("operator-0-state"));
    }

    @Test
    void testEpochManifestRoundTripsChannelState() {
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(loc("v1"), snapshotWithChannelState());

        EpochManifest manifest = new EpochManifest(
                5L, "job-1", "pipe-1", 200L,
                CheckpointType.CHECKPOINT, EpochState.DURABLE,
                taskSnapshots, null, null);

        byte[] bytes = CheckpointSerDe.serializeEpochManifest(manifest);
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(bytes);

        assertNotNull(restored);
        TaskStateSnapshot v1 = restored.getTaskSnapshots().get(loc("v1"));
        assertTrue(v1 instanceof TaskEpochSnapshot);
        TaskEpochSnapshot v1epoch = (TaskEpochSnapshot) v1;
        assertNotNull(v1epoch.getChannelState(), "Channel state must round-trip via EpochManifest");
        assertEquals(3, v1epoch.getChannelState().getTotalRecordCount());
    }

    /**
     * A snapshot with no operator/keyed state but WITH channel state must still
     * serialize/deserialize (channel state is the only content).
     */
    @Test
    void testChannelStateOnlySnapshotRoundTrips() {
        TaskEpochSnapshot snap = new TaskEpochSnapshot(loc("v1"), 5L);
        ChannelState cs = new ChannelState();
        cs.putRecords(0, java.util.Arrays.asList(new StreamRecord<>("only-in-flight")));
        snap.setChannelState(cs);

        Map<String, Object> map = CheckpointSerDe.serializeTaskStateSnapshot(snap);
        TaskStateSnapshot restored = CheckpointSerDe.deserializeTaskStateSnapshot(map, loc("v1"));

        assertTrue(restored instanceof TaskEpochSnapshot);
        assertNotNull(((TaskEpochSnapshot) restored).getChannelState());
        assertEquals(1, ((TaskEpochSnapshot) restored).getChannelState().getTotalRecordCount());
    }
}
