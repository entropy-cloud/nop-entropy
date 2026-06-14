package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end tests for the sticky-pause recovery protocol (design
 * §6.2 {@code pauseBehavior = sticky}).
 *
 * <p>Proves the full recovery chain is connected at runtime (Minimum Rules
 * #22 Anti-Hollow + #23 Wiring Verification):
 * <ul>
 *   <li>Sticky recovery: threshold pause → {@code resumeSession} clears the
 *       ledger → re-execute → the LLM continues and the session reaches
 *       {@code completed}.</li>
 *   <li>Sticky enforcement: calling {@code execute()} on a paused session
 *       (without resume) re-pauses at the ReAct-loop gate — auto-recovery is
 *       impossible without an explicit {@code resumeSession}.</li>
 *   <li>Backward compatibility: the shipped {@link NoOpDenialLedger} default
 *       never pauses, and {@code resumeSession} fails fast when no session is
 *       paused.</li>
 *   <li>Resume audit: the {@code SESSION_RESUMED} payload records the
 *       pre-reset denial count equal to the threshold.</li>
 * </ul>
 */
public class TestStickyPauseRecovery {

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
     * Tool-access checker whose verdict can be flipped at runtime. Used to
     * drive a session to a real pause (deny), then flip to allow so the
     * resumed execution can actually proceed.
     */
    static final class ToggleableChecker implements IToolAccessChecker {
        volatile boolean deny = true;

        @Override
        public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
            return deny ? ToolAccessResult.deny("toggled deny") : ToolAccessResult.allow();
        }
    }

    /**
     * Test-internal functional ledger mirroring the real contract: per-session
     * counting, threshold pause, tracked {@code reset}.
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
     * LLM mock returning a scripted sequence of responses, recording how many
     * times {@code call()} was invoked so tests can assert "the LLM was/was
     * not called".
     */
    static final class RecordingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();
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
            int idx = Math.min(callCount.getAndIncrement(), scripted.size() - 1);
            return scripted.get(idx);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

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

        int countOf(AgentEventType t) {
            int c = 0;
            for (AgentEvent e : events) {
                if (e.getEventType() == t) c++;
            }
            return c;
        }
    }

    private static ChatResponse assistantWithToolCalls(ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(calls));
        return ChatResponse.success(msg);
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private static ChatToolCall toolCall(String id, String name) {
        ChatToolCall c = new ChatToolCall();
        c.setId(id);
        c.setName(name);
        c.setArguments(Map.of("command", "ls"));
        return c;
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

    private static boolean containsToolSuccess(AgentExecutionResult result, String needle) {
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                String body = ((ChatToolResponseMessage) m).getContent();
                if (body != null && body.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========================================================================
    // Sticky recovery: threshold pause → resume → continued execution
    // ========================================================================

    /**
     * Core value test (Minimum Rules #22 Anti-Hollow): drive a session to a
     * real threshold pause, then {@code resumeSession}. The resume must clear
     * the ledger (reset), re-execute the session, and the LLM must continue
     * past the pause gate to completion — proving the full
     * pause → resume → continue chain is connected end-to-end.
     *
     * <p>After resume the tool checker is flipped to allow, so the resumed
     * LLM turn actually executes a tool successfully and then finishes.
     */
    @Test
    void stickyRecovery_thresholdPauseThenResume_continuesToCompletion() {
        String sessionId = "sticky-recovery";
        ToggleableChecker checker = new ToggleableChecker();
        CountingLedger ledger = new CountingLedger(3);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        // Scripted LLM turns:
        //   [0..2] three (denied) tool-call turns driving the session to pause
        //   [3]   a tool-call turn that now succeeds after checker is flipped
        //   [4]   a final (no-tool-call) turn completing the session
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("sr1", "test-calculator")),
                assistantWithToolCalls(toolCall("sr2", "test-calculator")),
                assistantWithToolCalls(toolCall("sr3", "test-calculator")),
                assistantWithToolCalls(toolCall("sr4", "test-calculator")),
                finalAssistant("done after resume")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                checker);
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        // Step 1: drive to a real threshold pause.
        AgentExecutionResult paused = engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.paused, paused.getStatus(),
                "session must be paused after 3 denials reach the threshold");
        assertTrue(ledger.isPaused(sessionId),
                "ledger must report the session as paused before resume");
        assertEquals(3, ledger.getDenialCount(sessionId));
        assertTrue(subscriber.hasType(AgentEventType.SESSION_PAUSED),
                "SESSION_PAUSED must be published on threshold pause");
        int llmCallsBeforeResume = chat.callCount.get();

        // Step 2: flip the checker to allow so the resumed turn can proceed.
        checker.deny = false;

        // Step 3: resume — clears the pause and re-executes.
        AgentExecutionResult resumed = engine.resumeSession(sessionId, "operator-1", "false positive")
                .toCompletableFuture().join();

        // The pause was cleared.
        assertEquals(1, ledger.resetCount.get(),
                "resume must call denialLedger.reset exactly once");
        assertFalse(ledger.isPaused(sessionId),
                "ledger must no longer report the session as paused after resume");
        assertEquals(0, ledger.getDenialCount(sessionId),
                "denial count must be zero after reset");

        // The resumed execution continued past the pause gate to completion.
        assertEquals(AgentExecStatus.completed, resumed.getStatus(),
                "resumed session must reach completed status (continued execution)");

        // The LLM was actually invoked during resume (the ReAct loop ran past
        // the isPaused gate), proving the chain is connected end-to-end.
        assertTrue(chat.callCount.get() > llmCallsBeforeResume,
                "the resumed execution must invoke the LLM (continued past the pause gate)");

        // The resumed turn executed a tool successfully (the "ok" result from
        // the stub tool manager appears), proving real progress was made.
        assertTrue(containsToolSuccess(resumed, "ok"),
                "the resumed execution must execute a tool successfully after the checker flip");

        // Audit event published.
        assertTrue(subscriber.hasType(AgentEventType.SESSION_RESUMED),
                "SESSION_RESUMED must be published on resume");
        assertTrue(subscriber.hasType(AgentEventType.EXECUTION_COMPLETED),
                "EXECUTION_COMPLETED must be published once the resumed session completes");
    }

    // ========================================================================
    // Sticky enforcement: execute without resume → re-pause
    // ========================================================================

    /**
     * Sticky contract test: a paused session cannot auto-recover by calling
     * {@code execute()} — the ReAct-loop {@code isPaused} gate re-pauses it
     * before any LLM call. Only an explicit {@code resumeSession} (which
     * resets the ledger) can clear the pause.
     */
    @Test
    void stickyEnforcement_executeWithoutResume_repausesAtGate() {
        String sessionId = "sticky-enforcement";
        ToggleableChecker checker = new ToggleableChecker();
        CountingLedger ledger = new CountingLedger(3);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("se1", "test-calculator")),
                assistantWithToolCalls(toolCall("se2", "test-calculator")),
                assistantWithToolCalls(toolCall("se3", "test-calculator")),
                finalAssistant("should not be reached")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                checker);
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        // Step 1: drive to pause.
        AgentExecutionResult paused = engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.paused, paused.getStatus());
        assertTrue(ledger.isPaused(sessionId));
        assertEquals(0, ledger.resetCount.get(),
                "no reset must have occurred yet");
        int llmCallsAfterFirstPause = chat.callCount.get();

        // Step 2: call execute() again WITHOUT resume — the ledger still has
        // the pause. The ReAct loop must re-pause at the iteration-start gate,
        // before any LLM call.
        AgentMessageRequest secondReq = new AgentMessageRequest(
                "test-react-agent", "another turn", sessionId, null, ChannelKind.WEBUI, Principal.user());
        AgentExecutionResult repaused = engine.execute(secondReq).toCompletableFuture().join();

        assertEquals(AgentExecStatus.paused, repaused.getStatus(),
                "a paused session called via execute() (without resume) must stay/re-pause — sticky");

        // The ledger was never reset — sticky enforcement is via isPaused.
        assertEquals(0, ledger.resetCount.get(),
                "execute() must NOT reset the ledger; only resumeSession clears the pause");
        assertTrue(ledger.isPaused(sessionId),
                "ledger must still report paused — execute() cannot clear it");

        // The LLM was NOT called during the second execute — the isPaused gate
        // aborted the ReAct loop before any LLM invocation.
        assertEquals(llmCallsAfterFirstPause, chat.callCount.get(),
                "the LLM must not be invoked when isPaused is true — re-paused at the gate");

        // EXECUTION_COMPLETED must not have been published for a re-paused session.
        assertEquals(0, subscriber.countOf(AgentEventType.EXECUTION_COMPLETED),
                "a re-paused session must not emit EXECUTION_COMPLETED");
    }

    // ========================================================================
    // Backward compatibility: NoOpDenialLedger default
    // ========================================================================

    /**
     * Backward compatibility: an engine with the shipped {@link NoOpDenialLedger}
     * default (never explicitly set) never pauses, even under repeated denials.
     * {@code resumeSession} is never needed in this configuration; if invoked
     * it fails fast because no session is paused.
     */
    @Test
    void backwardCompat_noOpLedgerNeverPauses_andResumeFailsFast() {
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("bc1", "test-calculator")),
                assistantWithToolCalls(toolCall("bc2", "test-calculator")),
                assistantWithToolCalls(toolCall("bc3", "test-calculator")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new ToggleableChecker()); // deny=true, but NoOp ledger never pauses
        // Note: no setDenialLedger → default NoOpDenialLedger.

        assertTrue(engine.getDenialLedger() instanceof NoOpDenialLedger,
                "engine default denial ledger must be NoOpDenialLedger");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "bc-session", null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertNotEquals(AgentExecStatus.paused, result.getStatus(),
                "NoOpDenialLedger default must never pause, even under repeated denials");

        // resumeSession is never needed here; if called it fails fast (no paused session).
        assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("bc-session", "op", "n/a")
                        .toCompletableFuture().join(),
                "resumeSession on a non-paused (NoOp-ledger) session must fail fast");
    }

    // ========================================================================
    // Resume audit: preResetDenialCount == threshold
    // ========================================================================

    /**
     * Audit test: the {@code SESSION_RESUMED} event payload must record the
     * denial count that existed <em>before</em> the reset, equal to the
     * configured threshold (proving the audit trail captures the conditions
     * under which the pause was triggered).
     */
    @Test
    void resumeAudit_preResetDenialCountEqualsThreshold() {
        String sessionId = "audit-e2e";
        ToggleableChecker checker = new ToggleableChecker();
        CountingLedger ledger = new CountingLedger(3);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("au1", "test-calculator")),
                assistantWithToolCalls(toolCall("au2", "test-calculator")),
                assistantWithToolCalls(toolCall("au3", "test-calculator")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                checker);
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.paused, engine.getSessionStatus(sessionId));

        checker.deny = false;
        engine.resumeSession(sessionId, "auditor-9", "denial false positive")
                .toCompletableFuture().join();

        AgentEvent resumed = subscriber.firstOf(AgentEventType.SESSION_RESUMED);
        assertTrue(resumed != null, "SESSION_RESUMED must be published");

        Map<String, Object> payload = resumed.getPayload();
        assertEquals("auditor-9", payload.get("approver"));
        assertEquals("denial false positive", payload.get("reason"));
        assertEquals(3, payload.get("preResetDenialCount"),
                "preResetDenialCount must equal the threshold (3) — the count that triggered the pause");
    }
}
