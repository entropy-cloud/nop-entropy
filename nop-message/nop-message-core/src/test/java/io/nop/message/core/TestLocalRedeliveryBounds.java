package io.nop.message.core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-04（plan 2558 裁定 C）carrier: the Local redelivery envelope — destroy hook
 * releases the lazily-created scheduler/worker (no thread leak); pending redeliveries
 * are bounded per subscription; the pending depth is observable as a Micrometer gauge;
 * {@code delay=0} ConsumeLater is throttled to a minimum interval.
 */
public class TestLocalRedeliveryBounds {

    /** Scheduler mock that captures due tasks without running them (deterministic). */
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

    @Test
    void destroyHookStopsSchedulerAndWorker() {
        LocalMessageService service = new LocalMessageService();
        capturingScheduler(service);
        // real delivery worker so the destroy hook must release both handles
        service.setDeliveryExecutorForTest(java.util.concurrent.Executors.newSingleThreadExecutor());
        service.setRedeliverMinIntervalMs(0);
        service.subscribe("t-destroy", (t, m, c) -> new ConsumeLater(0), null);
        service.send("t-destroy", "m1", null);
        assertTrue(service.isConsumePipelineActive(), "alive before destroy");

        service.destroy();
        assertFalse(service.isConsumePipelineActive(), "destroy must stop the pipeline");
        // destroy() is idempotent
        assertDoesNotThrow(service::destroy);
    }

    @Test
    void pendingRedeliveriesBoundedPerSubscription() {
        LocalMessageService service = new LocalMessageService();
        List<Runnable> captured = capturingScheduler(service);
        service.setDeliveryExecutorForTest(directExecutor());
        service.setMaxPendingRedeliveriesPerSubscription(2);
        service.setRedeliverMinIntervalMs(0);

        AtomicInteger invocations = new AtomicInteger();
        service.subscribe("t-bound", (t, m, c) -> {
            invocations.incrementAndGet();
            return new ConsumeLater(0);
        }, null);

        // three sends, cap=2: the first two defer, the third overflows and is dropped
        // with the typed overflow WARN (bounded pending queue).
        service.send("t-bound", "m1", null);
        service.send("t-bound", "m2", null);
        int pendingAfterTwo = service.getConsumers().get("t-bound").get(0).getPendingRedeliveries();
        assertEquals(2, pendingAfterTwo, "each ConsumeLater deferral holds one pending slot");

        service.send("t-bound", "m3", null);
        assertEquals(2, service.getConsumers().get("t-bound").get(0).getPendingRedeliveries(),
                "the third deferral must be rejected by the per-subscription cap");
        assertEquals(2, service.getPendingRedeliveryDepth(),
                "aggregate pending depth must respect the cap too");

        // drain: running the two due deliveries re-schedules (handler defers again) but
        // the pending slots transfer 1:1 — depth never grows past the cap.
        for (Runnable task : new ArrayList<>(captured)) {
            task.run();
        }
        assertEquals(2, service.getPendingRedeliveryDepth(),
                "re-scheduled deferrals transfer pending slots; depth stays at cap");
    }

    @Test
    void pendingDepthGaugeIsObservable() {
        LocalMessageService service = new LocalMessageService();
        capturingScheduler(service);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        service.setMetricRegistry(registry);
        service.setMaxPendingRedeliveriesPerSubscription(5);
        service.setRedeliverMinIntervalMs(0);

        service.subscribe("t-gauge", (t, m, c) -> new ConsumeLater(0), null);
        service.send("t-gauge", "g1", null);
        service.send("t-gauge", "g2", null);

        Gauge gauge = registry.find("nop.message.local.redelivery.pending").gauge();
        assertNotNull(gauge, "the pending-depth gauge must be registered");
        assertEquals(2.0, gauge.value(),
                "the gauge must observe the aggregate pending redelivery depth");
    }

    @Test
    void delayZeroRedeliveryIsThrottledToMinimumInterval() {
        LocalMessageService service = new LocalMessageService();
        List<Long> delays = new ArrayList<>();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    delays.add((Long) inv.getArgument(1));
                    return mock(ScheduledFuture.class);
                });
        service.setRedeliveryExecutorForTest(scheduler);
        service.setRedeliverMinIntervalMs(5000);

        service.subscribe("t-throttle", (t, m, c) -> new ConsumeLater(0), null);
        service.send("t-throttle", "m1", null);

        assertEquals(1, delays.size());
        Long delay = delays.get(0);
        assertNotNull(delay);
        assertTrue(delay >= 4000L && delay <= 5000L,
                "a delay=0 ConsumeLater must be throttled to the minimum interval, got " + delay);

        // an explicit positive delay bypasses the throttle
        delays.clear();
        service.setRedeliverMinIntervalMs(0);
        service.subscribe("t-throttle2", (t, m, c) -> new ConsumeLater(300), null);
        service.send("t-throttle2", "m2", null);
        assertEquals(1, delays.size());
        assertEquals(300L, delays.get(0),
                "an explicit positive ConsumeLater delay must not be inflated by the throttle");
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
