/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the unified {@link HeapInternalTimerService} carries a proper
 * {@code <K>} key type parameter (G16 unification) and that typed keys (not just
 * {@code Object}) survive register → fire for both event-time and processing-time timers.
 *
 * <p>The sibling {@code TestHeapInternalTimerService} exercises the {@code K=Object} path
 * (matching {@code ProcessOperator}'s usage). This class exercises {@code K=String}
 * (matching {@code WindowOperator}'s keyed usage where the key comes from a
 * {@code KeySelector}).
 */
public class TestHeapInternalTimerServiceTypedKey {

    @Test
    void testEventTimeTimerWithTypedKey() throws Exception {
        List<InternalTimer<String, String>> fired = new ArrayList<>();
        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
                fired.add(timer);
            }

            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
            }
        };

        HeapInternalTimerService<String, String> service = new HeapInternalTimerService<>(triggerable, () -> "key-A");

        service.registerEventTimeTimer("window-1", 1000L);
        assertEquals(1, service.numEventTimeTimers());

        service.advanceWatermark(1000L);

        assertEquals(0, service.numEventTimeTimers());
        assertEquals(1, fired.size());
        assertEquals("key-A", fired.get(0).getKey());
        assertEquals("window-1", fired.get(0).getNamespace());
        assertEquals(1000L, fired.get(0).getTimestamp());
    }

    @Test
    void testProcessingTimeTimerWithTypedKey() throws Exception {
        List<InternalTimer<String, String>> fired = new ArrayList<>();
        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
            }

            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
                fired.add(timer);
            }
        };

        HeapInternalTimerService<String, String> service = new HeapInternalTimerService<>(triggerable, () -> "key-A");

        service.registerProcessingTimeTimer("window-1", 1000L);
        assertEquals(1, service.numProcessingTimeTimers());

        service.fireProcessingTimeTimers(1000L);

        assertEquals(0, service.numProcessingTimeTimers());
        assertEquals(1, fired.size());
        assertEquals("key-A", fired.get(0).getKey());
        assertEquals("window-1", fired.get(0).getNamespace());
        assertEquals(1000L, fired.get(0).getTimestamp());
    }

    @Test
    void testTypedKeyFromSupplier() throws Exception {
        List<String> capturedKeys = new ArrayList<>();
        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
                capturedKeys.add(timer.getKey());
            }

            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
            }
        };

        String[] keyHolder = {"key-A"};
        HeapInternalTimerService<String, String> service =
                new HeapInternalTimerService<>(triggerable, () -> keyHolder[0]);

        service.registerEventTimeTimer("ns1", 1000L);
        keyHolder[0] = "key-B";
        service.registerEventTimeTimer("ns2", 2000L);

        service.advanceWatermark(3000L);

        assertEquals(2, capturedKeys.size());
        assertEquals("key-A", capturedKeys.get(0));
        assertEquals("key-B", capturedKeys.get(1));
    }

    @Test
    void testEventTimeAndProcessingTimeCoexistWithTypedKey() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        List<InternalTimer<String, String>> processingFired = new ArrayList<>();
        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
                eventFired.add(timer);
            }

            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
                processingFired.add(timer);
            }
        };

        HeapInternalTimerService<String, String> service = new HeapInternalTimerService<>(triggerable, () -> "key-X");

        service.registerEventTimeTimer("ns-et", 1000L);
        service.registerProcessingTimeTimer("ns-pt", 2000L);

        assertEquals(1, service.numEventTimeTimers());
        assertEquals(1, service.numProcessingTimeTimers());

        service.advanceWatermark(1500L);
        assertEquals(1, eventFired.size());
        assertEquals(0, processingFired.size());

        service.fireProcessingTimeTimers(2500L);
        assertEquals(1, processingFired.size());

        assertEquals("key-X", eventFired.get(0).getKey());
        assertEquals("key-X", processingFired.get(0).getKey());
    }
}
