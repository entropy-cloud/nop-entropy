package io.nop.ai.core.reliability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C7-007 (plan 2556 裁定 I): failure-memory entries (OPEN /
 * CLOSED-with-failures) are lazily TTL-swept inside the {@code allowCall}
 * compute critical section ({@code nop.ai.breaker.entry-ttl-ms} semantics,
 * default 1h — configurable here via the 4-arg constructor). Pristine
 * immediate cleanup is preserved.
 */
public class TestBreakerEntryTtl {

    private static int entriesSize(ThresholdBreaker b) throws Exception {
        Field field = ThresholdBreaker.class.getDeclaredField("entries");
        field.setAccessible(true);
        ConcurrentMap<?, ?> entries = (ConcurrentMap<?, ?>) field.get(b);
        return entries.size();
    }

    @Test
    void expiredOpenEntryIsLazilyCleared() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(1, 60_000L, 0L, 100L); // cooldown far beyond ttl
        b.recordFailure("openai:gpt-4"); // OPEN
        assertEquals(1, entriesSize(b));
        assertFalse(b.allowCall("openai:gpt-4"), "within the TTL the entry must still gate calls");

        Thread.sleep(150L); // past the entry TTL

        assertTrue(b.allowCall("openai:gpt-4"),
                "after the TTL the OPEN entry must be forgotten and the call allowed again");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "the swept entry resets to the healthy default");
        assertEquals(0, entriesSize(b), "the swept entry must be removed from the table");
    }

    @Test
    void closedWithFailuresEntryIsLazilyCleared() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(5, 60_000L, 0L, 100L);
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        assertEquals(1, entriesSize(b), "failure memory below the threshold must stay in the map");

        Thread.sleep(150L); // past the entry TTL

        assertTrue(b.allowCall("openai:gpt-4"), "calls are allowed while CLOSED regardless");
        assertEquals(0, entriesSize(b), "the stale failure memory must be swept on allowCall");

        // The key restarts fresh: two more failures must NOT trip (threshold 5,
        // swept counter restarted from zero).
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "after the sweep the failure counter restarts from zero");
    }

    @Test
    void failureMemoryWithinTtlIsKept() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(5, 60_000L, 0L, 3_600_000L);
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        b.allowCall("openai:gpt-4"); // sweep runs but TTL not elapsed
        assertEquals(1, entriesSize(b), "failure memory within the TTL must survive the sweep");
    }

    @Test
    void pristineImmediateCleanupPreserved() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(3, 60_000L, 0L, 3_600_000L);
        for (int i = 0; i < 50; i++) {
            assertTrue(b.allowCall("provider:model-" + i));
        }
        assertEquals(0, entriesSize(b),
                "pristine entries must still be dropped immediately (pre-existing shrink semantics)");
    }

    @Test
    void entryTtlZeroDisablesSweep() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(1, 60_000L, 0L, 0L); // TTL disabled
        b.recordFailure("openai:gpt-4"); // OPEN
        assertEquals(1, entriesSize(b));
        assertFalse(b.allowCall("openai:gpt-4"),
                "entryTtlMs=0 must keep the pre-裁定-I unbounded failure memory");
        assertEquals(1, entriesSize(b));
    }

    @Test
    void recoveredEntriesStillShrinkImmediately() throws Exception {
        ThresholdBreaker b = new ThresholdBreaker(5, 60_000L, 0L, 3_600_000L);
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        b.recordSuccess("openai:gpt-4"); // counter reset → pristine → immediate drop
        assertEquals(0, entriesSize(b), "success-driven shrink must remain immediate");
    }
}
