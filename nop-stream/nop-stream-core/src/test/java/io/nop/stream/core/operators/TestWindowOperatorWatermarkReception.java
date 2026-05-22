/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Bug N45: WindowOperator timer not registered with timeServiceManager. Fix should pass.")
public class TestWindowOperatorWatermarkReception {

    @Test
    void testWatermarkAdvancesTimerService() throws Exception {
        List<Long> firedTimers = new ArrayList<>();

        HeapInternalTimerService<String> heapTimerService = new HeapInternalTimerService<>(
                new Triggerable<Object, String>() {
                    @Override
                    public void onEventTime(InternalTimer<Object, String> timer) {
                        firedTimers.add(timer.getTimestamp());
                    }

                    @Override
                    public void onProcessingTime(InternalTimer<Object, String> timer) {
                    }
                });

        TimerServiceManager timeServiceManager = new TimerServiceManager();
        timeServiceManager.registerTimerService(heapTimerService);

        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void open() throws Exception {
                setTimeServiceManager(timeServiceManager);
            }
        };

        operator.open();

        heapTimerService.registerEventTimeTimer("window-1", 500);
        heapTimerService.registerEventTimeTimer("window-1", 1000);

        operator.processWatermark(new Watermark(800));

        assertEquals(1, firedTimers.size(), "Timer at 500 should have fired");
        assertEquals(500L, firedTimers.get(0));

        operator.processWatermark(new Watermark(1500));

        assertEquals(2, firedTimers.size(), "Timer at 1000 should have fired");
        assertEquals(1000L, firedTimers.get(1));
    }
}
