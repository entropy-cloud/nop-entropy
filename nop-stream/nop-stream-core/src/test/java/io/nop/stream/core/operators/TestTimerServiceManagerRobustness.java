package io.nop.stream.core.operators;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

class TestTimerServiceManagerRobustness {

    private static final Triggerable<Object, String> NOOP_TRIGGERABLE = new Triggerable<Object, String>() {
        @Override
        public void onEventTime(InternalTimer<Object, String> timer) throws Exception {}
        @Override
        public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {}
    };

    @Test
    void testAdvanceWatermarkContinuesAfterServiceFailure() throws Exception {
        TimerServiceManager manager = new TimerServiceManager();

        HeapInternalTimerService<String> failingService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "Simulated failure");
            }
        };

        final boolean[] secondCalled = {false};
        HeapInternalTimerService<String> goodService = new HeapInternalTimerService<>(NOOP_TRIGGERABLE) {
            @Override
            public void advanceWatermark(long timestamp) {
                secondCalled[0] = true;
            }
        };

        manager.registerTimerService(failingService);
        manager.registerTimerService(goodService);

        io.nop.stream.core.streamrecord.watermark.Watermark wm =
                new io.nop.stream.core.streamrecord.watermark.Watermark(100L);

        assertDoesNotThrow(() -> manager.advanceWatermark(wm));
        assertTrue(secondCalled[0], "Second service should still be advanced");
    }

    @Test
    void testAdvanceWatermarkWithNoServices() throws Exception {
        TimerServiceManager manager = new TimerServiceManager();
        io.nop.stream.core.streamrecord.watermark.Watermark wm =
                new io.nop.stream.core.streamrecord.watermark.Watermark(100L);
        assertDoesNotThrow(() -> manager.advanceWatermark(wm));
    }
}
