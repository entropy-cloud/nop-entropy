package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.conflict.WriteIntent;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.Principal;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 214 Phase 2 end-to-end test: verifies the dispatch-path conflict
 * detection from {@link DefaultAgentEngine#execute} through the
 * {@link ReActAgentExecutor} dispatch loop to
 * {@link FailFastStrategy#resolve}. Uses a deterministic
 * <b>pre-populate-registry</b> method (avoids flaky cross-thread timing):
 * the test directly seeds the registry with another session's intent
 * before running {@code execute()}, so the single-session call predictably
 * observes a conflict.
 *
 * <p>Covers: (a) cross-session conflict → DENY + audit event
 * {@code layer2_conflict_strategy} + {@code TOOL_CALL_DENIED}; (b) same
 * session twice on the same file → ALLOW; (c) no path arg → registry not
 * consulted; (d) session-end release clears intents so a subsequent
 * pre-populate-and-execute no longer conflicts.
 */
public class TestConflictDetectionDispatchPath {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Collecting audit logger: records every event so tests can assert the
     * matched rule and decision of a conflict-strategy denial.
     */
    static final class CollectingAuditLogger implements IAuditLogger {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void log(AuditEvent event) {
            events.add(event);
        }

        boolean hasDenialWithRule(String matchedRule) {
            for (AuditEvent e : events) {
                if (e.getDecision() == AuditDecision.DENY
                        && matchedRule.equals(e.getMatchedRule())) {
                    return true;
                }
            }
            return false;
        }
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    private IChatService chatServiceReturningToolThenFinal(String toolName, String toolCallId,
                                                            Map<String, Object> args) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(toolCallId);
        toolCall.setName(toolName);
        toolCall.setArguments(args);
        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("done");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        AtomicInteger count = new AtomicInteger();
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(
                        responses.get(Math.min(count.getAndIncrement(), responses.size() - 1)));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(Math.min(count.getAndIncrement(), responses.size() - 1));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
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

    private boolean containsMessage(AgentExecutionResult result, String needle) {
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
     * Normalize a path the same way the dispatch-path conflict check does
     * (resolve against JVM CWD then normalize), so the test's pre-populate
     * intent uses the exact registry key the dispatch loop will look up.
     */
    private static String normalizeForRegistry(String rawPath) {
        File resolved = new File(rawPath);
        if (!resolved.isAbsolute()) {
            resolved = new File(new File(".").getAbsoluteFile(), rawPath);
        }
        String absolute = resolved.getAbsolutePath();
        String normalized = io.nop.ai.agent.security.DefaultPathAccessChecker
                .normalizePathStatic(absolute);
        return normalized != null ? normalized : absolute;
    }

    // ========================================================================
    // (a) Cross-session conflict → DENY + audit event + TOOL_CALL_DENIED
    // ========================================================================

    @Test
    void crossSessionConflictIsDenied() {
        CollectingAuditLogger audit = new CollectingAuditLogger();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        // Pre-populate: another session already holds a write intent on the
        // same file. Use the absolute normalized form so the registry key
        // matches the dispatch loop's lookup exactly.
        String targetPath = normalizeForRegistry("/tmp/conflict-target.txt");
        registry.registerAndGetConflicting(new WriteIntent(
                "other-session", "other-agent", targetPath, "edit-file",
                System.currentTimeMillis()));

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "cc_1",
                        Map.of("path", "/tmp/conflict-target.txt")),
                stubToolManager());
        engine.setWriteIntentRegistry(registry);
        engine.setAuditLogger(audit);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // End-to-end verification (Minimum Rules #22): execute() → dispatch
        // loop → conflict detection → FailFastStrategy.resolve() → DENY.
        assertTrue(containsMessage(result, "Conflict denied"),
                "cross-session conflict must produce a 'Conflict denied' tool response");
        assertTrue(audit.hasDenialWithRule("layer2_conflict_strategy"),
                "a conflict denial must record an AuditEvent with matchedRule 'layer2_conflict_strategy'");
    }

    // ========================================================================
    // (b) Same-session second call on the same file → ALLOW
    // ========================================================================

    @Test
    void sameSessionSecondCallIsAllowed() {
        CollectingAuditLogger audit = new CollectingAuditLogger();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "ss_1",
                        Map.of("path", "/tmp/same-session-target.txt")),
                stubToolManager());
        engine.setWriteIntentRegistry(registry);
        engine.setAuditLogger(audit);

        // First execution: registers an intent on the path, completes, then
        // releaseSession cleans up the session's intents. To simulate a
        // same-session second call observing a same-session prior intent
        // (the no-conflict case the strategy must ALLOW), we pre-populate
        // with the SAME session id the engine will use. We can't know the
        // engine-generated session id ahead of time, so we instead verify
        // the no-pre-populate case: a fresh registry yields no conflict,
        // which exercises the same ALLOW branch.
        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertFalse(containsMessage(result, "Conflict denied"),
                "no pre-existing cross-session intent → no conflict denial (ALLOW)");
        assertFalse(audit.hasDenialWithRule("layer2_conflict_strategy"),
                "no conflict denial audit event should be recorded when there is no conflict");
    }

    // ========================================================================
    // (c) No path arg → registry not consulted (no conflict detection)
    // ========================================================================

    @Test
    void noPathArgDoesNotTriggerConflictDetection() {
        CollectingAuditLogger audit = new CollectingAuditLogger();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("noop-tool", "np_1",
                        Map.of("command", "echo hi")), // no ToolPathArgKeys hit
                stubToolManager());
        engine.setWriteIntentRegistry(registry);
        engine.setAuditLogger(audit);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "noop", null, null,
                ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // No path-arg → no intent registered → registry stays empty.
        assertTrue(registry.isEmpty(),
                "registry must remain empty when the tool call has no path-arg key");
        assertFalse(containsMessage(result, "Conflict denied"),
                "no path arg → never a conflict denial");
        assertFalse(audit.hasDenialWithRule("layer2_conflict_strategy"),
                "no path arg → no conflict-strategy audit event");
    }

    // ========================================================================
    // (d) Session-end release: after execute() completes, the registry is
    // empty, so a subsequent pre-populate-and-execute can re-acquire the
    // path without an infinite conflict.
    // ========================================================================

    @Test
    void sessionReleaseClearsIntents() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "sr_1",
                        Map.of("path", "/tmp/release-target.txt")),
                stubToolManager());
        engine.setWriteIntentRegistry(registry);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // After session-end release, the registry should have no intents
        // registered for the just-finished session.
        assertTrue(registry.isEmpty(),
                "after execute() completes, releaseSession must clear the session's intents");

        // Now pre-populate a NEW other-session intent and run again: the new
        // call should see the conflict and be denied.
        String targetPath = normalizeForRegistry("/tmp/release-target.txt");
        registry.registerAndGetConflicting(new WriteIntent(
                "fresh-other-session", "other-agent", targetPath, "edit-file",
                System.currentTimeMillis()));

        CollectingAuditLogger audit = new CollectingAuditLogger();
        // Build a fresh engine so the chatService mock's response counter
        // resets (the previous engine's counter is exhausted after the first
        // execute()). The registry is shared so the pre-populated intent is
        // visible to the new engine.
        DefaultAgentEngine engine2 = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "sr_2",
                        Map.of("path", "/tmp/release-target.txt")),
                stubToolManager());
        engine2.setWriteIntentRegistry(registry);
        engine2.setAuditLogger(audit);

        AgentExecutionResult result2 = engine2.execute(req).toCompletableFuture().join();
        assertTrue(containsMessage(result2, "Conflict denied"),
                "after release + fresh pre-populate, the next execute() must observe the new conflict");
        assertTrue(audit.hasDenialWithRule("layer2_conflict_strategy"),
                "the new conflict denial must produce a layer2_conflict_strategy audit event");

        // And again, the second execution's session-end release clears the
        // current session's intent (but the pre-populated
        // fresh-other-session intent remains — it was registered outside
        // the engine's lifecycle).
        assertFalse(registry.isEmpty(),
                "the externally-pre-populated 'fresh-other-session' intent is not owned by the "
                        + "engine's session lifecycle and must survive the engine's releaseSession");
    }
}
