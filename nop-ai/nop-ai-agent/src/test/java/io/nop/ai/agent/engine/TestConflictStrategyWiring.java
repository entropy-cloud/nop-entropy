package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.ConflictDecision;
import io.nop.ai.agent.conflict.ConflictResult;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.WriteIntent;
import io.nop.ai.agent.security.ChannelKind;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 214 Phase 2 wiring test: verifies that the dispatch-path →
 * {@code IConflictStrategy.resolve()} call chain is actually connected
 * (Minimum Rules #23 Wiring Verification). A test-double
 * {@link RecordingStrategy} records every {@code resolve()} call; the test
 * asserts that driving a single-session {@code execute()} with a path-arg
 * tool call triggers at least one {@code resolve()} invocation and that
 * the single-session case returns ALLOW (no spurious conflict denial).
 */
public class TestConflictStrategyWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Test-double strategy: records every {@code resolve()} invocation and
     * the size of the {@code existing} set each time. Always returns ALLOW
     * so the dispatch path proceeds (the wiring assertion is about whether
     * {@code resolve()} was called at all, not about its decision).
     */
    static final class RecordingStrategy implements IConflictStrategy {
        final AtomicInteger resolveCount = new AtomicInteger();
        final AtomicInteger lastExistingSize = new AtomicInteger(-1);

        @Override
        public ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing) {
            resolveCount.incrementAndGet();
            lastExistingSize.set(existing.size());
            return ConflictResult.allow(name());
        }

        @Override
        public String name() {
            return "RecordingStrategy";
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

    // ========================================================================
    // Wiring Verification (Minimum Rules #23): resolve() is actually called
    // ========================================================================

    @Test
    void resolveIsInvokedForPathArgToolCall() {
        RecordingStrategy strategy = new RecordingStrategy();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "wc_1",
                        Map.of("path", "/tmp/wiring-target.txt")),
                stubToolManager());
        engine.setConflictStrategy(strategy);
        engine.setWriteIntentRegistry(registry);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "write a file", null, null,
                ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring Verification: resolve() must have been called at least once
        // during the dispatch loop — proving the dispatch-path → conflict
        // strategy call chain is connected end-to-end.
        assertTrue(strategy.resolveCount.get() >= 1,
                "conflictStrategy.resolve(...) must be invoked in the dispatch loop for a path-arg tool call; "
                        + "actual resolveCount=" + strategy.resolveCount.get());

        // Single-session execution: registry had no other-session intents,
        // so existing must have been empty → ALLOW (no spurious denial).
        assertEquals(0, strategy.lastExistingSize.get(),
                "single-session execute() must observe an empty existing-intents set");
        assertFalse(containsMessage(result, "Conflict denied"),
                "single-session execute() must not produce a conflict denial");
    }

    // ========================================================================
    // No path arg → resolve() not called (registry is not consulted)
    // ========================================================================

    @Test
    void resolveNotInvokedWhenNoPathArg() {
        RecordingStrategy strategy = new RecordingStrategy();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("noop-tool", "np_1",
                        Map.of("command", "echo hi")),
                stubToolManager());
        engine.setConflictStrategy(strategy);
        engine.setWriteIntentRegistry(registry);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "noop", null, null,
                ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // A tool call with no path-arg key must not trigger conflict detection
        // at all — the registry is never consulted, resolve() is never called.
        assertEquals(0, strategy.resolveCount.get(),
                "resolve() must NOT be invoked when the tool call has no path-arg key; "
                        + "actual resolveCount=" + strategy.resolveCount.get());
    }

    // ========================================================================
    // Engine setter/getter wiring + null fallback
    // ========================================================================

    @Test
    void engineDefaultsAreFailFastAndInMemory() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "df_1",
                        Map.of("path", "/tmp/df.txt")),
                stubToolManager());

        assertTrue(engine.getConflictStrategy() instanceof io.nop.ai.agent.conflict.FailFastStrategy,
                "default conflict strategy must be FailFastStrategy (plan 214)");
        assertTrue(engine.getWriteIntentRegistry() instanceof InMemoryWriteIntentRegistry,
                "default write-intent registry must be InMemoryWriteIntentRegistry (plan 214)");
    }

    @Test
    void engineSetGetConflictStrategyAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "sg_1",
                        Map.of("path", "/tmp/sg.txt")),
                stubToolManager());

        RecordingStrategy custom = new RecordingStrategy();
        engine.setConflictStrategy(custom);
        assertTrue(engine.getConflictStrategy() == custom,
                "setConflictStrategy must wire the exact instance");

        engine.setConflictStrategy(null);
        assertTrue(engine.getConflictStrategy() instanceof io.nop.ai.agent.conflict.FailFastStrategy,
                "setConflictStrategy(null) must fall back to FailFastStrategy (plan 214)");
    }

    @Test
    void engineSetGetWriteIntentRegistryAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("edit-file", "sg_2",
                        Map.of("path", "/tmp/sg2.txt")),
                stubToolManager());

        InMemoryWriteIntentRegistry custom = new InMemoryWriteIntentRegistry();
        engine.setWriteIntentRegistry(custom);
        assertTrue(engine.getWriteIntentRegistry() == custom,
                "setWriteIntentRegistry must wire the exact instance");

        engine.setWriteIntentRegistry(null);
        assertTrue(engine.getWriteIntentRegistry() instanceof InMemoryWriteIntentRegistry,
                "setWriteIntentRegistry(null) must fall back to InMemoryWriteIntentRegistry (plan 214)");
    }
}
