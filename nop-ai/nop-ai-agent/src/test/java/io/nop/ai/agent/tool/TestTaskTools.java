package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 2: the task tools — idempotent output read (the
 * notification claim is separate), stop cancellation with the future
 * fallback, terminal-idempotent stop, and fail-loud seams (unknown taskId,
 * cross-session access, UOE registry bridge).
 */
public class TestTaskTools {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    private static DefaultAgentEngine newEngine(InMemorySessionStore store) {
        // Builder.build() runs applyTo → initBackgroundSupport: the engine
        // ships with a wired DefaultBackgroundTaskRegistry + fork executor
        return new DefaultAgentEngine.Builder(noChat(), null)
                .sessionStore(store)
                .build();
    }

    private static io.nop.ai.api.chat.IChatService noChat() {
        return new io.nop.ai.api.chat.IChatService() {
            @Override
            public CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException("no llm call expected");
            }

            @Override
            public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return subscriber -> subscriber.onComplete();
            }
        };
    }

    private static AgentToolExecuteContext contextFor(DefaultAgentEngine engine, InMemorySessionStore store,
                                                      String sessionId) {
        AgentSession session = store.getOrCreate(sessionId, "test-agent");
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), null, 0L, null, null, null,
                engine, null, sessionId, "test-agent");
        ctx.setSession(session);
        return ctx;
    }

    private static AiToolCall callWithTaskId(String taskId) {
        // engine-dispatch shape: JSON input, null node (dual-source read)
        AiToolCall call = new AiToolCall();
        call.setId(1);
        call.setInput("{\"taskId\": \"" + taskId + "\"}");
        return call;
    }

    @Test
    void taskOutputReadsStatusAndTailWithoutConsuming() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        DefaultBackgroundTaskRegistry registry = (DefaultBackgroundTaskRegistry) engine.getBackgroundTaskRegistry();
        AgentToolExecuteContext ctx = contextFor(engine, store, "s-tools-1");

        String taskId = registry.submit("bash", "s-tools-1", "call-1", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "build output line");

        AiToolCallResult first = new TaskOutputExecutor()
                .executeAsync(callWithTaskId(taskId), ctx).toCompletableFuture().join();
        assertEquals("success", first.getStatus(),
                first.getError() != null ? first.getError().getBody() : "no error body");
        String body = first.getOutput().getBody();
        assertTrue(body.contains("status=COMPLETED"), body);
        assertTrue(body.contains("build output line"), body);

        // idempotent read: a second read returns the same content and never
        // disturbs the notification claim
        AiToolCallResult second = new TaskOutputExecutor()
                .executeAsync(callWithTaskId(taskId), ctx).toCompletableFuture().join();
        assertEquals(first.getOutput().getBody(), second.getOutput().getBody());
    }

    @Test
    void taskOutputUnknownTaskIdFailsLoud() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        AgentToolExecuteContext ctx = contextFor(engine, store, "s-tools-1");

        AiToolCallResult result = new TaskOutputExecutor()
                .executeAsync(callWithTaskId("bg-404"), ctx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("unknown taskId"), result.getError().getBody());
    }

    @Test
    void taskOutputCrossSessionRefused() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        DefaultBackgroundTaskRegistry registry = (DefaultBackgroundTaskRegistry) engine.getBackgroundTaskRegistry();
        AgentToolExecuteContext ctx = contextFor(engine, store, "s-tools-reader");

        String taskId = registry.submit("bash", "s-tools-owner", "call-1", 1000L);
        AiToolCallResult result = new TaskOutputExecutor()
                .executeAsync(callWithTaskId(taskId), ctx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("cross-session"), result.getError().getBody());
    }

    @Test
    void taskToolsOutsideEngineContextFailLoud() {
        io.nop.ai.toolkit.api.IToolExecuteContext plainCtx = new io.nop.ai.toolkit.api.IToolExecuteContext() {
            @Override public File getWorkDir() {
                return new File(".");
            }

            @Override public Map<String, String> getEnvs() {
                return Map.of();
            }

            @Override public long getExpireAt() {
                return Long.MAX_VALUE;
            }

            @Override public io.nop.api.core.util.ICancelToken getCancelToken() {
                return null;
            }

            @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() {
                return null;
            }

            @Override public io.nop.commons.concurrent.executor.IThreadPoolExecutor getExecutor() {
                return io.nop.commons.concurrent.executor.SyncThreadPoolExecutor.INSTANCE;
            }
        };
        AiToolCallResult result = new TaskOutputExecutor()
                .executeAsync(callWithTaskId("bg-1"), plainCtx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        // errorResult unwraps to the root cause — either the executor's
        // wrapper message or the bridge UOE itself must name the seam
        assertTrue(result.getError().getBody().contains("agent engine context")
                        || result.getError().getBody().contains("getBackgroundTaskRegistry"),
                result.getError().getBody());
    }

    @Test
    void taskStopCancelsRunningTaskWithFutureFallback() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        DefaultBackgroundTaskRegistry registry = (DefaultBackgroundTaskRegistry) engine.getBackgroundTaskRegistry();
        AgentToolExecuteContext ctx = contextFor(engine, store, "s-tools-stop");

        ExecutorService forkPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-fork");
            t.setDaemon(true);
            return t;
        });
        try {
            registry.setBackgroundExecutor(forkPool);
            CountDownLatch bodyStarted = new CountDownLatch(1);
            String taskId = registry.submit("bash", "s-tools-stop", "call-stop-1", 3_600_000L);
            registry.run(taskId, () -> {
                bodyStarted.countDown();
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(bodyStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));

            AiToolCallResult result = new TaskStopExecutor()
                    .executeAsync(callWithTaskId(taskId), ctx).toCompletableFuture().join();
            assertEquals("success", result.getStatus());
            assertTrue(result.getOutput().getBody().contains("status=FAILED"),
                    "stop moves the running task to FAILED: " + result.getOutput().getBody());
            assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, registry.snapshot(taskId).getStatus());

            // idempotent: stopping the now-terminal task reports its status
            AiToolCallResult again = new TaskStopExecutor()
                    .executeAsync(callWithTaskId(taskId), ctx).toCompletableFuture().join();
            assertEquals("success", again.getStatus());
            assertTrue(again.getOutput().getBody().contains("status=FAILED"));
        } finally {
            forkPool.shutdownNow();
        }
    }

    @Test
    void taskStopUnknownTaskIdFailsLoud() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        AgentToolExecuteContext ctx = contextFor(engine, store, "s-tools-stop-404");

        AiToolCallResult result = new TaskStopExecutor()
                .executeAsync(callWithTaskId("bg-404"), ctx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("unknown taskId"), result.getError().getBody());
    }
}
