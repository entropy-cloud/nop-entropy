package io.nop.stream.core.operators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestHeapInternalTimerServiceReentrancy {

    private HeapInternalTimerService<String> timerService;
    private List<InternalTimer<Object, String>> firedTimers;

    @BeforeEach
    void setUp() {
        firedTimers = new ArrayList<>();
    }

    @Test
    void testCallbackCanRegisterNewTimerWithoutCME() throws Exception {
        AtomicInteger fireCount = new AtomicInteger(0);
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                fireCount.incrementAndGet();
                timerService.registerEventTimeTimer("future-" + timer.getNamespace(), timer.getTimestamp() + 1000);
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);

        timerService.registerEventTimeTimer("ns1", 1000L);
        timerService.registerEventTimeTimer("ns2", 1000L);

        assertDoesNotThrow(() -> timerService.advanceWatermark(1000L));
        assertEquals(2, fireCount.get());
        assertEquals(2, timerService.numEventTimeTimers(), "Newly registered timers should exist");
    }

    @Test
    void testCallbackDeleteTimerDuringAdvance() throws Exception {
        AtomicInteger fireCount = new AtomicInteger(0);
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                fireCount.incrementAndGet();
                timerService.deleteEventTimeTimer("ns2", 1000L);
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);

        timerService.registerEventTimeTimer("ns1", 1000L);
        timerService.registerEventTimeTimer("ns2", 1000L);

        assertDoesNotThrow(() -> timerService.advanceWatermark(1000L));
        assertEquals(2, fireCount.get(), "Both timers should fire before delete takes effect");
        assertEquals(0, timerService.numEventTimeTimers());
    }

    @Test
    void testNewTimerAtSameTimestampNotFiredImmediately() throws Exception {
        List<String> firedNamespaces = new ArrayList<>();
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                firedNamespaces.add(timer.getNamespace());
                if ("original".equals(timer.getNamespace())) {
                    timerService.registerEventTimeTimer("re-scheduled", timer.getTimestamp());
                }
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);

        timerService.registerEventTimeTimer("original", 1000L);

        timerService.advanceWatermark(1000L);
        assertEquals(1, firedNamespaces.size(), "Only original timer should fire");
        assertEquals("original", firedNamespaces.get(0));
        assertEquals(1, timerService.numEventTimeTimers(), "Re-scheduled timer should remain");

        timerService.advanceWatermark(1001L);
        assertEquals(2, firedNamespaces.size(), "Re-scheduled timer should fire on next advance");
        assertTrue(firedNamespaces.contains("re-scheduled"));
    }

    @Test
    void testRemoveThenFirePreventsDoubleFire() throws Exception {
        AtomicInteger fireCount = new AtomicInteger(0);
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                fireCount.incrementAndGet();
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);

        timerService.registerEventTimeTimer("ns1", 1000L);
        timerService.registerEventTimeTimer("ns2", 2000L);

        timerService.advanceWatermark(3000L);
        assertEquals(2, fireCount.get(), "Each timer should fire exactly once");
        assertEquals(0, timerService.numEventTimeTimers());
    }

    @Test
    void testCallbackRegistersTimerForAlreadyAdvancedTimestamp() throws Exception {
        List<Long> firedTimestamps = new ArrayList<>();
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                firedTimestamps.add(timer.getTimestamp());
                if (timer.getTimestamp() == 1000L) {
                    timerService.registerEventTimeTimer("late-added", 500L);
                }
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);

        timerService.registerEventTimeTimer("ns1", 1000L);

        timerService.advanceWatermark(2000L);
        assertEquals(1, firedTimestamps.size(), "Timer registered for past watermark should not fire in same advance");
        assertEquals(1, timerService.numEventTimeTimers(), "Late-added timer should still exist");

        timerService.advanceWatermark(3000L);
        assertEquals(2, firedTimestamps.size(), "Late-added timer should fire on subsequent advance");
    }
}
