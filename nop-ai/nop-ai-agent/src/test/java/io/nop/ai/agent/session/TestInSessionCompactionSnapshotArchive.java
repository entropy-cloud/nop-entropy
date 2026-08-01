package io.nop.ai.agent.session;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InSessionCompactionSnapshotArchive} — the per-session,
 * in-memory, per-compaction-event snapshot archive (design §8.3 Decision B).
 */
public class TestInSessionCompactionSnapshotArchive {

    private List<ChatMessage> msgs(String... contents) {
        return Arrays.stream(contents).map(ChatUserMessage::new).collect(java.util.stream.Collectors.toList());
    }

    @Test
    void putReturnsNonEmptySnapshotIdAndArchivesCopy() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        List<ChatMessage> original = msgs("a", "b", "c");

        String snapshotId = archive.put(original);

        assertNotNull(snapshotId, "put must return a non-null snapshotId");
        assertFalse(snapshotId.isEmpty(), "snapshotId must not be empty");
        assertTrue(snapshotId.startsWith("snap:s1:"), "snapshotId should be traceable to the session");
        assertEquals(1, archive.size(), "archive must hold one entry after a single put");
        assertTrue(archive.contains(snapshotId), "contains must report the just-put id");
    }

    @Test
    void getRetrievesExactArchivedMessages() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        List<ChatMessage> original = msgs("hello", "world", "again");

        String snapshotId = archive.put(original);
        List<ChatMessage> retrieved = archive.get(snapshotId);

        assertNotNull(retrieved);
        assertEquals(original, retrieved, "get must return the exact archived message list");
    }

    @Test
    void putIsPerEventAddressingNotContentDedup() {
        // Design §8.3 Decision B: per-compaction-event addressing, NOT content
        // hash dedup. The same content archived twice must yield two distinct
        // ids (contrast the §8.2 content-addressed archive which dedups).
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        List<ChatMessage> identical = msgs("same", "content");

        String id1 = archive.put(identical);
        String id2 = archive.put(identical);

        assertNotEquals(id1, id2, "per-event addressing: identical content must yield distinct ids");
        assertEquals(2, archive.size(), "both events must be archived separately");
        assertEquals(identical, archive.get(id1));
        assertEquals(identical, archive.get(id2));
    }

    @Test
    void putDefensiveCopyArchivedOriginalIsImmutableToLaterMutation() {
        // Reversibility guarantee: mutating the live message list after put
        // must NOT corrupt the archived original.
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        List<ChatMessage> live = new java.util.ArrayList<>(msgs("a", "b"));
        String snapshotId = archive.put(live);

        live.clear(); // simulate the coordinator replacing messages post-compaction

        List<ChatMessage> retrieved = archive.get(snapshotId);
        assertEquals(2, retrieved.size(), "archived original must be unaffected by later live-list mutation");
    }

    @Test
    void putRejectsNullMessagesFailFast() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        assertThrows(IllegalArgumentException.class, () -> archive.put(null),
                "null messages must be rejected fail-fast (Minimum Rules #24), not silently stored");
    }

    @Test
    void putRejectsEmptyMessagesFailFast() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        assertThrows(IllegalArgumentException.class, () -> archive.put(Collections.emptyList()),
                "empty messages must be rejected fail-fast — an empty entry would masquerade as 'compaction produced nothing'");
    }

    @Test
    void getReturnsNullForUnknownSnapshotId() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        archive.put(msgs("a"));
        assertNull(archive.get("snap:s1:0:999"),
                "unknown snapshotId must return null (legitimate 'no such snapshot'), not throw");
    }

    @Test
    void getRejectsNullSnapshotId() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        assertThrows(NullPointerException.class, () -> archive.get(null));
    }

    @Test
    void agentSessionLazyInitAndRetrievalRoundTrip() {
        // The session hosts the archive; the coordinator write side and the
        // audit read side reach the same instance through the session.
        AgentSession session = AgentSession.create("s1", "agent");
        assertNull(session.getCompactionSnapshotArchive(),
                "archive must be null until first materialisation");

        ICompactionSnapshotArchive archive = session.getOrCreateCompactionSnapshotArchive();
        assertNotNull(archive);
        assertSameInstance(archive, session.getOrCreateCompactionSnapshotArchive(),
                "repeated getOrCreate must return the same materialised instance");

        // ChatMessage uses identity equality, so archive and compare the SAME
        // list instance (the archive's defensive copy preserves references).
        List<ChatMessage> original = msgs("x", "y");
        String snapshotId = archive.put(original);
        List<ChatMessage> retrieved = session.getCompactionSnapshotArchive().get(snapshotId);
        assertEquals(original, retrieved,
                "read-back via the session must retrieve the archived original (same references)");
    }

    private static void assertSameInstance(Object a, Object b, String msg) {
        assertTrue(a == b, msg);
    }
}
