/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests watermark advancement of {@link HeapInternalTimerService} via
 * {@link AbstractStreamOperator#processWatermark}. Despite the class name, this
 * does NOT directly test {@code WindowOperator}; it validates the timer service
 * integration on the base operator. {@code WindowOperator}-specific watermark
 * tests live in {@code nop-stream-runtime}'s equivalent test class.
 */
public class TestWindowOperatorWatermarkReception {

    private AbstractStreamOperator<String> createOperator(TimerServiceManager mgr, TestOutput<String> out) {
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {};
        operator.setOutput((Output) out);
        operator.setTimeServiceManager(mgr);
        return operator;
    }

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

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = createOperator(timeServiceManager, testOutput);
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

    @Test
    void testWatermarkNotGoingBackwards() throws Exception {
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

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = createOperator(timeServiceManager, testOutput);
        operator.open();

        heapTimerService.registerEventTimeTimer("w1", 500);

        operator.processWatermark(new Watermark(1000));
        assertEquals(1, firedTimers.size());

        operator.processWatermark(new Watermark(500));
        assertEquals(1, firedTimers.size(), "Watermark going backwards should not fire timers again");
    }

    @Test
    void testNoTimerFiredBeforeWatermarkReachesThreshold() throws Exception {
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

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = createOperator(timeServiceManager, testOutput);
        operator.open();

        heapTimerService.registerEventTimeTimer("w1", 1000);
        heapTimerService.registerEventTimeTimer("w1", 2000);

        operator.processWatermark(new Watermark(500));
        assertEquals(0, firedTimers.size(), "No timer should fire before watermark reaches threshold");

        operator.processWatermark(new Watermark(1500));
        assertEquals(1, firedTimers.size(), "Only timer at 1000 should fire");
        assertEquals(1000L, firedTimers.get(0));
    }
}
