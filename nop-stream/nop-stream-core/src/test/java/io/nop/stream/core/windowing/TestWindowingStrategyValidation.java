package io.nop.stream.core.windowing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S-11 (2026-09-01 core audit, plan 0938-2): WindowingStrategy parameter
 * validation — negative allowed lateness is never meaningful (it would purge
 * windows before they open) and must fail fast.
 */
class TestWindowingStrategyValidation {

    @Test
    void testZeroAllowedLatenessIsLegal() {
        assertDoesNotThrow(() ->
                new WindowingStrategy("ws-1", "tumbling-1h", "eventTimeTrigger", 0L, AccumulationMode.ACCUMULATING));
    }

    @Test
    void testNegativeAllowedLatenessFailsFast() {
        assertThrows(IllegalArgumentException.class, () ->
                new WindowingStrategy("ws-1", "tumbling-1h", "eventTimeTrigger", -1L, AccumulationMode.DISCARDING));
    }
}
