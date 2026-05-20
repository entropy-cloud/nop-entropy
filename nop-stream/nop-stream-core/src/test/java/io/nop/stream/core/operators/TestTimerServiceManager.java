/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTimerServiceManager {

    private TimerServiceManager manager;
    private List<String> firedTimers;

    @BeforeEach
    void setUp() {
        manager = new TimerServiceManager();
        firedTimers = new ArrayList<>();
    }

    private HeapInternalTimerService<String> createTimerService(String name) {
        Triggerable<Object, String> triggerable = new Triggerable<Object, String>() {
            @Override
            public void onEventTime(InternalTimer<Object, String> timer) throws Exception {
                firedTimers.add(name + ":" + timer.getTimestamp());
            }

            @Override
            public void onProcessingTime(InternalTimer<Object, String> timer) throws Exception {
            }
        };
        return new HeapInternalTimerService<>(triggerable);
    }

    @Test
    void testAdvanceWatermarkPropagatesToAllServices() throws Exception {
        HeapInternalTimerService<String> svc1 = createTimerService("svc1");
        HeapInternalTimerService<String> svc2 = createTimerService("svc2");

        manager.registerTimerService(svc1);
        manager.registerTimerService(svc2);

        svc1.registerEventTimeTimer("a", 1000L);
        svc2.registerEventTimeTimer("b", 1000L);

        manager.advanceWatermark(new Watermark(1500L));

        assertEquals(2, firedTimers.size());
        assertTrue(firedTimers.contains("svc1:1000"));
        assertTrue(firedTimers.contains("svc2:1000"));
    }

    @Test
    void testNoServices() throws Exception {
        manager.advanceWatermark(new Watermark(1000L));
        assertEquals(0, firedTimers.size());
    }
}
