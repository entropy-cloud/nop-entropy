package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-C4-009 regression (plan 2498 Phase 4): the two defaulting points of
 * {@code SingleTurnExecutor} (the backward-compatible constructor and the
 * full constructor's null fallback) must not place the blocking
 * {@code chatService.call} (up to 120s) on {@code ForkJoinPool.commonPool()}.
 * Post-fix the default carrier is the platform global worker pool
 * ({@code nop-global-worker}) with a companion WARN (warn is visibility only —
 * the carrier change is the fix; warn-only alone is not accepted as terminal
 * state, rule 16). The production wiring (explicit executor via
 * {@code AgentExecutorResolver}) is pinned regression-free: explicit
 * executors are honoured and never warn.
 */
public class TestSingleTurnExecutorDefaultCarrier_C4_009 {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger executorLogger;

    @BeforeEach
    void setUp() {
        executorLogger = (Logger) LoggerFactory.getLogger(SingleTurnExecutor.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        executorLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null && executorLogger != null) {
            executorLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    /** Chat service that records the thread executing the blocking call body. */
    private static IChatService threadRecordingChatService(AtomicReference<String> callThread) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                callThread.set(Thread.currentThread().getName());
                return successResponse();
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                throw new UnsupportedOperationException("callStream not used in this test");
            }
        };
    }

    private static ChatResponse successResponse() {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("ok");
        return ChatResponse.success(msg);
    }

    private long warnCount() {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("default-timeout-executor"))
                .count();
    }

    @Test
    void backwardCompatibleConstructorRunsLlmCallOnGlobalWorkerNotCommonPool_C4_009() throws Exception {
        AtomicReference<String> callThread = new AtomicReference<>();
        SingleTurnExecutor executor = new SingleTurnExecutor(
                threadRecordingChatService(callThread), null);

        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        executor.execute(ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertNotNull(callThread.get(), "the LLM call body must have executed");
        assertTrue(callThread.get().startsWith("nop-global-worker"),
                "default carrier must be the platform global worker pool, got: " + callThread.get());
        assertFalse(callThread.get().contains("ForkJoinPool.commonPool-worker"),
                "the blocking LLM call must never land on the JVM common pool, got: " + callThread.get());

        // companion visibility signal (not the fix itself — the carrier change is)
        assertTrue(warnCount() >= 1,
                "defaulting to the shared worker pool must emit the companion WARN");
    }

    @Test
    void nullTimeoutExecutorFallsBackToGlobalWorkerWithWarn_C4_009() throws Exception {
        AtomicReference<String> callThread = new AtomicReference<>();
        SingleTurnExecutor executor = new SingleTurnExecutor(
                threadRecordingChatService(callThread), null, 120_000L, null);

        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        executor.execute(ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(callThread.get().startsWith("nop-global-worker"),
                "null timeoutExecutor must fall back to the global worker pool, got: " + callThread.get());
        assertFalse(callThread.get().contains("ForkJoinPool.commonPool-worker"),
                "null fallback must never land on the JVM common pool, got: " + callThread.get());
        assertTrue(warnCount() >= 1, "the null fallback must emit the companion WARN");
    }

    /**
     * Production-wiring regression pin ({@code AgentExecutorResolver:215-216}
     * passes a dedicated executor): explicit executors are used as-is and the
     * defaulting WARN is not emitted.
     */
    @Test
    void explicitExecutorIsHonouredWithoutWarn_C4_009() throws Exception {
        AtomicReference<String> callThread = new AtomicReference<>();
        DefaultThreadPoolExecutor dedicated = DefaultThreadPoolExecutor.newExecutor(
                "c4-009-dedicated-executor", 1, 16, true);
        try {
            SingleTurnExecutor executor = new SingleTurnExecutor(
                    threadRecordingChatService(callThread), null, 120_000L, dedicated);

            AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
            executor.execute(ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertNotNull(callThread.get());
            assertTrue(callThread.get().startsWith("c4-009-dedicated-executor"),
                    "an explicitly injected executor must be used as-is, got: " + callThread.get());
            assertFalse(warnCount() >= 1,
                    "the production wiring (explicit executor) must not trigger the defaulting WARN");
        } finally {
            dedicated.destroy();
        }
    }
}
