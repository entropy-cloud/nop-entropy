package io.nop.ai.agent.tool;

import io.nop.ai.shell.executor.ExecutionResult;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 3: the shell {@code &} sink adapter — submitting
 * through {@link BackgroundRegistryJobSink} yields an addressable registry
 * taskId whose terminal state captures the command result (exit code →
 * COMPLETED/FAILED, output → stdout+stderr). Test-wiring only: the shell
 * module has no production consumer (plan 2301 Known Residual).
 */
public class TestBackgroundRegistryJobSink {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    @Test
    void submitReturnsRegistryTaskIdAndCompletesWithResult() throws Exception {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        ExecutorService forkPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-sink-fork");
            t.setDaemon(true);
            return t;
        });
        try {
            registry.setBackgroundExecutor(forkPool);
            BackgroundRegistryJobSink sink = new BackgroundRegistryJobSink(registry, "s-sink");

            Callable<ExecutionResult> body = () -> new ExecutionResult(0, "job output\n", "");
            String taskId = sink.submit("echo job output", body);

            assertTrue(taskId.startsWith("bg-"), "taskId must be registry-addressable: " + taskId);
            assertEquals(IBackgroundTaskRegistry.STATUS_RUNNING, registry.snapshot(taskId).getStatus());

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && IBackgroundTaskRegistry.STATUS_RUNNING.equals(registry.snapshot(taskId).getStatus())) {
                Thread.sleep(20);
            }
            assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());
            assertTrue(registry.readOutput(taskId).contains("job output"));
            assertEquals("shell-&", registry.snapshot(taskId).getToolName());
        } finally {
            forkPool.shutdownNow();
        }
    }

    @Test
    void failingBodyMapsToFailedTerminalState() throws Exception {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        ExecutorService forkPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-sink-fork-2");
            t.setDaemon(true);
            return t;
        });
        try {
            registry.setBackgroundExecutor(forkPool);
            BackgroundRegistryJobSink sink = new BackgroundRegistryJobSink(registry, "s-sink");

            String taskId = sink.submit("false", () -> {
                throw new IllegalStateException("command not found");
            });

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && IBackgroundTaskRegistry.STATUS_RUNNING.equals(registry.snapshot(taskId).getStatus())) {
                Thread.sleep(20);
            }
            assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, registry.snapshot(taskId).getStatus());
            assertTrue(registry.readOutput(taskId).contains("command not found"),
                    "the failure text must be captured as output");
        } finally {
            forkPool.shutdownNow();
        }
    }
}
