/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.operators.ProcessingTimeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link TaskProcessingTimeService} and {@link TimerRegistrationFuture}
 * (plan `2026-08-13-0132-1` Phase 1, Rule #25): register / trigger / cancel, callback
 * exception policy, re-arm during fire, and due-flag maintenance.
 */
public class TestTaskProcessingTimeService {

    @Test
    void testRegisterAndFireDueTimers() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicInteger fired = new AtomicInteger();

        svc.registerTimer(1000L, time -> fired.addAndGet(1));
        svc.registerTimer(1500L, time -> fired.addAndGet(2));

        assertFalse(svc.isTimerDue(999L), "No timer due before the earliest timestamp");
        assertTrue(svc.isTimerDue(1000L), "Earliest timer due at its timestamp");
        assertTrue(svc.isTimerDue(2000L), "All timers due after the latest timestamp");

        svc.fireDueTimers(999L);
        assertEquals(0, fired.get(), "Nothing fires before the earliest timestamp");

        svc.fireDueTimers(1000L);
        assertEquals(1, fired.get(), "Only the 1000ms timer fires at 1000");

        svc.fireDueTimers(2000L);
        assertEquals(3, fired.get(), "Remaining timer fires at 2000");
        assertFalse(svc.isTimerDue(3000L), "No timers remain after firing");
    }

    @Test
    void testCallbackReceivesRegisteredTimestamp() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicReference<Long> seen = new AtomicReference<>();

        svc.registerTimer(777L, time -> seen.set(time));
        svc.fireDueTimers(1000L);

        assertEquals(777L, seen.get(), "Callback receives its registered timestamp");
    }

    @Test
    void testCancelRemovesRegistration() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicInteger fired = new AtomicInteger();

        ScheduledFuture<?> f1 = svc.registerTimer(500L, time -> fired.addAndGet(1));
        ScheduledFuture<?> f2 = svc.registerTimer(500L, time -> fired.addAndGet(2));

        assertTrue(f1.cancel(false), "First pending registration cancels");
        assertFalse(f1.cancel(false), "Double cancel returns false");
        assertTrue(f1.isCancelled());
        assertTrue(f1.isDone(), "Cancelled future is done");

        svc.fireDueTimers(1000L);
        assertEquals(2, fired.get(), "Only the non-cancelled timer fired");
        assertTrue(f2.isDone(), "Fired future is done");
        assertFalse(f2.isCancelled());
    }

    @Test
    void testCancelAfterFireReturnsFalse() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicInteger fired = new AtomicInteger();

        ScheduledFuture<?> f = svc.registerTimer(500L, time -> fired.addAndGet(1));
        svc.fireDueTimers(1000L);

        assertEquals(1, fired.get());
        assertFalse(f.cancel(false), "Already-fired registration cannot be cancelled");
    }

    @Test
    void testReArmDuringFire() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicInteger fires = new AtomicInteger();

        // Re-arms itself one second after each fire. The fire loop polls the entry BEFORE
        // invoking callbacks, so the re-registration must not disturb the current fire and
        // must not double-fire.
        java.util.concurrent.atomic.AtomicReference<ProcessingTimeService.ProcessingTimeCallback> reArming =
                new java.util.concurrent.atomic.AtomicReference<>();
        reArming.set(time -> {
            fires.incrementAndGet();
            if (fires.get() < 3) {
                svc.registerTimer(time + 1000L, reArming.get());
            }
        });
        svc.registerTimer(1000L, reArming.get());

        svc.fireDueTimers(1000L);
        assertEquals(1, fires.get(), "First fire at 1000");
        assertTrue(svc.isTimerDue(2000L), "Re-armed timer visible at 2000");
        svc.fireDueTimers(2000L);
        assertEquals(2, fires.get(), "Second fire at 2000");
        svc.fireDueTimers(3000L);
        assertEquals(3, fires.get(), "Third fire at 3000");
    }

    @Test
    void testCallbackExceptionFailsFast() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        AtomicInteger secondFired = new AtomicInteger();

        svc.registerTimer(1000L, time -> {
            throw new IllegalStateException("boom");
        });
        svc.registerTimer(1000L, time -> secondFired.addAndGet(1));

        // Recorded decision (Phase 1): PTS callbacks fail fast — the exception propagates
        // to the task thread, which fails the task visibly instead of silently continuing.
        TaskProcessingTimeService.ProcessingTimeCallbackException ex = assertThrows(
                TaskProcessingTimeService.ProcessingTimeCallbackException.class,
                () -> svc.fireDueTimers(1000L));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertEquals(0, secondFired.get(),
                "No further callbacks run after a failing callback (fail-fast)");
    }

    @Test
    void testGetCurrentProcessingTimeTracksWallClock() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        long before = System.currentTimeMillis();
        long now = svc.getCurrentProcessingTime();
        long after = System.currentTimeMillis();
        assertTrue(now >= before && now <= after, "Current processing time is wall-clock time");
    }

    @Test
    void testRegisterNullCallbackRejected() {
        TaskProcessingTimeService svc = new TaskProcessingTimeService();
        assertThrows(IllegalArgumentException.class, () -> svc.registerTimer(1000L, null));
    }
}
