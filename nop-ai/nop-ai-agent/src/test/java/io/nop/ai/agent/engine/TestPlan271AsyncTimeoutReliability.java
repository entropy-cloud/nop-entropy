package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.tool.CallAgentExecutor;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 271 Phase 2 focused tests (findings 14-01 / 14-03 / 14-04).
 *
 * <p>Verifies that the reliability / async-timeout production code (already
 * landed in {@code CallAgentExecutor}, {@code ReActAgentExecutor} and
 * {@code DefaultAgentEngine}) actually behaves correctly end-to-end — not just
 * that the fields/methods exist (Minimum Rules #22 End-to-End + #23 Wiring
 * Verification + #24 No Silent No-Op):
 * <ul>
 *     <li><b>P2.6 (14-01)</b> {@link #callAgentTimeoutCancelsChildSession} — a
 *         call-agent sub-agent execution that times out triggers
 *         {@code engine.cancelSession(childSessionId, ..., forced=true)} so the
 *         sub-agent does not continue as a zombie consuming LLM/DB resources.</li>
 *     <li><b>P2.7 (14-03)</b> {@link #reactLlmTimeoutDoesNotPermanentlyBlock} — a
 *         permanently hung LLM call inside the ReAct loop is bounded by the
 *         configured wall-clock timeout; the agent execution fails within a
 *         bounded time instead of blocking the session / worker thread /
 *         takeover lock indefinitely.</li>
 *     <li><b>P2.8 (14-04)</b> {@link #engineUsesDedicatedExecutorNotCommonPool},
 *         {@link #executionRunsOnDedicatedAgentThread},
 *         {@link #engineTimeoutConfigDefaultsAreNonZero} — the engine uses a
 *         dedicated executor (named {@code nop-ai-agent-exec} daemon threads),
 *         not {@link ForkJoinPool#commonPool()}, and the timeout config fields
 *         ship with non-zero defaults.</li>
 * </ul>
 */
public class TestPlan271AsyncTimeoutReliability {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Shared test-internal components
    // ========================================================================

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

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    /**
     * A chat service that blocks forever (until interrupted) on a never-counted
     * latch. Used to simulate a permanently hung LLM connection so the
     * wall-clock timeout can be exercised. {@code f.cancel(true)} issued by
     * {@code callChatWithTimeout} on timeout interrupts the blocking
     * {@code await()}, letting the worker thread unwind cleanly.
     */
    static final class HangingChatService implements IChatService {
        final CountDownLatch entered = new CountDownLatch(1);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            entered.countDown();
            try {
                // Never counts down — blocks until interrupted by f.cancel(true).
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ChatResponse.error("ERROR", "hung LLM call (should be unreachable)");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * A chat service that captures the name of the thread it runs on, then
     * returns an immediate scripted response. Used to prove an engine-driven
     * execution runs on the dedicated {@code nop-ai-agent-exec} thread.
     */
    static final class ThreadCapturingChatService implements IChatService {
        final AtomicReference<String> threadName = new AtomicReference<>();
        final String reply;

        ThreadCapturingChatService(String reply) {
            this.reply = reply;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            threadName.set(Thread.currentThread().getName());
            return finalResponse(reply);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private AgentToolExecuteContext createContext(IAgentEngine engine, String sessionId, String agentName) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, NoOpAgentMessenger.noOp(), sessionId, agentName);
    }

    private AiToolCall createCallAgentCall(String agentId, String input, String sessionId, int timeoutMs) {
        AiToolCall call = new AiToolCall();
        call.setToolName(CallAgentExecutor.TOOL_NAME);
        call.setId(1);
        StringBuilder json = new StringBuilder("{");
        json.append("\"agentId\":\"").append(agentId).append("\"");
        if (input != null) {
            json.append(",\"input\":\"").append(input).append("\"");
        }
        if (sessionId != null) {
            json.append(",\"sessionId\":\"").append(sessionId).append("\"");
        }
        json.append("}");
        call.setInput(json.toString());
        call.setTimeoutMs(timeoutMs);
        return call;
    }

    // ========================================================================
    // P2.6 (finding 14-01): call-agent timeout cancels child sub-agent
    // End-to-End (#22) + Wiring Verification (#23): the executor's exceptionally
    // block must invoke engine.cancelSession(childSessionId, ..., forced=true)
    // on a TimeoutException — NOT silently return an error and leave the
    // sub-agent running as a zombie.
    // ========================================================================

    @Test
    void callAgentTimeoutCancelsChildSession() throws Exception {
        AtomicInteger cancelCount = new AtomicInteger();
        AtomicReference<String> cancelledSessionId = new AtomicReference<>();
        AtomicReference<String> cancelReason = new AtomicReference<>();
        AtomicBoolean forcedFlag = new AtomicBoolean();

        // Recording engine: execute() returns a never-completing future so
        // the call-agent's .orTimeout fires; cancelSession() records the call.
        IAgentEngine engine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                return new CompletableFuture<>(); // never completes -> orTimeout fires
            }

            @Override
            public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
                cancelCount.incrementAndGet();
                cancelledSessionId.set(sessionId);
                cancelReason.set(reason);
                forcedFlag.set(forced);
                return CompletableFuture.completedFuture(null);
            }
        };

        AgentToolExecuteContext ctx = createContext(engine, "parent-timeout-sess", "parent-agent");
        // Provide a known sessionId (continue mode) so the child session id is
        // deterministic and the cancelSession argument can be asserted exactly.
        AiToolCall call = createCallAgentCall("test-agent", "hello", "child-timeout-sess", 200);

        CallAgentExecutor executor = new CallAgentExecutor();
        long start = System.currentTimeMillis();
        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        // The sub-agent execution must surface as a failure (not silent success).
        assertEquals("failure", result.getStatus(),
                "A timed-out call-agent must yield a failure result, not silent success");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().toLowerCase().contains("timed out")
                        || result.getError().getBody().toLowerCase().contains("timeout"),
                "Error message must identify a timeout: " + result.getError().getBody());

        // Wiring Verification (#23): cancelSession must have been called on the
        // child session id with forced=true so the zombie sub-agent is actually
        // released (not a silent no-op on the timeout path).
        assertEquals(1, cancelCount.get(),
                "engine.cancelSession must be invoked exactly once on call-agent timeout");
        assertEquals("child-timeout-sess", cancelledSessionId.get(),
                "cancelSession must target the child session id, not the parent's");
        assertTrue(forcedFlag.get(),
                "cancelSession must be forced=true so the sub-agent thread is interrupted");
        assertNotNull(cancelReason.get());
        assertTrue(cancelReason.get().contains("timeout"),
                "cancel reason must mention timeout: " + cancelReason.get());

        // The timeout must have fired promptly (well under the 30s get bound).
        assertTrue(elapsed < 5_000L,
                "call-agent timeout should fire within ~200ms, took " + elapsed + "ms");
    }

    // ========================================================================
    // P2.7 (finding 14-03): ReAct LLM wall-clock timeout does not permanently
    // block the agent session. A permanently hung LLM connection is bounded by
    // the configured llmTimeoutMs; the execution fails within a bounded time
    // instead of blocking the worker thread / takeover lock indefinitely.
    // End-to-End (#22) + No Silent No-Op (#24): the timeout converts the hang
    // into an explicit failed status (not a silent infinite block).
    // ========================================================================

    @Test
    void reactLlmTimeoutDoesNotPermanentlyBlock() throws Exception {
        HangingChatService chatService = new HangingChatService();
        // Use a dedicated executor (mirrors the engine's getAgentExecutor) so the
        // synchronous chatService.call is dispatched off the calling thread and
        // f.get(llmTimeoutMs) can time out. A direct/same-thread executor would
        // self-deadlock the timeout wrapper.
        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-react-timeout-exec");
            t.setDaemon(true);
            return t;
        });
        try {
            ReActAgentExecutor executor = ReActAgentExecutor.builder()
                    .chatService(chatService)
                    .toolManager(noOpToolManager())
                    .llmTimeoutMs(200L) // 200ms wall-clock timeout
                    .timeoutExecutor(exec)
                    .build();

            AgentModel model = new AgentModel();
            model.setTools(Collections.emptySet());
            AgentExecutionContext ctx = AgentExecutionContext.create(model, "react-llm-timeout-sess");
            ctx.setMaxIterations(5);

            long start = System.currentTimeMillis();
            // This get() must return within the 15s bound. Before the fix, a hung
            // LLM call would block this indefinitely (the bug we're guarding).
            AgentExecutionResult result = executor.execute(ctx)
                    .toCompletableFuture().get(15, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(chatService.entered.getCount() == 0,
                    "The hanging LLM call should have been entered");
            assertEquals(AgentExecStatus.failed, result.getStatus(),
                    "A timed-out LLM call must fail the agent execution explicitly, "
                            + "not silently hang or succeed");
            // The timeout must have fired promptly (well under the 15s bound),
            // proving the session / worker thread / takeover lock are released.
            assertTrue(elapsed < 5_000L,
                    "LLM timeout should fire within ~200ms, took " + elapsed + "ms");
        } finally {
            exec.shutdownNow();
            assertTrue(exec.awaitTermination(15, TimeUnit.SECONDS),
                    "Timeout-executor worker threads must unwind after interrupt");
        }
    }

    // ========================================================================
    // P2.8 (finding 14-04): the engine uses a dedicated executor, not
    // ForkJoinPool.commonPool(). Multiple concurrent agents previously shared
    // commonPool (~3-7 threads JVM-wide), causing cross-JVM functional
    // starvation. The shipped default is a cached daemon-thread pool whose
    // threads are named "nop-ai-agent-exec" (observable signal, #23 wiring).
    // ========================================================================

    @Test
    void engineUsesDedicatedExecutorNotCommonPool() throws Exception {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new HangingChatService(), noOpToolManager(), new InMemorySessionStore());

        ExecutorService agentExecutor = engine.getAgentExecutor();
        assertNotNull(agentExecutor, "Engine must provide a dedicated agent executor");

        // Hard signal: it is NOT the shared commonPool.
        assertNotSame(ForkJoinPool.commonPool(), agentExecutor,
                "Engine must not share ForkJoinPool.commonPool()");

        // Observable signal: tasks run on dedicated named daemon threads.
        CompletableFuture<String> nameFuture = CompletableFuture.supplyAsync(
                Thread::currentThread, agentExecutor).thenApply(Thread::getName);
        String threadName = nameFuture.get(15, TimeUnit.SECONDS);
        assertTrue(threadName.startsWith("nop-ai-agent-exec"),
                "Agent executor threads must be named 'nop-ai-agent-exec-*', got: " + threadName
                        + " (commonPool threads are named 'ForkJoinPool.commonPool-worker-*')");
        assertNotEquals(0, engine.getCallAgentTimeoutMs(),
                "callAgentTimeoutMs default must be non-zero");
        assertNotEquals(0, engine.getLlmTimeoutMs(),
                "llmTimeoutMs default must be non-zero");
        assertNotEquals(0, engine.getToolTimeoutMs(),
                "toolTimeoutMs default must be non-zero");
    }

    /**
     * Wiring Verification (#23) + End-to-End (#22): an engine-driven agent
     * execution actually runs on the dedicated {@code nop-ai-agent-exec}
     * thread (the engine's three entry points route supplyAsync through
     * {@code getAgentExecutor()}), proving the executor is wired into the
     * execution path — not just present as a field.
     */
    @Test
    void executionRunsOnDedicatedAgentThread() throws Exception {
        ThreadCapturingChatService chatService = new ThreadCapturingChatService("done");
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore());

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "dedicated-thread-sess", null))
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete normally");
        String threadName = chatService.threadName.get();
        assertNotNull(threadName, "Chat service should have captured the executing thread name");
        assertTrue(threadName.startsWith("nop-ai-agent-exec"),
                "Engine execution must run on a dedicated 'nop-ai-agent-exec' thread, "
                        + "not a commonPool worker. Got: " + threadName);
    }

    /**
     * Exit Criteria: the timeout config fields ship with non-zero defaults
     * (non-null / non-0) so a fresh engine is protected out-of-the-box without
     * explicit configuration.
     */
    @Test
    void engineTimeoutConfigDefaultsAreNonZero() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new HangingChatService(), noOpToolManager(), new InMemorySessionStore());

        assertTrue(engine.getCallAgentTimeoutMs() > 0,
                "Default callAgentTimeoutMs must be positive (got " + engine.getCallAgentTimeoutMs() + ")");
        assertTrue(engine.getLlmTimeoutMs() > 0,
                "Default llmTimeoutMs must be positive (got " + engine.getLlmTimeoutMs() + ")");
        assertTrue(engine.getToolTimeoutMs() > 0,
                "Default toolTimeoutMs must be positive (got " + engine.getToolTimeoutMs() + ")");
    }
}
