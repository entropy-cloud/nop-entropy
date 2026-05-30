package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCountTrigger {

    @Test
    void testCountTriggerCannotMerge() {
        CountTrigger<TimeWindow> trigger = CountTrigger.of(5);
        assertFalse(trigger.canMerge(), "CountTrigger.canMerge() should return false");
    }
}
