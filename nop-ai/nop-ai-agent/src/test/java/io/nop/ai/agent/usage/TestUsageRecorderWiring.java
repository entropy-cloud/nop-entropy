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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.support.ChatResponseFixtures;

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

    // ========================================================================
    // MA6.3-AR-4: NoOp default is observable (one-shot WARN at first
    // execution), and a Builder-wired functional recorder never produces the
    // spurious WARN (wiring-timing guard).
    // ========================================================================

    @Test
    void noOpDefaultEmitsUsageRecorderWarnAtExecution() throws Exception {
        Logger engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        engineLogger.addAppender(appender);
        try {
            DefaultAgentEngine engine = new DefaultAgentEngine(
                    singleTurnChatService("hello"), noOpToolManager());
            assertTrue(engine.getUsageRecorder() instanceof NoOpUsageRecorder,
                    "Shipped default must be the NoOp pass-through");

            AgentMessageRequest request = new AgentMessageRequest("test-agent", "hi");
            AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(AgentExecStatus.completed, result.getStatus());

            boolean warnSeen = appender.list.stream()
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage() != null
                            && e.getFormattedMessage().contains("NoOpUsageRecorder"));
            assertTrue(warnSeen,
                    "NoOp usage-recorder default must emit a WARN at first execution "
                            + "(MA6.3-AR-4 — no silent metering gap). Messages: "
                            + appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                                    .collect(Collectors.toList()));
        } finally {
            engineLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void builderWiredFunctionalRecorderDoesNotEmitUsageRecorderWarn() throws Exception {
        Logger engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        engineLogger.addAppender(appender);
        try {
            RecordingUsageRecorder recorder = new RecordingUsageRecorder();
            DefaultAgentEngine engine = DefaultAgentEngine.builder(
                            singleTurnChatService("hello"), noOpToolManager())
                    .usageRecorder(recorder)
                    .build();

            AgentMessageRequest request = new AgentMessageRequest("test-agent", "hi");
            AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(AgentExecStatus.completed, result.getStatus());

            // Anti-false-positive: the Builder path wires the recorder AFTER
            // construction; the WARN must fire only when the FINAL resolved
            // recorder is NoOp — never for a Builder-wired functional one.
            boolean warnSeen = appender.list.stream()
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage() != null
                            && e.getFormattedMessage().contains("NoOpUsageRecorder"));
            assertFalse(warnSeen,
                    "Builder-wired functional recorder must NOT produce the NoOp usage-recorder WARN. Messages: "
                            + appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                                    .collect(Collectors.toList()));
        } finally {
            engineLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void simpleUsageRecorderEmitsStructuredLogLine() {
        Logger recorderLogger = (Logger) LoggerFactory.getLogger(SimpleUsageRecorder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        recorderLogger.addAppender(appender);
        try {
            SimpleUsageRecorder recorder = new SimpleUsageRecorder();
            UsageRecord record = new UsageRecord();
            record.setSessionId("s1");
            record.setAgentName("test-agent");
            record.setRequestId("req-1");
            record.setAiProvider("test-provider");
            record.setAiModel("test-model");
            record.setPromptTokens(100);
            record.setCompletionTokens(20);
            record.setResponseDurationMs(42L);
            record.setResponseTimestamp(12345L);
            recorder.record(record);

            List<ILoggingEvent> infos = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO
                            && e.getFormattedMessage() != null
                            && e.getFormattedMessage().contains("nop.ai.agent.usage-record"))
                    .collect(Collectors.toList());
            assertEquals(1, infos.size(),
                    "SimpleUsageRecorder must emit exactly one structured log line");
            String line = infos.get(0).getFormattedMessage();
            assertTrue(line.contains("sessionId=s1"), "line must carry sessionId: " + line);
            assertTrue(line.contains("agentName=test-agent"), "line must carry agentName: " + line);
            assertTrue(line.contains("requestId=req-1"), "line must carry requestId: " + line);
            assertTrue(line.contains("aiProvider=test-provider"), "line must carry aiProvider: " + line);
            assertTrue(line.contains("aiModel=test-model"), "line must carry aiModel: " + line);
            assertTrue(line.contains("promptTokens=100"), "line must carry promptTokens: " + line);
            assertTrue(line.contains("completionTokens=20"), "line must carry completionTokens: " + line);
        } finally {
            recorderLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private static IChatService singleTurnChatService(String content) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.success(
                        new ChatAssistantMessage(content)));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(new ChatAssistantMessage(content));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
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

        ChatResponse toolResponse = ChatResponseFixtures.assistantWithToolCalls("", toolCall);
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
