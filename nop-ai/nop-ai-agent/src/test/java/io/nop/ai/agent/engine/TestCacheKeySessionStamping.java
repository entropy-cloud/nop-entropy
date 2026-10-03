package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 2302 (WI20): the engine stamps the agent session id onto every LLM
 * request's options — the convergence point (resolveRoutedOptions) covers the
 * regular iteration path, and SingleTurnExecutor creates a minimal options
 * carrying it. The session id is what the per-provider cache-key placement
 * keys off (OpenAI prompt_cache_key / session-affinity header).
 */
public class TestCacheKeySessionStamping {

    private static final String SESSION_ID = "s-cache-key";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class RecordingChatService implements io.nop.ai.api.chat.IChatService {
        final ConcurrentLinkedQueue<ChatResponse> steps = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<io.nop.ai.api.chat.ChatRequest> requests =
                new ConcurrentLinkedQueue<>();

        void enqueue(ChatResponse response) {
            steps.add(response);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(io.nop.ai.api.chat.ChatRequest request,
                                                       ICancelToken cancelToken) {
            requests.add(request);
            ChatResponse r = steps.poll();
            if (r == null) {
                throw new AssertionError("script exhausted");
            }
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    @Test
    void regularExecutionStampsSessionIdOnEveryLlmRequest() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        RecordingChatService chat = new RecordingChatService();
        chat.enqueue(ChatResponseFixtures.assistantText("done one"));
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, newTestToolManager(), store);

        engine.execute(new AgentMessageRequest("test-react-agent", "first",
                SESSION_ID, java.util.Collections.emptyMap())).get(30, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, chat.requests.size());
        io.nop.ai.api.chat.ChatRequest request = chat.requests.peek();
        assertNotNull(request.getOptions(), "routed options must be present");
        assertEquals(SESSION_ID, request.getOptions().getSessionId(),
                "the agent session id is stamped for cache-key placement");
    }

    @Test
    void singleTurnExecutionStampsSessionId() throws Exception {
        RecordingChatService chat = new RecordingChatService();
        chat.enqueue(ChatResponseFixtures.assistantText("single done"));
        SingleTurnExecutor executor = new SingleTurnExecutor(chat, new DefaultAgentEventPublisher());

        AgentExecutionContext ctx = AgentExecutionContext.create(new io.nop.ai.agent.model.AgentModel(), SESSION_ID);
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hello"));
        executor.execute(ctx).toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, chat.requests.size());
        io.nop.ai.api.chat.ChatRequest request = chat.requests.peek();
        assertNotNull(request.getOptions(), "single-turn options created from scratch");
        assertEquals(SESSION_ID, request.getOptions().getSessionId());
    }

    private static io.nop.ai.toolkit.api.IToolManager newTestToolManager() {
        return new io.nop.ai.toolkit.api.IToolManager() {
            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                    String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                    io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException("no tools");
            }

            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException("no tools");
            }

            @Override
            public java.util.List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
                return java.util.List.of();
            }

            @Override
            public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
                return null;
            }
        };
    }
}
