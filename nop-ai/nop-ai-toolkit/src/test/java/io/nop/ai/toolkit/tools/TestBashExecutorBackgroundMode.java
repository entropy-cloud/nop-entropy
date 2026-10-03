package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxConfig;
import io.nop.ai.toolkit.tools.sandbox.HostBashSandbox;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 2: bash runInBackground — immediate confirmation,
 * off-pool fork, terminal-state capture (COMPLETED / TIMEOUT), clamped
 * per-call wall-clock budget, and fail-loud seams (no registry bridge, no
 * sandbox).
 */
public class TestBashExecutorBackgroundMode {

    private ExecutorService forkPool;

    @BeforeEach
    void setUp() {
        forkPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-background-fork");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        forkPool.shutdownNow();
    }

    /**
     * Toolkit-local registry stub: same contract as the agent-side default
     * implementation, forked on a local pool so the test stays in this module.
     */
    static class StubRegistry implements IBackgroundTaskRegistry {
        final Map<String, TaskSnapshot> snapshots = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, String> outputs = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, java.util.concurrent.CountDownLatch> dones = new java.util.concurrent.ConcurrentHashMap<>();
        final ExecutorService pool;
        BiConsumer<String, String> onTerminal = (id, status) -> {
        };
        long ids = 0;

        StubRegistry(ExecutorService pool) {
            this.pool = pool;
        }

        @Override
        public String submit(String toolName, String sessionId, String callId, long timeoutMs) {
            String taskId = "bg-" + (++ids);
            snapshots.put(taskId, new TaskSnapshot(taskId, toolName, sessionId, STATUS_RUNNING,
                    System.currentTimeMillis(), timeoutMs, null, 0, false));
            dones.put(taskId, new CountDownLatch(1));
            return taskId;
        }

        @Override
        public void run(String taskId, Runnable body) {
            pool.submit(() -> {
                try {
                    body.run();
                } catch (Exception e) {
                    complete(taskId, STATUS_FAILED, "body failed: " + e);
                }
            });
        }

        @Override
        public void complete(String taskId, String status, String outputText) {
            TaskSnapshot old = snapshots.get(taskId);
            if (old == null || !STATUS_RUNNING.equals(old.getStatus())) {
                return; // idempotent
            }
            snapshots.put(taskId, new TaskSnapshot(taskId, old.getToolName(), old.getSessionId(), status,
                    old.getSubmitTime(), old.getTimeoutMs(),
                    outputText == null ? null
                            : (outputText.length() <= 8192 ? outputText : outputText.substring(outputText.length() - 8192)),
                    outputText == null ? 0 : outputText.length(), false));
            outputs.put(taskId, outputText);
            dones.get(taskId).countDown();
            onTerminal.accept(taskId, status);
        }

        @Override
        public TaskSnapshot snapshot(String taskId) {
            return snapshots.get(taskId);
        }

        @Override
        public String readOutput(String taskId) {
            return outputs.get(taskId);
        }

        @Override
        public String stop(String taskId) {
            TaskSnapshot snapshot = snapshots.get(taskId);
            if (snapshot == null || !STATUS_RUNNING.equals(snapshot.getStatus())) {
                return snapshot == null ? null : snapshot.getStatus();
            }
            complete(taskId, STATUS_FAILED, snapshot.getOutputTail());
            return STATUS_FAILED;
        }

        boolean awaitTerminal(String taskId, long timeoutMs) throws InterruptedException {
            return dones.get(taskId).await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private HostBashSandbox newHostSandbox(int wallSeconds) {
        BashSandboxConfig config = BashSandboxConfig.builder()
                .wallSeconds(wallSeconds)
                .build();
        List<java.nio.file.Path> allowed = List.of(
                Paths.get(System.getProperty("java.io.tmpdir")));
        return new HostBashSandbox(config, allowed);
    }

    static AiToolCall backgroundCall(String command, Long backgroundTaskTimeoutMs) {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.setAttr("runInBackground", "true");
        if (backgroundTaskTimeoutMs != null) {
            node.setAttr("backgroundTaskTimeoutMs", String.valueOf(backgroundTaskTimeoutMs));
        }
        node.makeChild("command").setContentValue(command);
        return AiToolCall.fromNode(node);
    }

    @Test
    void backgroundSubmissionReturnsConfirmationAndCompletesWithOutput() throws Exception {
        StubRegistry registry = new StubRegistry(forkPool);
        BashExecutor executor = new BashExecutor(newHostSandbox(15));

        AtomicReference<String> notifiedStatus = new AtomicReference<>();
        registry.onTerminal = (id, status) -> notifiedStatus.set(status);

        AiToolCallResult result = executor.executeAsync(
                backgroundCall("echo Hello Background", null),
                new ContextWithRegistry(registry)).toCompletableFuture().join();

        assertEquals("success", result.getStatus(), "background submission itself succeeds");
        String body = result.getOutput().getBody();
        assertTrue(body.contains("[BACKGROUND_TASK started taskId="), body);
        assertTrue(body.contains("backgroundTaskTimeoutMs=3600000"), "default 1h budget in the confirmation");
        String taskId = body.substring(body.indexOf("taskId=") + 7, body.indexOf(' ', body.indexOf("taskId=")));

        assertEquals(IBackgroundTaskRegistry.STATUS_RUNNING, registry.snapshot(taskId).getStatus(),
                "task must be RUNNING right after submission (off-pool fork)");

        assertTrue(registry.awaitTerminal(taskId, 15_000), "task must complete");
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, notifiedStatus.get());
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());
        assertTrue(registry.readOutput(taskId).contains("Hello Background"),
                "captured output carries the command stdout");
    }

