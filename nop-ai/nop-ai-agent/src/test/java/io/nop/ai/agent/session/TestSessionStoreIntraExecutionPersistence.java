package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 anti-hollow wiring test: verifies that the
 * {@link FileBackedSessionStore} receives {@code save} calls
 * <b>during</b> the ReAct dispatch loop (intra-execution persistence), not
 * only at the end of {@code doExecute}. This is the core crash-survival
 * property — if a crash occurs mid-execution (between a tool completion and
 * the final flush), the on-disk session file must already contain the
 * completed tool messages so the restore path can rebuild the context.
 *
 * <p>Verification strategy: a custom {@link IChatService} inspects the session
 * file during its <b>second</b> call (after the first tool has completed). If
 * the file exists and contains the tool-response message at that point,
 * intra-execution persistence has fired. Without intra-execution save, the
 * file would either not exist or contain only the pre-execution state (empty
 * messages) at that point.
 */
public class TestSessionStoreIntraExecutionPersistence {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TempDir
    Path tempDir;

    @Test
    void intraExecutionSaveWritesFileBeforeSecondLlmCall() throws Exception {
        Path root = tempDir.resolve("intra-exec");

        // The chat service inspects the session file during its 2nd call
        AtomicBoolean fileHadToolResponseDuringSecondCall = new AtomicBoolean(false);
        AtomicInteger callIndex = new AtomicInteger(0);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_intra_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("The result is 4.");

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                int idx = callIndex.getAndIncrement();
                if (idx == 1) {
                    // Second call: the first tool has completed and the
                    // intra-execution save should have written the session
                    // file. Check the file content.
                    Path sessionFile = root.resolve("session-intra")
                            .resolve(FileBackedSessionStore.SESSION_FILE_NAME);
                    if (Files.exists(sessionFile)) {
                        try {
                            String content = Files.readString(sessionFile);
                            if (content.contains("tool-response-from-first-call")) {
                                fileHadToolResponseDuringSecondCall.set(true);
                            }
                        } catch (Exception e) {
                            // ignore read errors — assertion below will fail
                        }
                    }
                }
                if (idx == 0) {
                    return ChatResponse.success(toolMsg);
                }
                return ChatResponse.success(finalMsg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(
                        AiToolCallResult.successResult(0, "tool-response-from-first-call"));
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

        FileBackedSessionStore store = new FileBackedSessionStore(root);
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager, store);

        CompletableFuture<AgentExecutionResult> future = engine.execute(
                new AgentMessageRequest("test-react-agent", "What is 2+2?", "session-intra", null));
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution must complete normally (single tool call + final message)");

        assertTrue(fileHadToolResponseDuringSecondCall.get(),
                "Intra-execution save must have written the session file with the tool response "
                        + "BEFORE the second LLM call. Without intra-execution persistence, the file "
                        + "would not contain the tool response at this point.");

        // Final verification: after execution, the session has no duplicate
        // messages (intra-execution + post-execution replaceMessages unify).
        AgentSession session = store.get("session-intra");
        long toolResponseCount = session.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .count();
        assertEquals(1, toolResponseCount,
                "Exactly one tool-response message must exist — no duplicates from intra + post sync");
    }

    @Test
    void noDuplicateMessagesWithFileBackedStoreMultiTurn() throws Exception {
        Path root = tempDir.resolve("no-dup");

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_dup_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "1+1"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("Result is 2.");

        AtomicInteger idx = new AtomicInteger(0);
        List<ChatResponse> responses = List.of(
                ChatResponse.success(toolMsg),
                ChatResponse.success(finalMsg));

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(idx.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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

        FileBackedSessionStore store = new FileBackedSessionStore(root);
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager, store);

        engine.execute(new AgentMessageRequest("test-react-agent", "calc 1+1", "session-dup", null))
                .get(30, TimeUnit.SECONDS);

        // After execution, the session's message count must equal what was in
        // the ctx at the end of execution (no duplicate appends from the two
        // sync paths — intra-execution save + post-execution replaceMessages).
        AgentSession session = store.get("session-dup");
        // Expected messages: [system, user, assistant(tool_call), tool_response, assistant(final)]
        // test-react-agent has a <prompt> → system message is prepended.
        assertEquals(5, session.getMessageCount(),
                "Session must have exactly 5 messages (system + user + assistant_tool + tool_response + assistant_final). "
                        + "Got: " + session.getMessageCount());

        // Cross-instance reload: same count (no duplicates persisted)
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("session-dup");
        assertEquals(session.getMessageCount(), restored.getMessageCount(),
                "Cross-instance reload must show the same message count (no duplicates in persisted state)");
    }
}
