/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.KeyedStreamImpl;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowTranslation {

    private KeyedStream<String, String> keyedStream;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        KeySelector<String, String> keySelector = element -> element;
        keyedStream = new KeyedStreamImpl<>(null, null, keySelector);
    }

    @Test
    public void testTumblingWindowTranslation() {
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(1000);
        WindowedStream<String, String, TimeWindow> windowed = keyedStream.window(assigner);

        assertNotNull(windowed);
        assertTrue(windowed instanceof WindowedStreamImpl);

        WindowedStreamImpl<String, String, TimeWindow> impl = (WindowedStreamImpl<String, String, TimeWindow>) windowed;
        assertSame(assigner, impl.getWindowAssigner());
    }

    @Test
    public void testSlidingWindowTranslation() {
        SlidingEventTimeWindows assigner = SlidingEventTimeWindows.of(1000, 500);
        WindowedStream<String, String, TimeWindow> windowed = keyedStream.window(assigner);

        assertNotNull(windowed);
        assertTrue(windowed instanceof WindowedStreamImpl);

        WindowedStreamImpl<String, String, TimeWindow> impl = (WindowedStreamImpl<String, String, TimeWindow>) windowed;
        assertSame(assigner, impl.getWindowAssigner());
    }

    @Test
    public void testDefaultTriggerSetFromAssigner() {
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(1000);
        WindowedStreamImpl<String, String, TimeWindow> windowed =
                (WindowedStreamImpl<String, String, TimeWindow>) keyedStream.window(assigner);

        assertNotNull(windowed.getTrigger());
        assertTrue(windowed.getTrigger() instanceof EventTimeTrigger);
    }

    @Test
    public void testTimeWindowShortcutCreatesTumbling() {
        WindowedStream<String, String, TimeWindow> windowed = keyedStream.timeWindow(1000);

        assertTrue(windowed instanceof WindowedStreamImpl);
        WindowedStreamImpl<String, String, TimeWindow> impl =
                (WindowedStreamImpl<String, String, TimeWindow>) windowed;
        assertTrue(impl.getWindowAssigner() instanceof TumblingEventTimeWindows);
    }

    @Test
    public void testTimeWindowWithSlideCreatesSliding() {
        WindowedStream<String, String, TimeWindow> windowed = keyedStream.timeWindow(1000, 500);

        assertTrue(windowed instanceof WindowedStreamImpl);
        WindowedStreamImpl<String, String, TimeWindow> impl =
                (WindowedStreamImpl<String, String, TimeWindow>) windowed;
        assertTrue(impl.getWindowAssigner() instanceof SlidingEventTimeWindows);
    }

    @Test
    public void testKeyedStreamRetainsKeySelector() {
        TumblingEventTimeWindows assigner = TumblingEventTimeWindows.of(1000);
        WindowedStreamImpl<String, String, TimeWindow> windowed =
                (WindowedStreamImpl<String, String, TimeWindow>) keyedStream.window(assigner);

        assertSame(keyedStream, windowed.getKeyedStream());
        assertNotNull(windowed.getKeyedStream().getKeySelector());
    }
}
