package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for {@link IAgentEngine#resumeSession}: verifies the
 * recovery contract surface — fail-fast on invalid session state, the
 * {@code denialLedger.reset} call clearing the pause, and the
 * {@link AgentEventType#SESSION_RESUMED} audit-event payload.
 *
 * <p>These are contract-level tests (fail-fast + reset + event). The full
 * pause → resume → continued-execution chain and the sticky-enforcement
 * contract are covered by the end-to-end tests in Phase 2.
 */
public class TestResumeSession {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Test-internal functional components
    // ========================================================================

    /**
     * Test-internal functional ledger that mirrors the contract: per-session
     * counting, threshold pause, and a tracked {@code reset} invocation so the
     * resume path can be verified to actually clear the pause.
     */
    static final class CountingLedger implements IDenialLedger {
        final AtomicInteger resetCount = new AtomicInteger();
        final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        final java.util.Set<String> paused =
                java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        final int threshold;

        CountingLedger(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            String sid = record.getSessionId();
            if (sid == null) {
                return DenialRecordOutcome.of(0, false);
            }
            int c = counts.computeIfAbsent(sid, k -> new AtomicInteger()).incrementAndGet();
            boolean exceeded = c >= threshold;
            if (exceeded) {
                paused.add(sid);
            }
            return DenialRecordOutcome.of(c, exceeded);
        }

        @Override
        public boolean isPaused(String sessionId) {
            return sessionId != null && paused.contains(sessionId);
        }

        @Override
        public int getDenialCount(String sessionId) {
            if (sessionId == null) return 0;
            AtomicInteger c = counts.get(sessionId);
            return c == null ? 0 : c.get();
        }

        @Override
        public void reset(String sessionId) {
            resetCount.incrementAndGet();
            if (sessionId == null) return;
            counts.remove(sessionId);
            paused.remove(sessionId);
        }
    }

    /**
     * Event subscriber that captures every published event so tests can assert
     * on the {@code SESSION_RESUMED} payload.
     */
    static final class CollectingSubscriber implements IAgentEventSubscriber {
        final List<AgentEvent> events = new ArrayList<>();

        @Override
        public void onEvent(AgentEvent event) {
            events.add(event);
        }

        AgentEvent firstOf(AgentEventType t) {
            for (AgentEvent e : events) {
                if (e.getEventType() == t) return e;
            }
            return null;
        }

        boolean hasType(AgentEventType t) {
            for (AgentEvent e : events) {
                if (e.getEventType() == t) return true;
            }
            return false;
        }
    }

    static final class RecordingChatService implements IChatService {
        final List<ChatResponse> scripted;

        RecordingChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            // Always return a terminal (no-tool-call) message so the resumed
            // ReAct loop completes in a single iteration.
            return scripted.get(0);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    @SuppressWarnings("unused")
    private static ChatResponse assistantWithToolCalls(ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(calls));
        return ChatResponse.success(msg);
    }

    private static IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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
                return null;
            }
        };
    }

    /**
     * Build an engine wired with the supplied ledger, a terminal chat service
     * (so any resumed re-execution completes), and an allow-all tool checker.
     */
    private static DefaultAgentEngine newEngine(IDenialLedger ledger, InMemorySessionStore store) {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("done"))),
                stubToolManager(),
                store,
                new AllowAllPermissionProvider());
        engine.setDenialLedger(ledger);
        return engine;
    }

    private static void recordDenials(CountingLedger ledger, String sessionId, int n) {
        for (int i = 0; i < n; i++) {
            ledger.recordDenial(DenialRecord.of(sessionId, "shell.exec",
                    DenialLayerSource.LAYER1_TOOL_ACCESS, "no", "rule", 1000L + i));
        }
    }

    // ========================================================================
    // Fail-fast: non-existent session
    // ========================================================================

    /**
     * resumeSession on a session that does not exist in the store must fail
     * fast with {@link NopAiAgentException} (Minimum Rules #24 No Silent
     * No-Op) — never silently return null or no-op.
     */
    @Test
    void resumeSession_nonExistentSession_throwsNopAiAgentException() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(new CountingLedger(2), store);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("no-such-session", "operator-1", "test"));
        assertTrue(ex.getMessage().contains("session not found"),
                "Exception must identify the missing session. Got: " + ex.getMessage());
    }

    // ========================================================================
    // Fail-fast: non-paused session (running / completed / cancelled)
    // ========================================================================

    /**
     * Only a paused session can be resumed. Resuming a running session is an
     * operator error and must fail fast rather than silently no-op.
     */
    @Test
    void resumeSession_runningSession_throwsNopAiAgentException() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession session = store.getOrCreate("running-sess", "test-react-agent");
        session.setStatus(AgentExecStatus.running);

        DefaultAgentEngine engine = newEngine(new CountingLedger(2), store);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("running-sess", "operator-1", "test"));
        assertTrue(ex.getMessage().contains("not paused"),
                "Exception must state the session is not paused. Got: " + ex.getMessage());
    }

    @Test
    void resumeSession_completedSession_throwsNopAiAgentException() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession session = store.getOrCreate("completed-sess", "test-react-agent");
        session.setStatus(AgentExecStatus.completed);

        DefaultAgentEngine engine = newEngine(new CountingLedger(2), store);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("completed-sess", "operator-1", "test"));
        assertTrue(ex.getMessage().contains("not paused"),
                "Exception must state the session is not paused. Got: " + ex.getMessage());
    }

    @Test
    void resumeSession_cancelledSession_throwsNopAiAgentException() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession session = store.getOrCreate("cancelled-sess", "test-react-agent");
        session.setStatus(AgentExecStatus.cancelled);

        DefaultAgentEngine engine = newEngine(new CountingLedger(2), store);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("cancelled-sess", "operator-1", "test"));
        assertTrue(ex.getMessage().contains("not paused"),
                "Exception must state the session is not paused. Got: " + ex.getMessage());
    }

    // ========================================================================
    // Reset call: resume clears the pause via denialLedger.reset
    // ========================================================================

    /**
     * resumeSession on a paused session must call
     * {@link IDenialLedger#reset}, clearing both the paused state and the
     * per-session denial count (Minimum Rules #23 Wiring Verification — the
     * reset call is actually invoked, not just the method existing).
     */
    @Test
    void resumeSession_pausedSession_callsDenialLedgerResetClearingPause() {
        InMemorySessionStore store = new InMemorySessionStore();
        CountingLedger ledger = new CountingLedger(2);

        // Construct a paused session: status=paused + denials at/above threshold.
        AgentSession session = store.getOrCreate("paused-sess", "test-react-agent");
        session.appendMessages(List.of(new io.nop.ai.api.chat.messages.ChatUserMessage("hi")));
        session.setStatus(AgentExecStatus.paused);
        recordDenials(ledger, "paused-sess", 2);

        assertTrue(ledger.isPaused("paused-sess"),
                "precondition: session must be paused before resume");
        assertEquals(2, ledger.getDenialCount("paused-sess"));

        DefaultAgentEngine engine = newEngine(ledger, store);

        AgentExecutionResult result = engine.resumeSession("paused-sess", "operator-1", "false positive")
                .toCompletableFuture().join();

        // reset was actually invoked (wiring verification).
        assertEquals(1, ledger.resetCount.get(),
                "resumeSession must call denialLedger.reset exactly once");

        // The pause is cleared: count back to zero, isPaused false.
        assertEquals(0, ledger.getDenialCount("paused-sess"),
                "reset must zero the per-session denial count");
        assertFalse(ledger.isPaused("paused-sess"),
                "reset must clear the paused state");

        // The resumed execution completed (status propagated from ctx).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "resumed execution must complete normally after the pause is cleared");
    }

    // ========================================================================
    // Event publish: SESSION_RESUMED carries approver + reason + count
    // ========================================================================

    /**
     * resumeSession must publish a {@link AgentEventType#SESSION_RESUMED}
     * audit event whose payload carries {@code approver}, {@code reason}, and
     * {@code preResetDenialCount}.
     */
    @Test
    void resumeSession_publishesSessionResumedEventWithAuditPayload() {
        InMemorySessionStore store = new InMemorySessionStore();
        CountingLedger ledger = new CountingLedger(3);

        AgentSession session = store.getOrCreate("audit-sess", "test-react-agent");
        session.appendMessages(List.of(new io.nop.ai.api.chat.messages.ChatUserMessage("hi")));
        session.setStatus(AgentExecStatus.paused);
        recordDenials(ledger, "audit-sess", 3);

        DefaultAgentEngine engine = newEngine(ledger, store);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        engine.resumeSession("audit-sess", "operator-7", "denial false positive")
                .toCompletableFuture().join();

        AgentEvent resumed = subscriber.firstOf(AgentEventType.SESSION_RESUMED);
        assertTrue(resumed != null,
                "SESSION_RESUMED event must be published on resume");

        Map<String, Object> payload = resumed.getPayload();
        assertEquals("operator-7", payload.get("approver"),
                "payload.approver must match the resume call argument");
        assertEquals("denial false positive", payload.get("reason"),
                "payload.reason must match the resume call argument");
        assertEquals(3, payload.get("preResetDenialCount"),
                "payload.preResetDenialCount must record the count before reset (audit trail)");
    }
}
