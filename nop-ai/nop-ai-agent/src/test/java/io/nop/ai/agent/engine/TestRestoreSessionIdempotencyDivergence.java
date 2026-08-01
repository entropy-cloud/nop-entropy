package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.FileBackedSessionStore;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 tests for checkpoint idempotency_key divergence detection on the
 * restore path (design §13.2 Decisions A/B). Verifies that
 * {@code restoreSession} (a) accepts a checkpoint whose key matches the
 * recomputed live key, (b) rejects + degrades to session replay when the key
 * mismatches (observable via the {@code divergenceDetected} event flag and the
 * {@code rejectedCheckpointWatermark}), (c) falls back to best-effort when the
 * key is null (legacy data / non-{@code TOOL_EXECUTION}), and (d) treats a
 * missing tool-call (callId not in session) as divergence.
 *
 * <p>End-to-end (Minimum Rules #22): each test drives the full
 * {@code restoreSession} entry point through to recovery completion, proving
 * the reject+degrade path never deadlocks (session replay is always reachable).
 */
public class TestRestoreSessionIdempotencyDivergence {

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
    // Key match → checkpoint accepted (no divergence)
    // ========================================================================

    @Test
    void restoreSession_keyMatchesCheckpoint_noDivergence() throws Exception {
        String sessionId = "div-match";
        Harness h = buildHarness("div-match");

        // Session contains a tool call with callId="call-match".
        ChatToolCall tc = toolCall("call-match", "echo", Map.of("x", "1"));
        persistSessionWithToolCall(h, sessionId, tc);

        // Checkpoint's inputSummary == the tool call's getArgumentsText() →
        // the stored key matches the recomputed live key.
        h.ckptMgr.saveCheckpoint(Checkpoint.of(sessionId, "wm-match", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-match",
                tc.getArgumentsText(), "out", 2, 10L));

        List<AgentEvent> events = captureEvents(h.engine);
        AgentExecutionResult result = h.engine.restoreSession(sessionId, "op", "test")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Recovery must complete (session replay) when key matches");
        AgentEvent restored = findRestoredEvent(events);
        assertNotNull(restored);
        assertEquals(Boolean.FALSE, restored.getPayload().get("divergenceDetected"),
                "Matching key must not flag divergence");
        assertEquals("", restored.getPayload().get("rejectedCheckpointWatermark"),
                "No watermark rejected when key matches");
        assertEquals("wm-match", restored.getPayload().get("latestCheckpointWatermark"));
    }

    // ========================================================================
    // Key mismatch → checkpoint rejected + degraded session replay (Decision B)
    // ========================================================================

    @Test
    void restoreSession_keyMismatch_rejectsCheckpointAndDegradesToReplay() throws Exception {
        String sessionId = "div-mismatch";
        Harness h = buildHarness("div-mismatch");

        // Session's tool call has input {"x":"1"}.
        ChatToolCall tc = toolCall("call-mismatch", "echo", Map.of("x", "1"));
        persistSessionWithToolCall(h, sessionId, tc);

        // Checkpoint claims a DIFFERENT inputSummary ("corrupted-input") for
        // the same callId → its stored key will not match the recomputed live
        // key (which derives from the session's real argumentsText).
        Checkpoint diverged = Checkpoint.of(sessionId, "wm-mismatch", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-mismatch",
                "corrupted-input", "out", 2, 10L);
        // Sanity: the stored key differs from the live key.
        String liveKey = Checkpoint.computeIdempotencyKey(CheckpointType.TOOL_EXECUTION,
                "echo", "call-mismatch", tc.getArgumentsText());
        assertFalse(diverged.getIdempotencyKey().equals(liveKey),
                "Sanity: corrupted inputSummary must yield a different key");
        h.ckptMgr.saveCheckpoint(diverged);

        List<AgentEvent> events = captureEvents(h.engine);
        AgentExecutionResult result = h.engine.restoreSession(sessionId, "op", "test")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Recovery is NOT blocked — session replay completes (Decision B: the
        // persisted session is the source of truth; rejecting a verification
        // supplement never removes the recovery data source).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Recovery must still complete via session replay after divergence "
                        + "(reject + degrade must not deadlock)");
        AgentEvent restored = findRestoredEvent(events);
        assertNotNull(restored);
        assertEquals(Boolean.TRUE, restored.getPayload().get("divergenceDetected"),
                "Key mismatch must flag divergence (Decision B observable signal)");
        assertEquals("wm-mismatch", restored.getPayload().get("rejectedCheckpointWatermark"),
                "The rejected checkpoint watermark must be recorded for audit");
    }

    // ========================================================================
    // callId not found in session → divergence (Decision A point 5)
    // ========================================================================

    @Test
    void restoreSession_callIdNotFoundInSession_flagsDivergence() throws Exception {
        String sessionId = "div-missing";
        Harness h = buildHarness("div-missing");

        // Session has a tool call with callId="call-present".
        ChatToolCall tc = toolCall("call-present", "echo", Map.of("x", "1"));
        persistSessionWithToolCall(h, sessionId, tc);

        // Checkpoint references a callId that does NOT exist in the session →
        // the live recompute returns null (matching tool-call missing) →
        // divergence (the tool-call message history is incomplete/diverged).
        h.ckptMgr.saveCheckpoint(Checkpoint.of(sessionId, "wm-missing", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-absent",
                "some-input", "out", 2, 10L));

        List<AgentEvent> events = captureEvents(h.engine);
        AgentExecutionResult result = h.engine.restoreSession(sessionId, "op", "test")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Recovery must still complete (degraded replay)");
        AgentEvent restored = findRestoredEvent(events);
        assertNotNull(restored);
        assertEquals(Boolean.TRUE, restored.getPayload().get("divergenceDetected"),
                "Missing callId (truncated history) must flag divergence");
    }

    // ========================================================================
    // Null key (legacy / non-TOOL_EXECUTION) → best-effort fallback (zero regression)
    // ========================================================================

    @Test
    void restoreSession_nullKey_fallsBackToBestEffortNoDivergence() throws Exception {
        String sessionId = "div-null";
        Harness h = buildHarness("div-null");

        persistSessionWithToolCall(h, sessionId,
                toolCall("call-null", "echo", Map.of("x", "1")));

        // A non-TOOL_EXECUTION checkpoint has a null key (Decision F) → the
        // divergence check is skipped (null key = "no check"), restoring the
        // pre-§13.2 best-effort behavior. Simulates legacy/old data.
        Checkpoint cp = Checkpoint.of(sessionId, "wm-null", 0, 1000L,
                CheckpointType.LLM_TURN, null, null, null, "llm-out", 2, 10L);
        h.ckptMgr.saveCheckpoint(cp);

        List<AgentEvent> events = captureEvents(h.engine);
        AgentExecutionResult result = h.engine.restoreSession(sessionId, "op", "test")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Recovery must complete (best-effort fallback for null key)");
        AgentEvent restored = findRestoredEvent(events);
        assertNotNull(restored);
        assertEquals(Boolean.FALSE, restored.getPayload().get("divergenceDetected"),
                "Null key must NOT flag divergence (best-effort fallback, zero regression)");
        assertEquals("", restored.getPayload().get("rejectedCheckpointWatermark"));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Holds the wired engine + its backing stores so tests can persist
     * session/checkpoint state through the same instances the engine consults.
     */
    private static final class Harness {
        final DefaultAgentEngine engine;
        final FileBackedSessionStore store;
        final FileBackedCheckpointManager ckptMgr;

        Harness(DefaultAgentEngine engine, FileBackedSessionStore store, FileBackedCheckpointManager ckptMgr) {
            this.engine = engine;
            this.store = store;
            this.ckptMgr = ckptMgr;
        }
    }

    private Harness buildHarness(String subdir) {
        Path sessionRoot = tempDir.resolve(subdir + "-session");
        Path ckptRoot = tempDir.resolve(subdir + "-ckpt");
        FileBackedSessionStore store = new FileBackedSessionStore(sessionRoot);
        FileBackedCheckpointManager ckptMgr = new FileBackedCheckpointManager(ckptRoot);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(Collections.emptyList()),
                toolManagerReturning("ok"),
                store);
        engine.setCheckpointManager(ckptMgr);
        return new Harness(engine, store, ckptMgr);
    }

    private void persistSessionWithToolCall(Harness h, String sessionId, ChatToolCall tc) {
        AgentSession session = AgentSession.create(sessionId, "test-react-agent");
        ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
        assistantMsg.setContent("");
        assistantMsg.setToolCalls(List.of(tc));
        session.appendMessages(List.of(
                new ChatUserMessage("hi"),
                assistantMsg));
        session.setStatus(AgentExecStatus.running);
        h.store.save(session);
    }

    private List<AgentEvent> captureEvents(DefaultAgentEngine engine) {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(events::add);
        return events;
    }

    private static AgentEvent findRestoredEvent(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e.getEventType() == AgentEventType.SESSION_RESTORED)
                .findFirst()
                .orElse(null);
    }

    private static ChatToolCall toolCall(String callId, String toolName, Map<String, Object> args) {
        ChatToolCall call = new ChatToolCall();
        call.setId(callId);
        call.setName(toolName);
        call.setArguments(args);
        return call;
    }

    /**
     * Chat service that returns a single final assistant message (no tool
     * calls), so the restored ReAct loop completes in one turn regardless of
     * the divergence outcome.
     */
    static final class ScriptedChatService implements IChatService {
        private final AtomicInteger idx = new AtomicInteger(0);

        ScriptedChatService(List<ChatResponse> ignored) {
            // The restore path always finishes with one final response; the
            // scripted list is ignored in favour of a deterministic terminal
            // message so the test is robust regardless of divergence.
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            idx.getAndIncrement();
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("restored-final");
            return ChatResponse.success(msg);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static IToolManager toolManagerReturning(String output) {
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
}
