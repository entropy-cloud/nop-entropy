package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.IPostDenialGuard;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 278 Phase 2 (AR-02): focused tests verifying {@code resumeSession}
 * fully resets the governance state — both the {@link IDenialLedger} AND the
 * {@link IPostDenialGuard} — so a resumed session's next identical tool call
 * is NOT blocked as a blind retry and the session does NOT re-pause within
 * 3 iterations.
 *
 * <p>Before the fix, {@code resumeSession} only called
 * {@code denialLedger.reset(sessionId)} but never
 * {@code postDenialGuard.reset(sessionId)}, so the
 * {@link DefaultPostDenialGuard}'s per-session fingerprint set still
 * contained the previously-denied action's fingerprint. The resumed
 * execution's first identical tool call was immediately blocked by
 * {@code checkBeforeDispatch} (treated as a blind retry), re-entering the
 * denial cycle → re-pause within 3 rounds — the recovery path was
 * effectively useless.
 *
 * <p>Anti-Hollow + Wiring Verification (Minimum Rules #22 / #23): the test
 * proves the guard's {@code reset} is actually called on the resume path
 * (grep-confirmed) and that the fingerprint set is actually cleared
 * (asserted via {@code checkBeforeDispatch} returning null).
 */
public class TestResumeSessionPostDenialGuardReset {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Core AR-02 value test: drive a session to a real threshold pause using
     * the shipped {@link DefaultPostDenialGuard} (not PassThrough), then
     * {@code resumeSession}. The resume must reset BOTH the ledger AND the
     * guard. After flipping the checker to allow, the resumed execution's
     * next IDENTICAL tool call (same fingerprint) must NOT be blocked by the
     * guard, must succeed, and the session must complete — NOT re-pause
     * within 3 rounds.
     */
    @Test
    void resumeSession_resetsPostDenialGuard_sameFingerprintNotBlockedAfterResume() {
        String sessionId = "ar02-resume-guard-reset";
        ToggleableChecker checker = new ToggleableChecker();
        CountingLedger ledger = new CountingLedger(3);

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("ar02-1", "test-calculator")),
                assistantWithToolCalls(toolCall("ar02-2", "test-calculator")),
                assistantWithToolCalls(toolCall("ar02-3", "test-calculator")),
                // After resume + checker flip: same tool call (same fingerprint)
                // must NOT be blocked by the guard.
                assistantWithToolCalls(toolCall("ar02-4", "test-calculator")),
                finalAssistant("done after resume")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                checker);
        engine.setDenialLedger(ledger);
        // IMPORTANT: do NOT override postDenialGuard — use the engine's shipped
        // DefaultPostDenialGuard so the guard actually records fingerprints.
        // The pre-AR-02 bug only manifests with a functional guard.

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        // Step 1: drive to a real threshold pause (3 identical denials).
        AgentExecutionResult paused = engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.paused, paused.getStatus(),
                "session must be paused after 3 denials reach the threshold");
        assertEquals(3, ledger.getDenialCount(sessionId));

        // Verify the guard has recorded the fingerprint (pre-resume state).
        // After 3 denials of the same tool call, the guard's checkBeforeDispatch
        // for the same fingerprint should return non-null (a blind-retry deny).
        Map<String, Object> args = Map.of("command", "ls");
        IPostDenialGuard guard = engine.getPostDenialGuard();
        assertFalse(guard instanceof io.nop.ai.agent.security.PassThroughPostDenialGuard,
                "precondition: engine must use DefaultPostDenialGuard (not PassThrough)");
        // The guard MUST have the fingerprint recorded at this point.
        assertTrue(guard.checkBeforeDispatch(sessionId, "test-calculator", args, null) != null,
                "guard must have the denied fingerprint recorded before resume");

        // Step 2: flip the checker to allow so the resumed turn can proceed.
        checker.deny = false;

        // Step 3: resume — must reset BOTH ledger AND guard.
        AgentExecutionResult resumed = engine.resumeSession(sessionId, "operator-1", "false positive")
                .toCompletableFuture().join();

        // The ledger was reset.
        assertEquals(1, ledger.resetCount.get(),
                "resume must call denialLedger.reset exactly once");
        assertEquals(0, ledger.getDenialCount(sessionId),
                "denial count must be zero after reset");

        // AR-02: the guard's fingerprint set was also reset — the SAME
        // fingerprint is no longer treated as a blind retry.
        assertNull(guard.checkBeforeDispatch(sessionId, "test-calculator", args, null),
                "postDenialGuard.reset must have cleared the fingerprint set: "
                        + "the same fingerprint must not be blocked after resume (AR-02)");

        // The resumed execution continued past the guard, executed the tool
        // successfully, and completed — NOT re-paused within 3 rounds.
        assertEquals(AgentExecStatus.completed, resumed.getStatus(),
                "resumed session must reach completed (the same-fingerprint call "
                        + "was NOT blocked by the guard after reset). Messages: "
                        + resumed.getMessages());
        assertFalse(ledger.isPaused(sessionId),
                "session must not be re-paused after resume (guard reset prevents "
                        + "the immediate blind-retry block that previously caused re-pause)");

        // The resumed turn executed a tool successfully (the "ok" result).
        assertTrue(containsToolSuccess(resumed, "ok"),
                "the resumed execution must execute a tool successfully after the "
                        + "checker flip + guard reset");
    }

    // ========================================================================
    // Test-internal functional components
    // ========================================================================

    static final class ToggleableChecker implements IToolAccessChecker {
        volatile boolean deny = true;

        @Override
        public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
            return deny ? ToolAccessResult.deny("toggled deny") : ToolAccessResult.allow();
        }
    }

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
}
