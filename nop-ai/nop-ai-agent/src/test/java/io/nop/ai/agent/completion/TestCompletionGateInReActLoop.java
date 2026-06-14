package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.DefaultAgentEventPublisher;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompletionGateInReActLoop {

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

    private static ChatAssistantMessage assistantNoTools(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    private static ChatAssistantMessage assistantWithTools(String content, ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        msg.setToolCalls(List.of(calls));
        return msg;
    }

    @Test
    void noOpDefaultTerminatesLoopAsBefore() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("Done.")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, chatCallCount.get(), "NoOp judge should terminate the loop immediately on no-tool-calls");
    }

    @Test
    void continueDecisionInjectsUserMessageAndReEntersReasoning() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ICompletionJudge judge = new ICompletionJudge() {
            final AtomicInteger count = new AtomicInteger(0);

            @Override
            public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
                if (count.incrementAndGet() == 1) {
                    return new CompletionDecision.Continue("You have not finished, keep working");
                }
                return CompletionDecision.Complete.instance();
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> secondCallMessages = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                if (n == 1) {
                    secondCallMessages.set(new ArrayList<>(request.getMessages()));
                }
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("Now done.")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(judge)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, chatCallCount.get(), "LLM should be called twice: initial + re-entry after Continue");

        assertNotNull(secondCallMessages.get(), "Second LLM call messages should be captured");
        boolean hasContinuationUserMessage = secondCallMessages.get().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> "You have not finished, keep working".equals(m.getContent()));
        assertTrue(hasContinuationUserMessage,
                "Continuation message should be injected as a ChatUserMessage before the re-entry LLM call");
    }

    @Test
    void deadLoopProtectionForceExitsAfterThreeConsecutiveContinues() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(100);

        ICompletionJudge alwaysContinue = (msg, c) -> new CompletionDecision.Continue("keep going");

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("not done")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(alwaysContinue)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Dead-loop force-exit should set status to completed");
        assertEquals(4, chatCallCount.get(),
                "LLM should be called 4 times: 3 continuation injections then the 4th Continue hits the guard");
    }

    @Test
    void consecutiveContinueCounterResetsOnToolCallIterations() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(100);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));

        AtomicInteger judgeInvocations = new AtomicInteger(0);
        ICompletionJudge judge = (msg, c) -> {
            int n = judgeInvocations.incrementAndGet();
            if (n <= 4) {
                return new CompletionDecision.Continue("keep going");
            }
            return CompletionDecision.Complete.instance();
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 1) {
                    resp = ChatResponse.success(assistantWithTools("using tool", toolCall));
                } else {
                    resp = ChatResponse.success(assistantNoTools("no tools"));
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "echoed"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .completionJudge(judge)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(5, judgeInvocations.get(),
                "Judge should reach 5 invocations (Complete on 5th). Without counter reset it would force-exit at invocation 4.");
    }

    @Test
    void escalateExitsWithEscalatedStatusAndNoCompletionEvent() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ICompletionJudge escalateJudge = (msg, c) -> new CompletionDecision.Escalate("needs human approval before proceeding");

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("I am stuck.")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(escalateJudge)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "Escalate decision should set status to escalated");
        assertEquals("needs human approval before proceeding", result.getError(),
                "Escalate reason should be recorded in lastError");
        assertEquals("needs human approval before proceeding", ctx.getMetadata().get("completion.escalateReason"),
                "Escalate reason should be recorded in metadata");

        boolean hasCompletionEvent = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED);
        assertFalse(hasCompletionEvent,
                "EXECUTION_COMPLETED event should NOT be published for escalated status");

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED),
                "EXECUTION_STARTED should still be published");
    }

    @Test
    void completeFromCustomJudgeSetsCompletedStatus() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger judgeInvoked = new AtomicInteger(0);
        ICompletionJudge completeJudge = (msg, c) -> {
            judgeInvoked.incrementAndGet();
            return CompletionDecision.Complete.instance();
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.success(assistantNoTools("Here is the answer.")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .completionJudge(completeJudge)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, judgeInvoked.get(), "Judge should be invoked exactly once");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "EXECUTION_COMPLETED should be published for normal completion");
    }
}
