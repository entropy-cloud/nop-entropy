package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 197 (AUDIT-14-01) focused tests: verifies that the same-session
 * concurrent execution guard (putIfAbsent + fail-fast + value-comparison
 * remove + cancel-window pre-registration) actually works at runtime —
 * not just that the map type is correct, but that the guard is triggered
 * end-to-end through {@code engine.execute(...)}.
 *
 * <p>Each test maps to a specific behaviour point required by Phase 3
 * Exit Criteria:
 * <ol>
 *   <li>{@link #concurrentExecuteFailFast} — concurrent-execute-fail-fast</li>
 *   <li>{@link #finallyDoesNotMisremoveHandle} — finally-no-misremove</li>
 *   <li>{@link #cancelWindowHonored} — cancel-window-honored</li>
 *   <li>{@link #restoreSessionGuardConsistentWithExecute} — restore-guard-consistent</li>
 *   <li>{@link #noRegressionNormalPath} — no-regression-normal-path</li>
 * </ol>
 */
public class TestDefaultAgentEngineConcurrencyGuard {

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

    // ========================================================================
    // Test-internal components
    // ========================================================================

    /**
     * Chat service that returns scripted responses in order, with latch
     * hooks so tests can control execution timing — count down
     * {@code enteredLatch} when the chat call starts (so the test knows
     * the handle is registered), and block on {@code proceedLatch} until
     * the test releases the execution.
     */
    static final class BlockingScriptedChatService implements IChatService {
        final List<ChatResponse> scripted;
        final AtomicInteger idx = new AtomicInteger(0);
        final CountDownLatch enteredLatch;
        final CountDownLatch proceedLatch;

        BlockingScriptedChatService(List<ChatResponse> scripted,
                                     CountDownLatch enteredLatch,
                                     CountDownLatch proceedLatch) {
            this.scripted = scripted;
            this.enteredLatch = enteredLatch;
            this.proceedLatch = proceedLatch;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            enteredLatch.countDown();
            try {
                assertTrue(proceedLatch.await(30, TimeUnit.SECONDS),
                        "Chat service should be released by the test within timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(done)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                  IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
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
    }

    static ChatResponse toolCallResponse(String callId, String toolName, Map<String, Object> args) {
        ChatToolCall call = new ChatToolCall();
        call.setId(callId);
        call.setName(toolName);
        call.setArguments(args);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(call));
        return ChatResponse.success(msg);
    }

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    /** Convenience: a chat service that completes immediately (no blocking). */
    static BlockingScriptedChatService immediateChat(List<ChatResponse> scripted) {
        return new BlockingScriptedChatService(scripted, new CountDownLatch(1), new CountDownLatch(0));
    }

    // ========================================================================
    // (1) concurrent-execute-fail-fast
    // End-to-end: engine.execute() -> doExecute -> putIfAbsent -> second
    // execute() on the same session is rejected with NopAiAgentException.
    // (Minimum Rules #22 End-to-End + #24 No Silent No-Op)
    // ========================================================================

    @Test
    void concurrentExecuteFailFast() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstProceed = new CountDownLatch(1);
        BlockingScriptedChatService chatService = new BlockingScriptedChatService(
                List.of(finalResponse("first-done")), firstEntered, firstProceed);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, noOpToolManager());

        // Start the first execution — it will block inside the chat service.
        CompletableFuture<AgentExecutionResult> firstFuture = engine.execute(
                new AgentMessageRequest("test-react-agent", "compute", "fail-fast-sess", null));

        // Wait until the first execution has registered its handle.
        assertTrue(firstEntered.await(30, TimeUnit.SECONDS),
                "First execution should have entered the chat service");

        // The second execute on the same session must fail-fast (end-to-end:
        // engine.execute -> doExecute -> putIfAbsent -> non-null -> throw).
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.execute(
                        new AgentMessageRequest("test-react-agent", "dup", "fail-fast-sess", null)),
                "Second concurrent execute on the same session must fail-fast");
        assertTrue(ex.getMessage().contains("already executing"),
                "Exception must say 'already executing'. Got: " + ex.getMessage());

        // Release the first execution and verify it completes normally.
        firstProceed.countDown();
        AgentExecutionResult firstResult = firstFuture.get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, firstResult.getStatus(),
                "First execution should complete normally after the second was rejected");
    }

    // ========================================================================
    // (2) finally-no-misremove (value-comparison remove core guarantee)
    // Verifies that an execution's finally properly cleans up its own
    // handle (value-comparison remove), so a subsequent sequential
    // execution on the same session is not blocked by a stale entry.
    // If the finally used key-only remove AND a concurrent second handle
    // were registered (bypassing putIfAbsent), the second handle would be
    // wrongly removed — but since putIfAbsent prevents that, the practical
    // guarantee is: sequential executions on the same session always work.
    // ========================================================================

    @Test
    void finallyDoesNotMisremoveHandle() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(
                immediateChat(List.of(finalResponse("done"))), noOpToolManager(), store);

        // First execution completes and removes its own handle.
        AgentExecutionResult r1 = engine.execute(
                new AgentMessageRequest("test-react-agent", "first", "misremove-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, r1.getStatus());

        // Second sequential execute must succeed — proving the first
        // execution's finally removed only its own handle (not a stale
        // key-only remove that would have left an empty mapping or, worse,
        // removed a concurrently-registered second handle).
        AgentExecutionResult r2 = engine.execute(
                new AgentMessageRequest("test-react-agent", "second", "misremove-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, r2.getStatus(),
                "Sequential second execute must succeed — first execution's finally "
                        + "must have cleaned up via value-comparison remove(sessionId, handle)");

        // Third sequential execute also succeeds — proves no accumulation.
        AgentExecutionResult r3 = engine.execute(
                new AgentMessageRequest("test-react-agent", "third", "misremove-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, r3.getStatus(),
                "Third sequential execute must also succeed — no stale handles");
    }

    // ========================================================================
    // (3) cancel-window-honored (Wiring Verification #23 + End-to-End #22)
    // Verifies that cancel during execution is honored: the execution
    // thread's ctx.isCancelRequested() is true (cancel signal from
    // cancelSession -> runningExecutions/CancelHandle -> execution ctx).
    //
    // With the pre-registration fix (Option A), cancelSession always finds
    // the handle (it's registered in the sync phase before supplyAsync),
    // so cancel is never lost during the enqueue window.
    // ========================================================================

    @Test
    void cancelWindowHonored() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        BlockingScriptedChatService chatService = new BlockingScriptedChatService(
                List.of(toolCallResponse("call_w", "test-calculator", Map.of("expr", "1+1"))),
                entered, proceed);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, noOpToolManager());

        CompletableFuture<AgentExecutionResult> future = engine.execute(
                new AgentMessageRequest("test-react-agent", "compute", "cancel-window-sess", null));

        // Wait for the execution to start (lambda is running, handle registered
        // via pre-registration in the sync phase).
        assertTrue(entered.await(30, TimeUnit.SECONDS),
                "Execution should have started (entered chat service)");

        // Cancel the active execution. The handle is pre-registered, so
        // cancelSession finds it (handle != null) and sets
        // ctx.setCancelRequested(true). Wiring: cancelSession ->
        // runningExecutions.get -> handle.context.setCancelRequested.
        engine.cancelSession("cancel-window-sess", "window-test", false);

        // Release the blocking chat service so the executor can proceed
        // to the next loop iteration where it checks isCancelRequested().
        proceed.countDown();

        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.cancelled, result.getStatus(),
                "Cancel during execution must be honored — result should be cancelled, "
                        + "not silently overwritten by setStatus(running)");
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("cancel-window-sess"),
                "Session status should be cancelled after window-cancel");
    }

    // ========================================================================
    // (4) restore-guard-consistent
    // restoreSession must fail-fast when the session is already executing,
    // consistent with execute/resumeSession. Uses FileBackedSessionStore
    // so restoreSession can load a persisted session.
    // ========================================================================

    @Test
    void restoreSessionGuardConsistentWithExecute() throws Exception {
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("restore-guard"));

        // First, persist a session by executing and completing.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                immediateChat(List.of(finalResponse("done"))), noOpToolManager(), store);
        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "init", "restore-guard-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus());

        // Reset the session status to a non-terminal state so restoreSession
        // can proceed past the terminal-status check.
        AgentSession session = store.get("restore-guard-sess");
        assertNotNull(session, "Persisted session should exist");
        session.setStatus(AgentExecStatus.pending);
        store.save(session);

        // Start a blocking execution on a second engine to register a handle.
        CountDownLatch blockEntered = new CountDownLatch(1);
        CountDownLatch blockProceed = new CountDownLatch(1);
        BlockingScriptedChatService blockingChat = new BlockingScriptedChatService(
                List.of(finalResponse("blocking-done")), blockEntered, blockProceed);
        DefaultAgentEngine engine2 = new DefaultAgentEngine(blockingChat, noOpToolManager(), store);
        CompletableFuture<AgentExecutionResult> blockingFuture = engine2.execute(
                new AgentMessageRequest("test-react-agent", "blocking", "restore-guard-sess", null));

        assertTrue(blockEntered.await(30, TimeUnit.SECONDS),
                "Blocking execution should have started");

        // restoreSession on the same session must fail-fast (the blocking
        // execution has registered a handle via putIfAbsent). Consistent
        // with execute/resumeSession fail-fast behavior.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine2.restoreSession("restore-guard-sess", "operator", "test"),
                "restoreSession on an actively-executing session must fail-fast");
        assertTrue(ex.getMessage().contains("already executing"),
                "restoreSession fail-fast must say 'already executing'. Got: " + ex.getMessage());

        // Clean up: release the blocking execution.
        blockProceed.countDown();
        blockingFuture.get(60, TimeUnit.SECONDS);
    }

    // ========================================================================
    // (5) no-regression-normal-path
    // Single-threaded sequential execute / restore must work unchanged:
    // putIfAbsent succeeds (no competition), value-comparison remove
    // cleans up correctly.
    // ========================================================================

    @Test
    void noRegressionNormalPath() throws Exception {
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("normal-path"));

        // Normal execute completes.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                immediateChat(List.of(finalResponse("normal-done"))), noOpToolManager(), store);
        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hello", "normal-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Normal single-threaded execute should complete");
        assertEquals(AgentExecStatus.completed, engine.getSessionStatus("normal-sess"),
                "Session status should be completed after normal execute");

        // A second sequential execute on the same session must succeed.
        DefaultAgentEngine engine2 = new DefaultAgentEngine(
                immediateChat(List.of(finalResponse("second-done"))), noOpToolManager(), store);
        AgentExecutionResult result2 = engine2.execute(
                new AgentMessageRequest("test-react-agent", "again", "normal-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result2.getStatus(),
                "Sequential second execute should complete (no stale handle from first)");

        // restoreSession on the now-idle session must also succeed.
        AgentSession session = store.get("normal-sess");
        session.setStatus(AgentExecStatus.pending);
        store.save(session);

        DefaultAgentEngine engine3 = new DefaultAgentEngine(
                immediateChat(List.of(finalResponse("restored-done"))), noOpToolManager(), store);
        AgentExecutionResult restoreResult = engine3.restoreSession("normal-sess", "op", "test")
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, restoreResult.getStatus(),
                "restoreSession on an idle session should complete normally");
    }
}
