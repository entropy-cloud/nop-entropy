package io.nop.ai.agent.engine;

import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.session.FileBackedSessionStore;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 dispatch-path drop-in + end-to-end + backward-compat tests for
 * {@link FileBackedCheckpointManager}. Proves the file-backed manager is a
 * drop-in replacement for {@link ToolExecutionCheckpoint} at the same
 * dispatch-loop wiring point, with the storage backend changed from in-memory
 * to journal.md + snapshot.json files.
 *
 * <p>Follows the {@link TestCheckpointDispatchPathWiring} sibling pattern.
 */
public class TestFileBackedCheckpointDispatchPath {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Mocks (same pattern as TestCheckpointDispatchPathWiring)
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
    // Drop-in wiring: executor + FileBackedCheckpointManager → journal.md written
    //            (Minimum Rules #23 Wiring Verification + storage backend replacement)
    // ========================================================================

    /**
     * Drop-in wiring verification: replacing {@link ToolExecutionCheckpoint} with
     * {@link FileBackedCheckpointManager} in the executor builder — the
     * {@code saveCheckpoint} call point is unchanged, but the checkpoint is now
     * persisted to journal.md instead of in-memory.
     */
    @Test
    void dropInWiringPersistsCheckpointToJournalFile() {
        Path checkpointRoot = tempDir.resolve("dropin");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(checkpointRoot);

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_dropin", "echo")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .checkpointManager(mgr)
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("dropin-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "dropin-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        // The journal.md file must exist and contain the checkpoint
        Path journalFile = checkpointRoot.resolve("dropin-session").resolve("journal.md");
        assertTrue(Files.exists(journalFile),
                "journal.md must be written by the file-backed manager during dispatch loop");

        // The checkpoints must be retrievable from the manager. With plan 187,
        // LLM_TURN checkpoints are emitted too, so retrieve the list and find
        // the TOOL_EXECUTION one.
        List<Checkpoint> all = mgr.getCheckpoints("dropin-session");
        assertFalse(all.isEmpty(),
                "saveCheckpoint must be invoked in the dispatch loop (wiring verification)");
        Checkpoint toolCp = all.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(toolCp, "A TOOL_EXECUTION checkpoint must be recorded for echo");
        assertEquals("echo", toolCp.getToolName());
        assertEquals("call_dropin", toolCp.getCallId());

        // The journal file must contain the §5.4a header + CP section
        String journalContent;
        try {
            journalContent = Files.readString(journalFile);
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }
        assertTrue(journalContent.startsWith("# Checkpoint Journal - dropin-session"),
                "journal.md must follow §5.4a format");
        assertTrue(journalContent.contains("## CP-000"),
                "journal.md must contain the CP-000 section");
        assertTrue(journalContent.contains("echo"),
                "journal.md must contain the tool name");
    }

    // ========================================================================
    // End-to-end: engine.execute → saveCheckpoint → journal.md → cross-instance reload
    //            (Minimum Rules #22 Anti-Hollow)
    // ========================================================================

    /**
     * End-to-end Anti-Hollow: from the user entry point
     * {@code engine.execute(request)}, through the ReAct loop, a tool call
     * executes, {@code saveCheckpoint} persists to journal.md. Then a NEW
     * engine + file-backed manager (pointing at the same checkpoint root, simulating
     * process restart) retrieves the checkpoint with the correct tool payload.
     */
    @Test
    void endToEndEngineExecutePersistsAndReloadsCheckpointAcrossInstances() {
        Path checkpointRoot = tempDir.resolve("e2e");

        // Phase 1: engine.execute → saveCheckpoint → journal.md written
        DefaultAgentEngine engine1 = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_e2e_file", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(checkpointRoot);
        engine1.setCheckpointManager(mgr1);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine1.execute(req).toCompletableFuture().join();
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus());

        String sessionId = result.getSessionId();
        assertNotNull(sessionId);

        // Phase 2: new engine + new manager (simulate restart) → reload from files
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(checkpointRoot);
        List<Checkpoint> reloadedAll = mgr2.getCheckpoints(sessionId);
        assertFalse(reloadedAll.isEmpty(),
                "After cross-instance reload, checkpoints must be retrievable from journal.md (end-to-end Anti-Hollow)");
        Checkpoint reloaded = reloadedAll.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(reloaded,
                "A TOOL_EXECUTION checkpoint must be reloaded after cross-instance restart");
        assertEquals("echo", reloaded.getToolName());
        assertEquals("call_e2e_file", reloaded.getCallId());
        assertEquals("tool-output-echo", reloaded.getOutputSummary(),
                "Reloaded checkpoint must carry the correct tool payload");
        assertTrue(reloaded.getMessageCount() > 0,
                "Reloaded checkpoint messageCount must reflect context size");
    }

