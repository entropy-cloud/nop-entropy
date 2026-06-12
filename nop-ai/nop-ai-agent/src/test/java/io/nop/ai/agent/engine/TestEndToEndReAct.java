package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
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
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEndToEndReAct {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void testLoadAgentModelWithToolsFromXml() {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");

        assertNotNull(model, "AgentModel should not be null");
        assertEquals("test-react-agent", model.getName());

        assertNotNull(model.getPrompt(), "Prompt should not be null");
        String promptText = model.getPrompt().getSource();
        assertNotNull(promptText);
        assertTrue(promptText.contains("calculator agent"), "Prompt should contain 'calculator agent'");

        assertNotNull(model.getChatOptions(), "ChatOptions should not be null");
        assertEquals("test-provider", model.getChatOptions().getProvider());
        assertEquals("test-model", model.getChatOptions().getModel());
        assertEquals(0.7f, model.getChatOptions().getTemperature(), 0.001f);

        assertNotNull(model.getTools(), "Tools should not be null");
        Set<String> tools = model.getTools();
        assertEquals(2, tools.size(), "Should have 2 tools");
        assertTrue(tools.contains("test-calculator"), "Should contain test-calculator");
        assertTrue(tools.contains("test-echo"), "Should contain test-echo");
    }

    @Test
    void testE2eReActLoopWithToolCalls() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
        assertNotNull(model.getTools());
        assertTrue(model.getTools().contains("test-calculator"),
                "Agent model must declare test-calculator tool for this test");

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_e2e_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result of 2+2 is 4.");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        AtomicInteger chatCallCount = new AtomicInteger(0);
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(chatCallCount.getAndIncrement()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(chatCallCount.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        AtomicInteger toolCallCount = new AtomicInteger(0);
        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                toolCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "4"));
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
                m.setDescription("Test tool: " + toolName);
                return m;
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 2+2?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete successfully");
        assertNotNull(result.getSessionId());
        assertEquals(1, result.getTotalIterations(),
                "Should have exactly 1 tool-call iteration");

        assertEquals(2, chatCallCount.get(),
                "LLM should be called twice (tool call + final response)");

        assertEquals(1, toolCallCount.get(),
                "Tool should be called exactly once");

        boolean hasToolResponse = result.getMessages().stream()
                .anyMatch(m -> m instanceof io.nop.ai.api.chat.messages.ChatToolResponseMessage);
        assertTrue(hasToolResponse, "Messages should contain a tool response");

        boolean hasFinalAssistant = result.getMessages().stream()
                .filter(m -> m instanceof ChatAssistantMessage)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("4"));
        assertTrue(hasFinalAssistant, "Messages should contain final assistant response with '4'");
    }

    @Test
    void testE2eEventPublishingInOrder() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_evt_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "3*3"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result is 9.");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        AtomicInteger chatCallCount = new AtomicInteger(0);
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(chatCallCount.getAndIncrement()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(chatCallCount.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "9"));
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
                m.setDescription("Test tool: " + toolName);
                return m;
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 3*3?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED),
                "Should have EXECUTION_STARTED event");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.LLM_RESPONSE_RECEIVED),
                "Should have LLM_RESPONSE_RECEIVED event");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_STARTED),
                "Should have TOOL_CALL_STARTED event");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED),
                "Should have TOOL_CALL_COMPLETED event");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "Should have EXECUTION_COMPLETED event");

        int startedIdx = indexOf(events, AgentEventType.EXECUTION_STARTED);
        int llmIdx = indexOf(events, AgentEventType.LLM_RESPONSE_RECEIVED);
        int toolStartedIdx = indexOf(events, AgentEventType.TOOL_CALL_STARTED);
        int toolCompletedIdx = indexOf(events, AgentEventType.TOOL_CALL_COMPLETED);
        int completedIdx = indexOf(events, AgentEventType.EXECUTION_COMPLETED);

        assertTrue(startedIdx < llmIdx,
                "EXECUTION_STARTED should come before LLM_RESPONSE_RECEIVED");
        assertTrue(llmIdx < toolStartedIdx,
                "LLM_RESPONSE_RECEIVED should come before TOOL_CALL_STARTED");
        assertTrue(toolStartedIdx < toolCompletedIdx,
                "TOOL_CALL_STARTED should come before TOOL_CALL_COMPLETED");
        assertTrue(toolCompletedIdx < completedIdx,
                "TOOL_CALL_COMPLETED should come before EXECUTION_COMPLETED");

        AgentEvent toolStartedEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.TOOL_CALL_STARTED)
                .findFirst().orElse(null);
        assertNotNull(toolStartedEvent);
        assertEquals("test-calculator", toolStartedEvent.getPayload().get("toolName"));
    }

    private int indexOf(List<AgentEvent> events, AgentEventType type) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getEventType() == type) {
                return i;
            }
        }
        return -1;
    }
}
