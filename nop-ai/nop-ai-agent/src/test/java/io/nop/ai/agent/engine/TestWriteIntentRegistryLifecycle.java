package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 214 Phase 2 lifecycle test: verifies that a normal single-session
 * {@code execute()} leaves the {@link InMemoryWriteIntentRegistry} empty
 * once the session terminates — i.e. the engine's finally-block
 * {@code releaseSession} call correctly reclaims the intents registered
 * during the dispatch loop. This is the structural complement of the
 * end-to-end {@link TestConflictDetectionDispatchPath} test: it asserts
 * the lifecycle cleanup rather than the conflict decision itself.
 */
public class TestWriteIntentRegistryLifecycle {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

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

    /**
     * After a single-session execute() with a path-arg tool call completes,
     * the registry must be empty: the dispatch loop registered an intent,
     * the call was allowed (no cross-session conflict), and the finally
     * block released the session's intent.
     */
    @Test
    void registryIsEmptyAfterSingleSessionExecute() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "lc_1",
                        Map.of("path", "/tmp/lifecycle-target.txt")),
                stubToolManager());
        engine.setWriteIntentRegistry(registry);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        assertTrue(registry.isEmpty(),
                "after execute() completes, the engine's finally-block releaseSession must clear "
                        + "all intents registered during the dispatch loop");
    }

    /**
     * Even with a conflict-deny (path pre-populated by another session), the
     * finally-block release must still reclaim the current session's
     * registered-but-denied intent. The externally-pre-populated
     * other-session intent survives because it is outside the engine's
     * session lifecycle.
     */
    @Test
    void registryReleasesDeniedSessionIntent() {
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        // Pre-populate the registry with an other-session intent so the
        // engine's session observes a conflict and is denied.
        String targetPath = io.nop.ai.agent.security.DefaultPathAccessChecker
                .normalizePathStatic(new java.io.File("/tmp/lifecycle-deny.txt").getAbsolutePath());
        registry.registerAndGetConflicting(new io.nop.ai.agent.conflict.WriteIntent(
                "external-session", "ext-agent", targetPath, "edit-file",
                System.currentTimeMillis()));

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "lc_2",
                        Map.of("path", "/tmp/lifecycle-deny.txt")),
                stubToolManager());
        engine.setWriteIntentRegistry(registry);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // The engine's session registered its own (denied) intent during
        // the dispatch loop; the finally block must release it. The
        // external-session intent remains because it was not registered
        // through the engine's lifecycle.
        assertTrue(registry.getIntentsForPath(targetPath).size() == 1,
                "after a denied session terminates, only the externally-pre-populated "
                        + "other-session intent should remain on the path");
        assertTrue(registry.getIntentsForPath(targetPath).iterator().next()
                        .getSessionId().equals("external-session"),
                "the surviving intent must be the external session's, not the engine's");
    }
}
