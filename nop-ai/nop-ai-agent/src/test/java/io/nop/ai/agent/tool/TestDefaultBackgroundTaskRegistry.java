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

    /**
     * P1-7.1-001（plan 2398）：FAILED 源的懒重投递——桥回调必须收到真实业务终态
     * FAILED（非簿记态 NOTIFICATION_FAILED），且重投成功后状态恢复 FAILED
     * （修复前被无条件改写 COMPLETED——失败任务被永久误报完成）。
     */
    @Test
    void failedSourceLazyRedeliveryKeepsFailedStatusAndPassesRealStatusToBridge() {
        java.util.List<String> bridgeStatuses = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry((entry, status) -> {
            bridgeStatuses.add(status);
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("session store down");
            }
        });
        String taskId = registry.submit("bash", "s1", "call-f", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_FAILED, "biz failed");

        assertEquals(IBackgroundTaskRegistry.STATUS_NOTIFICATION_FAILED, registry.rawStatus(taskId));

        registry.snapshot(taskId); // 触发懒重投（第二次桥调用成功）

        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED,
                registry.snapshot(taskId).getStatus(),
                "FAILED source must stay FAILED after successful redelivery (P1-7.1-001)");
        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, bridgeStatuses.get(0),
                "bridge must receive the real terminal status, not the bookkeeping state");
        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, bridgeStatuses.get(1),
                "redelivery must also carry the real terminal status");
    }

    /** TIMEOUT 源同型：重投成功后状态保持 TIMEOUT。 */
    @Test
    void timeoutSourceLazyRedeliveryKeepsTimeoutStatus() {
        java.util.List<String> bridgeStatuses = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        DefaultBackgroundTaskRegistry registry = new DefaultBackgroundTaskRegistry((entry, status) -> {
            bridgeStatuses.add(status);
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("session store down");
            }
        });
        String taskId = registry.submit("bash", "s1", "call-t", 1000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_TIMEOUT, "timed out");

        registry.snapshot(taskId);

        assertEquals(IBackgroundTaskRegistry.STATUS_TIMEOUT,
                registry.snapshot(taskId).getStatus(),
                "TIMEOUT source must stay TIMEOUT after successful redelivery (P1-7.1-001)");
        assertEquals(IBackgroundTaskRegistry.STATUS_TIMEOUT, bridgeStatuses.get(1),
                "redelivery must carry the real TIMEOUT status");
    }
}
