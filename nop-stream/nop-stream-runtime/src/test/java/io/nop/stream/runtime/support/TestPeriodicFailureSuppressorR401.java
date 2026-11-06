/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01 E2 pin for W-13 (finding 1.1-C7-005 + C7-014 ⑥-⑨ + 8.16 msgqueue/
 * StreamMetricsReporter 补遗; plan 2534 ruling E.1): the three-state periodic-failure
 * log suppressor. A site in continuous failure logs its FIRST entry in full, stays
 * SILENT but counted inside the 30s aggregation window, emits exactly ONE aggregate
 * summary at the window boundary (class+message summary + window count, never the
 * full repeating stack), and logs one CLEARED entry on the site's recovery signal.
 * Object-level deterministic assertions (no log-capture infra — the suppressor
 * instance is the test seam S4, ruling E.1).
 */
class TestPeriodicFailureSuppressorR401 {

    @Test
    void firstFailureLogsFullThenWindowSilencesAndAggregates() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        PeriodicFailureSuppressor suppressor =
                new PeriodicFailureSuppressor("test-site", 30_000L, clock::get);

        // first failure: full entry
        assertEquals(PeriodicFailureSuppressor.Action.LOG_FULL,
                suppressor.failure(new IllegalStateException("boom-1")),
                "the first failure must log in full");
        assertEquals(1, suppressor.getWindowFailureCount());

        // same-window failures: silent, counted
        for (int i = 0; i < 99; i++) {
            assertEquals(PeriodicFailureSuppressor.Action.SUPPRESS,
                    suppressor.failure(new IllegalStateException("boom-" + (i + 2))),
                    "failures inside the window must be silent: failure " + (i + 2));
        }
        assertEquals(100, suppressor.getWindowFailureCount(), "the window counts every failure");
        assertEquals(99, suppressor.getSuppressedCount(), "99 same-window failures were suppressed");
        assertTrue(suppressor.getLastSummary().contains("IllegalStateException"),
                "the last-failure summary carries the exception class");

        // window expiry: exactly ONE aggregate entry, then a fresh window
        clock.addAndGet(31_000L);
        assertEquals(PeriodicFailureSuppressor.Action.LOG_AGGREGATE,
                suppressor.failure(new IllegalStateException("boom-next-window")),
                "the first failure after window expiry logs the aggregate summary");
        assertEquals(99, suppressor.getLastWindowSuppressedCount(),
                "the aggregate line reports the closed window's suppressed count");
        assertEquals(1, suppressor.getWindowFailureCount(),
                "the aggregate entry opens a fresh window");
        assertEquals(99, suppressor.getSuppressedCount(),
                "the cumulative suppressed counter is unchanged by the aggregate entry");
    }

    @Test
    void successSignalLogsClearedOnceAndResetsTheWindow() {
        AtomicLong clock = new AtomicLong(0L);
        PeriodicFailureSuppressor suppressor =
                new PeriodicFailureSuppressor("test-site", 30_000L, clock::get);

        suppressor.failure(new RuntimeException("f1"));
        suppressor.failure(new RuntimeException("f2"));
        assertEquals(2, suppressor.getWindowFailureCount());

        // recovery: exactly one cleared entry
        assertEquals(PeriodicFailureSuppressor.Action.LOG_CLEARED,
                suppressor.success(), "the first success signal logs the cleared entry");
        assertEquals(PeriodicFailureSuppressor.Action.NONE,
                suppressor.success(), "subsequent successes are silent");
        assertEquals(0, suppressor.getWindowFailureCount(), "cleared resets the failure window");
        assertTrue(suppressor.isCleared(), "state is cleared after the success signal");

        // a failure after cleared logs full again (fresh cycle)
        assertEquals(PeriodicFailureSuppressor.Action.LOG_FULL,
                suppressor.failure(new RuntimeException("after-cleared")),
                "a failure after cleared logs full (first-in-window)");
    }

    @Test
    void successWithoutAnyFailureIsSilent() {
        AtomicLong clock = new AtomicLong(0L);
        PeriodicFailureSuppressor suppressor =
                new PeriodicFailureSuppressor("idle-site", 30_000L, clock::get);
        assertEquals(PeriodicFailureSuppressor.Action.NONE, suppressor.success(),
                "a success on a never-failing site logs nothing");
        assertTrue(suppressor.isCleared());
    }

    @Test
    void hundredFailureBurstLogsExactlyOneCallerEntry() {
        AtomicLong clock = new AtomicLong(0L);
        PeriodicFailureSuppressor suppressor =
                new PeriodicFailureSuppressor("burst-site", 30_000L, clock::get);

        int callerLogEntries = 0;
        for (int i = 0; i < 100; i++) {
            if (suppressor.failure(new RuntimeException("burst-" + i))
                    != PeriodicFailureSuppressor.Action.SUPPRESS) {
                callerLogEntries++;
            }
        }
        assertEquals(1, callerLogEntries,
                "a 100-failure burst inside one window must produce exactly ONE caller log entry");
        assertEquals(99, suppressor.getSuppressedCount());
    }
}
