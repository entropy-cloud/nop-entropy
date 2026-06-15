package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatOptions;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 209 Phase 1 wiring test (Minimum Rules #23): injects a
 * {@link SmartModelRouter} into the ReAct loop (the exact injection path
 * {@code DefaultAgentEngine} delegates to via
 * {@code ReActAgentExecutor.Builder.modelRouter(...)}) and asserts that
 * {@code route()} is actually called at runtime and that the routed model
 * selection reaches the {@code chatService.call(...)} request.
 */
public class TestSmartModelRouterWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
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
            AiToolModel model = new AiToolModel();
            model.setName(toolName);
            model.setDescription("Mock tool: " + toolName);
            return model;
        }
    }

    private static ChatOptions tier(String provider, String model) {
        ChatOptions o = new ChatOptions();
        o.setProvider(provider);
        o.setModel(model);
        return o;
    }

    private static SmartModelRouter threeTierRouter() {
        return SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("cheap", "cheap-model"))
                .tierModel(Complexity.MEDIUM, tier("mid", "mid-model"))
                .tierModel(Complexity.COMPLEX, tier("strong", "strong-model"))
                .build();
    }

    private AgentExecutionContext newContext(String userMessage) {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        io.nop.ai.core.model.ChatOptionsModel chatOptionsModel = new io.nop.ai.core.model.ChatOptionsModel();
        chatOptionsModel.setModel("default-model");
        chatOptionsModel.setProvider("default");
        model.setChatOptions(chatOptionsModel);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "wiring-session");
        ctx.setMaxIterations(10);
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage(userMessage));
        return ctx;
    }

    private IChatService capturingChatService(AtomicReference<ChatOptions> captured) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                captured.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("ok.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    @Test
    void simpleMessageIsRoutedToCheapModelInReActLoop() {
        AtomicReference<ChatOptions> captured = new AtomicReference<>();
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(capturingChatService(captured))
                .toolManager(new StubToolManager() {})
                .modelRouter(threeTierRouter())
                .build();

        executor.execute(newContext("hi")).toCompletableFuture().join();

        assertNotNull(captured.get(), "route() must have been called and options reached chatService");
        assertEquals("cheap-model", captured.get().getModel(),
                "A simple message must be routed to the cheap model");
        assertEquals("cheap", captured.get().getProvider());
    }

    @Test
    void complexMessageIsRoutedToStrongModelInReActLoop() {
        AtomicReference<ChatOptions> captured = new AtomicReference<>();
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(capturingChatService(captured))
                .toolManager(new StubToolManager() {})
                .modelRouter(threeTierRouter())
                .build();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            sb.append('x');
        }
        executor.execute(newContext(sb.toString())).toCompletableFuture().join();

        assertNotNull(captured.get());
        assertEquals("strong-model", captured.get().getModel(),
                "A complex (long) message must be routed to the strong model");
        assertEquals("strong", captured.get().getProvider());
    }
}
