package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.compact.CompactionContext;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 187 tests for the two new checkpoint trigger points:
 * {@link CheckpointType#LLM_TURN} (emitted after each successful LLM response)
 * and {@link CheckpointType#COMPACTION} (emitted after actual context
 * compaction). Verifies emission conditions, field completeness, shared
 * checkpointSeq interleaving, intra-execution persistence, NoOp defaults, and
 * the forced-stop compaction path.
 *
 * <p>Follows the {@link TestCheckpointDispatchPathWiring} sibling pattern.
 */
public class TestCheckpointTriggersLLMTurnAndCompaction {

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

    private static ChatResponse failureResponse(String error) {
        return ChatResponse.error("test-error", error);
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

    private static io.nop.ai.agent.model.AgentModel agentModel(String name) {
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName(name);
        return model;
    }

    /**
     * A compactor that returns a CompactionResult with non-null
     * compactedMessages but tokensAfter >= tokensBefore (no reduction), so the
     * performCompaction branch skips replacement and does NOT emit a
     * COMPACTION checkpoint.
     */
    private static IContextCompactor noReductionCompactor() {
        return ctx -> new CompactionResult(
                ctx.getSessionId(), 100, 200, ctx.getMessages().size(), "no-reduce",
                new ArrayList<>(ctx.getMessages()));
    }

    /**
     * Pre-load a context with enough tool-result-bearing messages to exceed
     * DEFAULT_TRIGGER_MAX_MESSAGES (30), forcing shouldTriggerCompaction to
     * return true on the next reactLoop iteration.
     */
    private static void preloadMessagesForCompaction(AgentExecutionContext ctx, int turns) {
        for (int i = 0; i < turns; i++) {
            String id = "pre-tc-" + i;
            ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
            ChatToolCall tc = new ChatToolCall();
            tc.setId(id);
            tc.setName("bash");
            assistantMsg.setToolCalls(Collections.singletonList(tc));
            ctx.addMessage(assistantMsg);
            ctx.addMessage(new ChatToolResponseMessage(id, "bash", "X".repeat(5000)));
        }
    }

    // ========================================================================
    // Phase 1: LLM_TURN checkpoint emission
    // ========================================================================

    @Test
    void llmTurnEmittedOnSuccessfulLlmResponse() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("llm-turn-test"), "llm-session-1");
        ctx.addMessage(new ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        // Only 1 LLM turn (no tool calls), so exactly 1 checkpoint of type LLM_TURN.
        List<Checkpoint> all = mgr.getCheckpoints("llm-session-1");
        assertEquals(1, all.size(), "Exactly 1 LLM_TURN checkpoint (no tool calls)");
        assertEquals(CheckpointType.LLM_TURN, all.get(0).getType());
    }

    @Test
    void llmTurnNotEmittedOnFailure() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        RecordingChatService chat = new RecordingChatService(List.of(
                failureResponse("LLM unavailable")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("llm-fail-test"), "llm-fail-session");
        ctx.addMessage(new ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        // LLM failure → reactLoop breaks before LLM_TURN emission → no checkpoint.
        assertEquals(AgentExecStatus.failed, ctx.getStatus());
        List<Checkpoint> all = mgr.getCheckpoints("llm-fail-session");
        assertTrue(all.isEmpty(), "No checkpoint must be emitted on LLM failure");
        assertNull(mgr.getLatestCheckpoint("llm-fail-session"));
    }

    @Test
    void llmTurnFieldCompleteness() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("The answer is 42")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("llm-field-test"), "llm-field-session");
        ctx.addMessage(new ChatUserMessage("What is the answer?"));

        executor.execute(ctx).toCompletableFuture().join();

        Checkpoint cp = mgr.getLatestCheckpoint("llm-field-session");
        assertNotNull(cp);
        assertEquals(CheckpointType.LLM_TURN, cp.getType());
        assertNull(cp.getToolName(), "LLM_TURN toolName must be null");
        assertNull(cp.getCallId(), "LLM_TURN callId must be null");
        assertNull(cp.getInputSummary(), "LLM_TURN inputSummary must be null");
        assertNotNull(cp.getOutputSummary(), "LLM_TURN outputSummary must capture the assistant content");
        assertTrue(cp.getOutputSummary().contains("42"), "outputSummary must contain the assistant response");
        assertTrue(cp.getMessageCount() >= 2,
                "messageCount must include user + assistant messages");
        assertTrue(cp.getSeq() >= 0);
    }

    @Test
    void llmTurnAndToolExecutionSeqInterleaving() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        // Turn 1: tool call; Turn 2: final response.
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call-interleave", "echo")),
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("seq-test"), "seq-session");
        ctx.addMessage(new ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("seq-session");
        // Expected: LLM_TURN(0) → TOOL_EXECUTION(1) → LLM_TURN(2)
        assertEquals(3, all.size(), "3 checkpoints: LLM_TURN, TOOL_EXECUTION, LLM_TURN");

        assertEquals(CheckpointType.LLM_TURN, all.get(0).getType());
        assertEquals(0, all.get(0).getSeq());

        assertEquals(CheckpointType.TOOL_EXECUTION, all.get(1).getType());
        assertEquals(1, all.get(1).getSeq());

        assertEquals(CheckpointType.LLM_TURN, all.get(2).getType());
        assertEquals(2, all.get(2).getSeq());

        // LLM_TURN seq < TOOL_EXECUTION seq within the same turn (LLM before tool).
        assertTrue(all.get(0).getSeq() < all.get(1).getSeq(),
                "LLM_TURN seq must be less than the subsequent TOOL_EXECUTION seq");
    }

    @Test
    void noOpDefaultPassesThroughLLMTurn() {
        // NoOpCheckpoint default (not explicitly registered) → no exceptions.
        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("noop-test"), "noop-session");
        ctx.addMessage(new ChatUserMessage("hi"));

        executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, ctx.getStatus(),
                "NoOpCheckpoint default must not affect execution");
    }

    @Test
    void intraExecutionPersistenceAfterLLMTurnDoesNotThrow() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("persist-session", "persist-test");

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .sessionStore(store)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("persist-test"), "persist-session");
        ctx.addMessage(new ChatUserMessage("hi"));

        executor.execute(ctx).toCompletableFuture().join();

        // The session in the store must carry the assistant message (synced by
        // the LLM_TURN intra-execution persistence).
        io.nop.ai.agent.session.AgentSession persisted = store.get("persist-session");
        assertNotNull(persisted);
        assertTrue(persisted.getMessageCount() >= 2,
                "Persisted session must contain user + assistant messages");
    }

    // ========================================================================
    // Phase 2: COMPACTION checkpoint emission
    // ========================================================================

    @Test
    void compactionEmittedOnRealCompaction() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();

        // Chat returns a final response immediately; compaction triggers at
        // the top of the reactLoop because the pre-loaded messages exceed
        // DEFAULT_TRIGGER_MAX_MESSAGES.
        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("compacted-and-done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(realCompactor)
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("compact-test"), "compact-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        int beforeSize = ctx.getMessages().size();
        assertTrue(beforeSize > 30, "Pre-loaded messages must exceed trigger threshold");

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("compact-session");
        List<Checkpoint> compactionCps = all.stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());

        assertFalse(compactionCps.isEmpty(),
                "At least one COMPACTION checkpoint must be emitted when real compaction occurs");
    }

    @Test
    void compactionNotEmittedWithNoOpCompactor() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(NoOpContextCompactor.INSTANCE)
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("noop-compact-test"), "noop-compact-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("noop-compact-session");
        List<Checkpoint> compactionCps = all.stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());

        assertTrue(compactionCps.isEmpty(),
                "NoOpContextCompactor must not trigger COMPACTION checkpoint (no actual compaction)");
    }

    @Test
    void compactionNotEmittedWhenNoReduction() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(noReductionCompactor())
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("no-reduce-test"), "no-reduce-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("no-reduce-session");
        List<Checkpoint> compactionCps = all.stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());

        assertTrue(compactionCps.isEmpty(),
                "Compactor returning tokensAfter >= tokensBefore must not trigger COMPACTION checkpoint");
    }

    @Test
    void compactionFieldCompleteness() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(realCompactor)
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("compact-field-test"), "compact-field-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        int beforeMsgCount = ctx.getMessages().size();

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("compact-field-session");
        Checkpoint compactionCp = all.stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .findFirst().orElse(null);

        assertNotNull(compactionCp, "A COMPACTION checkpoint must exist");
        assertEquals(CheckpointType.COMPACTION, compactionCp.getType());
        assertNull(compactionCp.getToolName(), "COMPACTION toolName must be null");
        assertNull(compactionCp.getCallId(), "COMPACTION callId must be null");
        assertNotNull(compactionCp.getOutputSummary(),
                "COMPACTION outputSummary must capture compaction metadata");
        assertTrue(compactionCp.getMessageCount() > 0,
                "COMPACTION messageCount must reflect the post-compaction message list");
        assertTrue(compactionCp.getSeq() >= 0);
    }

    @Test
    void threeTypeSeqInterleavingMonotonicallyIncreases() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();

        // Turn 1: tool call (triggers LLM_TURN + TOOL_EXECUTION + compaction);
        // Turn 2: final response.
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call-three", "echo")),
                finalAssistant("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(realCompactor)
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel("three-type-test"), "three-type-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        executor.execute(ctx).toCompletableFuture().join();

        List<Checkpoint> all = mgr.getCheckpoints("three-type-session");
        assertFalse(all.isEmpty());

        // seq must be monotonically increasing across ALL types.
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getSeq() > all.get(i - 1).getSeq(),
                    "seq must monotonically increase across checkpoint types: "
                            + all.get(i - 1).getSeq() + " -> " + all.get(i).getSeq());
        }

        // At least two types must be present.
        long distinctTypes = all.stream().map(Checkpoint::getType).distinct().count();
        assertTrue(distinctTypes >= 2,
                "At least 2 checkpoint types must be present in one execution");
    }

    @Test
    void compactionEmittedViaForcedStopPath() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();

        // Set a very low maxContextTokens so the pre-loaded large messages
        // (each ~5000 chars) trigger shouldForceStop on the first iteration.
        // The default CalibratedTokenEstimator estimates based on content
        // length, so compaction shows a real token reduction.
        io.nop.ai.agent.model.AgentModel model = agentModel("forced-stop-test");
        io.nop.ai.core.model.ChatOptionsModel opts = new io.nop.ai.core.model.ChatOptionsModel();
        opts.setMaxTokens(1000);
        model.setChatOptions(opts);

        RecordingChatService chat = new RecordingChatService(List.of(
                finalAssistant("forced-stop-done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .contextCompactor(realCompactor)
                .checkpointManager(mgr)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "forced-stop-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        preloadMessagesForCompaction(ctx, 20);

        executor.execute(ctx).toCompletableFuture().join();

        // handleForcedStop should have triggered → performCompaction → COMPACTION checkpoint.
        List<Checkpoint> all = mgr.getCheckpoints("forced-stop-session");
        List<Checkpoint> compactionCps = all.stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());

        assertFalse(compactionCps.isEmpty(),
                "COMPACTION checkpoint must be emitted via the forced-stop path (handleForcedStop → performCompaction)");

        // The seq must be consistent with the shared counter (not restarted).
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getSeq() > all.get(i - 1).getSeq(),
                    "seq must monotonically increase even on the forced-stop path");
        }
    }

    // ========================================================================
    // Phase 3: End-to-end multi-type checkpoint
    // ========================================================================

    @Test
    void multiTypeCheckpointEndToEndViaEngine() {
        // Plan 278 (AR-10): override remove() so post-execution inspection
        // still sees the recorded checkpoints. AR-10's remove-on-terminal
        // behavior is tested separately in TestEngineLifecycleAndCheckpointBounded.
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint() {
            @Override
            public void remove(String sessionId) {
            }
        };

        MicroCompressionCompactor realCompactor = new MicroCompressionCompactor();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call-e2e-187", "echo")),
                        finalAssistant("e2e-done"))),
                stubToolManager(),
                new InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker(),
                new io.nop.ai.agent.security.AllowAllPathAccessChecker(),
                io.nop.ai.agent.guardrail.NoOpContentGuardrail.noOp(),
                io.nop.ai.agent.router.PassThroughModelRouter.passThrough(),
                realCompactor);
        engine.setCheckpointManager(mgr);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "e2e-187-session", null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());

        String sessionId = result.getSessionId();
        assertNotNull(sessionId);

        List<Checkpoint> all = mgr.getCheckpoints(sessionId);
        assertFalse(all.isEmpty(), "Engine execute must produce checkpoints (end-to-end Anti-Hollow)");

        // TOOL_EXECUTION and LLM_TURN must both be present.
        assertTrue(all.stream().anyMatch(c -> c.getType() == CheckpointType.TOOL_EXECUTION),
                "TOOL_EXECUTION checkpoint must be present");
        assertTrue(all.stream().anyMatch(c -> c.getType() == CheckpointType.LLM_TURN),
                "LLM_TURN checkpoint must be present");

        // seq monotonically increases across types.
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getSeq() > all.get(i - 1).getSeq(),
                    "seq must monotonically increase across types (end-to-end)");
        }
    }

    @Test
    void backwardCompatNoOpDefaultsAllExistingTestsPass() {
        // NoOpCheckpoint (default) + NoOpContextCompactor (default) → 0 side effects.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call-bc-187", "echo")),
                        finalAssistant("bc-done"))),
                stubToolManager());

        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "Default checkpoint manager must be NoOpCheckpoint");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "bc-187-session", null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "NoOp defaults must not affect execution");
    }
}
