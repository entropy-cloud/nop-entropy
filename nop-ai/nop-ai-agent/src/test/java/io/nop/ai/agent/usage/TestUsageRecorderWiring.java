package io.nop.ai.agent.usage;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUsage;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 201 (L2-17) Phase 1 end-to-end wiring test (Minimum Rules #22, #23):
 * inject a counting {@link IUsageRecorder} test-double into
 * {@link DefaultAgentEngine}, run a full ReAct loop, and verify that
 * {@link IUsageRecorder#record} is invoked once per LLM call at the token
 * accumulation point with correctly populated {@link UsageRecord} fields.
 *
 * <p>This is the anti-hollow check evidence: it proves the record() call is
 * reached at runtime (not dead wiring) and that the fields threaded from the
 * response usage + routed options are correct.
 */
public class TestUsageRecorderWiring {

    /**
     * Test-double that captures every {@link UsageRecord} handed to
     * {@link #record} for post-execution assertion.
     */
    static final class RecordingUsageRecorder implements IUsageRecorder {
        final List<UsageRecord> records = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void record(UsageRecord record) {
            records.add(record);
        }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void engineDefaultsToNoOpUsageRecorder() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IUsageRecorder recorder = engine.getUsageRecorder();
        assertNotNull(recorder, "Engine must default to a non-null usage recorder");
        assertTrue(recorder instanceof NoOpUsageRecorder,
                "Shipped default must be the NoOpUsageRecorder pass-through");
    }

    @Test
    void setUsageRecorderOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        RecordingUsageRecorder custom = new RecordingUsageRecorder();
        engine.setUsageRecorder(custom);
        assertSameRecorder(custom, engine.getUsageRecorder());
    }

    @Test
    void setUsageRecorderNullFallsBackToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setUsageRecorder(null);
        IUsageRecorder recorder = engine.getUsageRecorder();
        assertNotNull(recorder, "null setter must fall back to a non-null NoOp default");
        assertTrue(recorder instanceof NoOpUsageRecorder);
    }

    @Test
    void recordInvokedOncePerLlmCallWithCorrectFields() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
        assertTrue(model.getTools().contains("test-calculator"),
                "Agent model must declare test-calculator tool for this test");

        // Turn 1: LLM responds with a tool call + usage.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_usage_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);
        toolResponse.setRequestId("req-turn-1");
        toolResponse.setUsage(new ChatUsage(100, 20));

        // Turn 2: LLM responds with the final answer + usage.
        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result of 2+2 is 4.");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        finalResponse.setRequestId("req-turn-2");
        finalResponse.setUsage(new ChatUsage(150, 30));

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
                return subscriber -> {
                };
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
        RecordingUsageRecorder recorder = new RecordingUsageRecorder();
        engine.setUsageRecorder(recorder);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 2+2?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete successfully");
        assertEquals(2, chatCallCount.get(),
                "LLM should be called twice (tool call + final response)");

        // Anti-hollow check: record() must be invoked exactly once per LLM call
        // that returned usage data.
        assertEquals(2, recorder.records.size(),
                "usageRecorder.record() must be called once per LLM call (2 calls)");

        // Turn 1 record
        UsageRecord r1 = recorder.records.get(0);
        assertEquals(result.getSessionId(), r1.getSessionId(),
                "UsageRecord sessionId must match the execution session id");
        assertEquals("test-react-agent", r1.getAgentName(),
                "UsageRecord agentName must match the agent model name");
        assertEquals("req-turn-1", r1.getRequestId(),
                "UsageRecord requestId must match response.getRequestId()");
        assertEquals("test-provider", r1.getAiProvider(),
                "UsageRecord aiProvider must match routedOptions.getProvider()");
        assertEquals("test-model", r1.getAiModel(),
                "UsageRecord aiModel must match routedOptions.getModel()");
        assertEquals(100, r1.getPromptTokens(),
                "UsageRecord promptTokens must match response usage (turn 1)");
        assertEquals(20, r1.getCompletionTokens(),
                "UsageRecord completionTokens must match response usage (turn 1)");
        assertTrue(r1.getResponseTimestamp() > 0,
                "UsageRecord responseTimestamp must be populated");

        // Turn 2 record
        UsageRecord r2 = recorder.records.get(1);
        assertEquals(result.getSessionId(), r2.getSessionId());
        assertEquals("test-react-agent", r2.getAgentName());
        assertEquals("req-turn-2", r2.getRequestId());
        assertEquals("test-provider", r2.getAiProvider());
        assertEquals("test-model", r2.getAiModel());
        assertEquals(150, r2.getPromptTokens(),
                "UsageRecord promptTokens must match response usage (turn 2)");
        assertEquals(30, r2.getCompletionTokens(),
                "UsageRecord completionTokens must match response usage (turn 2)");
    }

    private static void assertSameRecorder(IUsageRecorder expected, IUsageRecorder actual) {
        // Avoid needing a public assertSame import path; both must be identical.
        assertTrue(expected == actual,
                "getUsageRecorder must return the exact instance set via setUsageRecorder");
    }

    private static IChatService noOpChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }
        };
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return null;
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
                return null;
            }
        };
    }
}
