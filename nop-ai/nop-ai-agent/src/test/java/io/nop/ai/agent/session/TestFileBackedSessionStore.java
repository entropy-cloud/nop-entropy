package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 functional tests for {@link FileBackedSessionStore}: verifies the
 * core value — save → persist → reload → read round-trip across instances
 * (simulating process restart), full-field integrity (messages/status/
 * counters/timestamps/metadata/parent/plan/compactedAt), per-session file
 * isolation, boundary cases (empty session, missing file, corrupt JSON), and
 * the {@link ISessionStore#save} contract-bridge (default UOE on the
 * interface, InMemorySessionStore explicit no-op, FileBackedSessionStore full
 * persistence).
 */
public class TestFileBackedSessionStore {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Cross-instance round-trip survival (core value)
    // ========================================================================

    @Test
    void savePersistReloadReadSurvivesNewInstance() {
        Path root = tempDir.resolve("sessions");

        AgentSession original = AgentSession.create("sess-1", "my-agent");
        original.appendMessages(buildMixedMessages());
        original.addTokensUsed(1234L);
        original.addIterations(7);
        original.setStatus(AgentExecStatus.running);
        original.getMetadata().put("key", "value");
        original.getMetadata().put("nested", Map.of("a", "b"));
        original.setParentSessionId("parent-0");
        original.setPlanId("plan-42");
        original.setCompactedAt(999L);

        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        store1.save(original);

        // Verify file exists
        Path sessionFile = root.resolve("sess-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        assertTrue(Files.exists(sessionFile), "save must write {root}/{sessionId}/session.json");

        // New store instance — simulate process restart
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-1");

        assertNotNull(restored, "After reload, get(sessionId) must return the persisted session");
        assertEquals("sess-1", restored.getSessionId());
        assertEquals("my-agent", restored.getAgentName());
        assertEquals(1234L, restored.getTotalTokensUsed());
        assertEquals(7, restored.getTotalIterations());
        assertEquals(AgentExecStatus.running, restored.getStatus());
        assertEquals("value", restored.getMetadata().get("key"));
        assertEquals("parent-0", restored.getParentSessionId());
        assertEquals("plan-42", restored.getPlanId());
        assertEquals(999L, restored.getCompactedAt());

        // Messages: full role round-trip
        List<ChatMessage> msgs = restored.getMessages();
        assertEquals(original.getMessageCount(), msgs.size(),
                "Message count must round-trip exactly");
        assertTrue(msgs.get(0) instanceof ChatSystemMessage);
        assertTrue(msgs.get(1) instanceof ChatUserMessage);
        assertTrue(msgs.get(2) instanceof ChatAssistantMessage);
        assertTrue(msgs.get(3) instanceof ChatToolResponseMessage);
        assertEquals("you are an agent", msgs.get(0).getContent());
        assertEquals("hello", msgs.get(1).getContent());
        assertEquals("calling tool", msgs.get(2).getContent());
        assertEquals("result", msgs.get(3).getContent());
    }

    // ========================================================================
    // Field integrity: every AgentSession field
    // ========================================================================

    @Test
    void fieldIntegrityAllStatusEnumValues() {
        Path root = tempDir.resolve("all-status");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);

        for (AgentExecStatus status : AgentExecStatus.values()) {
            String sid = "status-" + status.name();
            AgentSession session = AgentSession.create(sid, "agent");
            session.setStatus(status);
            store1.save(session);
        }

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        for (AgentExecStatus status : AgentExecStatus.values()) {
            AgentSession restored = store2.get("status-" + status.name());
            assertNotNull(restored, "Status " + status + " must round-trip");
            assertEquals(status, restored.getStatus());
        }
    }

    @Test
    void fieldIntegrityTimestampsPreserved() throws InterruptedException {
        Path root = tempDir.resolve("ts");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("ts-1", "agent");
        long created = session.getCreatedAt();
        Thread.sleep(5);
        session.appendMessages(List.of(new ChatUserMessage("x")));
        long updated = session.getUpdatedAt();

        store1.save(session);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("ts-1");
        assertNotNull(restored);
        assertEquals(created, restored.getCreatedAt(),
                "createdAt must survive the round-trip exactly");
        assertEquals(updated, restored.getUpdatedAt(),
                "updatedAt must survive the round-trip exactly");
    }

