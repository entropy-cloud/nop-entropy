package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 280 focused tests for ReAct fan-out future lifecycle:
 * <ul>
 *   <li>AR-15: build-loop synchronous throw cancels already-started tool
 *       futures (no orphans), and the exception propagates to the caller.</li>
 *   <li>14-02: fan-out join is interruptible (lease-lost / forced-cancel
 *       thread interrupt breaks the wait immediately), the interrupt flag is
 *       restored, and started tool futures are cancelled.</li>
 * </ul>
 *
 * <p>Both tests use a spy {@link CompletableFuture} (overriding {@code thenApply})
 * returned by the stub {@link IToolManager}. With {@code toolTimeoutMs <= 0}
 * (the builder default), the executor adds exactly the
 * {@code callTool().thenApply(...)} result to its internal {@code futures} list,
 * so the spy captures the same CF that the AR-15 / 14-02 catch blocks cancel —
 * allowing white-box assertion of {@code isCancelled()}.
 */
public class TestFanOutFutureLifecycle {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static ChatToolCall toolCall(String id, String name) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(Map.of());
        return tc;
    }

    private abstract static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                             IToolExecuteContext context) {
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
            AiToolModel m = new AiToolModel();
            m.setName(toolName);
            m.setDescription("Mock tool: " + toolName);
            return m;
        }
    }

    private static IChatService chatServiceReturningToolCallsThenDone(List<ChatToolCall> firstBatch) {
        AtomicInteger callCount = new AtomicInteger();
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (callCount.getAndIncrement() == 0) {
                    msg.setToolCalls(firstBatch);
                } else {
                    msg.setContent("done");
                }
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    /**
     * A never-completing {@link CompletableFuture} that records the dependent CF
     * created by {@link CompletableFuture#thenApply(Function)} so the test can
     * observe whether the executor's catch block cancels it.
     */
    private static CompletableFuture<AiToolCallResult> spyNeverCompletingFuture(
            AtomicReference<CompletableFuture<?>> capture) {
        return new CompletableFuture<AiToolCallResult>() {
            @Override
            public <U> CompletableFuture<U> thenApply(
                    Function<? super AiToolCallResult, ? extends U> fn) {
                CompletableFuture<U> derived = super.thenApply(fn);
                capture.set(derived);
                return derived;
            }
        };
    }

    /**
     * AR-15 (plan 280): when the 2nd tool's {@code callTool} throws
     * synchronously mid-build-loop, the already-started 1st tool future must be
     * cancelled (no orphan), and the exception must propagate to the caller
     * (not be silently swallowed).
     */
    @Test
    void fanOutBuildLoopSyncThrowCancelsAlreadyStartedFuture() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("tool1", "tool2"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-ar15");
        ctx.setMaxIterations(10);

        AtomicReference<CompletableFuture<?>> capturedTool1Derived = new AtomicReference<>();

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                if ("tool2".equals(toolName)) {
                    throw new NopAiAgentException("tool2 sync validation failed");
                }
                return spyNeverCompletingFuture(capturedTool1Derived);
            }
        };

        IChatService chatService = chatServiceReturningToolCallsThenDone(
                List.of(toolCall("call_1", "tool1"), toolCall("call_2", "tool2")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        // (b) exception propagated — status=failed, error carries tool2's message
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "tool2's synchronous throw must propagate (not be swallowed)");
        assertNotNull(result.getError(), "error must be recorded");
        assertTrue(result.getError().contains("tool2 sync validation failed"),
                "error must carry tool2's exception message, got: " + result.getError());

        // (a) + (c) tool1's already-started future was cancelled (no orphan)
        assertNotNull(capturedTool1Derived.get(),
                "tool1's derived CF should have been created during the build loop");
        assertTrue(capturedTool1Derived.get().isCancelled(),
                "AR-15: tool1's started future must be cancelled, not left as an orphan");
    }

    /**
     * 14-02 (plan 280): the fan-out {@code allOf.get()} join must respond to a
     * thread interrupt (simulating lease-lost / forced-cancel) immediately, not
     * park until all tool futures settle. On interrupt the started tool futures
     * must be cancelled and the interrupt flag must be restored.
     *
     * <p>Each tool returns a never-completing CF, so without interruptibility the
     * join would block forever — a fast return after interrupt is definitive
     * proof that {@code get()} (not {@code join()}) is used.
     */
    @Test
    void fanOutJoinIsInterruptibleAndCancelsFutures() throws Exception {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("slow1", "slow2"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-14-02");
        ctx.setMaxIterations(10);

        AtomicReference<CompletableFuture<?>> capturedSlow1 = new AtomicReference<>();
        AtomicReference<CompletableFuture<?>> capturedSlow2 = new AtomicReference<>();
        AtomicInteger callToolCount = new AtomicInteger();

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                int idx = callToolCount.getAndIncrement();
                AtomicReference<CompletableFuture<?>> target = (idx == 0) ? capturedSlow1 : capturedSlow2;
                return spyNeverCompletingFuture(target);
            }
        };

        IChatService chatService = chatServiceReturningToolCallsThenDone(
                List.of(toolCall("call_s1", "slow1"), toolCall("call_s2", "slow2")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        AtomicReference<AgentExecutionResult> resultRef = new AtomicReference<>();
        AtomicBoolean interruptFlagAtReturn = new AtomicBoolean(false);
        CountDownLatch doneLatch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            AgentExecutionResult r = executor.execute(ctx).toCompletableFuture().join();
            resultRef.set(r);
            // Capture the interrupt flag BEFORE the thread exits — it should
            // have been restored by the catch (Thread.currentThread().interrupt()).
            interruptFlagAtReturn.set(Thread.currentThread().isInterrupted());
            doneLatch.countDown();
        });
        worker.start();

        // Wait until both tools' callTool have been invoked — the build loop
        // is done and allOf.get() is about to block.
        long waitStart = System.currentTimeMillis();
        while (callToolCount.get() < 2 && System.currentTimeMillis() - waitStart < 5000) {
            Thread.sleep(20);
        }
        assertEquals(2, callToolCount.get(),
                "both tools should have been dispatched before interrupt");
        // Small delay to ensure allOf.get() is entered.
        Thread.sleep(200);

        long interruptTime = System.currentTimeMillis();
        worker.interrupt();

        // (a) the wait must be broken quickly — without interruptibility the
        // never-completing futures would block forever.
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - interruptTime;
        assertTrue(finished,
                "14-02: worker thread must terminate after interrupt (join broken by interrupt)");
        assertTrue(elapsed < 3000,
                "14-02: fan-out join should be broken by interrupt in << tool-settle time, took " + elapsed + "ms");

        // (b) interrupt flag was restored by the catch
        assertTrue(interruptFlagAtReturn.get(),
                "14-02: interrupt flag must be restored (Thread.currentThread().interrupt())");

        // The NopAiAgentException path → status=failed, error mentions "interrupted"
        assertNotNull(resultRef.get(), "execute should return a result");
        assertEquals(AgentExecStatus.failed, resultRef.get().getStatus(),
                "interrupted fan-out must surface as failed status");
        assertNotNull(resultRef.get().getError());
        assertTrue(resultRef.get().getError().contains("interrupted"),
                "error must indicate interruption, got: " + resultRef.get().getError());

        // (c) both started tool futures were cancelled (no orphans)
        assertNotNull(capturedSlow1.get(), "slow1 derived CF should exist");
        assertNotNull(capturedSlow2.get(), "slow2 derived CF should exist");
        assertTrue(capturedSlow1.get().isCancelled(),
                "14-02: slow1 future must be cancelled after interrupt");
        assertTrue(capturedSlow2.get().isCancelled(),
                "14-02: slow2 future must be cancelled after interrupt");
    }
}
