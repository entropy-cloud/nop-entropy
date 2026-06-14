package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
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
import io.nop.ai.toolkit.model.AiAgentCallResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.util.ICancelToken;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end tests:
 * <ul>
 *     <li>call-agent in ReAct loop: LLM emits call-agent → executor →
 *         engine.execute → sub-agent runs → result returns as tool response →
 *         parent agent incorporates it</li>
 *     <li>send-message delivery: send-message → messenger.send → registered
 *         handler receives envelope with correct payload</li>
 *     <li>Backward compatibility: engine without explicitly registered
 *         call-agent/send-message tools still works</li>
 * </ul>
 */
public class TestCallAgentSendMessageEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService createMockChatService(ChatResponse response) {
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
                return subscriber -> {
                };
            }
        };
    }

    /**
     * Creates a tool manager that resolves call-agent and send-message to the
     * functional executors, wiring them with the engine from the context.
     */
    private IToolManager createToolManagerWithCallAgentAndSendMessage() {
        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        SendMessageExecutor sendMessageExecutor = new SendMessageExecutor();

        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("call-agent".equals(toolName)) {
                    return callAgentExecutor.executeAsync(call, context).toCompletableFuture();
                }
                if ("send-message".equals(toolName)) {
                    return sendMessageExecutor.executeAsync(call, context).toCompletableFuture();
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
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

    /**
     * End-to-end: parent agent's ReAct loop emits a call-agent tool call →
     * functional executor invokes the engine on a sub-session for a target
     * agent → sub-agent executes → result flows back as the tool response →
     * parent agent incorporates it.
     *
     * <p>This test proves the full path and the anti-hollow check: the
     * sub-agent actually executes (real AgentExecutionResult with sub-agent
     * output), NOT the hollow mock's hardcoded string.
     */
    @Test
    void callAgentInReActLoopFullEndToEnd() throws Exception {
        String subAgentResponse = "SUB_AGENT_RESULT_42";

        IChatService chatService = new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
                int n = callCount.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n == 0) {
                    msg.setContent("");
                    ChatToolCall toolCall = new ChatToolCall();
                    toolCall.setId("e2e-call-1");
                    toolCall.setName("call-agent");
                    Map<String, Object> args = new HashMap<>();
                    args.put("agentId", "test-agent");
                    args.put("input", "what is the answer?");
                    toolCall.setArguments(args);
                    msg.setToolCalls(List.of(toolCall));
                } else if (n == 1) {
                    msg.setContent(subAgentResponse);
                } else {
                    msg.setContent("The sub-agent replied with the answer.");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgentAndSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "delegate to sub-agent");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        boolean hasToolResponse = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains(subAgentResponse));
        assertTrue(hasToolResponse,
                "The sub-agent's actual response must flow back as the tool response. Messages: "
                        + result.getMessages());

        boolean toolCallStarted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_STARTED
                        && "call-agent".equals(e.getPayload().get("toolName")));
        assertTrue(toolCallStarted, "TOOL_CALL_STARTED event for call-agent should be published");

        boolean toolCallCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED
                        && "call-agent".equals(e.getPayload().get("toolName"))
                        && "success".equals(e.getPayload().get("status")));
        assertTrue(toolCallCompleted, "TOOL_CALL_COMPLETED event for call-agent should be published with success status");
    }

    /**
     * End-to-end: send-message delivers to a registered inbox handler.
     * send-message → messenger.send → registered handler receives the envelope
     * with correct payload.
     */
    @Test
    void sendMessageDeliversToRegisteredHandlerEndToEnd() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        IMessageSubscription subscription = messenger.registerHandler(
                AgentMessageTopics.inboxTopic("e2e-target-sess"),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        received.set(envelope);
                        return null;
                    }
                });

        String senderResponse = "Message sent.";
        IChatService chatService = new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
                int n = callCount.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n == 0) {
                    msg.setContent("");
                    ChatToolCall toolCall = new ChatToolCall();
                    toolCall.setId("e2e-send-1");
                    toolCall.setName("send-message");
                    Map<String, Object> args = new HashMap<>();
                    args.put("targetSessionId", "e2e-target-sess");
                    args.put("input", "async hello from parent");
                    toolCall.setArguments(args);
                    msg.setToolCalls(List.of(toolCall));
                } else {
                    msg.setContent(senderResponse);
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgentAndSendMessage();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "send a message to target");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        AgentMessageEnvelope envelope = received.get();
        assertNotNull(envelope, "Handler should have received the message envelope");
        assertEquals("async hello from parent", envelope.getPayload(),
                "Handler should receive the exact message payload");
        assertEquals(AgentMessageKind.ASYNC, envelope.getKind());

        subscription.cancel();
    }

    /**
     * Backward compatibility: engine constructed without explicitly
     * registering call-agent/send-message tools still works for normal
     * execution (the tools are simply absent; existing tests pass).
     */
    @Test
    void engineWithoutExplicitToolRegistrationStillWorks() throws Exception {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("plain response");
        IChatService chatService = createMockChatService(ChatResponse.success(msg));

        IToolManager noOpToolManager = new IToolManager() {
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
                return model;
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, noOpToolManager);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Engine without call-agent/send-message registration should still work normally");
    }
}
