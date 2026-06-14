package io.nop.ai.agent.talent;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
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

import java.util.ArrayList;
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

public class TestTalentInReActLoop {

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

    private static IChatService plainAssistantChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Done.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static boolean optionsContainTool(ChatOptions options, String toolName) {
        if (options == null || options.getTools() == null) {
            return false;
        }
        for (ChatToolDefinition def : options.getTools()) {
            if (toolName.equals(def.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean messagesContainSystemInstruction(List<ChatMessage> messages, String fragment) {
        if (messages == null) {
            return false;
        }
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatSystemMessage && msg.getContent() != null && msg.getContent().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void defaultNoTalentsRunsUnchanged() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger chatCallCount = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                if (chatCallCount.getAndIncrement() == 0) {
                    firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // No talent instructions injected: only the user message the test ctx carries.
        assertNotNull(firstRequestMessages.get());
        boolean hasSystemInstruction = firstRequestMessages.get().stream()
                .anyMatch(m -> m instanceof ChatSystemMessage);
        assertFalse(hasSystemInstruction, "No system messages should be present with no talents and no agent prompt");
    }

    @Test
    void noOpTalentDefaultDoesNotInject() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .talents(List.of(NoOpTalent.noOp()))
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // NoOpTalent contributes no tools, so only the agent-declared "echo" tool remains.
        assertTrue(optionsContainTool(firstRequestOptions.get(), "echo"));
        assertFalse(optionsContainTool(firstRequestOptions.get(), "should-not-exist"));
    }

    @Test
    void supportingTalentInjectsInstructionAndTools() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger attachCount = new AtomicInteger(0);
        AtomicBoolean supportedCalled = new AtomicBoolean(false);
        ITalent supportingTalent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                supportedCalled.set(true);
                return true;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
                attachCount.incrementAndGet();
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return "TALENT-INSTRUCTION-FRAGMENT";
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("talent_tool");
            }
        };

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .talents(List.of(supportingTalent))
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(supportedCalled.get(), "Admission gate isSupported() must be consulted");
        assertEquals(1, attachCount.get(), "onAttach() must be invoked exactly once for a supporting talent");
        assertTrue(messagesContainSystemInstruction(firstRequestMessages.get(), "TALENT-INSTRUCTION-FRAGMENT"),
                "Talent instruction fragment must reach the LLM system context");
        assertTrue(optionsContainTool(firstRequestOptions.get(), "talent_tool"),
                "Talent-provided tool must appear in tool definitions sent to the LLM");
    }

    @Test
    void nonSupportingTalentInjectsNothingAndCallbackNotInvoked() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger attachCount = new AtomicInteger(0);
        ITalent nonSupportingTalent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return false;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
                attachCount.incrementAndGet();
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return "SHOULD-NOT-APPEAR";
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("secret_tool");
            }
        };

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .talents(List.of(nonSupportingTalent))
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(0, attachCount.get(), "onAttach() must never be invoked for a non-supporting talent");
        assertFalse(messagesContainSystemInstruction(firstRequestMessages.get(), "SHOULD-NOT-APPEAR"),
                "Non-supporting talent instruction must not be injected");
        assertFalse(optionsContainTool(firstRequestOptions.get(), "secret_tool"),
                "Non-supporting talent tools must not be injected");
    }

    @Test
    void talentProvidedToolIsInvocableThroughNormalPath() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ITalent supportingTalent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return true;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return null;
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("talent_tool");
            }
        };

        ChatToolCall talentToolCall = new ChatToolCall();
        talentToolCall.setId("call_1");
        talentToolCall.setName("talent_tool");
        talentToolCall.setArguments(Map.of("input", "hello"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Using talent tool.");
                    msg.setToolCalls(List.of(talentToolCall));
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

        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicReference<String> toolCalledName = new AtomicReference<>();
        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolCallCount.incrementAndGet();
                toolCalledName.set(toolName);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "talent-tool-output"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .talents(List.of(supportingTalent))
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, toolCallCount.get(), "Talent-provided tool must be invoked through the normal tool-execution path");
        assertEquals("talent_tool", toolCalledName.get());
        assertEquals(2, chatCallCount.get(), "Two LLM calls: first triggers the tool call, second receives the final answer");
    }
}
