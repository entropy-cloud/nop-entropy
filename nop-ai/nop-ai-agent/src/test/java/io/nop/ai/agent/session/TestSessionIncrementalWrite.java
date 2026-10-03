package io.nop.ai.agent.session;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2295 (WI14): incremental snapshot write path tests for
 * {@link FileBackedSessionStore} — append fast path (journal sidecar, snapshot
 * untouched), fold triggers (first save / in-place content mutation /
 * structural change / threshold), crash-window degradation (torn tail, stale
 * journal) with self-heal, legacy compatibility, scalar-state persistence
 * through journal entries, restart recovery, discovery, and concurrent
 * writers.
 */
public class TestSessionIncrementalWrite {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Append fast path
    // ========================================================================

    @Test
    void appendSaveKeepsSnapshotAndWritesJournal() throws Exception {
        Path root = tempDir.resolve("append");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-app", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session); // first save → fold (v1)

        Path snapshot = root.resolve("sess-app").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Path journal = root.resolve("sess-app").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME);
        byte[] snapshotBefore = Files.readAllBytes(snapshot);
        assertFalse(Files.exists(journal), "fold clears the journal");

        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // append fast path

        assertTrue(Files.exists(journal), "append save must write the journal sidecar");
        assertEquals(1, countJournalLines(journal), "one journal entry per append save");
        assertTrue(java.util.Arrays.equals(snapshotBefore, Files.readAllBytes(snapshot)),
                "append save must NOT rewrite the snapshot file");