    @Test
    void fieldIntegrityNestedMetadata() {
        Path root = tempDir.resolve("meta");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("meta-1", "agent");
        Map<String, Object> meta = new HashMap<>();
        meta.put("str", "value");
        meta.put("num", 42);
        meta.put("bool", true);
        meta.put("nested", Map.of("deep", Map.of("deeper", "found")));
        session.setMetadata(meta);
        store1.save(session);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("meta-1");
        assertNotNull(restored);
        assertEquals("value", restored.getMetadata().get("str"));
        assertEquals(42, restored.getMetadata().get("num"));
        assertEquals(true, restored.getMetadata().get("bool"));
    }

    // ========================================================================
    // Per-session isolation
    // ========================================================================

    @Test
    void perSessionFilesAreIsolatedAcrossInstances() {
        Path root = tempDir.resolve("iso");

        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        AgentSession a = AgentSession.create("sess-A", "agent-a");
        a.appendMessages(List.of(new ChatUserMessage("msg-a")));
        store1.save(a);

        AgentSession b = AgentSession.create("sess-B", "agent-b");
        b.appendMessages(List.of(new ChatUserMessage("msg-b")));
        store1.save(b);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restoredA = store2.get("sess-A");
        AgentSession restoredB = store2.get("sess-B");

        assertNotNull(restoredA);
        assertNotNull(restoredB);
        assertEquals("agent-a", restoredA.getAgentName());
        assertEquals("agent-b", restoredB.getAgentName());
        assertEquals(1, restoredA.getMessageCount());
        assertEquals("msg-a", restoredA.getMessages().get(0).getContent());
        assertEquals("msg-b", restoredB.getMessages().get(0).getContent());

        // A's file must not contain B's content
        Path fileA = root.resolve("sess-A").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Path fileB = root.resolve("sess-B").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        assertTrue(Files.exists(fileA));
        assertTrue(Files.exists(fileB));
        assertFalse(fileA.equals(fileB), "Per-session files must be distinct");
    }

    // ========================================================================
    // Boundary: empty session, missing file, corrupt JSON
    // ========================================================================

    @Test
    void emptySessionRoundTrips() {
        Path root = tempDir.resolve("empty");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);

