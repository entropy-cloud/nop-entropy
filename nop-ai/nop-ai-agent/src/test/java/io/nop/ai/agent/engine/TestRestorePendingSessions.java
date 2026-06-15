package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 184 functional tests for {@link IAgentEngine#restorePendingSessions}:
 * verifies the auto-restore-on-startup batch orchestrator — end-to-end crash
 * survival via automatic discovery + batch restore (core value), status
 * filtering (running/pending restored; paused/terminal skipped), per-session
 * failure isolation, empty-disk handling, backward compatibility
 * (InMemorySessionStore returns empty summary), wiring verification
 * (restorePendingSessions delegates to restoreSession), and the default UOE
 * contract.
 *
 * <p>These tests prove the "unattended automation" path is complete:
 * engine A executes → crash → engine B → {@code restorePendingSessions} →
 * every unfinished session automatically discovered and resumed, without any
 * caller having to know a sessionId ahead of time (Minimum Rules #22
 * Anti-Hollow end-to-end + #23 Wiring).
 */
public class TestRestorePendingSessions {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TempDir
    Path tempDir;

    // ========================================================================
    // Test-internal components (mirrors TestRestoreSession patterns)
    // ========================================================================

    /**
     * Chat service that returns scripted responses in order; once exhausted,
     * returns a terminal assistant message so each restore completes.
     */
    static final class ScriptedChatService implements IChatService {
        final List<ChatResponse> scripted;
        final AtomicInteger idx = new AtomicInteger(0);

        ScriptedChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(no more scripted responses — auto-final)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static IToolManager toolManagerReturning(String output) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, output));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
    }

    static ChatResponse toolCallResponse(String callId, String toolName, Map<String, Object> args) {
        ChatToolCall call = new ChatToolCall();
        call.setId(callId);
        call.setName(toolName);
        call.setArguments(args);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(call));
        return ChatResponse.success(msg);
    }

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    /** Persist a session directly to the store with the given status. */
    private static void persistSession(FileBackedSessionStore store, String sessionId,
                                       String agentName, AgentExecStatus status) {
        AgentSession s = AgentSession.create(sessionId, agentName);
        s.appendMessages(List.of(new ChatUserMessage("seed-msg-" + sessionId)));
        s.setStatus(status);
        store.save(s);
    }

    // ========================================================================
    // End-to-end auto-restore (core value — Minimum Rules #22 Anti-Hollow)
    // ========================================================================

    @Test
    void restorePendingSessions_endToEndAutoRestoreAfterCrash() throws Exception {
        // Engine A executes → completes → simulate crash by setting persisted
        // status back to running → discard engine A → new engine B with same
        // roots → restorePendingSessions discovers the "crashed" sessions and
        // restores them automatically — no caller needs to know any sessionId.
        Path sessionRoot = tempDir.resolve("e2e-auto-session");
        Path ckptRoot = tempDir.resolve("e2e-auto-ckpt");

        // --- Engine A: run two sessions to completion (so message history is
        // persisted), then simulate a crash by reverting status to running. ---
        FileBackedSessionStore storeA = new FileBackedSessionStore(sessionRoot);
        FileBackedCheckpointManager ckptA = new FileBackedCheckpointManager(ckptRoot);

        ChatToolCall toolCall1 = new ChatToolCall();
        toolCall1.setId("call-auto-1");
        toolCall1.setName("test-calculator");
        toolCall1.setArguments(Map.of("expr", "1+1"));

        ScriptedChatService chatA = new ScriptedChatService(List.of(
                toolCallResponse("call-auto-1", "test-calculator", Map.of("expr", "1+1")),
                finalResponse("auto-1-done")));

        DefaultAgentEngine engineA = new DefaultAgentEngine(chatA, toolManagerReturning("auto-tool-out"), storeA);
        engineA.setCheckpointManager(ckptA);

        engineA.execute(new AgentMessageRequest("test-react-agent", "What is 1+1?", "auto-1", null))
                .get(30, TimeUnit.SECONDS);

        ChatToolCall toolCall2 = new ChatToolCall();
        toolCall2.setId("call-auto-2");
        toolCall2.setName("test-calculator");
        toolCall2.setArguments(Map.of("expr", "2+2"));

        ScriptedChatService chatA2 = new ScriptedChatService(List.of(
                toolCallResponse("call-auto-2", "test-calculator", Map.of("expr", "2+2")),
                finalResponse("auto-2-done")));

        DefaultAgentEngine engineA2 = new DefaultAgentEngine(chatA2, toolManagerReturning("auto-tool-out"), storeA);
        engineA2.setCheckpointManager(ckptA);
        engineA2.execute(new AgentMessageRequest("test-react-agent", "What is 2+2?", "auto-2", null))
                .get(30, TimeUnit.SECONDS);

        // Simulate crash: both completed sessions revert to running (as if
        // the crash happened mid-execution). A brand-new store instance
        // simulates the lost in-memory cache.
        FileBackedSessionStore storeCrashed = new FileBackedSessionStore(sessionRoot);
        AgentSession s1 = storeCrashed.get("auto-1");
        assertNotNull(s1);
        s1.setStatus(AgentExecStatus.running);
        storeCrashed.save(s1);
        AgentSession s2 = storeCrashed.get("auto-2");
        assertNotNull(s2);
        s2.setStatus(AgentExecStatus.running);
        storeCrashed.save(s2);

        // --- Engine B: fresh engine + fresh store, same roots. Auto-restore
        // must discover BOTH crashed sessions and restore them. ---
        FileBackedSessionStore storeB = new FileBackedSessionStore(sessionRoot);
        FileBackedCheckpointManager ckptB = new FileBackedCheckpointManager(ckptRoot);

        // The chat service must supply enough final responses for both
        // sequential restores (each restore needs one final assistant message
        // to terminate the ReAct loop without tool calls).
        ScriptedChatService chatB = new ScriptedChatService(List.of(
                finalResponse("auto-1-restored-final"),
                finalResponse("auto-2-restored-final")));

        DefaultAgentEngine engineB = new DefaultAgentEngine(chatB, toolManagerReturning("ok"), storeB);
        engineB.setCheckpointManager(ckptB);

        // No sessionId is passed — the orchestrator discovers everything.
        SessionRestoreSummary summary = engineB.restorePendingSessions("operator", "crash recovery");

        assertEquals(2, summary.getRestoredCount(),
                "Both crashed (running) sessions must be auto-restored. Summary=" + summary);
        assertEquals(0, summary.getFailedCount(),
                "No failures expected. Summary=" + summary);
        assertEquals(0, summary.getSkippedCount(),
                "No skipped sessions expected. Summary=" + summary);

        // Both sessions must end up completed after the restore execution.
        for (SessionRestoreSummary.Entry e : summary.getRestored()) {
            assertEquals("completed", e.getDetail(),
                    "Restored session must reach completed status: " + e);
        }

        // Verify on-disk state reflects the restore.
        AgentSession restored1 = storeB.get("auto-1");
        AgentSession restored2 = storeB.get("auto-2");
        assertNotNull(restored1);
        assertNotNull(restored2);
        assertEquals(AgentExecStatus.completed, restored1.getStatus());
        assertEquals(AgentExecStatus.completed, restored2.getStatus());
        // History from before the crash must survive (tool result present).
        assertTrue(restored1.getMessageCount() > 0, "Restored session must retain pre-crash history");
    }

    // ========================================================================
    // Status filtering: running/pending restored; paused/terminal skipped
    // ========================================================================

    @Test
    void restorePendingSessions_statusFiltering() {
        // Persist one session per status; only running+pending are restored,
        // paused is skipped (governance), terminal statuses are skipped too.
        Path sessionRoot = tempDir.resolve("filter-session");
        FileBackedSessionStore store = new FileBackedSessionStore(sessionRoot);

        persistSession(store, "filter-running", "test-react-agent", AgentExecStatus.running);
        persistSession(store, "filter-pending", "test-react-agent", AgentExecStatus.pending);
        persistSession(store, "filter-paused", "test-react-agent", AgentExecStatus.paused);
        persistSession(store, "filter-completed", "test-react-agent", AgentExecStatus.completed);
        persistSession(store, "filter-failed", "test-react-agent", AgentExecStatus.failed);
        persistSession(store, "filter-cancelled", "test-react-agent", AgentExecStatus.cancelled);
        persistSession(store, "filter-forced-stopped", "test-react-agent", AgentExecStatus.forced_stopped);
        persistSession(store, "filter-escalated", "test-react-agent", AgentExecStatus.escalated);

        // Fresh store + engine on the same root.
        FileBackedSessionStore store2 = new FileBackedSessionStore(sessionRoot);
        ScriptedChatService chat = new ScriptedChatService(List.of(
                finalResponse("running-restored"),
                finalResponse("pending-restored")));
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManagerReturning("ok"), store2);

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "filtering test");

        // Exactly the two candidates restored.
        assertEquals(2, summary.getRestoredCount(),
                "Only running + pending must be restored. Summary=" + summary);
        assertEquals(6, summary.getSkippedCount(),
                "paused + 5 terminal statuses must be skipped. Summary=" + summary);
        assertEquals(0, summary.getFailedCount());

        List<String> restoredIds = new ArrayList<>();
        for (SessionRestoreSummary.Entry e : summary.getRestored()) {
            restoredIds.add(e.getSessionId());
        }
        assertTrue(restoredIds.contains("filter-running"), "running must be restored");
        assertTrue(restoredIds.contains("filter-pending"), "pending must be restored");

        // Paused must be skipped with the governance reason.
        SessionRestoreSummary.SkipEntry pausedSkip = summary.getSkipped().stream()
                .filter(s -> "filter-paused".equals(s.getSessionId())).findFirst().orElse(null);
        assertNotNull(pausedSkip, "paused session must appear in skipped");
        assertEquals(AgentExecStatus.paused, pausedSkip.getPreRestoreStatus());
        assertTrue(pausedSkip.getReason().contains("paused"),
                "Paused skip reason must explain governance: " + pausedSkip.getReason());

        // Each terminal status must be skipped with a terminal reason.
        for (AgentExecStatus terminal : new AgentExecStatus[]{
                AgentExecStatus.completed, AgentExecStatus.failed, AgentExecStatus.cancelled,
                AgentExecStatus.forced_stopped, AgentExecStatus.escalated}) {
            SessionRestoreSummary.SkipEntry skip = summary.getSkipped().stream()
                    .filter(s -> s.getPreRestoreStatus() == terminal)
                    .findFirst().orElse(null);
            assertNotNull(skip, "Terminal status " + terminal + " must be skipped");
            assertEquals(terminal, skip.getPreRestoreStatus());
            assertTrue(skip.getReason().contains("terminal"),
                    "Terminal skip reason must mention terminal: " + skip.getReason());
        }
    }

    // ========================================================================
    // Per-session failure isolation (Minimum Rules #24 — corrupt surfaced)
    // ========================================================================

    @Test
    void restorePendingSessions_failureIsolationCorruptJson() throws Exception {
        // Three running sessions on disk; one has a corrupt session.json.
        // restorePendingSessions must mark the corrupt one failed, restore the
        // other two, and NEVER abort the batch.
        Path sessionRoot = tempDir.resolve("isolation-session");
        FileBackedSessionStore storeA = new FileBackedSessionStore(sessionRoot);
        persistSession(storeA, "iso-good-1", "test-react-agent", AgentExecStatus.running);
        persistSession(storeA, "iso-good-2", "test-react-agent", AgentExecStatus.running);
        persistSession(storeA, "iso-corrupt", "test-react-agent", AgentExecStatus.running);

        // Corrupt one session.json in place.
        Path corruptFile = sessionRoot.resolve("iso-corrupt").resolve("session.json");
        Files.createDirectories(corruptFile.getParent());
        Files.writeString(corruptFile, "{ broken json for isolation");

        // Fresh store + engine. listAllSessions skips the corrupt file during
        // discovery (so it won't even be a candidate). To verify the
        // orchestrator's per-session try/catch isolation specifically, also
        // test the path where restoreSession itself throws: see
        // restorePendingSessions_restoreFailureIsIsolated below.
        FileBackedSessionStore store2 = new FileBackedSessionStore(sessionRoot);
        ScriptedChatService chat = new ScriptedChatService(List.of(
                finalResponse("iso-1-done"),
                finalResponse("iso-2-done")));
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManagerReturning("ok"), store2);

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "isolation test");

        // The corrupt file was skipped at discovery, so it is neither
        // restored nor failed here (it's simply not discovered). The two good
        // sessions are restored.
        assertEquals(2, summary.getRestoredCount(),
                "Two good sessions must be restored. Summary=" + summary);
        assertEquals(0, summary.getSkippedCount());
        // The corrupt session was filtered out at discovery (not discovered).
        // A later get(iso-corrupt) still fails fast — the corruption surfaces.
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> store2.get("iso-corrupt"),
                "get() on the corrupt session must fail fast (corruption surfaced, not hidden)");
    }

    @Test
    void restorePendingSessions_restoreFailureIsIsolated() {
        // A restore candidate whose restoreSession throws (e.g. agent model
        // not found) must be recorded in `failed`, while other candidates
        // still get restored. This proves the orchestrator-level try/catch.
        Path sessionRoot = tempDir.resolve("restore-fail-session");
        FileBackedSessionStore storeA = new FileBackedSessionStore(sessionRoot);

        // A running session whose agent model does NOT exist — restoreSession
        // will throw when loadAgentModel fails.
        persistSession(storeA, "fail-missing-agent", "no-such-agent-name", AgentExecStatus.running);
        // A running session whose agent model exists — restores fine.
        persistSession(storeA, "fail-good", "test-react-agent", AgentExecStatus.running);

        FileBackedSessionStore store2 = new FileBackedSessionStore(sessionRoot);
        ScriptedChatService chat = new ScriptedChatService(List.of(finalResponse("fail-good-done")));
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManagerReturning("ok"), store2);

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "restore-fail test");

        // The good one is restored; the missing-agent one is failed, NOT
        // aborting the batch.
        assertEquals(1, summary.getRestoredCount(), "Good session must be restored. Summary=" + summary);
        assertEquals(1, summary.getFailedCount(), "Missing-agent session must be failed. Summary=" + summary);

        SessionRestoreSummary.Entry failed = summary.getFailed().get(0);
        assertEquals("fail-missing-agent", failed.getSessionId());
        assertNotNull(failed.getDetail());
    }

    // ========================================================================
    // Empty disk (root missing / no sessions) — empty summary, NOT exception
    // ========================================================================

    @Test
    void restorePendingSessions_emptyDiskReturnsEmptySummary() {
        // Root directory does not exist yet — discovery returns empty,
        // restorePendingSessions returns an empty summary (legitimate state).
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("empty-auto"));
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(Collections.emptyList()),
                toolManagerReturning("ok"),
                store);

        SessionRestoreSummary summary = assertDoesNotThrow(
                () -> engine.restorePendingSessions("operator", "empty test"));
        assertNotNull(summary);
        assertEquals(0, summary.getRestoredCount());
        assertEquals(0, summary.getSkippedCount());
        assertEquals(0, summary.getFailedCount());
    }

    @Test
    void restorePendingSessions_emptyRootDirReturnsEmptySummary() throws Exception {
        Path root = tempDir.resolve("empty-root-auto");
        Files.createDirectories(root);
        FileBackedSessionStore store = new FileBackedSessionStore(root);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(Collections.emptyList()),
                toolManagerReturning("ok"),
                store);

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "empty root");
        assertEquals(0, summary.getRestoredCount());
        assertEquals(0, summary.getSkippedCount());
        assertEquals(0, summary.getFailedCount());
    }

    // ========================================================================
    // Backward compatibility: InMemorySessionStore returns empty summary
    // (cache empty after restart → no unfinished sessions — legitimate)
    // ========================================================================

    @Test
    void restorePendingSessions_inMemoryStoreReturnsEmptySummary() {
        // InMemorySessionStore.listAllSessions == getAll. A fresh instance
        // has an empty cache → discovery returns empty → restorePendingSessions
        // returns an empty summary (NOT an exception — "no unfinished sessions"
        // is a legitimate state, documented in the Javadoc).
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(Collections.emptyList()),
                toolManagerReturning("ok"),
                store);

        SessionRestoreSummary summary = assertDoesNotThrow(
                () -> engine.restorePendingSessions("operator", "in-memory test"));
        assertNotNull(summary);
        assertEquals(0, summary.getRestoredCount());
        assertEquals(0, summary.getSkippedCount());
        assertEquals(0, summary.getFailedCount());
    }

    @Test
    void restorePendingSessions_inMemoryStoreWithRunningSessionRestoresIt() {
        // If the in-memory store DOES hold a running session (e.g. the process
        // did not actually restart, but the caller invokes restorePendingSessions
        // for some reason), that session is a candidate and gets restored.
        // This proves the orchestrator works against any discovery-capable
        // store, not just FileBackedSessionStore.
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession s = store.getOrCreate("inmem-running", "test-react-agent");
        s.appendMessages(List.of(new ChatUserMessage("hi")));
        s.setStatus(AgentExecStatus.running);

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("inmem-done"))),
                toolManagerReturning("ok"),
                store);

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "in-memory running");
        assertEquals(1, summary.getRestoredCount(), "In-memory running session must be restored. Summary=" + summary);
        assertEquals(0, summary.getFailedCount());
    }

    // ========================================================================
    // Fail-fast: store that does not support discovery (UOE → NopAiAgentException)
    // ========================================================================

    @Test
    void restorePendingSessions_failFastOnNonDiscoveryStore() {
        // A custom store that does NOT override listAllSessions → the
        // interface default UOE must surface as NopAiAgentException, not a
        // silent empty summary.
        ISessionStore nonDiscoveryStore = new ISessionStore() {
            @Override
            public AgentSession getOrCreate(String sessionId, String agentName) { return null; }
            @Override
            public AgentSession get(String sessionId) { return null; }
            @Override
            public void remove(String sessionId) {}
            @Override
            public Collection<AgentSession> getAll() { return Collections.emptyList(); }
            // listAllSessions intentionally NOT overridden → default UOE
        };
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(Collections.emptyList()),
                toolManagerReturning("ok"),
                nonDiscoveryStore);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.restorePendingSessions("operator", "non-discovery test"));
        assertTrue(ex.getMessage().contains("does not support discovery"),
                "Exception must identify unsupported discovery. Got: " + ex.getMessage());
    }

    // ========================================================================
    // Wiring verification (Minimum Rules #23): restorePendingSessions calls
    // restoreSession internally, rather than reimplementing the protocol.
    // ========================================================================

    @Test
    void restorePendingSessions_delegatesToRestoreSession() {
        // Subclass DefaultAgentEngine to count restoreSession invocations.
        Path sessionRoot = tempDir.resolve("wiring-session");
        FileBackedSessionStore storeA = new FileBackedSessionStore(sessionRoot);
        persistSession(storeA, "wire-1", "test-react-agent", AgentExecStatus.running);
        persistSession(storeA, "wire-2", "test-react-agent", AgentExecStatus.pending);
        // A paused + a terminal session to verify they do NOT trigger restoreSession.
        persistSession(storeA, "wire-paused", "test-react-agent", AgentExecStatus.paused);
        persistSession(storeA, "wire-done", "test-react-agent", AgentExecStatus.completed);

        FileBackedSessionStore store2 = new FileBackedSessionStore(sessionRoot);
        ScriptedChatService chat = new ScriptedChatService(List.of(
                finalResponse("wire-1-done"), finalResponse("wire-2-done")));

        AtomicInteger restoreCalls = new AtomicInteger(0);
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManagerReturning("ok"), store2) {
            @Override
            public CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
                restoreCalls.incrementAndGet();
                return super.restoreSession(sessionId, approver, reason);
            }
        };

        SessionRestoreSummary summary = engine.restorePendingSessions("operator", "wiring test");

        // restoreSession must be called exactly twice (wire-1 + wire-2). The
        // paused + completed sessions must NOT trigger a restoreSession call.
        assertEquals(2, restoreCalls.get(),
                "restorePendingSessions must delegate to restoreSession for each candidate. "
                        + "Summary=" + summary);
        assertEquals(2, summary.getRestoredCount());
        assertEquals(2, summary.getSkippedCount());
    }

    // ========================================================================
    // Default UOE (Minimum Rules #24)
    // ========================================================================

    @Test
    void iAgentEngineDefaultRestorePendingSessionsThrowsUOE() {
        IAgentEngine engine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) { return null; }
            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) { return null; }
        };
        assertThrows(UnsupportedOperationException.class,
                () -> engine.restorePendingSessions("x", "y"),
                "IAgentEngine default restorePendingSessions must throw UOE");
    }

    @Test
    void sessionRestoreSummaryIsImmutableAndConsistent() {
        // Smoke test for the summary value type.
        List<SessionRestoreSummary.Entry> restored = List.of(
                new SessionRestoreSummary.Entry("a", "completed"));
        List<SessionRestoreSummary.SkipEntry> skipped = List.of(
                new SessionRestoreSummary.SkipEntry("b", AgentExecStatus.paused, "paused"));
        List<SessionRestoreSummary.Entry> failed = List.of(
                new SessionRestoreSummary.Entry("c", "boom"));

        SessionRestoreSummary summary = new SessionRestoreSummary(restored, skipped, failed);
        assertEquals(1, summary.getRestoredCount());
        assertEquals(1, summary.getSkippedCount());
        assertEquals(1, summary.getFailedCount());
        assertEquals("a", summary.getRestored().get(0).getSessionId());
        assertEquals("completed", summary.getRestored().get(0).getDetail());
        assertEquals(AgentExecStatus.paused, summary.getSkipped().get(0).getPreRestoreStatus());
        assertEquals("boom", summary.getFailed().get(0).getDetail());

        // Immutability
        assertThrows(UnsupportedOperationException.class, () -> summary.getRestored().add(
                new SessionRestoreSummary.Entry("x", "y")));
    }
}
