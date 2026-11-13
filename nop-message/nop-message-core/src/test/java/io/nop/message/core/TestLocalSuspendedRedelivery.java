package io.nop.message.core;

import io.nop.api.core.message.ConsumeLater;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 4.1-C3-006（plan 2558 裁定 J）carrier: a due redelivery finding its subscription
 * SUSPENDED at fire time must be RE-SCHEDULED (suspension accumulates a resume-flush
 * backlog bounded by the pending cap) — pre-fix it was permanently dropped together with
 * cancelled subscriptions. Only {@code cancelled} (or detachment from the consumers map)
 * discards a pending redelivery. The abandon matrix is declared in the
 * {@code LocalMessageService#scheduleRedelivery} javadoc and pinned here.
 */
public class TestLocalSuspendedRedelivery {

    private static List<Runnable> capturingScheduler(LocalMessageService service) {
        List<Runnable> captured = new ArrayList<>();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    captured.add((Runnable) inv.getArgument(0));
                    return mock(ScheduledFuture.class);
                });
        service.setRedeliveryExecutorForTest(scheduler);
        return captured;
    }

    private static LocalMessageService newService() {
        LocalMessageService service = new LocalMessageService();
        service.setDeliveryExecutorForTest(directExecutor());
        service.setRedeliverMinIntervalMs(0);
        service.setSuspendedRedeliverDelayMs(10);
        return service;
    }

    @Test
    void suspendedAtFireTimeIsRescheduledThenDeliveredOnResume() {
        LocalMessageService service = newService();
        List<Runnable> captured = capturingScheduler(service);

        AtomicInteger deliveries = new AtomicInteger();
        service.subscribe("t-susp", (t, m, c) -> {
            if (deliveries.incrementAndGet() == 1) {
                // first delivery defers; the subscription is suspended before the
                // redelivery fires
                return new ConsumeLater(0);
            }
            return null;
        }, null);

        service.send("t-susp", "deferred-msg", null);
        assertEquals(1, deliveries.get());
        assertEquals(1, captured.size(), "one redelivery task scheduled");
        assertEquals(1, service.getPendingRedeliveryDepth());

        LocalMessageService.Subscription subscription =
                service.getConsumers().get("t-susp").get(0);
        subscription.suspend();

        // fire-time suspended: pre-fix this DROPPED the message permanently;
        // post-fix it re-schedules.
        captured.get(0).run();
        assertEquals(1, deliveries.get(), "suspended subscription must not be delivered to");
        assertEquals(2, captured.size(),
                "fire-time suspended must RE-SCHEDULE the redelivery, not drop it (裁定 J)");
        assertEquals(1, service.getPendingRedeliveryDepth(),
                "the re-scheduled redelivery keeps its pending slot");

        subscription.resume();
        captured.get(1).run();
        assertEquals(2, deliveries.get(), "resume flush: the deferred message is delivered");
        assertEquals(0, service.getPendingRedeliveryDepth(),
                "delivered redelivery releases its pending slot");
    }

    @Test
    void cancelledAtFireTimeDiscardsThePendingRedelivery() {
        LocalMessageService service = newService();
        List<Runnable> captured = capturingScheduler(service);

        AtomicInteger deliveries = new AtomicInteger();
        service.subscribe("t-cancel", (t, m, c) -> {
            deliveries.incrementAndGet();
            return new ConsumeLater(0);
        }, null);
        service.send("t-cancel", "deferred-msg", null);
        assertEquals(1, captured.size());

        LocalMessageService.Subscription subscription =
                service.getConsumers().get("t-cancel").get(0);
        subscription.cancel();

        // abandon matrix: cancelled → discard (the ONLY abandon path besides detachment)
        captured.get(0).run();
        assertEquals(1, deliveries.get(), "cancelled subscription must not be delivered to");
        assertEquals(1, captured.size(), "cancelled discards — no re-schedule");
        assertEquals(0, service.getPendingRedeliveryDepth(),
                "the discarded redelivery releases its pending slot");
        assertTrue(subscription.isCancelled());
    }

    /** Same-thread executor: due deliveries run inline for deterministic assertions. */
    private static java.util.concurrent.ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return java.util.List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }
}
