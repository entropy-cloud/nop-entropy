package io.nop.stream.core.checkpoint;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S-2 (2026-09-01 core audit, plan 0938-2): per-element isolation must cover
 * envelope RECONSTRUCTION, not just decode. A snapshot whose envelope fields
 * are malformed (e.g. a non-String "type") must skip only that in-flight record
 * (LOG.warn, best-effort) instead of aborting the whole channel-state restore —
 * the P1-09-01 rule the surrounding code documents.
 */
class TestChannelStateRestoreIsolation {

    private Map<String, Object> envelope(String valueType, Object payload) {
        Map<String, Object> env = new HashMap<>();
        env.put("epochId", 1L);
        env.put("type", "STREAM_RECORD");
        env.put("valueType", valueType);
        env.put("payload", payload);
        env.put("timestamp", 0L);
        env.put("hasTimestamp", false);
        env.put("outputTagId", null);
        return env;
    }

    /**
     * A channel entry with one valid and one malformed record (non-String "type")
     * restores the valid record and skips the malformed one — the restore itself
     * must not throw.
     */
    @Test
    void testMalformedEnvelopeFieldSkipsOnlyThatRecord() {
        Map<String, Object> serializable = new HashMap<>();

        List<Object> records = new ArrayList<>();
        records.add(envelope("java.lang.String", "\"valid-record\"")); // JSON-encoded payload
        Map<String, Object> malformed = envelope("java.lang.String", "broken");
        malformed.put("type", 42); // non-String type: CCE inside mapToEnvelope
        records.add(malformed);

        serializable.put("0", records);

        ChannelState restored = ChannelState.fromSerializableForm(serializable);

        assertNotNull(restored);
        List<StreamElement> ch0 = restored.getRecords(0);
        assertEquals(1, ch0.size(), "only the valid record survives; the malformed one is skipped (best-effort)");
        assertEquals("valid-record", ch0.get(0).asRecord().getValue()); // decoded via JsonTool
    }

    /**
     * A second channel's valid records survive even when another channel contains
     * a malformed envelope — isolation is per element, not per restore.
     */
    @Test
    void testOtherChannelsSurviveMalformedRecord() {
        Map<String, Object> serializable = new HashMap<>();

        Map<String, Object> malformedEnv = envelope("java.lang.String", "broken");
        malformedEnv.put("type", Arrays.asList("not", "a", "string"));
        List<Object> badChannel = new ArrayList<>();
        badChannel.add(malformedEnv);

        List<Object> goodChannel = new ArrayList<>();
        goodChannel.add(envelope("java.lang.Integer", "7")); // JSON-encoded payload

        serializable.put("0", badChannel);
        serializable.put("1", goodChannel);

        ChannelState restored = ChannelState.fromSerializableForm(serializable);

        assertTrue(restored.getRecords(0).isEmpty(), "malformed record skipped");
        assertEquals(1, restored.getRecords(1).size());
        assertEquals(7, restored.getRecords(1).get(0).asRecord().getValue());
    }

    /**
     * Sanity counterpart: a fully well-formed entry restores every record — the
     * isolation guard must not over-skip.
     */
    @Test
    void testWellFormedEntriesRestoreFully() {
        ChannelState original = new ChannelState();
        original.putRecords(3, Arrays.asList(
                new StreamRecord<>("a"),
                new StreamRecord<>("b")));

        ChannelState restored = ChannelState.fromSerializableForm(original.toSerializableForm());

        assertEquals(2, restored.getRecords(3).size());
        assertEquals("a", restored.getRecords(3).get(0).asRecord().getValue());
        assertEquals("b", restored.getRecords(3).get(1).asRecord().getValue());
    }
}
