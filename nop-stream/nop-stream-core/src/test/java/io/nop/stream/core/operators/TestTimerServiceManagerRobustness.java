package io.nop.stream.core.operators;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6.7-007（plan 2387）：TimerServiceManager 定时器异常 fail-fast 契约——
 * 任一 service 失败时首个异常在全部 service 尝试完毕后抛出（其余 addSuppressed），
 * 不允许「失败 service 被跳过 + watermark 照常转发 + 作业无感知」的静默丢发射。
 * （修复前本测试类以 assertDoesNotThrow 固化吞异常契约——随修复一并改写。）
 */
class TestTimerServiceManagerRobustness {

    private static final Triggerable<Object, String> NOOP_TRIGGERABLE = new Triggerable<Object, String>() {
        @Override
        public void onEventTime(InternalTimer<Object, String> timer) throws Exception {}
        @Override
        public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {}
    };

    private static StreamException failure(String detail) {
        StreamException e = new StreamException(ARG_DETAIL);
        e.param(ARG_DETAIL, detail);
        return e;
    }

    @Test
    void testAdvanceWatermarkThrowsAfterAllServicesAttempted() throws Exception {
        TimerServiceManager manager = new TimerServiceManager();

        StreamException boom = failure("Simulated failure");
        HeapInternalTimerService<Object, String> failingService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                throw boom;
            }
        };

        final boolean[] secondCalled = {false};
        StreamException secondBoom = failure("Second failure");
        HeapInternalTimerService<Object, String> secondFailingService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                secondCalled[0] = true;
                throw secondBoom;
            }
        };

        manager.registerTimerService(failingService);
        manager.registerTimerService(secondFailingService);

        io.nop.stream.core.streamrecord.watermark.Watermark wm =
                new io.nop.stream.core.streamrecord.watermark.Watermark(100L);

        StreamException thrown = assertThrows(StreamException.class, () -> manager.advanceWatermark(wm),
                "failing timer service must fail the watermark advance (fail-fast, P1-6.7-007)");
        assertTrue(thrown == boom, "the FIRST service's exception is the primary cause");
        assertTrue(secondCalled[0], "all registered services are attempted despite the first failure");
        assertTrue(thrown.getSuppressed().length >= 1 && containsIdentity(thrown.getSuppressed(), secondBoom),
                "subsequent failures are attached as suppressed");
    }

    @Test
    void testAdvanceWatermarkThrowsWhenOnlyOneServiceFails() {
        TimerServiceManager manager = new TimerServiceManager();

        HeapInternalTimerService<Object, String> failingService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                throw failure("Only failure");
            }
        };
        final boolean[] goodAdvanced = {false};
        HeapInternalTimerService<Object, String> goodService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                goodAdvanced[0] = true;
            }
        };

        manager.registerTimerService(failingService);
        manager.registerTimerService(goodService);

        io.nop.stream.core.streamrecord.watermark.Watermark wm =
                new io.nop.stream.core.streamrecord.watermark.Watermark(100L);

        assertThrows(StreamException.class, () -> manager.advanceWatermark(wm));
        assertTrue(goodAdvanced[0], "the healthy service is still advanced before the failure surfaces");
    }

    @Test
    void testFireProcessingTimeTimersThrowsAfterAllServicesAttempted() {
        TimerServiceManager manager = new TimerServiceManager();

        HeapInternalTimerService<Object, String> failingService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void fireProcessingTimeTimers(long timestamp) {
                throw failure("Fire failure");
            }
        };
        final boolean[] secondFired = {false};
        HeapInternalTimerService<Object, String> goodService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void fireProcessingTimeTimers(long timestamp) {
                secondFired[0] = true;
            }
        };

        manager.registerTimerService(failingService);
        manager.registerTimerService(goodService);

        assertThrows(StreamException.class, () -> manager.fireProcessingTimeTimers(1000L),
                "firing failure must surface (fail-fast, P1-6.7-007)");
        assertTrue(secondFired[0], "all registered services are attempted despite the first failure");
    }

    @Test
    void testAdvanceWatermarkWithNoServices() throws Exception {
        TimerServiceManager manager = new TimerServiceManager();
        io.nop.stream.core.streamrecord.watermark.Watermark wm =
                new io.nop.stream.core.streamrecord.watermark.Watermark(100L);
        assertDoesNotThrow(() -> manager.advanceWatermark(wm));
    }

    private static boolean containsIdentity(Throwable[] suppressed, Throwable candidate) {
        for (Throwable t : suppressed) {
            if (t == candidate)
                return true;
        }
        return false;
    }
}
