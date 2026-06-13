package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestModeDispatch {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService createMockChatService() {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("Hello!");
        ChatResponse response = ChatResponse.success(msg);

        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager createNoOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
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

    private DefaultAgentEngine createEngine() {
        return new DefaultAgentEngine(createMockChatService(), createNoOpToolManager());
    }

    @Test
    void testReactModeReturnsReActAgentExecutor() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode("react");

        IAgentExecutor executor = engine.resolveExecutor(model);

        assertInstanceOf(ReActAgentExecutor.class, executor,
                "react mode should return ReActAgentExecutor");
    }

    @Test
    void testNullModeReturnsReActAgentExecutor() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode(null);

        IAgentExecutor executor = engine.resolveExecutor(model);

        assertInstanceOf(ReActAgentExecutor.class, executor,
                "null mode should return ReActAgentExecutor (default)");
    }

    @Test
    void testEmptyModeReturnsReActAgentExecutor() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode("");

        IAgentExecutor executor = engine.resolveExecutor(model);

        assertInstanceOf(ReActAgentExecutor.class, executor,
                "empty mode should return ReActAgentExecutor (default)");
    }

    @Test
    void testSingleTurnModeReturnsSingleTurnExecutor() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode("single-turn");

        IAgentExecutor executor = engine.resolveExecutor(model);

        assertInstanceOf(SingleTurnExecutor.class, executor,
                "single-turn mode should return SingleTurnExecutor");
    }

    @Test
    void testPlanModeThrowsUnsupportedOperationException() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode("plan");

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> engine.resolveExecutor(model));

        assertTrue(ex.getMessage().contains("Plan execution mode is not yet implemented"),
                "Exception message should mention plan mode. Got: " + ex.getMessage());
    }

    @Test
    void testUnknownModeThrowsNopAiAgentException() {
        DefaultAgentEngine engine = createEngine();
        AgentModel model = new AgentModel();
        model.setName("test");
        model.setMode("unknown");

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resolveExecutor(model));

        assertTrue(ex.getMessage().contains("Unknown agent execution mode"),
                "Exception message should mention unknown mode. Got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("unknown"),
                "Exception message should contain the mode value. Got: " + ex.getMessage());
    }
}