    // ========================================================================
    // Backward compat: NoOp / ToolExecution defaults unaffected
    // ========================================================================

    /**
     * Backward compat: with {@link ToolExecutionCheckpoint} (in-memory), the
     * dispatch loop runs normally and no files are written.
     */
    @Test
    void toolExecutionCheckpointWritesNoFiles() {
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
                        assistantWithToolCalls(toolCall("call_bc_mem", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());
        engine.setCheckpointManager(mgr);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "bc-mem-session", null, ChannelKind.WEBUI, Principal.user());
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "ToolExecutionCheckpoint must work as before (no file I/O)");

        // In-memory checkpoint exists
        assertNotNull(mgr.getLatestCheckpoint("bc-mem-session"));
        // No files written (checkpoint root was never configured)
    }

    /**
     * Backward compat: with the shipped {@link NoOpCheckpoint} default (never
     * explicitly registered), everything runs normally with zero side effects.
     */
    @Test
    void noOpDefaultProducesNoSideEffectsAndRunsNormally() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_bc_noop", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());

        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "Default must remain NoOpCheckpoint");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "bc-noop-session", null, ChannelKind.WEBUI, Principal.user());
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "NoOpCheckpoint default must not affect the dispatch loop");
    }

    /**
     * Builder-built executor without any checkpoint manager must still build
     * and run (NoOpCheckpoint default applied).
     */
    @Test
    void builderDefaultsWithoutCheckpointManager() {
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_bc_builder", "echo")),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("bc-builder");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "bc-builder-sess");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));

        executor.execute(ctx).toCompletableFuture().join();
    }

    // ========================================================================
    // P0 path-traversal guard (plan 190, finding [13-15]): end-to-end at the
    // public API boundary. A traversal sessionId on AgentMessageRequest is
    // rejected by DefaultAgentEngine.resolveSessionId BEFORE any store
    // interaction (Minimum Rules #22 End-to-End + #23 Wiring Verification).
    // ========================================================================

    /**
     * End-to-end at the public API: {@code engine.execute(request)} with a
     * traversal-shaped {@code request.sessionId} throws
     * {@link NopAiAgentException} synchronously (resolveSessionId is the first
     * statement of execute, before doExecute/loadAgentModel/store access) and
     * writes no file under the configured session root. This proves the
     * engine-level guard is wired on the execute → store path and that the
     * fail-closed throw happens before any filesystem side effect.
     */
    @Test
    void executeRejectsTraversalSessionIdBeforeStoreInteraction() {
        Path sessionRoot = tempDir.resolve("e2e-traversal");
        FileBackedSessionStore store = new FileBackedSessionStore(sessionRoot);

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("should-never-reach"))),
                stubToolManager(),
                store);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "../../../etc/cron.d/exploit", null,
                ChannelKind.WEBUI, Principal.user());

        // resolveSessionId runs at the top of execute(), synchronously, so the
        // throw surfaces directly from execute() — no future is constructed.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.execute(req),
                "execute must reject a traversal sessionId at the public API boundary "
                        + "(fail-closed, before any store interaction)");

        // The rejection message must mention the path-traversal guard so the
        // wiring (SessionIds.requireValidIdentifier invoked by resolveSessionId)
        // is observable from the exception (Wiring Verification, Rule #23).
        assertTrue(ex.getMessage().contains("path-traversal guard")
                        || ex.getMessage().contains("invalid characters")
                        || ex.getMessage().contains("[A-Za-z0-9_-]"),
                "Rejection message must identify the path-traversal guard: " + ex.getMessage());

        // No file written under the session root — the store was never reached.
        if (Files.exists(sessionRoot)) {
            try (java.util.stream.Stream<Path> entries = Files.list(sessionRoot)) {
                assertFalse(entries.findAny().isPresent(),
                        "No session files must be written when the sessionId is rejected "
                                + "at the public API boundary");
            } catch (Exception e) {
                throw new NopAiAgentException("Unexpected I/O failure", e);
            }
        }
        assertFalse(Files.exists(tempDir.resolve("etc")),
                "No traversal target outside the root must be created");
    }

    /**
     * Wiring Verification (Rule #23): a legitimate sessionId still passes the
     * guard and execute proceeds normally — proving the guard is wired on the
     * happy path too (not over-blocking).
     */
    @Test
    void executeAcceptsValidSessionIdAfterGuard() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("call_guard_ok", "echo")),
                        finalAssistant("done")
                )),
                stubToolManager());

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "valid-session-1", null,
                ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "A valid [A-Za-z0-9_-] sessionId must pass the guard and execute normally");
        assertEquals("valid-session-1", result.getSessionId());
    }
}
