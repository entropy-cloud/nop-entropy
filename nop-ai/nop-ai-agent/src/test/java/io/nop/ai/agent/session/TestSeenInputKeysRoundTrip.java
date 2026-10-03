package io.nop.ai.agent.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2288 (WI7): seen-input dedup keys — Writer/Reader roundtrip (including
 * legacy-format tolerance) and FIFO eviction bound.
 */
public class TestSeenInputKeysRoundTrip {

    @Test
    void roundTripThroughWriterAndReader() {
        AgentSession session = AgentSession.create("s-rt", "agent");
        assertTrue(session.markInputSeen("k1"));
        assertTrue(session.markInputSeen("k2"));
        assertTrue(session.markInputSeen(null)); // no key = no dedup, always "fresh"

        String json = SessionFileWriter.serialize(session);
        AgentSession restored = SessionFileReader.deserialize(json);
        assertEquals(2, restored.getSeenInputKeys().size());
        assertTrue(restored.hasSeenInput("k1"));
        assertTrue(restored.hasSeenInput("k2"));
        // dedup still works after restore
        assertFalse(restored.markInputSeen("k1"));
    }

    @Test
    void legacyFormatWithoutFieldReadsAsEmpty() {
        AgentSession session = AgentSession.create("s-legacy", "agent");
        session.markInputSeen("k1");
        String json = SessionFileWriter.serialize(session);
        // a legacy reader/writer pair without the field — the field is simply
        // absent from the serialized map
        String withoutField = json.replace("\"seenInputKeys\":{\"k1\":", "\"removed\":{\"k1\":");
        AgentSession restored = SessionFileReader.deserialize(withoutField);
        assertEquals(0, restored.getSeenInputKeys().size(),
                "absent field → empty dedup keys (backward compatible)");
        assertTrue(restored.markInputSeen("k1"), "fresh after legacy restore");
    }

    @Test
    void fifoEvictionBound() {
        AgentSession session = AgentSession.create("s-fifo", "agent");
        for (int i = 0; i < AgentSession.MAX_SEEN_INPUT_KEYS + 10; i++) {
            assertTrue(session.markInputSeen("k" + i));
        }
        assertEquals(AgentSession.MAX_SEEN_INPUT_KEYS, session.getSeenInputKeys().size(),
                "bounded at MAX_SEEN_INPUT_KEYS");
        assertFalse(session.hasSeenInput("k0"), "eldest key evicted");
        assertTrue(session.hasSeenInput("k" + (AgentSession.MAX_SEEN_INPUT_KEYS + 9)),
                "newest keys retained");
    }
}
