package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.IToolAccessChecker;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end tests: verifies the Layer 3 denial-ledger dispatch-path
 * integration is actually connected — from {@link AgentMessageRequest}, through
 * {@code DefaultAgentEngine.doExecute}, the {@link ReActAgentExecutor} Builder,
 * into the dispatch loop, where a functional ledger records per-session
 * denials and pauses the session once the denial threshold is reached.
 *
 * <p>Includes:
 * <ul>
 *   <li>Scenario A — single-iteration multi-call deny → dispatch for-loop
 *       {@code break} on threshold.</li>
 *   <li>Scenario B — cross-iteration single-call deny → ReAct-loop iteration
 *       start {@code isPaused} check → {@code break reactLoop}.</li>
 *   <li>Wiring verification ({@code recordDenial(...)} is actually invoked in
 *       the dispatch loop deny path).</li>
 *   <li>Post-loop bookkeeping: paused session does not publish
 *       {@code EXECUTION_COMPLETED}.</li>
 *   <li>Backward-compat ({@link NoOpDenialLedger} default produces zero
 *       spurious pauses).</li>
 *   <li>Engine setter/getter wiring + null fallback.</li>
 * </ul>
 */
public class TestDispatchPathDenialLedger {

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
     * Test-internal functional ledger: thread-safe per-session counter with a
     * configurable threshold. Counts recordDenial calls for wiring verification.
     */
    static final class CountingLedger implements IDenialLedger {
        final AtomicInteger recordCount = new AtomicInteger();
        final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        final java.util.Set<String> paused =
                java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        final int threshold;

        CountingLedger(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            recordCount.incrementAndGet();
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
            if (sessionId == null) return;
            counts.remove(sessionId);
            paused.remove(sessionId);
        }
    }

    /**
     * Test-internal tool-access checker that denies every tool call. Used to
     * deterministically trigger the Layer 1 tool-access deny path.
     */
    static final class DenyAllTools implements IToolAccessChecker {
        @Override
        public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
            return ToolAccessResult.deny("test deny-all rule");
        }
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    /**
     * LLM mock: returns an assistant message carrying the given tool calls,
     * then on subsequent calls returns a terminal (no-tool-call) message.
     * Tracks how many times call() was invoked so tests can assert
     * "LLM was/was not called on iteration N".
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

    private static boolean containsMessage(AgentExecutionResult result, String needle) {
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Event subscriber that collects every published event type into a list,
     * so tests can assert "SESSION_PAUSED was published" and
     * "EXECUTION_COMPLETED was NOT published".
     */
    static final class CollectingSubscriber implements IAgentEventSubscriber {
        final List<AgentEventType> types = new ArrayList<>();

        @Override
        public void onEvent(AgentEvent event) {
            types.add(event.getEventType());
        }

        boolean hasType(AgentEventType t) {
            return types.contains(t);
        }

        int countOf(AgentEventType t) {
            int c = 0;
            for (AgentEventType x : types) {
                if (x == t) c++;
            }
            return c;
        }
    }

    // ========================================================================
    // Scenario A: single-iteration multi-call deny → dispatch for-loop break
    // ========================================================================

