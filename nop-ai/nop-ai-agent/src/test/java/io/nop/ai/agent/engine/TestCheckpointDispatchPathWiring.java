package io.nop.ai.agent.engine;

import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end + wiring + backward-compat tests for the Layer 3-4
 * checkpoint dispatch-path integration. Verifies the full call chain:
 * {@link DefaultAgentEngine#execute} → {@link ReActAgentExecutor} dispatch loop
 * → tool execution → {@code checkpointManager.saveCheckpoint(...)} →
 * {@link ToolExecutionCheckpoint#getLatestCheckpoint(String)} retrieval.
 *
 * <p>Includes:
 * <ul>
 *   <li>Functional save→retrieve round-trip via {@link ToolExecutionCheckpoint}.</li>
 *   <li>Wiring verification: {@code saveCheckpoint(...)} is actually invoked
 *       after tool execution in the dispatch loop (Minimum Rules #23).</li>
 *   <li>End-to-end verification: {@code engine.execute(request)} → checkpoint
 *       recorded with correct tool payload (Minimum Rules #22 Anti-Hollow).</li>
 *   <li>Backward-compat: {@link NoOpCheckpoint} default → zero side effects,
 *       executor builds without a checkpoint manager.</li>
 *   <li>Engine setter/getter wiring + null fallback.</li>
 *   <li>Per-session isolation in the dispatch-loop integration.</li>
 * </ul>
 *
 * <p>Follows the {@code TestDispatchPathDenialLedger} sibling pattern.
 */
public class TestCheckpointDispatchPathWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    /**
     * LLM mock: returns scripted responses in order. The first response
     * carries tool calls; the second is a terminal message (no tool calls).
     */
    static final class RecordingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();
        final List<ChatResponse> scripted;

        RecordingChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int idx = Math.min(callCount.getAndIncrement(), scripted.size() - 1);
            return scripted.get(idx);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    private static ChatResponse assistantWithToolCalls(ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(calls));
        return ChatResponse.success(msg);
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private static ChatToolCall toolCall(String id, String name) {
        ChatToolCall c = new ChatToolCall();
        c.setId(id);
        c.setName(name);
        c.setArguments(Map.of("command", "echo hello"));
        return c;
    }

    private static IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "tool-output-" + toolName));
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

    // ========================================================================
    // Wiring verification: saveCheckpoint invoked after tool execution
    //            (Minimum Rules #23 Wiring Verification)
    // ========================================================================

    /**
     * Builder-based wiring verification: the checkpoint manager is passed via
     * the Builder and {@code saveCheckpoint(...)} is actually invoked in the
     * dispatch loop after tool execution completes. The functional
     * {@link ToolExecutionCheckpoint} records the checkpoint, so
     * {@code getLatestCheckpoint} returns a non-null result.
     */
    @Test
    void saveCheckpointInvokedViaBuilderWiring() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_w1", "echo")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("wiring-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "wiring-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        // Wiring verification: at least one checkpoint was recorded for the session.
        Checkpoint latest = mgr.getLatestCheckpoint("wiring-session");
        assertNotNull(latest,
                "checkpointManager.saveCheckpoint(...) must be invoked in the dispatch loop after tool execution (wiring verification)");
        assertEquals(CheckpointType.TOOL_EXECUTION, latest.getType());
        assertEquals("echo", latest.getToolName());
        assertEquals("call_w1", latest.getCallId());
    }

    /**
     * Multiple tool calls across iterations produce multiple checkpoints,
     * with the latest being the last tool executed. Also verifies the
     * per-execution seq counter increments.
     */
    @Test
    void multipleToolExecutionsProduceMultipleCheckpointsWithIncreasingSeq() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        // Iteration 1: one tool call "echo". Iteration 2: one tool call "ls".
        // Iteration 3: final message (no tool call).
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_m1", "echo")),
                assistantWithToolCalls(toolCall("call_m2", "ls")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("multi-tool-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "multi-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        // The latest checkpoint should be the second tool ("ls").
        Checkpoint latest = mgr.getLatestCheckpoint("multi-session");
        assertNotNull(latest);
        assertEquals("ls", latest.getToolName());
        assertEquals("call_m2", latest.getCallId());
        assertEquals(1, latest.getSeq(), "seq must increment per checkpoint within one execute() call");

        // The first checkpoint should be retrievable by watermark.
        // The watermark format is sessionId:tool:callId:seq
        Checkpoint first = mgr.getCheckpoint("multi-session:tool:call_m1:0");
        assertNotNull(first, "First checkpoint must be retrievable by watermark");
        assertEquals("echo", first.getToolName());
        assertEquals(0, first.getSeq());
    }

    // ========================================================================
    // End-to-end verification (Minimum Rules #22 Anti-Hollow)
    //   engine.execute(request) → tool call → saveCheckpoint → getLatestCheckpoint
    // ========================================================================

    /**
     * End-to-end: from the user entry point {@code engine.execute(request)},
     * through the ReAct loop, a tool call passes all security layers and
     * executes, after which {@code saveCheckpoint} records a checkpoint
     * carrying the correct tool payload. This proves the full path from entry
     * to checkpoint save/retrieve is connected.
     */
    @Test
    void endToEndEngineExecuteRecordsCheckpointWithToolPayload() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_e2e", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());
        engine.setCheckpointManager(mgr);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // The session completed.
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "Session must complete normally with the NoOp defaults for all other layers");

        // Extract the auto-generated sessionId from the result.
        String sessionId = result.getSessionId();
        assertNotNull(sessionId, "Result must carry a sessionId");

        // The checkpoint was recorded with the correct tool payload.
        Checkpoint latest = mgr.getLatestCheckpoint(sessionId);
        assertNotNull(latest,
                "engine.execute → dispatch loop → saveCheckpoint must record a checkpoint (end-to-end Anti-Hollow)");
        assertEquals(CheckpointType.TOOL_EXECUTION, latest.getType());
        assertEquals("echo", latest.getToolName());
        assertEquals("call_e2e", latest.getCallId());
        assertEquals("tool-output-echo", latest.getOutputSummary(),
                "Checkpoint output summary must match the tool result body");
        assertTrue(latest.getMessageCount() > 0,
                "Checkpoint messageCount must reflect the context size after tool execution");
    }

    // ========================================================================
    // Backward-compat: NoOpCheckpoint default → zero side effects
    // ========================================================================

    /**
     * Backward-compat: with the shipped {@link NoOpCheckpoint} default (never
     * explicitly registered), tool execution proceeds normally and the session
     * completes. No checkpoints are stored, no exceptions are thrown.
     */
    @Test
    void defaultNoOpCheckpointProducesNoSideEffects() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_bc", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());

        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "Default checkpoint manager must be NoOpCheckpoint");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "Session must complete normally with the NoOpCheckpoint default");
    }

    /**
     * A Builder-built executor without a checkpoint manager must still build
     * (NoOpCheckpoint default applied) and never throw during a dispatch loop.
     */
    @Test
    void builderDefaultsWhenCheckpointManagerNotSet() {
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_bd", "echo")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("bd-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "bd-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));

        // No exception thrown = default manager is sound.
        executor.execute(ctx).toCompletableFuture().join();
    }

    // ========================================================================
    // Engine setter/getter wiring + null fallback
    // ========================================================================

    @Test
    void engineSetGetCheckpointManagerAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("done"))),
                stubToolManager());

        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "Engine default checkpoint manager must be NoOpCheckpoint");

        ToolExecutionCheckpoint custom = new ToolExecutionCheckpoint();
        engine.setCheckpointManager(custom);
        assertTrue(engine.getCheckpointManager() == custom,
                "setCheckpointManager must wire the exact instance");

        // Null setter must fall back to the default, not silently store null.
        engine.setCheckpointManager(null);
        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "setCheckpointManager(null) must fall back to NoOpCheckpoint default");
    }

    // ========================================================================
    // Per-session isolation in dispatch-loop integration
    // ========================================================================

    /**
     * Two independent sessions executing through the same engine + checkpoint
     * manager produce isolated checkpoint lists — session A's checkpoint does
     * not appear in session B's getLatestCheckpoint.
     */
    @Test
    void perSessionCheckpointsAreIsolatedAcrossEngineExecutions() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        // Run session A with an explicit sessionId.
        DefaultAgentEngine engineA = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_iso_a", "echo")),
                        finalAssistant("done-a")
                )),
                stubToolManager());
        engineA.setCheckpointManager(mgr);

        AgentMessageRequest reqA = new AgentMessageRequest(
                "test-react-agent", "run", "iso-session-a", null, ChannelKind.WEBUI, Principal.user());
        engineA.execute(reqA).toCompletableFuture().join();

        // Run session B with a different sessionId.
        DefaultAgentEngine engineB = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_iso_b", "ls")),
                        finalAssistant("done-b")
                )),
                stubToolManager());
        engineB.setCheckpointManager(mgr);

        AgentMessageRequest reqB = new AgentMessageRequest(
                "test-react-agent", "run", "iso-session-b", null, ChannelKind.WEBUI, Principal.user());
        engineB.execute(reqB).toCompletableFuture().join();

        // Session A's latest is "echo", session B's latest is "ls".
        Checkpoint latestA = mgr.getLatestCheckpoint("iso-session-a");
        Checkpoint latestB = mgr.getLatestCheckpoint("iso-session-b");

        assertNotNull(latestA, "Session A must have a checkpoint");
        assertNotNull(latestB, "Session B must have a checkpoint");
        assertEquals("echo", latestA.getToolName(),
                "Session A's latest checkpoint must be its own tool, not session B's");
        assertEquals("ls", latestB.getToolName(),
                "Session B's latest checkpoint must be its own tool, not session A's");
    }
}
