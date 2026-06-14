package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentModel;
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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for {@link AgentToolExecuteContext}:
 * <ul>
 *     <li>Fields populated correctly (engine, messenger, sessionId, agentName)</li>
 *     <li>Backward-compatible with {@link IToolExecuteContext} interface</li>
 *     <li>Wiring verification: the enriched context constructed by
 *         {@link ReActAgentExecutor} carries the same engine instance and
 *         session ID as the active execution</li>
 * </ul>
 */
public class TestAgentToolExecuteContext {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void contextFieldsPopulatedCorrectly() {
        IAgentEngine engine = new StubEngine();
        IAgentMessenger messenger = NoOpAgentMessenger.noOp();

        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("/tmp"),
                Map.of("KEY", "val"),
                12345L,
                null,
                null,
                null,
                engine,
                messenger,
                "sess-1",
                "my-agent");

        assertEquals(new File("/tmp"), ctx.getWorkDir());
        assertEquals("val", ctx.getEnvs().get("KEY"));
        assertEquals(12345L, ctx.getExpireAt());
        assertSame(engine, ctx.getEngine());
        assertSame(messenger, ctx.getMessenger());
        assertEquals("sess-1", ctx.getSessionId());
        assertEquals("my-agent", ctx.getAgentName());
    }

    @Test
    void contextBackwardCompatibleWithInterface() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), null, 0L, null, null, null,
                null, null, "s", "a");

        IToolExecuteContext asInterface = ctx;
        assertNotNull(asInterface.getWorkDir());
        assertNotNull(asInterface.getEnvs());
        assertTrue(asInterface.getEnvs().isEmpty());
    }

    @Test
    void contextNullEnvsDefaultedToEmptyMap() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                null, null, 0L, null, null, null, null, null, null, null);
        assertNotNull(ctx.getEnvs());
        assertTrue(ctx.getEnvs().isEmpty());
    }

    @Test
    void reactExecutorPassesEngineSessionIdAndAgentNameToTools() {
        IAgentEngine engine = new StubEngine();
        IAgentMessenger messenger = NoOpAgentMessenger.noOp();

        AtomicReference<AgentToolExecuteContext> capturedCtx = new AtomicReference<>();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("capture-tool");
        Map<String, Object> args = new HashMap<>();
        args.put("x", "1");
        toolCall.setArguments(args);

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse(callCount, toolCall));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse(callCount, toolCall);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = new CapturingToolManager(capturedCtx);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .engine(engine)
                .messenger(messenger)
                .build();

        AgentModel agentModel = new AgentModel();
        agentModel.setName("wired-agent");
        agentModel.setTools(Set.of("capture-tool"));
        AgentExecutionContext execCtx = AgentExecutionContext.create(agentModel, "session-xyz");
        execCtx.setMaxIterations(5);

        executor.execute(execCtx).toCompletableFuture().join();

        AgentToolExecuteContext captured = capturedCtx.get();
        assertNotNull(captured, "Tool should have been called and context captured");
        assertSame(engine, captured.getEngine(),
                "Enriched context must carry the same engine instance passed to the Builder");
        assertSame(messenger, captured.getMessenger(),
                "Enriched context must carry the same messenger instance passed to the Builder");
        assertEquals("session-xyz", captured.getSessionId(),
                "Enriched context must carry the active execution's sessionId");
        assertEquals("wired-agent", captured.getAgentName(),
                "Enriched context must carry the agent model's name");
    }

    @Test
    void reactExecutorWithoutEngineProducesContextWithNullEngine() {
        AtomicReference<AgentToolExecuteContext> capturedCtx = new AtomicReference<>();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("capture-tool");
        toolCall.setArguments(new HashMap<>(Map.of("x", "1")));

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse(callCount, toolCall));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse(callCount, toolCall);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = new CapturingToolManager(capturedCtx);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        AgentModel agentModel = new AgentModel();
        agentModel.setTools(Set.of("capture-tool"));
        AgentExecutionContext execCtx = AgentExecutionContext.create(agentModel, "session-no-engine");
        execCtx.setMaxIterations(5);

        executor.execute(execCtx).toCompletableFuture().join();

        AgentToolExecuteContext captured = capturedCtx.get();
        assertNotNull(captured);
        assertNull(captured.getEngine(),
                "When no engine is wired, the context engine is null (engine-aware tools must fail fast)");
    }

    private static ChatResponse buildResponse(AtomicInteger callCount, ChatToolCall toolCall) {
        int n = callCount.getAndIncrement();
        ChatAssistantMessage msg = new ChatAssistantMessage();
        if (n == 0) {
            msg.setContent("");
            msg.setToolCalls(List.of(toolCall));
        } else {
            msg.setContent("done");
        }
        return ChatResponse.success(msg);
    }

    static class CapturingToolManager implements IToolManager {
        final AtomicReference<AgentToolExecuteContext> capturedCtx;

        CapturingToolManager(AtomicReference<AgentToolExecuteContext> capturedCtx) {
            this.capturedCtx = capturedCtx;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            assertInstanceOf(AgentToolExecuteContext.class, context,
                    "ReAct executor should construct AgentToolExecuteContext");
            capturedCtx.set((AgentToolExecuteContext) context);
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
            AiToolModel model = new AiToolModel();
            model.setName(toolName);
            model.setDescription("Mock tool: " + toolName);
            return model;
        }
    }

    static class StubEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            return null;
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            return null;
        }
    }
}
