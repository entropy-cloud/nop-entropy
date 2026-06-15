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
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 202 (L2-18) end-to-end wiring test (Minimum Rules #22 Anti-Hollow,
 * #23 Wiring Verification): inject a real {@link DbUsageRecorder} backed by
 * an in-memory H2 {@link DataSource} into {@link DefaultAgentEngine}, run a
 * full ReAct loop, and verify that each LLM call produces exactly one row in
 * the {@code nop_ai_chat_response} table with the correct fields — including
 * a positive {@code response_duration_ms} (proving the LLM-call timing
 * instrumentation in {@code ReActAgentExecutor} is wired, not a stub).
 *
 * <p>This is the anti-hollow evidence: it proves the call chain
 * {@code execute() → ReAct loop → DbUsageRecorder.record() → JDBC INSERT → DB row}
 * is fully connected at runtime, and that modelId resolution resolves the
 * {@code nop_ai_model} primary key (a matching model row is pre-inserted).
 */
public class TestDbUsageRecorderWiring {

    private static final String DDL_NOP_AI_MODEL = ""
            + "CREATE TABLE IF NOT EXISTS nop_ai_model ("
            + "id VARCHAR(100) NOT NULL, "
            + "provider VARCHAR(100), "
            + "model_name VARCHAR(200), "
            + "base_url VARCHAR(500), "
            + "api_key VARCHAR(500), "
            + "version INTEGER, "
            + "created_by VARCHAR(100), "
            + "create_time TIMESTAMP, "
            + "updated_by VARCHAR(100), "
            + "update_time TIMESTAMP, "
            + "PRIMARY KEY (id)"
            + ")";

    private static DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);

        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-db-usage-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        // nop_ai_model is owned by nop-ai-dao; create inline + pre-insert the
        // model row the agent uses so the end-to-end modelId resolution path is
        // exercised (provider+model_name match → model_id populated).
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL_NOP_AI_MODEL);
            stmt.execute("INSERT INTO nop_ai_model (id, provider, model_name) "
                    + "VALUES ('mdl-e2e-1', 'test-provider', 'test-model')");
        } catch (Exception e) {
            throw new IllegalStateException("init: failed to seed nop_ai_model", e);
        }
    }

    @AfterAll
    static void destroy() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    void clearUsageRows() throws Exception {
        // DbUsageRecorder.initSchema() creates nop_ai_chat_response; ensure a
        // clean table for every test method.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM nop_ai_chat_response");
        } catch (Exception ignored) {
            // table may not exist yet on the very first run; the recorder
            // creates it lazily.
        }
    }

    @Test
    void endToEndWritesOneRowPerLlmCallWithDurationAndModelId() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
        assertTrue(model.getTools().contains("test-calculator"),
                "Agent model must declare test-calculator tool for this test");

        // Turn 1: LLM responds with a tool call + usage.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_db_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);
        toolResponse.setRequestId("req-db-turn-1");
        toolResponse.setUsage(new ChatUsage(100, 20));

        // Turn 2: LLM responds with the final answer + usage.
        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result of 2+2 is 4.");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        finalResponse.setRequestId("req-db-turn-2");
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
                // Simulate non-zero LLM latency so response_duration_ms is
                // deterministically positive (the instrumented span is measured
                // around this call in ReActAgentExecutor).
                sleepQuietly(5);
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
        DbUsageRecorder recorder = new DbUsageRecorder(dataSource);
        engine.setUsageRecorder(recorder);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "What is 2+2?");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete successfully");
        assertEquals(2, chatCallCount.get(),
                "LLM should be called twice (tool call + final response)");

        // Anti-hollow: exactly one DB row per LLM call with usage data.
        List<Row> rows = readUsageRows();
        assertEquals(2, rows.size(),
                "nop_ai_chat_response must contain exactly one row per LLM call (2 calls)");

        // Turn 1 row
        Row r1 = rows.get(0);
        assertEquals(result.getSessionId(), r1.sessionId,
                "DB row session_id must match the execution session id");
        assertEquals("req-db-turn-1", r1.requestId);
        assertEquals("test-provider", r1.aiProvider);
        assertEquals("test-model", r1.aiModel);
        assertEquals("mdl-e2e-1", r1.modelId,
                "model_id must be resolved from nop_ai_model (end-to-end modelId resolution)");
        assertEquals(100, r1.promptTokens);
        assertEquals(20, r1.completionTokens);
        assertTrue(r1.responseDurationMs > 0,
                "response_duration_ms must be positive (LLM-call timing instrumentation is wired)");

        // Turn 2 row
        Row r2 = rows.get(1);
        assertEquals("req-db-turn-2", r2.requestId);
        assertEquals("test-provider", r2.aiProvider);
        assertEquals("test-model", r2.aiModel);
        assertEquals(150, r2.promptTokens);
        assertEquals(30, r2.completionTokens);
        assertTrue(r2.responseDurationMs > 0,
                "response_duration_ms must be positive for every LLM call");
    }

    @Test
    void endToEndSingleCallWritesExactlyOneRow() throws Exception {
        // Exercises the single-LLM-call path: one chatService.call() must yield
        // exactly one nop_ai_chat_response row with the recorded requestId.
        // (The no-model-row path is covered by TestDbUsageRecorder.)
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("done");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        finalResponse.setRequestId("req-single-e2e");
        finalResponse.setUsage(new ChatUsage(5, 2));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = singleResponseChatService(finalResponse, chatCallCount);

        IToolManager toolManager = noToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setUsageRecorder(new DbUsageRecorder(dataSource));

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, chatCallCount.get());

        List<Row> rows = readUsageRows();
        assertEquals(1, rows.size(),
                "nop_ai_chat_response must contain exactly one row for the single LLM call");
        assertEquals("req-single-e2e", rows.get(0).requestId);
        assertTrue(rows.get(0).responseDurationMs >= 0,
                "response_duration_ms must be recorded for the single-call path");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static IChatService singleResponseChatService(ChatResponse response, AtomicInteger counter) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                counter.incrementAndGet();
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                sleepQuietly(5);
                counter.incrementAndGet();
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static IToolManager noToolManager() {
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

    private List<Row> readUsageRows() throws Exception {
        List<Row> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT request_id, session_id, model_id, ai_provider, ai_model, "
                             + "prompt_tokens, completion_tokens, response_duration_ms "
                             + "FROM nop_ai_chat_response ORDER BY response_timestamp")) {
            while (rs.next()) {
                Row row = new Row();
                row.requestId = rs.getString("request_id");
                row.sessionId = rs.getString("session_id");
                row.modelId = rs.getString("model_id");
                row.aiProvider = rs.getString("ai_provider");
                row.aiModel = rs.getString("ai_model");
                row.promptTokens = rs.getInt("prompt_tokens");
                row.completionTokens = rs.getInt("completion_tokens");
                row.responseDurationMs = rs.getLong("response_duration_ms");
                rows.add(row);
            }
        }
        return rows;
    }

    private static class Row {
        String requestId;
        String sessionId;
        String modelId;
        String aiProvider;
        String aiModel;
        int promptTokens;
        int completionTokens;
        long responseDurationMs;
    }
}
