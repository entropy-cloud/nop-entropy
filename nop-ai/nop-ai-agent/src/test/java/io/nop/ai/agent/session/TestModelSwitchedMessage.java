package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 205 (L2-21) Phase 2 focused tests covering the five required scenarios
 * for model-switched audit message generation in the ReAct loop (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.5):
 *
 * <ol>
 *   <li>First iteration: {@code lastModelKey} is null → no message produced</li>
 *   <li>Model unchanged: consecutive iterations same model → no message</li>
 *   <li>Model changed: second iteration model differs → message with correct metadata</li>
 *   <li>{@code PassThroughModelRouter}: never changes model → no message</li>
 *   <li>Message not injected into LLM context: role=80 message absent from
 *       the messages sent to the LLM</li>
 * </ol>
 *
 * <p>These tests use {@link ReActAgentExecutor} directly (not
 * {@code DefaultAgentEngine}) to exercise the ReAct loop in isolation with a
 * capturing {@link IModelSwitchedMessageWriter} test-double.
 */
public class TestModelSwitchedMessage {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Capturing test-double that records every
     * {@link IModelSwitchedMessageWriter#writeModelSwitched} invocation.
     */
    static final class CapturingWriter implements IModelSwitchedMessageWriter {
        final List<CapturedSwitch> switches = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void writeModelSwitched(String sessionId, String fromModel, String toModel,
                                       String routingReason, String complexity, long seq) {
            switches.add(new CapturedSwitch(sessionId, fromModel, toModel, routingReason, complexity, seq));
        }
    }

    static final class CapturedSwitch {
        final String sessionId;
        final String fromModel;
        final String toModel;
        final String routingReason;
        final String complexity;
        final long seq;

        CapturedSwitch(String sessionId, String fromModel, String toModel,
                       String routingReason, String complexity, long seq) {
            this.sessionId = sessionId;
            this.fromModel = fromModel;
            this.toModel = toModel;
            this.routingReason = routingReason;
            this.complexity = complexity;
            this.seq = seq;
        }
    }

    private abstract static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
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
            AiToolModel m = new AiToolModel();
            m.setName(toolName);
            m.setDescription("Mock tool: " + toolName);
            return m;
        }
    }

    private static IChatService singleTurnChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Done.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Done.");
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    // ========================================================================
    // Scenario 1: first iteration — lastModelKey is null → no message
    // ========================================================================

    @Test
    void firstIterationProducesNoModelSwitchedMessage() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-s1");
        ctx.setMaxIterations(10);

        CapturingWriter writer = new CapturingWriter();

        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                return new RoutingResult(options, null, "test");
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(singleTurnChatService())
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .modelSwitchedMessageWriter(writer)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(0, writer.switches.size(),
                "First iteration (lastModelKey=null) must not produce a model-switched message");
    }

    // ========================================================================
    // Scenario 2: model unchanged across iterations → no message
    // ========================================================================

    @Test
    void sameModelAcrossIterationsProducesNoMessage() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-s2");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_s2");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = twoTurnChatService(toolCall, chatCallCount);

        CapturingWriter writer = new CapturingWriter();

        // Router always returns the same model (no switch)
        IModelRouter router = constantRouter("openai", "gpt-4o", "stable");

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .modelSwitchedMessageWriter(writer)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(2, chatCallCount.get(), "Two LLM calls expected (tool call + final)");
        assertEquals(0, writer.switches.size(),
                "Same model across iterations must not produce a model-switched message");
    }

    // ========================================================================
    // Scenario 3: model changed between iterations → message with correct metadata
    // ========================================================================

    @Test
    void modelChangedProducesMessageWithCorrectMetadata() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-s3");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_s3");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = twoTurnChatService(toolCall, chatCallCount);

        CapturingWriter writer = new CapturingWriter();

        // Router returns model A on first call, model B on second call
        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                ChatOptions routed = options.copy();
                if (ctx.getCurrentIteration() == 0) {
                    routed.setProvider("openai");
                    routed.setModel("gpt-4o");
                    return new RoutingResult(routed, "simple", "complexity-based");
                } else {
                    routed.setProvider("anthropic");
                    routed.setModel("claude-3");
                    return new RoutingResult(routed, "complex", "budget-downgrade");
                }
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .modelSwitchedMessageWriter(writer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, chatCallCount.get(), "Two LLM calls expected");
        assertEquals(1, writer.switches.size(),
                "Model change between iterations must produce exactly one model-switched message");

        CapturedSwitch sw = writer.switches.get(0);
        assertEquals("session-s3", sw.sessionId);
        assertEquals("openai:gpt-4o", sw.fromModel,
                "fromModel must be the first iteration's model key");
        assertEquals("anthropic:claude-3", sw.toModel,
                "toModel must be the second iteration's model key");
        assertEquals("complex", sw.complexity,
                "complexity must come from RoutingResult of the switching iteration");
        assertEquals("budget-downgrade", sw.routingReason,
                "routingReason must come from RoutingResult of the switching iteration");
        assertEquals(1, sw.seq,
                "SEQ must start at 1 for the first model-switched message");
    }

    // ========================================================================
    // Scenario 4: PassThroughModelRouter never changes model → no message
    // ========================================================================

    @Test
    void passThroughRouterProducesNoModelSwitchedMessage() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-s4");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_s4");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = twoTurnChatService(toolCall, chatCallCount);

        CapturingWriter writer = new CapturingWriter();

        // No .modelRouter() → defaults to PassThroughModelRouter (same options every call)
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelSwitchedMessageWriter(writer)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(2, chatCallCount.get(), "Two LLM calls expected");
        assertEquals(0, writer.switches.size(),
                "PassThroughModelRouter (same options every call) must not produce a model-switched message");
    }

    // ========================================================================
    // Scenario 5: model-switched message not injected into LLM context
    // ========================================================================

    @Test
    void modelSwitchedMessageNotInjectedIntoLlmContext() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-s5");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_s5");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));

        AtomicInteger chatCallCount = new AtomicInteger(0);

        // Capture messages sent to LLM on each call
        List<List<ChatMessage>> messagesSentToLlm = Collections.synchronizedList(new ArrayList<>());

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                messagesSentToLlm.add(new ArrayList<>(request.getMessages()));
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Done.");
                    resp = ChatResponse.success(msg);
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        CapturingWriter writer = new CapturingWriter();

        // Router switches model between iterations
        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                ChatOptions routed = options.copy();
                if (ctx.getCurrentIteration() == 0) {
                    routed.setProvider("openai");
                    routed.setModel("gpt-4o");
                } else {
                    routed.setProvider("anthropic");
                    routed.setModel("claude-3");
                }
                return new RoutingResult(routed, null, "switch-test");
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .modelSwitchedMessageWriter(writer)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(1, writer.switches.size(),
                "Model switch must have been detected and written");

        // The model-switched audit message must NOT appear in ctx.getMessages()
        // nor in any ChatRequest messages sent to the LLM. ctx.getMessages()
        // should contain only the normal LLM exchange messages (no role=80
        // audit message injected). We verify by checking that no message
        // content contains the "model-switched:" prefix used by the writer.
        for (ChatMessage msg : ctx.getMessages()) {
            String content = msg.getContent();
            assertNotNull(content);
            assertTrue(!content.contains("model-switched:"),
                    "Model-switched audit message must not be injected into ctx.getMessages()."
                            + " Found content containing 'model-switched:': " + content);
        }
        for (List<ChatMessage> llmMessages : messagesSentToLlm) {
            for (ChatMessage msg : llmMessages) {
                String content = msg.getContent();
                if (content != null) {
                    assertTrue(!content.contains("model-switched:"),
                            "Model-switched audit message must not appear in LLM request messages."
                                    + " Found: " + content);
                }
            }
        }

        // ctx.getMessages() should have exactly the normal exchange:
        // [assistant(tool-call), tool-response, assistant(final)] = 3 messages
        // (no system/user prompt added in this direct-executor test path)
        assertEquals(3, ctx.getMessages().size(),
                "ctx.getMessages() must contain only the normal LLM exchange, no audit message");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static IChatService twoTurnChatService(ChatToolCall toolCall, AtomicInteger chatCallCount) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Done.");
                    resp = ChatResponse.success(msg);
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static IModelRouter constantRouter(String provider, String model, String reason) {
        return new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                ChatOptions routed = options.copy();
                routed.setProvider(provider);
                routed.setModel(model);
                return new RoutingResult(routed, null, reason);
            }
        };
    }
}
