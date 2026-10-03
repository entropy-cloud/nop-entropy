package io.nop.ai.agent.tool;

import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 1: background task registry state machine — terminal
 * transitions, exactly-once notification claim, NOTIFICATION_FAILED lazy
 * redelivery, stop channels, snapshot evidence.
 */
public class TestDefaultBackgroundTaskRegistry {

    @Test
    void stateMachineTerminalTransitionsAndIdempotentComplete() {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        String taskId = registry.submit("bash", "s1", "call-1", 3_600_000L);

        assertEquals(IBackgroundTaskRegistry.STATUS_RUNNING, registry.snapshot(taskId).getStatus());

        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "output body");
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());

        // Idempotent: a second complete (e.g. duplicate whenComplete) does not
        // overwrite the first terminal state.
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_FAILED, "late error");
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());
        assertEquals("output body", registry.readOutput(taskId));
    }

    @Test
    void bridgeInvokedExactlyOncePerTask() {
        AtomicInteger bridges = new AtomicInteger();
        List<String> statuses = new ArrayList<>();
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry((entry, status) -> {
            bridges.incrementAndGet();
            statuses.add(status);
        });
        String taskId = registry.submit("bash", "s1", "call-1", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "out");
        // Duplicate complete must not re-bridge (claim already set).
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_FAILED, "late");

        assertEquals(1, bridges.get(), "notification claim must be exactly-once");
        assertEquals(1, statuses.size());
        assertTrue(registry.snapshot(taskId).isNotificationSent());
    }

    @Test
    void bridgeFailureMovesToNotificationFailedAndLazyRedeliveryRecovers() {
        AtomicInteger attempts = new AtomicInteger();
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry((entry, status) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("session store down");
            }
        });
        String taskId = registry.submit("bash", "s1", "call-1", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "out");

        assertEquals(IBackgroundTaskRegistry.STATUS_NOTIFICATION_FAILED,
                registry.rawStatus(taskId), "bridge failure must be fail-visible (raw, pre-redelivery)");

        // Lazy redelivery on the next snapshot/read: the bridge now succeeds.
        registry.snapshot(taskId);
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());
        assertTrue(registry.snapshot(taskId).isNotificationSent());
        assertEquals(2, attempts.get());
    }

    @Test
    void unknownTaskIdReturnsNullSnapshot() {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        assertNull(registry.snapshot("bg-unknown"));
        assertNull(registry.readOutput("bg-unknown"));
        assertNull(registry.stop("bg-unknown"));
    }

    @Test
    void stopTerminalTaskIsIdempotent() {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        String taskId = registry.submit("bash", "s1", "call-1", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "done");
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.stop(taskId),
                "stopping a terminal task reports its status without state change");
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus());
    }

    @Test
    void stopRunningWithoutTrackerUsesFutureFallback() throws Exception {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        String taskId = registry.submit("bash", "s1", "call-1", 1000L);
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        registry.attachFuture(taskId, future);

        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, registry.stop(taskId));
        assertTrue(future.isCancelled(), "fallback channel must cancel the registered future");
        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, registry.snapshot(taskId).getStatus());
    }

    @Test
    void stopRunningPrefersTrackerKillChannel() {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        AtomicInteger trackerCalls = new AtomicInteger();
        registry.setTrackerCancelHook((sessionId, callId) -> trackerCalls.incrementAndGet());
        String taskId = registry.submit("bash", "s1", "call-9", 1000L);
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        registry.attachFuture(taskId, future);

        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, registry.stop(taskId));
        assertEquals(1, trackerCalls.get(), "tracker kill channel must be preferred (design §三.6)");
        assertFalse(future.isCancelled(), "future fallback must not fire when the tracker channel succeeded");
    }

    @Test
    void outputTailIsBoundedAndBytesAreTotal() {
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry(null);
        String taskId = registry.submit("bash", "s1", "call-1", 1000L);
        String big = "X".repeat(50_000);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, big);

        assertEquals(50_000, registry.snapshot(taskId).getTotalBytes(), "total bytes reflect the full output");
        assertEquals(DefaultBackgroundTaskRegistry.OUTPUT_TAIL_LIMIT,
                registry.readOutput(taskId).length(), "captured tail is bounded");
    }
}