    @Test
    void backgroundTimeoutMapsToTimeoutStatus() throws Exception {
        StubRegistry registry = new StubRegistry(forkPool);
        // sandbox with generous config wallSeconds; the per-call 1s budget is
        // what kills the 5s sleep (wallSeconds = clamp(backgroundTaskTimeoutMs))
        BashExecutor executor = new BashExecutor(newHostSandbox(30));

        AiToolCallResult result = executor.executeAsync(
                backgroundCall("sleep 5", 1000L),
                new ContextWithRegistry(registry)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        String body = result.getOutput().getBody();
        String taskId = body.substring(body.indexOf("taskId=") + 7, body.indexOf(' ', body.indexOf("taskId=")));
        assertEquals(1000L, registry.snapshot(taskId).getTimeoutMs(), "per-call budget recorded");

        assertTrue(registry.awaitTerminal(taskId, 20_000), "wallSeconds must terminate the sleep");
        assertEquals(IBackgroundTaskRegistry.STATUS_TIMEOUT, registry.snapshot(taskId).getStatus(),
                "sandbox wall-clock timeout maps to the TIMEOUT terminal status");
    }

    @Test
    void backgroundTimeoutIsClampedToHardCap() {
        assertEquals(BashExecutor.DEFAULT_BACKGROUND_TIMEOUT_MS,
                BashExecutor.clampBackgroundTimeout(0), "non-positive falls back to the default");
        assertEquals(BashExecutor.DEFAULT_BACKGROUND_TIMEOUT_MS,
                BashExecutor.clampBackgroundTimeout(-5), "negative falls back to the default");
        assertEquals(5_000L, BashExecutor.clampBackgroundTimeout(5_000), "in-budget value passes through");
        assertEquals(3_600_000L, BashExecutor.clampBackgroundTimeout(99_000_000),
                "value above the 1h cap is clamped, not rejected");
    }

    @Test
    void backgroundWithoutRegistryBridgeFailsLoud() {
        BashExecutor executor = new BashExecutor(newHostSandbox(15));
        AiToolCallResult result = executor.executeAsync(
                backgroundCall("echo hi", null),
                new PlainContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("runInBackground requires the agent engine context"),
                result.getError().getBody());
    }

    @Test
    void backgroundWithoutSandboxFailsClosed() {
        BashExecutor executor = new BashExecutor();
        StubRegistry registry = new StubRegistry(forkPool);
        AiToolCallResult result = executor.executeAsync(
                backgroundCall("echo hi", null),
                new ContextWithRegistry(registry)).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("fail-closed"),
                "no sandbox wired must refuse before any submission");
        assertNull(registry.snapshot("bg-1"), "no task may be submitted on the refused path");
    }

    /** Toolkit context exposing a registry (simulates the agent bridge). */
    static class ContextWithRegistry extends PlainContext {
        private final IBackgroundTaskRegistry registry;

        ContextWithRegistry(IBackgroundTaskRegistry registry) {
            this.registry = registry;
        }

        @Override
        public String getSessionId() {
            return "s-test";
        }

        @Override
        public IBackgroundTaskRegistry getBackgroundTaskRegistry() {
            return registry;
        }
    }

    static class PlainContext implements IToolExecuteContext {
        @Override public java.io.File getWorkDir() {
            return Paths.get(System.getProperty("java.io.tmpdir")).toFile();
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

}
}
