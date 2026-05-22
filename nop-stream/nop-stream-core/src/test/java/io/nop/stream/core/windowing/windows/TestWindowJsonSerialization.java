package io.nop.stream.core.windowing.windows;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestWindowJsonSerialization {

    @Test
    void testTimeWindowRoundTrip() {
        TimeWindow original = new TimeWindow(100, 200);
        String json = JsonTool.serialize(original, false);

        TimeWindow restored = JsonTool.parseBeanFromText(json, TimeWindow.class);

        assertEquals(original, restored);
        assertEquals(100, restored.getStart());
        assertEquals(200, restored.getEnd());
    }

    @Test
    void testTimeWindowZeroValues() {
        TimeWindow original = new TimeWindow(0, 0);
        String json = JsonTool.serialize(original, false);

        TimeWindow restored = JsonTool.parseBeanFromText(json, TimeWindow.class);

        assertEquals(original, restored);
    }
}