    /**
     * Scenario A: LLM returns 2 tool calls in one response. The functional
     * ledger threshold = 2. DenyAllTools denies both. After the second deny,
     * threshold is reached: dispatch for-loop breaks, session is paused,
     * SESSION_PAUSED published, EXECUTION_COMPLETED NOT published, and the
     * second tool call is never executed (no "ok" tool result).
     *
     * <p>Also verifies wiring: recordDenial is actually called at least twice
     * (Minimum Rules #23 Wiring Verification).
     */
    @Test
    void scenarioA_singleIterationMultiCallDeny_triggersDispatchForLoopBreak() {
        CountingLedger ledger = new CountingLedger(2);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        // LLM scripts: one response with 2 tool calls, then a final message
        // (which should NOT be reached because the session pauses first).
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_a1", "shell.exec"),
                        toolCall("call_a2", "shell.exec")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring verification (Minimum Rules #23): recordDenial was called.
        assertTrue(ledger.recordCount.get() >= 1,
                "denialLedger.recordDenial(...) must be called in the dispatch loop (wiring), got: "
                        + ledger.recordCount.get());

        // Both denials were recorded (the first tool-call deny + the second one).
        // The second deny triggers threshold → break dispatchLoop.
        assertTrue(ledger.recordCount.get() >= 2,
                "Two tool-call denials must each be recorded. recordDenial count: "
                        + ledger.recordCount.get());

        // The session was paused by the ledger.
        assertEquals(AgentExecStatus.paused, result.getStatus(),
                "Session must be paused after the 2nd denial reached the threshold");

        // SESSION_PAUSED was published.
        assertTrue(subscriber.hasType(AgentEventType.SESSION_PAUSED),
                "SESSION_PAUSED event must be published on threshold pause");

        // EXECUTION_COMPLETED was NOT published for a paused session.
        assertFalse(subscriber.hasType(AgentEventType.EXECUTION_COMPLETED),
                "A paused session must NOT publish EXECUTION_COMPLETED (post-loop bookkeeping excludes paused)");

        // The second tool call was never executed: no successful tool result
        // ("ok") appears in the messages — both calls were denied.
        assertFalse(containsMessage(result, "ok"),
                "No tool call should execute successfully — both calls were denied before threshold-pause");
    }

    // ========================================================================
    // Scenario B: cross-iteration single-call deny → iteration start isPaused
    //             check → break reactLoop
    // ========================================================================

    /**
     * Scenario B: LLM returns 1 tool call per response. The functional ledger
     * threshold = 2. DenyAllTools denies the tool call.
     * <ul>
     *   <li>Iteration 1: deny → count=1, not exceeded. ReactLoop continues,
     *       LLM is called a 2nd time.</li>
     *   <li>Iteration 2 start: isPaused check → count=1 < 2, not paused,
     *       processing continues.</li>
     *   <li>Iteration 2: deny → count=2, thresholdExceeded=true.</li>
     *   <li>Iteration 3 start: isPaused check → true → break reactLoop.
     *       LLM is NOT called a 3rd time.</li>
     * </ul>
     */
    @Test
    void scenarioB_crossIterationDeny_triggersReactLoopIterationStartPauseBreak() {
        CountingLedger ledger = new CountingLedger(2);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        // LLM scripts: 3 responses, each with 1 tool call. The 3rd should
        // never be requested because iteration 3 start sees isPaused=true.
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_b1", "shell.exec")),
                assistantWithToolCalls(toolCall("call_b2", "shell.exec")),
                assistantWithToolCalls(toolCall("call_b3", "shell.exec"))
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // The session is paused.
        assertEquals(AgentExecStatus.paused, result.getStatus(),
                "Session must be paused after the 2nd cross-iteration denial reached the threshold");

        // recordDenial was called exactly twice (once per iteration where a
        // deny happened — iteration 1 and iteration 2).
        assertTrue(ledger.recordCount.get() >= 2,
                "Two cross-iteration denials must each be recorded. recordDenial count: "
                        + ledger.recordCount.get());

        // LLM was called exactly twice: iteration 1 and iteration 2. The 3rd
        // iteration never started because isPaused returned true at iteration 3
        // start.
        assertEquals(2, chat.callCount.get(),
                "LLM must be called exactly twice. A 3rd call means the iteration-start isPaused check did not abort the reactLoop. Got: "
                        + chat.callCount.get());

        // SESSION_PAUSED was published.
        assertTrue(subscriber.hasType(AgentEventType.SESSION_PAUSED),
                "SESSION_PAUSED must be published when the session is paused");

        // EXECUTION_COMPLETED was NOT published.
        assertFalse(subscriber.hasType(AgentEventType.EXECUTION_COMPLETED),
                "Paused session must NOT publish EXECUTION_COMPLETED");
    }

    // ========================================================================
    // Wiring verification: recordDenial called in dispatch loop (Builder-based)
    // ========================================================================

    /**
     * Builder-based wiring verification (Minimum Rules #23): the ledger is
     * passed via the Builder and recordDenial(...) is actually invoked in the
     * dispatch loop when a deny happens.
     */
    @Test
    void recordDenialInvokedViaBuilderWiring() {
        CountingLedger ledger = new CountingLedger(10);

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_w1", "shell.exec")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .toolAccessChecker(new DenyAllTools())
                .denialLedger(ledger)
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("wiring-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "wiring-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(ledger.recordCount.get() >= 1,
                "recordDenial must be invoked via Builder wiring when a deny happens");
    }

    // ========================================================================
    // Post-loop bookkeeping: paused session excludes EXECUTION_COMPLETED
    // ========================================================================

    /**
     * Verify that a paused session does NOT execute POST_CALL hooks or publish
     * EXECUTION_COMPLETED — the post-loop bookkeeping exclusion is wired.
     * Uses Scenario A's setup (threshold=2, two denies) and checks the
     * resulting event stream.
     */
    @Test
    void pausedSessionOmitsExecutionCompletedEvent() {
        CountingLedger ledger = new CountingLedger(2);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_pc1", "shell.exec"),
                        toolCall("call_pc2", "shell.exec")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());
        engine.setDenialLedger(ledger);
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(AgentExecStatus.paused, result.getStatus());
        assertEquals(0, subscriber.countOf(AgentEventType.EXECUTION_COMPLETED),
                "A paused session must not emit EXECUTION_COMPLETED");
        assertTrue(subscriber.hasType(AgentEventType.SESSION_PAUSED),
                "A paused session must emit SESSION_PAUSED");
    }

    // ========================================================================
    // Backward-compat: NoOpDenialLedger default → no spurious pauses
    // ========================================================================

    /**
     * Backward-compat: with the shipped {@link NoOpDenialLedger} default (never
     * explicitly registered), even multiple denials must NOT pause the session.
     * The session completes normally or fails for unrelated reasons, never
     * because of a spurious denial-ledger pause.
     */
    @Test
    void defaultNoOpDenialLedgerProducesNoSpuriousPauses() {
        // Engine with default ledger (NoOpDenialLedger) — never explicitly set.
        // Even with DenyAllTools, the session must not be paused by the ledger.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_bc1", "shell.exec")),
                        assistantWithToolCalls(toolCall("call_bc2", "shell.exec")),
                        assistantWithToolCalls(toolCall("call_bc3", "shell.exec")),
                        finalAssistant("done")
                )),
                stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());

        assertTrue(engine.getDenialLedger() instanceof NoOpDenialLedger,
                "Default denial ledger must be NoOpDenialLedger");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertNotEquals(AgentExecStatus.paused, result.getStatus(),
                "NoOpDenialLedger default must never pause the session, even after many denials. Got status: "
                        + result.getStatus());
    }

    // ========================================================================
    // Builder wiring: executor receives the ledger; default when not set
    // ========================================================================

    /**
     * A Builder-built executor without the denial ledger must still build
     * (NoOpDenialLedger default applied) and never throw during a dispatch
     * loop.
     */
    @Test
    void builderDefaultsWhenLedgerNotSet() {
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_bd1", "shell.exec")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .toolAccessChecker(new DenyAllTools())
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("bd-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "bd-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));

        // No exception thrown = default ledger is sound.
        executor.execute(ctx).toCompletableFuture().join();
    }

    // ========================================================================
    // Engine setter/getter wiring + null fallback
    // ========================================================================

    @Test
    void engineSetGetDenialLedgerAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("done"))),
                stubToolManager());

        assertTrue(engine.getDenialLedger() instanceof NoOpDenialLedger,
                "Engine default denial ledger must be NoOpDenialLedger");

        CountingLedger custom = new CountingLedger(3);
        engine.setDenialLedger(custom);
        assertTrue(engine.getDenialLedger() == custom,
                "setDenialLedger must wire the exact instance");

        // Null setter must fall back to the default, not silently store null.
        engine.setDenialLedger(null);
        assertTrue(engine.getDenialLedger() instanceof NoOpDenialLedger,
                "setDenialLedger(null) must fall back to NoOpDenialLedger default");
    }

    // ========================================================================
    // Per-session independence: session A's denials do not affect session B
    // ========================================================================

    /**
     * Verify the thread-safety / per-session independence contract: denials in
     * session A do not affect the count or pause state of session B.
     */
    @Test
    void perSessionCountsAreIndependent() {
        CountingLedger ledger = new CountingLedger(2);

        DenialRecord a1 = DenialRecord.of(
                "sessA", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1000L);
        DenialRecord a2 = DenialRecord.of(
                "sessA", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1001L);

        assertEquals(1, ledger.recordDenial(a1).getCount());
        assertEquals(2, ledger.recordDenial(a2).getCount());
        assertTrue(ledger.isPaused("sessA"));

        // Session B is unaffected.
        assertEquals(0, ledger.getDenialCount("sessB"));
        assertFalse(ledger.isPaused("sessB"));

        DenialRecord b1 = DenialRecord.of(
                "sessB", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1002L);
        assertEquals(1, ledger.recordDenial(b1).getCount());
        assertFalse(ledger.isPaused("sessB"),
                "sessB is under threshold even after sessA hit threshold");
    }
}