        // Replay from a fresh instance sees the journal tail.
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-app");
        assertNotNull(restored);
        assertEquals(2, restored.getMessageCount());
        assertEquals("m1", restored.getMessages().get(0).getContent());
        assertEquals("m2", restored.getMessages().get(1).getContent());
    }

    @Test
    void scalarOnlySavePersistsThroughJournalWithoutFold() throws Exception {
        Path root = tempDir.resolve("scalars");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-scal", "agent");
        session.appendMessages(List.of(new ChatUserMessage("hello")));
        store.save(session); // fold v1

        Path snapshot = root.resolve("sess-scal").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        byte[] before = Files.readAllBytes(snapshot);

        // Per-tool-call style save: no new messages, but counters/status/metadata change.
        session.addTokensUsed(77L);
        session.addIterations(3);
        session.setStatus(AgentExecStatus.completed);
        session.getMetadata().put("escalateReason", "budget");
        session.markInputSeen("feishu-msg-1");
        store.save(session);

        assertEquals(1, countJournalLines(root.resolve("sess-scal").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME)),
                "scalar-only save still records a journal entry (state block)");
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(snapshot)),
                "scalar-only append must not rewrite the snapshot");

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-scal");
        assertEquals(77L, restored.getTotalTokensUsed(), "journal state block carries token totals");
        assertEquals(3, restored.getTotalIterations());
        assertEquals(AgentExecStatus.completed, restored.getStatus());
        assertEquals("budget", restored.getMetadata().get("escalateReason"));
        assertTrue(restored.hasSeenInput("feishu-msg-1"), "WI7 dedup keys persist through journal entries");
    }

    // ========================================================================
    // Fold triggers
    // ========================================================================

    @Test
    void firstSaveFoldsAndStampsSnapshotVersion() throws Exception {
        Path root = tempDir.resolve("first");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-first", "agent");
        session.appendMessages(List.of(new ChatUserMessage("boot")));
        store.save(session);

        Path snapshot = root.resolve("sess-first").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        assertTrue(Files.exists(snapshot), "first save must create the base snapshot");
        assertFalse(Files.exists(root.resolve("sess-first").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME)),
                "first save must fold (append never creates the base)");
        assertEquals(1L, readSnapshotVersion(snapshot), "first fold stamps snapshotVersion=1");
    }

    @Test
    void guardrailStyleInPlaceRewriteTriggersFoldAndReloadReturnsMutatedContent() {
        // Regression for the round-1 blocker: checkOutputGuardrail rewrites the
        // persisted assistant message in place (same reference, new content);
        // the fingerprint diff must catch it and fold, and reload must return
        // the mutated content — never the stale original.
        Path root = tempDir.resolve("guardrail");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-gr", "agent");
        ChatAssistantMessage assistant = new ChatAssistantMessage("raw model output");
        session.appendMessages(List.of(new ChatUserMessage("q"), assistant));
        store.save(session); // assistant persisted with raw content

        // Guardrail Block/Modify: same instance, content replaced in place.
        assistant.setContent("Output blocked by content guardrail: policy");

        store.save(session); // fingerprint mismatch → fold

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-gr");
        assertEquals("Output blocked by content guardrail: policy",
                restored.getMessages().get(1).getContent(),
                "reload must see the guardrail-rewritten content, not the stale original");
    }

    @Test
    void structuralInsertAndShrinkBothFold() {
        Path root = tempDir.resolve("structure");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-st", "agent");
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(new ChatUserMessage("m" + i));
        }
        session.appendMessages(messages);
        store.save(session);

        // WI3-style in-place insertion before the tail (wait-phase history).
        List<ChatMessage> live = new ArrayList<>(session.getMessages());
        live.add(2, new ChatAssistantMessage("inserted"));
        session.replaceMessages(live);
        store.save(session);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-st");
        assertEquals(6, restored.getMessageCount());
        assertEquals("inserted", restored.getMessages().get(2).getContent());

        // Shrink (compaction-style truncation) — reference-shrink must fold too.
        // After the insertion the list is [m0, m1, inserted, m2, m3, m4];
        // sublist(3, 6) = [m2, m3, m4].
        List<ChatMessage> truncated = new ArrayList<>(restored.getMessages().subList(3, 6));
        restored.replaceMessages(truncated);
        store.save(restored);

        FileBackedSessionStore store3 = new FileBackedSessionStore(root);
        AgentSession reloaded = store3.get("sess-st");
        assertEquals(3, reloaded.getMessageCount(), "shrink must be visible after reload");
        assertEquals("m2", reloaded.getMessages().get(0).getContent());
        assertEquals("m4", reloaded.getMessages().get(2).getContent());
    }

    @Test
    void journalThresholdFoldsAndClearsJournal() throws Exception {
        Path root = tempDir.resolve("threshold");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-th", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m0")));
        store.save(session); // fold

        Path journal = root.resolve("sess-th").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME);
        Path snapshot = root.resolve("sess-th").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        long versionBefore = readSnapshotVersion(snapshot);

        for (int i = 1; i <= SessionPersistenceState.FOLD_THRESHOLD; i++) {
            session.appendMessages(List.of(new ChatUserMessage("m" + i)));
            store.save(session);
        }
        assertEquals(SessionPersistenceState.FOLD_THRESHOLD, countJournalLines(journal));

        session.appendMessages(List.of(new ChatUserMessage("overflow")));
        store.save(session); // threshold reached → fold

        assertFalse(Files.exists(journal), "fold clears the journal");
        assertTrue(readSnapshotVersion(snapshot) > versionBefore, "fold bumps snapshotVersion");

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-th");
        assertEquals(SessionPersistenceState.FOLD_THRESHOLD + 2, restored.getMessageCount());
        assertEquals("overflow", restored.getMessages().get(restored.getMessageCount() - 1).getContent());
    }

    // ========================================================================
    // Crash-window degradation + self-heal
    // ========================================================================

    @Test
    void tornTailIsDiscardedAndJournalSelfHeals() throws Exception {
        Path root = tempDir.resolve("torn");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-torn", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session); // fold
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // journal entry #0
        session.addTokensUsed(5L);
        store.save(session); // journal entry #1

        Path journal = root.resolve("sess-torn").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME);
        // Simulate a torn final line (crash mid-append).
        Files.write(journal, "{'baseVersion':1,'seq':2,\"torn".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-torn");
        assertEquals(2, restored.getMessageCount(), "valid prefix replays; torn tail discarded");
        assertEquals(5L, restored.getTotalTokensUsed());

        assertFalse(Files.exists(journal), "self-heal removes the residual journal");

        // Post-heal appends accumulate from a clean baseline.
        restored.appendMessages(List.of(new ChatUserMessage("m3")));
        store2.save(restored);
        FileBackedSessionStore store3 = new FileBackedSessionStore(root);
        assertEquals(3, store3.get("sess-torn").getMessageCount(), "post-heal appends survive restart");
    }

    @Test
    void staleJournalFromFoldCrashIsDiscardedAndHealed() throws Exception {
        Path root = tempDir.resolve("stale");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-stale", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session); // fold v1
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // journal entry baseVersion=1

        // Simulate the fold-crash window: new snapshot (v2) written, journal NOT cleared.
        Path snapshot = root.resolve("sess-stale").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        new SessionFileWriter().write(snapshot, session, 2L);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-stale");
        assertEquals(2, restored.getMessageCount(), "v2 snapshot is self-sufficient");
        assertFalse(Files.exists(root.resolve("sess-stale").resolve(FileBackedSessionStore.JOURNAL_FILE_NAME)),
                "stale journal must be self-healed away");

        // After the heal, appends work and survive a restart (the round-2
        // Major-1 scenario: without the heal, new entries would be shadowed
        // by the stale head until the next fold).
        restored.appendMessages(List.of(new ChatUserMessage("m3")));
        store2.save(restored);
        FileBackedSessionStore store3 = new FileBackedSessionStore(root);
        assertEquals(3, store3.get("sess-stale").getMessageCount(),
                "post-heal append must be replay-visible across restart");
    }

    // ========================================================================
    // Legacy compatibility, remove, discovery, restart
    // ========================================================================

    @Test
    void legacySnapshotWithoutVersionFieldLoadsAndKeepsWorking() {
        Path root = tempDir.resolve("legacy");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-leg", "agent");
        session.appendMessages(List.of(new ChatUserMessage("legacy")));
        store.save(session);

        // Strip snapshotVersion → simulate a pre-WI14 session file.
        Path snapshot = root.resolve("sess-leg").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        try {
            Object parsed = JsonTool.parseNonStrict(Files.readString(snapshot, StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            map.remove("snapshotVersion");
            Files.write(snapshot, JsonTool.stringify(map).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        // Legacy file loads as version 0; the load path fingerprints the
        // persisted prefix, so the next save can append directly against the
        // unversioned base (baseVersion=0 journal entries replay fine).
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-leg");
        assertNotNull(restored);
        assertEquals(1, restored.getMessageCount(), "legacy file (no version field) loads");

        restored.appendMessages(List.of(new ChatUserMessage("after-upgrade")));
        store2.save(restored);

        FileBackedSessionStore store3 = new FileBackedSessionStore(root);
        AgentSession reloaded = store3.get("sess-leg");
        assertEquals(2, reloaded.getMessageCount(), "append on legacy base survives restart");
        assertEquals("after-upgrade", reloaded.getMessages().get(1).getContent());
    }

    @Test
    void removeDeletesSnapshotJournalAndDirectory() throws Exception {
        Path root = tempDir.resolve("remove");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-rm", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session);

        Path sessionDir = root.resolve("sess-rm");
        assertTrue(Files.exists(sessionDir.resolve(FileBackedSessionStore.SESSION_FILE_NAME)));
        assertTrue(Files.exists(sessionDir.resolve(FileBackedSessionStore.JOURNAL_FILE_NAME)));

        store.remove("sess-rm");
        assertFalse(Files.exists(sessionDir), "remove cleans the whole per-session directory");
        assertNull(store.get("sess-rm"));
    }

    @Test
    void listAllSessionsSeesJournalTail() {
        Path root = tempDir.resolve("discover");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-disc", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session); // tail only in the journal

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        List<AgentSession> discovered = new ArrayList<>(store2.listAllSessions());
        assertEquals(1, discovered.size());
        assertEquals(2, discovered.get(0).getMessageCount(),
                "discovery must replay the journal, not return the snapshot-only tail");
    }

    @Test
    void restartRestoresFullStateThroughJournal() {
        Path root = tempDir.resolve("restart");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-restart", "agent");
        session.appendMessages(List.of(new ChatUserMessage("m1")));
        session.addTokensUsed(10L);
        session.setStatus(AgentExecStatus.running);
        store.save(session); // fold

        // All subsequent changes go through journal entries only.
        session.appendMessages(List.of(new ChatAssistantMessage("a1")));
        session.addTokensUsed(15L);
        session.addIterations(2);
        session.getMetadata().put("key", "value");
        session.markInputSeen("k1");
        session.addPendingInjection("inject", "{\"content\":\"x\"}");
        session.setStatus(AgentExecStatus.completed);
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("m2")));
        store.save(session);

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-restart");
        assertEquals(3, restored.getMessageCount());
        assertEquals(25L, restored.getTotalTokensUsed(), "journal state blocks are last-writer-wins");
        assertEquals(2, restored.getTotalIterations());
        assertEquals(AgentExecStatus.completed, restored.getStatus());
        assertEquals("value", restored.getMetadata().get("key"));
        assertTrue(restored.hasSeenInput("k1"));
        assertEquals(1, restored.getPendingInjections().size());
        assertEquals("inject", restored.getPendingInjections().get(0).getSemantics());
    }

    // ========================================================================
    // Concurrent writers
    // ========================================================================

    @Test
    void concurrentWritersProduceConsistentReplayableJournal() throws Exception {
        Path root = tempDir.resolve("concurrent");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        // Shared live list — the engine's real shape (executor thread appends
        // tool results while the injectInput/steering caller thread saves the
        // same live list).
        List<ChatMessage> shared = new ArrayList<>();
        shared.add(new ChatUserMessage("m0"));
        AgentSession session = AgentSession.create("sess-conc", "agent");
        session.appendMessages(shared);
        store.save(session); // fold

        int writers = 2;
        int savesPerWriter = 40;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        for (int w = 0; w < writers; w++) {
            final int id = w;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < savesPerWriter; i++) {
                        // Mutation and save atomic w.r.t. the shared list —
                        // the session object itself has a single-writer
                        // contract (engine: one ReAct worker thread; the
                        // concurrent-save shape is the WI8 staging-save
                        // pattern, not concurrent list mutation).
                        synchronized (shared) {
                            shared.add(new ChatUserMessage("w" + id + "-" + i));
                            session.replaceMessages(shared);
                            store.save(session);
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "writers must finish");
        pool.shutdownNow();

        assertEquals(writers * savesPerWriter + 1, session.getMessageCount());

        // Convergence save: the engine's real sequence always ends with a
        // terminal save of the live list — after it, the full state must be
        // durable and replayable (interleaved saves may legally persist any
        // intermediate prefix; the convergence save is what defines the final
        // state, so equality IS the "legal serialized terminal state" here).
        synchronized (shared) {
            session.replaceMessages(shared);
        }
        store.save(session);

        // Every journal line must be parseable (no torn/interleaved lines) and
        // a fresh instance must replay to the full final state.
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-conc");
        assertNotNull(restored);
        assertEquals(session.getMessageCount(), restored.getMessageCount(),
                "replayed state must equal the converged live list");
        for (int i = 0; i < restored.getMessageCount(); i++) {
            assertEquals(shared.get(i).getContent(), restored.getMessages().get(i).getContent(),
                    "message order must match the shared live list order");
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static int countJournalLines(Path journal) throws Exception {
        List<String> lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
        int count = 0;
        for (String line : lines) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static long readSnapshotVersion(Path snapshot) throws Exception {
        Object parsed = JsonTool.parseNonStrict(Files.readString(snapshot, StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        Object version = map.get("snapshotVersion");
        assertTrue(version instanceof Number, "snapshot must carry snapshotVersion");
        return ((Number) version).longValue();
    }
}
