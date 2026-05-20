/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestHeapInternalTimerService {

    private HeapInternalTimerService<String> timerService;
    private List<InternalTimer<Object, String>> firedTimers;

    @BeforeEach
    void setUp() {
        firedTimers = new ArrayList<>();
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                firedTimers.add(timer);
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        timerService = new HeapInternalTimerService<>(triggerable);
    }

    @Test
    void testInitialWatermark() {
        assertEquals(Long.MIN_VALUE, timerService.currentWatermark());
    }

    @Test
    void testRegisterAndFireSingleTimer() throws Exception {
        timerService.registerEventTimeTimer("window-1", 1000L);
        assertEquals(1, timerService.numEventTimeTimers());

        timerService.advanceWatermark(1000L);

        assertEquals(0, timerService.numEventTimeTimers());
        assertEquals(1, firedTimers.size());
        assertEquals(1000L, firedTimers.get(0).getTimestamp());
        assertEquals("window-1", firedTimers.get(0).getNamespace());
    }

    @Test
    void testFireMultipleTimers() throws Exception {
        timerService.registerEventTimeTimer("w1", 500L);
        timerService.registerEventTimeTimer("w2", 1000L);
        timerService.registerEventTimeTimer("w3", 1500L);

        timerService.advanceWatermark(1200L);

        assertEquals(1, timerService.numEventTimeTimers());
        assertEquals(2, firedTimers.size());
        assertEquals(500L, firedTimers.get(0).getTimestamp());
        assertEquals(1000L, firedTimers.get(1).getTimestamp());
    }

    @Test
    void testDeleteEventTimeTimer() throws Exception {
        timerService.registerEventTimeTimer("w1", 1000L);
        timerService.deleteEventTimeTimer("w1", 1000L);

        assertEquals(0, timerService.numEventTimeTimers());

        timerService.advanceWatermark(2000L);
        assertEquals(0, firedTimers.size());
    }

    @Test
    void testWatermarkAdvancesButNoTimerFiredBelowThreshold() throws Exception {
        timerService.registerEventTimeTimer("w1", 1000L);
        timerService.advanceWatermark(500L);
        assertEquals(500L, timerService.currentWatermark());
        assertEquals(0, firedTimers.size());

        timerService.advanceWatermark(1500L);
        assertEquals(1, firedTimers.size());
    }

    @Test
    void testAdvanceWatermarkToSameValue() throws Exception {
        timerService.advanceWatermark(1000L);
        timerService.registerEventTimeTimer("w1", 1000L);

        timerService.advanceWatermark(1000L);
        assertEquals(0, firedTimers.size());
    }

    @Test
    void testForEachEventTimeTimer() throws Exception {
        timerService.registerEventTimeTimer("w1", 500L);
        timerService.registerEventTimeTimer("w2", 1000L);

        List<String> namespaces = new ArrayList<>();
        timerService.forEachEventTimeTimer((ns, ts) -> namespaces.add(ns));

        assertEquals(2, namespaces.size());
        assertTrue(namespaces.contains("w1"));
        assertTrue(namespaces.contains("w2"));
    }

    @Test
    void testFireAtExactWatermark() throws Exception {
        timerService.registerEventTimeTimer("w1", 1000L);
        timerService.advanceWatermark(1000L);
        assertEquals(1, firedTimers.size());
    }

    @Test
    void testMaxWatermarkFiresAll() throws Exception {
        timerService.registerEventTimeTimer("w1", 1000L);
        timerService.registerEventTimeTimer("w2", Long.MAX_VALUE - 1);

        timerService.advanceWatermark(Long.MAX_VALUE);
        assertEquals(2, firedTimers.size());
    }
}
