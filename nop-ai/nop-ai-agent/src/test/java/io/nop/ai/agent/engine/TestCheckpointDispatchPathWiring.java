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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

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

        // Wiring verification: checkpoints were recorded for the session. With
        // plan 187, LLM_TURN checkpoints are emitted after each LLM response
        // too, so the latest checkpoint is the trailing LLM_TURN from the
        // final "done" response (not the TOOL_EXECUTION). Both types must be
        // present.
        List<Checkpoint> all = mgr.getCheckpoints("wiring-session");
        assertFalse(all.isEmpty(),
                "checkpointManager.saveCheckpoint(...) must be invoked in the dispatch loop (wiring verification)");

        Checkpoint toolCp = all.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(toolCp, "A TOOL_EXECUTION checkpoint must be recorded after tool execution");
        assertEquals("echo", toolCp.getToolName());
        assertEquals("call_w1", toolCp.getCallId());

        assertTrue(all.stream().anyMatch(c -> c.getType() == CheckpointType.LLM_TURN),
                "An LLM_TURN checkpoint must be recorded after each LLM response (plan 187)");
    }

    /**
     * Multiple tool calls across iterations produce multiple checkpoints,
     * with the latest being the last tool executed. Also verifies the
     * per-execution seq counter increments.
     */
    @Test
    void multipleToolExecutionsProduceMultipleCheckpointsWithIncreasingSeq() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

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

        // With plan 187, the checkpoint sequence across one execute() call is:
        //   LLM_TURN(0) -> TOOL_EXECUTION(1, echo) -> LLM_TURN(2)
        //   -> TOOL_EXECUTION(3, ls) -> LLM_TURN(4)
        // All three trigger-point types share the per-execution counter.
        List<Checkpoint> all = mgr.getCheckpoints("multi-session");
        assertFalse(all.isEmpty());

        List<Checkpoint> toolCps = all.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, toolCps.size(), "Two TOOL_EXECUTION checkpoints (echo, ls) must be recorded");

        // First tool checkpoint is "echo", second is "ls".
        assertEquals("echo", toolCps.get(0).getToolName());
        assertEquals("call_m1", toolCps.get(0).getCallId());
        assertEquals("ls", toolCps.get(1).getToolName());
        assertEquals("call_m2", toolCps.get(1).getCallId());

        // seq must monotonically increase across ALL checkpoint types.
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getSeq() > all.get(i - 1).getSeq(),
                    "seq must monotonically increase across checkpoint types");
        }

        // The TOOL_EXECUTION checkpoints must still be retrievable by their
        // watermark (the format embeds an exec-start-time disambiguator so it
        // is unique across execute() calls). Use the checkpoint's own
        // watermark to verify round-trip retrieval rather than hardcoding it.
        Checkpoint first = mgr.getCheckpoint(toolCps.get(0).getWatermark());
        assertNotNull(first, "First TOOL_EXECUTION checkpoint must be retrievable by watermark");
        assertEquals("echo", first.getToolName());
        Checkpoint second = mgr.getCheckpoint(toolCps.get(1).getWatermark());
        assertNotNull(second, "Second TOOL_EXECUTION checkpoint must be retrievable by watermark");
        assertEquals("ls", second.getToolName());
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
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

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
        List<Checkpoint> all = mgr.getCheckpoints(sessionId);
        assertFalse(all.isEmpty(),
                "engine.execute -> dispatch loop -> saveCheckpoint must record checkpoints (end-to-end Anti-Hollow)");
        Checkpoint toolCp = all.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(toolCp, "A TOOL_EXECUTION checkpoint must be recorded for the echo tool");
        assertEquals("echo", toolCp.getToolName());
        assertEquals("call_e2e", toolCp.getCallId());
        assertEquals("tool-output-echo", toolCp.getOutputSummary(),
                "Checkpoint output summary must match the tool result body");
        assertTrue(toolCp.getMessageCount() > 0,
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
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

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

        // Session A's TOOL_EXECUTION is "echo", session B's is "ls". Each
        // session's checkpoint list is isolated.
        List<Checkpoint> allA = mgr.getCheckpoints("iso-session-a");
        List<Checkpoint> allB = mgr.getCheckpoints("iso-session-b");

        Checkpoint toolA = allA.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        Checkpoint toolB = allB.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);

        assertNotNull(toolA, "Session A must have a TOOL_EXECUTION checkpoint");
        assertNotNull(toolB, "Session B must have a TOOL_EXECUTION checkpoint");
        assertEquals("echo", toolA.getToolName(),
                "Session A's TOOL_EXECUTION checkpoint must be its own tool, not session B's");
        assertEquals("ls", toolB.getToolName(),
                "Session B's TOOL_EXECUTION checkpoint must be its own tool, not session A's");

        // No cross-session contamination of watermarks.
        for (Checkpoint c : allA) {
            assertTrue(!allB.contains(c), "Session A's checkpoints must not appear in session B");
        }
    }
}