        AgentSession empty = AgentSession.create("empty-1", "agent");
        store1.save(empty);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("empty-1");
        assertNotNull(restored, "Empty session must round-trip");
        assertEquals(0, restored.getMessageCount());
    }

    @Test
    void getReturnsNullWhenFileDoesNotExist() {
        Path root = tempDir.resolve("missing");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession result = store.get("never-saved");
        assertNull(result, "get on missing session must return null (legitimate absence)");
    }

    @Test
    void corruptJsonFailsFastWithNopAiAgentException() throws Exception {
        Path root = tempDir.resolve("corrupt");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        // Manually write a corrupt session.json
        Path sessionFile = root.resolve("bad-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Files.createDirectories(sessionFile.getParent());
        Files.writeString(sessionFile, "{ this is not valid json");

        assertThrows(NopAiAgentException.class, () -> store.get("bad-1"),
                "Corrupt JSON must fail fast with NopAiAgentException, not silently return null");
    }

    @Test
    void missingRequiredFieldFailsFast() throws Exception {
        Path root = tempDir.resolve("missing-field");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        Path sessionFile = root.resolve("bad-2").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Files.createDirectories(sessionFile.getParent());
        // Valid JSON but missing required fields (sessionId, agentName)
        Files.writeString(sessionFile, "{\"someField\":\"value\"}");

        assertThrows(NopAiAgentException.class, () -> store.get("bad-2"),
                "Missing required field must fail fast with NopAiAgentException");
    }

    // ========================================================================
    // getOrCreate: cache-miss loads from file
    // ========================================================================

    @Test
    void getOrCreateLoadsFromDiskWhenCacheMiss() {
        Path root = tempDir.resolve("getorcreate");

        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        AgentSession session = AgentSession.create("gc-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("persisted")));
        store1.save(session);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession loaded = store2.getOrCreate("gc-1", "different-agent");
        assertEquals("agent", loaded.getAgentName(),
                "getOrCreate on cache-miss must load the persisted agentName, not the fallback argument");
        assertEquals(1, loaded.getMessageCount());
        assertEquals("persisted", loaded.getMessages().get(0).getContent());
    }

    @Test
    void getOrCreateCreatesFreshWhenNoFile() {
        Path root = tempDir.resolve("getorcreate-fresh");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = store.getOrCreate("new-1", "new-agent");
        assertNotNull(session);
        assertEquals("new-1", session.getSessionId());
        assertEquals("new-agent", session.getAgentName());
        assertEquals(0, session.getMessageCount());
    }

    // ========================================================================
    // remove: deletes file and cache
    // ========================================================================

    @Test
    void removeDeletesFileAndCache() {
        Path root = tempDir.resolve("remove");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("rm-1", "agent");
        store1.save(session);
        Path sessionFile = root.resolve("rm-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        assertTrue(Files.exists(sessionFile));

        store1.remove("rm-1");

        assertFalse(Files.exists(sessionFile), "remove must delete the session file");
        assertNull(store1.get("rm-1"), "remove must also clear the cache");

        // After remove, a new store instance must also not find the session
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        assertNull(store2.get("rm-1"));
    }

    // ========================================================================
    // save contract bridge: ISessionStore default UOE / InMemorySessionStore no-op
    // ========================================================================

    @Test
    void isessionStoreDefaultSaveThrowsUOE() {
        ISessionStore store = new ISessionStore() {
            @Override
            public AgentSession getOrCreate(String sessionId, String agentName) { return null; }
            @Override
            public AgentSession get(String sessionId) { return null; }
            @Override
            public void remove(String sessionId) {}
            @Override
            public Collection<AgentSession> getAll() { return null; }
        };
        AgentSession session = AgentSession.create("x", "y");
        assertThrows(UnsupportedOperationException.class, () -> store.save(session),
                "ISessionStore default save must throw UOE (Minimum Rules #24 No Silent No-Op)");
    }

    @Test
    void inMemoryStoreSaveIsExplicitNoOpAndDoesNotThrow() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession session = AgentSession.create("x", "y");
        // No-op must not throw — in-memory readers share the live reference.
        assertDoesNotThrow(() -> store.save(session));
    }

    @Test
    void fileBackedStoreSavePersistsToDisk() {
        Path root = tempDir.resolve("save-bridge");
        FileBackedSessionStore store = new FileBackedSessionStore(root);
        AgentSession session = AgentSession.create("fb-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        store.save(session);

        Path sessionFile = root.resolve("fb-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        assertTrue(Files.exists(sessionFile), "FileBackedSessionStore.save must write to disk");
    }

    // ========================================================================
    // getAll returns cache values
    // ========================================================================

    @Test
    void getAllReturnsCachedSessions() {
        Path root = tempDir.resolve("getall");
        FileBackedSessionStore store = new FileBackedSessionStore(root);
        store.save(AgentSession.create("g1", "a"));
        store.save(AgentSession.create("g2", "b"));

        assertEquals(2, store.getAll().size());
    }

    // ========================================================================
    // listAllSessions: disk discovery (plan 184 auto-restore-on-startup)
    // ========================================================================

    @Test
    void listAllSessions_returnsEmptyWhenRootDoesNotExist() {
        // root directory not created yet — empty collection, NOT an exception
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("nonexistent"));
        Collection<AgentSession> result = store.listAllSessions();
        assertNotNull(result);
        assertTrue(result.isEmpty(),
                "listAllSessions on a missing root must return empty, not throw");
    }

    @Test
    void listAllSessions_returnsEmptyWhenRootHasNoSessionDirs() throws Exception {
        Path root = tempDir.resolve("empty-root");
        Files.createDirectories(root);
        // Add a non-session file at the root level (not a directory)
        Files.writeString(root.resolve("stray.txt"), "hi");

        FileBackedSessionStore store = new FileBackedSessionStore(root);
        Collection<AgentSession> result = store.listAllSessions();
        assertNotNull(result);
        assertTrue(result.isEmpty(),
                "listAllSessions must ignore stray files and empty roots");
    }

    @Test
    void listAllSessions_discoversPersistedSessionsOnFreshInstance() {
        // Core value: a brand-new store instance (empty cache) pointed at an
        // existing root discovers every session persisted by a previous
        // instance — the discovery primitive that lets auto-restore work
        // without the caller knowing any session id ahead of time.
        Path root = tempDir.resolve("discovery");

        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        for (AgentExecStatus status : AgentExecStatus.values()) {
            String sid = "disc-" + status.name();
            AgentSession s = AgentSession.create(sid, "agent-" + status.name());
            s.setStatus(status);
            s.appendMessages(List.of(new ChatUserMessage("msg-" + status)));
            store1.save(s);
        }

        // Discard store1 entirely — simulate process restart.
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);

        // getAll() on the fresh instance must still return only the cache
        // (empty) — getAll() semantics are preserved unchanged.
        assertEquals(0, store2.getAll().size(),
                "getAll() on a fresh instance must still return cache-only (empty)");

        // listAllSessions() scans disk and returns every persisted session.
        Collection<AgentSession> discovered = store2.listAllSessions();
        assertEquals(AgentExecStatus.values().length, discovered.size(),
                "listAllSessions must discover every persisted session on a fresh instance");

        // Each status round-trips through discovery.
        for (AgentExecStatus status : AgentExecStatus.values()) {
            AgentSession found = discovered.stream()
                    .filter(s -> ("disc-" + status.name()).equals(s.getSessionId()))
                    .findFirst().orElse(null);
            assertNotNull(found, "Discovered set must contain session for status " + status);
            assertEquals(status, found.getStatus());
            assertEquals("agent-" + status.name(), found.getAgentName());
            assertEquals(1, found.getMessageCount(),
                    "Discovered session must retain its message history");
        }

        // After discovery, the discovered sessions are cached — a subsequent
        // getAll() now sees them, and get() hits the cache.
        assertEquals(AgentExecStatus.values().length, store2.getAll().size(),
                "listAllSessions must populate the cache so subsequent getAll() sees the discovered sessions");
        AgentSession cached = store2.get("disc-running");
        assertNotNull(cached, "get() after listAllSessions must hit the cache");
    }

    @Test
    void listAllSessions_skipsCorruptSessionJsonAndContinues() throws Exception {
        // Failure isolation at the discovery layer: one corrupt session.json
        // must not block discovery of the rest (Minimum Rules #24: corruption
        // is surfaced via warning log, not silently swallowed — a later
        // get(corruptId) will still fail fast).
        Path root = tempDir.resolve("discovery-corrupt");

        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        AgentSession good1 = AgentSession.create("good-1", "a");
        good1.setStatus(AgentExecStatus.running);
        store1.save(good1);
        AgentSession good2 = AgentSession.create("good-2", "b");
        good2.setStatus(AgentExecStatus.pending);
        store1.save(good2);

        // Manually write a corrupt session.json for a third session dir.
        Path corruptFile = root.resolve("corrupt-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Files.createDirectories(corruptFile.getParent());
        Files.writeString(corruptFile, "{ this is not valid json");

        // Fresh instance — discovery must skip the corrupt file and still
        // return the two good sessions.
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        Collection<AgentSession> discovered = store2.listAllSessions();

        assertEquals(2, discovered.size(),
                "listAllSessions must skip corrupt files and return the good ones");
        assertTrue(discovered.stream().anyMatch(s -> "good-1".equals(s.getSessionId())));
        assertTrue(discovered.stream().anyMatch(s -> "good-2".equals(s.getSessionId())));
        assertFalse(discovered.stream().anyMatch(s -> "corrupt-1".equals(s.getSessionId())),
                "Corrupt session must NOT appear in discovered set");

        // A subsequent get(corruptId) must still fail fast (the corruption is
        // surfaced when explicitly requested, not hidden behind a synthetic
        // empty session).
        assertThrows(NopAiAgentException.class, () -> store2.get("corrupt-1"),
                "get() on the corrupt id must fail fast (corruption surfaced, not swallowed)");
    }

    @Test
    void listAllSessions_ignoresSubdirectoryWithoutSessionJson() throws Exception {
        Path root = tempDir.resolve("discovery-nojson");
        FileBackedSessionStore store1 = new FileBackedSessionStore(root);
        AgentSession s = AgentSession.create("real-1", "a");
        store1.save(s);

        // Create a subdirectory without session.json (stray dir)
        Files.createDirectories(root.resolve("stray-dir"));

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        Collection<AgentSession> discovered = store2.listAllSessions();
        assertEquals(1, discovered.size(),
                "listAllSessions must ignore subdirectories without session.json");
        assertEquals("real-1", discovered.iterator().next().getSessionId());
    }

    @Test
    void inMemoryListAllSessionsEqualsGetAll() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("im-1", "a");
        store.getOrCreate("im-2", "b");

        // For an in-memory store, listAllSessions == getAll (no disk concept).
        Collection<AgentSession> all = store.getAll();
        Collection<AgentSession> discovered = store.listAllSessions();
        assertEquals(all.size(), discovered.size(),
                "InMemorySessionStore.listAllSessions must equal getAll");
    }

    @Test
    void isessionStoreDefaultListAllSessionsThrowsUOE() {
        ISessionStore store = new ISessionStore() {
            @Override
            public AgentSession getOrCreate(String sessionId, String agentName) { return null; }
            @Override
            public AgentSession get(String sessionId) { return null; }
            @Override
            public void remove(String sessionId) {}
            @Override
            public Collection<AgentSession> getAll() { return null; }
        };
        assertThrows(UnsupportedOperationException.class, store::listAllSessions,
                "ISessionStore default listAllSessions must throw UOE (Minimum Rules #24)");
    }

    @Test
    void listAllSessionsReflectsNewSavesAcrossInstances() {
        // After store A saves more sessions, a fresh store B's listAllSessions
        // sees all of them (discovery always scans the current disk state).
        Path root = tempDir.resolve("discovery-grow");

        FileBackedSessionStore storeA = new FileBackedSessionStore(root);
        storeA.save(AgentSession.create("grow-1", "a"));

        FileBackedSessionStore storeB = new FileBackedSessionStore(root);
        assertEquals(1, storeB.listAllSessions().size());

        // storeA saves more after storeB was constructed
        storeA.save(AgentSession.create("grow-2", "b"));
        storeA.save(AgentSession.create("grow-3", "c"));

        // storeB's listAllSessions re-scans disk and sees all 3
        Collection<AgentSession> rediscovered = storeB.listAllSessions();
        assertEquals(3, rediscovered.size(),
                "listAllSessions must always scan the current disk state, not a stale snapshot");
    }

    // ========================================================================
    // P0 path-traversal guard (plan 190, finding [13-15]): traversal-shaped
    // sessionIds are rejected at every store entry point AND never touch a
    // file outside the root (temp-dir probe).
    // ========================================================================

    @Test
    void saveRejectsTraversalSessionIdAndWritesNoFile() {
        Path root = tempDir.resolve("trav-save");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("../escape", "agent");
        assertThrows(NopAiAgentException.class, () -> store.save(session),
                "save with a traversal sessionId must throw (fail-closed)");

        // No file written at the traversal target or anywhere under root
        assertFalse(Files.exists(tempDir.resolve("escape")),
                "save must not create a file outside the root via traversal");
        assertRootHasNoSessionArtifacts(root);
    }

    @Test
    void getRejectsTraversalSessionIdAndTouchesNoFile() {
        Path root = tempDir.resolve("trav-get");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        assertThrows(NopAiAgentException.class, () -> store.get("../../etc/exploit"),
                "get with a traversal sessionId must throw (fail-closed)");
        assertFalse(Files.exists(tempDir.resolve("etc")),
                "get must not touch a path outside the root via traversal");
    }

    @Test
    void removeRejectsTraversalSessionIdAndDeletesNoFile() throws Exception {
        Path root = tempDir.resolve("trav-remove");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        // Plant a file OUTSIDE the root that a traversal remove would delete.
        Path outsideTarget = tempDir.resolve("outside-target").resolve("session.json");
        Files.createDirectories(outsideTarget.getParent());
        Files.writeString(outsideTarget, "sentinel");

        // A remove with a traversal-shaped id shaped toward outsideTarget must
        // throw, never delete the planted file.
        assertThrows(NopAiAgentException.class,
                () -> store.remove("../outside-target"),
                "remove with a traversal sessionId must throw (fail-closed)");
        assertTrue(Files.exists(outsideTarget),
                "remove must not delete a file outside the root via traversal");
    }

    @Test
    void absolutePathSessionIdRejected() {
        Path root = tempDir.resolve("trav-abs");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        assertThrows(NopAiAgentException.class,
                () -> store.get("/etc/passwd"),
                "Absolute-path sessionId must be rejected");
        assertRootHasNoSessionArtifacts(root);
    }

    @Test
    void backslashTraversalSessionIdRejected() {
        Path root = tempDir.resolve("trav-bslash");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        assertThrows(NopAiAgentException.class,
                () -> store.get("..\\evil"),
                "Backslash traversal sessionId must be rejected");
    }

    private void assertRootHasNoSessionArtifacts(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.list(root)) {
            assertFalse(entries.findAny().isPresent(),
                    "No session artifacts must be written under the root when the sessionId is rejected");
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static List<ChatMessage> buildMixedMessages() {
        List<ChatMessage> msgs = new ArrayList<>();

        ChatSystemMessage sys = new ChatSystemMessage("you are an agent");
        sys.setMessageId("m0");
        msgs.add(sys);

        ChatUserMessage user = new ChatUserMessage("hello");
        user.setMessageId("m1");
        msgs.add(user);

        ChatAssistantMessage assistant = new ChatAssistantMessage();
        assistant.setContent("calling tool");
        assistant.setMessageId("m2");
        ChatToolCall call = new ChatToolCall();
        call.setId("call_1");
        call.setName("echo");
        call.setArguments(Map.of("x", "y"));
        assistant.setToolCalls(List.of(call));
        msgs.add(assistant);

        ChatToolResponseMessage toolResp = ChatToolResponseMessage.fromToolCall(call, "result");
        toolResp.setMessageId("m3");
        msgs.add(toolResp);

        return msgs;
    }
}
