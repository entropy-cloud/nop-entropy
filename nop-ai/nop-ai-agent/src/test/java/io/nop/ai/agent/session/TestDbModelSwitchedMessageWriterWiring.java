package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
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
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * Plan 205 (L2-21) Phase 2 end-to-end wiring test (Minimum Rules #22
 * Anti-Hollow, #23 Wiring Verification): inject a real
 * {@link DbModelSwitchedMessageWriter} backed by an in-memory H2
 * {@link DataSource} and a functional {@link IModelRouter} that switches
 * models between iterations into {@link DefaultAgentEngine}, run a full ReAct
 * loop, and verify that the model-switched audit message is persisted to the
 * {@code nop_ai_session_message} table with the correct metadata.
 *
 * <p>This is the anti-hollow evidence: it proves the call chain
 * {@code DefaultAgentEngine.execute() → ReAct loop → IModelRouter.route()
 * → model-switch detection → DbModelSwitchedMessageWriter.writeModelSwitched()
 * → JDBC INSERT → DB row} is fully connected at runtime.
 */
public class TestDbModelSwitchedMessageWriterWiring {

    private static DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);

        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-model-switched-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterAll
    static void destroy() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // dataSource close failure during test teardown is not actionable
            }
        }
        CoreInitialization.destroy();
    }

    @Test
    void endToEndModelSwitchProducesAuditMessageInDb() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
        assertTrue(model.getTools().contains("test-calculator"),
                "Agent model must declare test-calculator tool for this test");

        // Turn 1: LLM responds with a tool call + usage.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_switch_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);
        toolResponse.setRequestId("req-switch-turn-1");
        toolResponse.setUsage(new ChatUsage(100, 20));

        // Turn 2: LLM responds with the final answer + usage.
        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result of 2+2 is 4.");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        finalResponse.setRequestId("req-switch-turn-2");
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

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                IToolExecuteContext context) {
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

        // Functional router: returns the agent's default model on iteration 0,
        // then switches to a different model on iteration 1.
        IModelRouter switchingRouter = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, 
                                       io.nop.ai.agent.engine.AgentExecutionContext ctx) {
                ChatOptions routed = options.copy();
                if (ctx.getCurrentIteration() == 0) {
                    // Keep the agent's default model (test-provider:test-model)
                    routed.setProvider("test-provider");
                    routed.setModel("test-model");
                    return new RoutingResult(routed, "simple", "initial");
                } else {
                    // Switch to a different model
                    routed.setProvider("anthropic");
                    routed.setModel("claude-3-opus");
                    return new RoutingResult(routed, "complex", "complexity-upgrade");
                }
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.DefaultToolAccessChecker(),
                new io.nop.ai.agent.security.DefaultPathAccessChecker(),
                io.nop.ai.agent.guardrail.NoOpContentGuardrail.noOp(),
                switchingRouter);
        engine.setModelSwitchedMessageWriter(new DbModelSwitchedMessageWriter(dataSource));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 2+2?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete successfully");
        assertEquals(2, chatCallCount.get(),
                "LLM should be called twice (tool call + final response)");

        // Anti-hollow: verify the model-switched audit message was persisted
        List<SwitchRow> rows = readSwitchedMessages(result.getSessionId());
        assertEquals(1, rows.size(),
                "Exactly one model-switched message must be persisted (model changed once)");

        SwitchRow row = rows.get(0);
        assertEquals(result.getSessionId(), row.sessionId,
                "DB row session_id must match the execution session id");
        assertEquals(80, row.role,
                "role must be MESSAGE_TYPE_MODEL_SWITCHED (80)");
        assertEquals(1, row.seq,
                "seq must be 1 (first model-switched message in this execution)");
        assertNotNull(row.metadata, "metadata must be populated");

        // Verify the metadata JSON contains the correct from/to models
        assertTrue(row.metadata.contains("test-provider:test-model"),
                "metadata fromModel must be the first iteration's model key");
        assertTrue(row.metadata.contains("anthropic:claude-3-opus"),
                "metadata toModel must be the second iteration's model key");
        assertTrue(row.metadata.contains("complexity-upgrade"),
                "metadata routingReason must be the switching iteration's reason");
        assertTrue(row.metadata.contains("complex"),
                "metadata complexity must be the switching iteration's complexity");
    }

    @Test
    void endToEndNoModelSwitchProducesNoAuditMessage() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");

        // Turn 1: tool call
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_no_switch");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "3+3"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);
        toolResponse.setRequestId("req-no-switch-1");
        toolResponse.setUsage(new ChatUsage(80, 10));

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("6");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        finalResponse.setRequestId("req-no-switch-2");
        finalResponse.setUsage(new ChatUsage(120, 5));

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

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "6"));
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
                return m;
            }
        };

        // PassThrough router (engine default) — no model switch
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setModelSwitchedMessageWriter(new DbModelSwitchedMessageWriter(dataSource));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 3+3?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // No model switch → no audit message
        List<SwitchRow> rows = readSwitchedMessages(result.getSessionId());
        assertEquals(0, rows.size(),
                "No model-switched message should be persisted when the model does not change");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private List<SwitchRow> readSwitchedMessages(String sessionId) throws Exception {
        List<SwitchRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT session_id, role, seq, metadata "
                             + "FROM nop_ai_session_message WHERE session_id = '"
                             + sessionId + "' ORDER BY seq")) {
            while (rs.next()) {
                SwitchRow row = new SwitchRow();
                row.sessionId = rs.getString("session_id");
                row.role = rs.getInt("role");
                row.seq = rs.getLong("seq");
                row.metadata = rs.getString("metadata");
                rows.add(row);
            }
        }
        return rows;
    }

    private static class SwitchRow {
        String sessionId;
        int role;
        long seq;
        String metadata;
    }
}
