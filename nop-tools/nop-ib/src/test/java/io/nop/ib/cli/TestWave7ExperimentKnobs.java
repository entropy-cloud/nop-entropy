package io.nop.ib.cli;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** wave7 E1/E4/E5 experiment knobs: pure helpers pinned; switches default off. */
class TestWave7ExperimentKnobs {

    /** E1 helper: failed classes extract from worker summary; null when absent. */
    @Test
    void failedClassesOfExtractsClassNames() {
        Map<String, Object> d = Map.of("class", "a.B", "method", "m");
        List<String> out = TestCommand.failedClassesOf(Map.of("failed", List.of(d)));
        assertEquals(List.of("a.B"), out);
        assertNull(TestCommand.failedClassesOf(Map.of("tests", 5)));
        assertNull(TestCommand.failedClassesOf(Map.of("failed", List.of())));
    }

    /** E4 knob: override floor at 1, default path = budget-derived. */
    @Test
    void moduleSlotsOverride() {
        int saved = Integer.getInteger("nop.ib.moduleSlots", 0);
        try {
            System.setProperty("nop.ib.moduleSlots", "1");
            assertEquals(1, TestCommand.moduleSlots());
            System.setProperty("nop.ib.moduleSlots", "64");
            assertEquals(64, TestCommand.moduleSlots());
        } finally {
            if (saved == 0)
                System.clearProperty("nop.ib.moduleSlots");
            else
                System.setProperty("nop.ib.moduleSlots", String.valueOf(saved));
        }
        // default (no override) must be at least the old floor 4
        assertEquals(Math.max(4, TestCommand.WORKER_BUDGET / 4), TestCommand.moduleSlots());
    }
}
