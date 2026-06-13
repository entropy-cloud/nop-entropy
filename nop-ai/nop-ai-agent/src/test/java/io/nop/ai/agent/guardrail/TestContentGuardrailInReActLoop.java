package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContentGuardrailInReActLoop {

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

    @Test
    void inputGuardrailFiresBeforeLlmCall() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicBoolean inputChecked = new AtomicBoolean(false);
        AtomicBoolean outputChecked = new AtomicBoolean(false);

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.INPUT) {
                    inputChecked.set(true);
                }
                if (direction == GuardrailDirection.OUTPUT) {
                    outputChecked.set(true);
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .contentGuardrail(guardrail)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(inputChecked.get(), "INPUT guardrail should have fired before LLM call");
        assertTrue(outputChecked.get(), "OUTPUT guardrail should have fired after LLM response");
        assertEquals(1, chatCallCount.get());
    }

    @Test
    void outputGuardrailFiresAfterLlmResponseBeforeToolDispatch() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/test"));

        AtomicBoolean outputChecked = new AtomicBoolean(false);
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.OUTPUT) {
                    outputChecked.set(true);
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Reading file.");
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
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolExecuted.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .contentGuardrail(guardrail)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(outputChecked.get(), "OUTPUT guardrail should have been checked");
        assertTrue(toolExecuted.get(), "Tool should have been executed since guardrail passed");
    }

    @Test
    void blockResultFromInputPreventsLlmInvocation() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.INPUT) {
                    return new GuardrailResult.BlockResult("dangerous input");
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicBoolean llmCalled = new AtomicBoolean(false);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                llmCalled.set(true);
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Should not reach here.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .contentGuardrail(guardrail)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertFalse(llmCalled.get(), "LLM should not have been called when INPUT guardrail blocks");

        boolean hasGuardrailBlock = ctx.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("Input blocked by content guardrail"));
        assertTrue(hasGuardrailBlock, "A guardrail block message should be present in context");
    }

    @Test
    void blockResultFromOutputPreventsToolDispatch() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/test"));

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.OUTPUT) {
                    return new GuardrailResult.BlockResult("unsafe output");
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Reading file.");
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
                return subscriber -> {};
            }
        };

        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolExecuted.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .contentGuardrail(guardrail)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertFalse(toolExecuted.get(), "Tool should not have been executed when OUTPUT guardrail blocks");

        boolean hasOutputBlock = ctx.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("Output blocked by content guardrail"));
        assertTrue(hasOutputBlock, "An output guardrail block message should be present in context");
    }

    @Test
    void modifyResultFromInputReplacesUserMessageContent() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.addMessage(new ChatUserMessage("original user input"));
        ctx.setMaxIterations(10);

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.INPUT) {
                    return new GuardrailResult.ModifyResult("sanitized input");
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicReference<String> capturedLlmInput = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                for (ChatMessage msg : request.getMessages()) {
                    if (msg instanceof ChatUserMessage) {
                        capturedLlmInput.set(msg.getContent());
                    }
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Response.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .contentGuardrail(guardrail)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals("sanitized input", capturedLlmInput.get(),
                "LLM should receive the modified content, not the original");
    }

    @Test
    void modifyResultFromOutputReplacesLlmResponseContent() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hello"));

        IContentGuardrail guardrail = new IContentGuardrail() {
            @Override
            public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
                if (direction == GuardrailDirection.OUTPUT) {
                    return new GuardrailResult.ModifyResult("modified output content");
                }
                return GuardrailResult.PassResult.instance();
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("original output");
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
                return subscriber -> {};
            }
        };

        AtomicReference<String> capturedAssistantContent = new AtomicReference<>();
        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                for (ChatMessage msg : ctx.getMessages()) {
                    if (msg instanceof ChatAssistantMessage) {
                        capturedAssistantContent.set(msg.getContent());
                    }
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "echo result"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .contentGuardrail(guardrail)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals("modified output content", capturedAssistantContent.get(),
                "Assistant message content should be modified by OUTPUT guardrail");
    }

    @Test
    void noOpDefaultDoesNotInterfereWithExistingBehavior() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hello"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Echoing.");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Echo done.");
                    resp = ChatResponse.success(msg);
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "hello"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());
    }
}
