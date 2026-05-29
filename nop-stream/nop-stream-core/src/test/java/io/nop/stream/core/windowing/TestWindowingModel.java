package io.nop.stream.core.windowing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAccumulationMode {

    @Tag("low-value")
    @Test
    void testEnumValueOf_roundTrips() {
        assertSame(AccumulationMode.DISCARDING, AccumulationMode.valueOf("DISCARDING"));
        assertSame(AccumulationMode.ACCUMULATING, AccumulationMode.valueOf("ACCUMULATING"));
    }
}

class TestWindowingStrategy {

    @Tag("low-value")
    @Test
    void testCreation() {
        WindowingStrategy ws = new WindowingStrategy("ws-1", "tumbling-1h", "eventTimeTrigger",
                5000, AccumulationMode.ACCUMULATING);
        assertEquals("ws-1", ws.getStrategyId());
        assertEquals("tumbling-1h", ws.getWindowFnId());
        assertEquals(AccumulationMode.ACCUMULATING, ws.getAccumulationMode());
    }
}

class TestPaneInfo {

    @Tag("low-value")
    @Test
    void testPaneInfoCreation() {
        PaneInfo info = new PaneInfo(0, true, false, PaneInfo.PaneTiming.EARLY);
        assertEquals(0, info.getIndex());
        assertTrue(info.isFirst());
        assertFalse(info.isLast());
        assertEquals(PaneInfo.PaneTiming.EARLY, info.getTiming());
    }
}
