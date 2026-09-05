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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ==================== P1-09-01 negative tests (skip contract is observable) ====================

    /**
     * P1-09-01: a malformed channel index must NOT fail the whole restore and must be
     * logged (before the fix all skip paths in {@code fromSerializableForm} were
     * silent — in-flight records are the only exactly-once carrier on the unaligned
     * recovery path, so silent skips would downgrade exactly-once to at-least-once
     * without any diagnostic).
     */
    @Test
    void testMalformedChannelIndexIsSkippedWithWarning() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChannelState.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.addAppender(appender);
        appender.start();
        try {
            Map<String, Object> serializable = new LinkedHashMap<>();
            serializable.put("abc", recordEnvelopes("in-flight-a"));
            serializable.put("0", recordEnvelopes("in-flight-b"));

            ChannelState restored = ChannelState.fromSerializableForm(serializable);

            assertFalse(restored.getRecords(0).isEmpty(),
                    "Valid channel must still be restored when a sibling channel index is malformed");
            assertTrue(restored.getAllRecords().containsKey(0));
            assertFalse(restored.getAllRecords().containsKey(-1),
                    "Malformed channel index must not be restored as a channel");
            assertTrue(appender.list.stream().anyMatch(
                            ev -> ev.getFormattedMessage().contains("malformed channel index")),
                    "Malformed channel index skip must be observable via LOG.warn");
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * P1-09-01: an undecodable in-flight record (payload referencing a class that
     * cannot be loaded) must be skipped WITHOUT failing the whole restore, the other
     * records of the same channel must still be restored, and the skip must be
     * observable via LOG.warn with the throwable attached (error-handling.md
     * per-element isolation rule).
     */
    @Test
    void testUndecodableRecordIsSkippedWithWarningAndOthersRestored() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChannelState.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.addAppender(appender);
        appender.start();
        try {
            Map<String, Object> serializable = new LinkedHashMap<>();
            List<Map<String, Object>> records = new ArrayList<>();
            records.add(envelope("STREAM_RECORD", "java.lang.String", "\"good\""));
            records.add(envelope("STREAM_RECORD", "com.example.DoesNotExist", "{}"));
            serializable.put("0", records);

            ChannelState restored = ChannelState.fromSerializableForm(serializable);

            assertEquals(1, restored.getRecords(0).size(),
                    "Undecodable record must be skipped, the decodable one restored");
            assertEquals("good", restored.getRecords(0).get(0).asRecord().getValue());
            assertTrue(appender.list.stream().anyMatch(
                            ev -> ev.getFormattedMessage().contains("undecodable in-flight record")),
                    "Undecodable record skip must be observable via LOG.warn");
            assertTrue(appender.list.stream().anyMatch(ev -> ev.getThrowableProxy() != null),
                    "LOG.warn must carry the throwable (per-element isolation rule)");
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * P1-09-01: a non-List channel value and a non-Map record item are also skip
     * paths — both must be observable, not silent.
     */
    @Test
    void testNonListChannelAndNonMapItemAreSkippedWithWarning() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChannelState.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.addAppender(appender);
        appender.start();
        try {
            Map<String, Object> serializable = new LinkedHashMap<>();
            serializable.put("0", "not-a-list");
            List<Object> records = new ArrayList<>();
            records.add("not-a-map");
            serializable.put("1", records);

            ChannelState restored = ChannelState.fromSerializableForm(serializable);

            assertTrue(restored.isEmpty(), "Nothing restorable must be restored");
            assertTrue(appender.list.stream().anyMatch(
                            ev -> ev.getFormattedMessage().contains("value is not a List")),
                    "Non-List channel skip must be observable via LOG.warn");
            assertTrue(appender.list.stream().anyMatch(
                            ev -> ev.getFormattedMessage().contains("item is not a Map")),
                    "Non-Map item skip must be observable via LOG.warn");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static List<Map<String, Object>> recordEnvelopes(String... values) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String v : values) {
            out.add(envelope("STREAM_RECORD", "java.lang.String", "\"" + v + "\""));
        }
        return out;
    }

    private static Map<String, Object> envelope(String type, String valueType, Object payload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("epochId", 0L);
        m.put("type", type);
        m.put("valueType", valueType);
        m.put("payload", payload);
        m.put("timestamp", 0L);
        m.put("hasTimestamp", false);
        return m;
    }
}
