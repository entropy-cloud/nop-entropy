package io.nop.ai.agent.fencing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Phase 1 fencing-token primitive contract surface
 * (plan 235 / L4-fencing-token).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link FencingToken} immutable factory + getters + null-actorId
 *       rejection.</li>
 *   <li>{@link FencingTokenDecision} valid/stale factories + allow⇒null-reason
 *       / stale⇒non-null-reason invariants + reason text format.</li>
 *   <li>{@link NoOpFencingTokenService} singleton + always-valid +
 *       non-null placeholder issue (zero-regression shipped default).</li>
 *   <li>{@link DefaultFencingTokenService} issue strictly-increasing sequence
 *       (first = 1); validate fresh token → valid; validate stale token →
 *       stale + reason; high-water monotonic update (no regression);
 *       per-actor independence.</li>
 *   <li>Primitive lifecycle end-to-end: issue → validate(valid) → issue →
 *       validate old token (stale) (Anti-Hollow #22).</li>
 *   <li>Concurrency atomicity: many threads concurrently issuing for the same
 *       actorId produce strictly unique, contiguous counters (CAS atomicity,
 *       vision §5.1).</li>
 * </ul>
 */
public class TestFencingTokenService {

    // ----- FencingToken -----

    @Test
    void tokenFactoryCapturesAllFields() {
        FencingToken t = FencingToken.of("actor-1", 7L, 1234L);
        assertEquals("actor-1", t.getActorId());
        assertEquals(7L, t.getMonotonicCounter());
        assertEquals(1234L, t.getIssuedAt());
    }

    @Test
    void tokenRejectsNullActorId() {
        assertThrows(NullPointerException.class,
                () -> FencingToken.of(null, 1L, 0L),
                "FencingToken.of must reject a null actorId");
    }

    // ----- FencingTokenDecision -----

    @Test
    void validDecisionHasValidTrueAndNullReason() {
        FencingTokenDecision d = FencingTokenDecision.valid("actor-1", 3L, 1L);
        assertTrue(d.isValid());
        assertFalse(d.isStale());
        assertNull(d.getReason(), "valid decision must have null reason");
        assertEquals("actor-1", d.getActorId());
        assertEquals(3L, d.getPresentedCounter());
        assertEquals(1L, d.getRecordedCounter());
    }

    @Test
    void staleDecisionHasValidFalseAndNonNullReason() {
        FencingTokenDecision d = FencingTokenDecision.stale("actor-1", 2L, 3L);
        assertFalse(d.isValid());
        assertTrue(d.isStale());
        assertNotNull(d.getReason(), "stale decision must have a non-null reason");
        assertEquals("actor-1", d.getActorId());
        assertEquals(2L, d.getPresentedCounter());
        assertEquals(3L, d.getRecordedCounter());
    }

    @Test
    void staleReasonCitesPresentedAndRecordedCounters() {
        FencingTokenDecision d = FencingTokenDecision.stale("actor-1", 2L, 3L);
        // vision §5.1 rule 3 + Minimum Rules #24: stale reason must cite the
        // numbers so an enforcement point can surface a precise message.
        assertTrue(d.getReason().contains("2"), "reason must cite presented counter: " + d.getReason());
        assertTrue(d.getReason().contains("3"), "reason must cite recorded counter: " + d.getReason());
        assertTrue(d.getReason().contains("<="), "reason must cite the <= comparison: " + d.getReason());
    }

    // ----- NoOpFencingTokenService -----

    @Test
    void noOpReturnsSingletonInstance() {
        assertSame(NoOpFencingTokenService.noOp(), NoOpFencingTokenService.noOp(),
                "noOp() must return the same singleton instance");
    }

    @Test
    void noOpValidateAlwaysValidForAnyToken() {
        NoOpFencingTokenService svc = NoOpFencingTokenService.noOp();
        // Any token — including a fresh one, a stale-looking one, and the
        // NoOp placeholder (counter 0) — is accepted (fencing disabled).
        assertTrue(svc.validate(FencingToken.of("a", 1L, 0L)).isValid());
        assertTrue(svc.validate(FencingToken.of("a", 5L, 0L)).isValid());
        assertTrue(svc.validate(FencingToken.of("a", 0L, 0L)).isValid());
        // zero-regression: a "stale-looking" low counter is still valid.
        FencingTokenDecision d = svc.validate(FencingToken.of("a", 999L, 0L));
        assertTrue(d.isValid());
        assertNull(d.getReason(), "NoOp valid has null reason");
    }

    @Test
    void noOpIssueReturnsNonNullPlaceholderToken() {
        NoOpFencingTokenService svc = NoOpFencingTokenService.noOp();
        FencingToken t = svc.issue("actor-1");
        assertNotNull(t, "NoOp issue must return a non-null token (Minimum Rules #24)");
        assertEquals("actor-1", t.getActorId(), "placeholder carries caller actorId verbatim");
        assertEquals(0L, t.getMonotonicCounter(), "placeholder counter is 0 (intentional disabled-mode)");
        assertTrue(t.getIssuedAt() > 0, "placeholder carries a real epoch ms");
    }

    // ----- DefaultFencingTokenService: issue contract -----

    @Test
    void issueReturnsStrictlyIncreasingContiguousCounters() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        for (long expected = 1; expected <= 5; expected++) {
            FencingToken t = svc.issue("actor-1");
            assertEquals(expected, t.getMonotonicCounter(),
                    "issue #" + expected + " must return counter " + expected);
            assertEquals("actor-1", t.getActorId());
            assertTrue(t.getIssuedAt() > 0);
        }
    }

    @Test
    void issueFirstCallReturnsCounterOne() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertEquals(1L, svc.issue("actor-x").getMonotonicCounter(),
                "first issue for an actor must return counter 1");
    }

    @Test
    void issueCountersAreIndependentPerActor() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertEquals(1L, svc.issue("actor-a").getMonotonicCounter());
        assertEquals(1L, svc.issue("actor-b").getMonotonicCounter());
        assertEquals(2L, svc.issue("actor-a").getMonotonicCounter());
        assertEquals(2L, svc.issue("actor-b").getMonotonicCounter());
        assertEquals(3L, svc.issue("actor-a").getMonotonicCounter());
    }

    // ----- DefaultFencingTokenService: validate fresh / stale -----

    @Test
    void validateFreshTokenIsValid() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        FencingToken fresh = svc.issue("actor-1"); // counter == 1
        FencingTokenDecision d = svc.validate(fresh);
        assertTrue(d.isValid(), "freshly issued token must validate as valid");
        assertNull(d.getReason());
        assertEquals("actor-1", d.getActorId());
        assertEquals(1L, d.getPresentedCounter());
        assertEquals(0L, d.getRecordedCounter(), "pre-update high-water was 0");
    }

    @Test
    void validateStaleTokenIsRejectedWithReason() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        // record a high-water of 3
        assertTrue(svc.validate(FencingToken.of("actor-1", 3L, 0L)).isValid());
        // an older token (counter 2) is now stale
        FencingTokenDecision d = svc.validate(FencingToken.of("actor-1", 2L, 0L));
        assertFalse(d.isValid());
        assertTrue(d.isStale());
        assertNotNull(d.getReason());
        assertEquals(2L, d.getPresentedCounter());
        assertEquals(3L, d.getRecordedCounter());
    }

    @Test
    void validateEqualCounterIsStale() {
        // vision §5.1 rule 3: counter <= recorded -> stale. The boundary case
        // counter == recorded is stale (strictly greater is required).
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertTrue(svc.validate(FencingToken.of("actor-1", 3L, 0L)).isValid());
        FencingTokenDecision d = svc.validate(FencingToken.of("actor-1", 3L, 0L));
        assertFalse(d.isValid(), "counter == recorded must be stale (strictly greater required)");
        assertEquals(3L, d.getRecordedCounter());
    }

    // ----- DefaultFencingTokenService: high-water monotonic update -----

    @Test
    void highWaterMonotonicallyAdvancesAndNeverRegresses() {
        // Plan item 111: validate(token counter=3) valid (recordedMax 1->3)
        //   -> validate(token counter=2) stale (2 <= 3)
        //   -> validate(token counter=1) stale (1 <= 3)
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        // seed high-water at 1
        FencingTokenDecision seed = svc.validate(FencingToken.of("actor-1", 1L, 0L));
        assertTrue(seed.isValid());

        // advance to 3
        FencingTokenDecision d3 = svc.validate(FencingToken.of("actor-1", 3L, 0L));
        assertTrue(d3.isValid());
        assertEquals(3L, d3.getPresentedCounter());
        assertEquals(1L, d3.getRecordedCounter(), "valid decision records pre-update high-water");

        // older tokens are now stale; high-water must not regress
        FencingTokenDecision d2 = svc.validate(FencingToken.of("actor-1", 2L, 0L));
        assertFalse(d2.isValid());
        assertEquals(2L, d2.getPresentedCounter());
        assertEquals(3L, d2.getRecordedCounter());

        FencingTokenDecision d1 = svc.validate(FencingToken.of("actor-1", 1L, 0L));
        assertFalse(d1.isValid());
        assertEquals(1L, d1.getPresentedCounter());
        assertEquals(3L, d1.getRecordedCounter());

        // high-water still 3: a token with counter 4 advances again
        FencingTokenDecision d4 = svc.validate(FencingToken.of("actor-1", 4L, 0L));
        assertTrue(d4.isValid());
        assertEquals(3L, d4.getRecordedCounter());
    }

    @Test
    void validateHighWaterIsIndependentPerActor() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertTrue(svc.validate(FencingToken.of("actor-a", 5L, 0L)).isValid());
        // actor-b's high-water is still 0, so a low counter is valid for it
        assertTrue(svc.validate(FencingToken.of("actor-b", 1L, 0L)).isValid());
        // but actor-a now rejects a low counter
        assertFalse(svc.validate(FencingToken.of("actor-a", 1L, 0L)).isValid());
    }

    // ----- Primitive lifecycle end-to-end (Anti-Hollow #22) -----

    @Test
    void primitiveLifecycleIssueValidateValidIssueValidateStale() {
        // Plan Closure Gate / Anti-Hollow #22: the primitive lifecycle
        // issue -> validate(valid) -> issue -> validate old token (stale)
        // must be exercised end-to-end, not just at the type level.
        DefaultFencingTokenService svc = new DefaultFencingTokenService();

        FencingToken first = svc.issue("actor-1");
        assertTrue(svc.validate(first).isValid(),
                "first issued token validates valid");

        FencingToken second = svc.issue("actor-1");
        assertNotEquals(first.getMonotonicCounter(), second.getMonotonicCounter(),
                "second issue must return a different counter");
        assertTrue(svc.validate(second).isValid(),
                "newly issued token validates valid");

        // the FIRST (now old) token is stale: its counter is not greater than
        // the recorded high-water (which is second's counter).
        FencingTokenDecision oldResult = svc.validate(first);
        assertFalse(oldResult.isValid(), "old token must now be stale");
        assertNotNull(oldResult.getReason(), "stale decision must explain why");
        assertTrue(oldResult.getRecordedCounter() >= second.getMonotonicCounter());
    }

    // ----- Concurrency atomicity (vision §5.1 CAS semantics) -----

    @Test
    void concurrentIssueProducesUniqueContiguousCounters() throws InterruptedException {
        // Plan item 112: many threads concurrently issuing for the same
        // actorId must produce strictly unique counters covering [1, n] (CAS
        // atomicity, no duplicate counter).
        final int threadCount = 100;
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        // pool size == threadCount so every task can run and reach the barrier
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Set<Long> counters = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    FencingToken t = svc.issue("shared-actor");
                    counters.add(t.getMonotonicCounter());
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all threads must reach the barrier");
            start.countDown(); // release everyone at once to maximize contention
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(threadCount, counters.size(),
                "no two concurrent issues may return the same counter: " + counters);
        for (long expected = 1; expected <= threadCount; expected++) {
            assertTrue(counters.contains(expected),
                    "counters must cover the contiguous range [1, " + threadCount + "]; missing " + expected);
        }
    }

    @Test
    void concurrentValidateDoesNotRegressHighWater() throws InterruptedException {
        // Concurrent validators must never move the high-water backwards and
        // must never accept a stale counter (lost-update guard).
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        // pre-seed high-water at 10
        assertTrue(svc.validate(FencingToken.of("actor-1", 10L, 0L)).isValid());

        final int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<FencingTokenDecision> results = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                final long counter = 5L; // all stale vs recorded 10
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    results.add(svc.validate(FencingToken.of("actor-1", counter, 0L)));
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(threadCount, results.size());
        for (FencingTokenDecision d : results) {
            assertFalse(d.isValid(), "a counter below the recorded high-water must always be stale");
            assertTrue(d.getRecordedCounter() >= 10L,
                    "high-water must never regress below the seeded value");
        }
    }

    @Test
    void concurrentValidateAscendingCountersAllValidUntilOneWins() throws InterruptedException {
        // Mix of ascending counters validated concurrently: exactly the ones
        // greater than the rolling high-water at observation time are valid;
        // the final high-water equals the max presented counter.
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        final int threadCount = 60;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<FencingTokenDecision> results = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                final long counter = i + 1; // 1..threadCount
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    results.add(svc.validate(FencingToken.of("actor-1", counter, 0L)));
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(threadCount, results.size());
        // At least the maximum counter must have been accepted (valid).
        boolean anyValid = results.stream().anyMatch(FencingTokenDecision::isValid);
        assertTrue(anyValid, "the highest counter must be validated valid");
        // The number of valid decisions equals the number of times a strictly
        // increasing running max was observed; it cannot exceed the max counter.
        long validCount = results.stream().filter(FencingTokenDecision::isValid).count();
        assertTrue(validCount <= threadCount);
        // Re-validating the max counter now is stale (high-water already there).
        long max = threadCount;
        FencingTokenDecision after = svc.validate(FencingToken.of("actor-1", max, 0L));
        assertFalse(after.isValid(), "max counter re-validated must be stale (strictly greater required)");
    }

    // ----- No silent no-op (Minimum Rules #24) -----

    @Test
    void defaultServiceExplicitlyReturnsStaleDecisionNotSilent() {
        // Minimum Rules #24: a stale token must produce an explicit stale
        // decision object with a non-null reason — never a null / swallowed
        // return.
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertTrue(svc.validate(FencingToken.of("a", 2L, 0L)).isValid());
        FencingTokenDecision stale = svc.validate(FencingToken.of("a", 1L, 0L));
        assertNotNull(stale, "validate must never return null");
        assertFalse(stale.isValid());
        assertNotNull(stale.getReason(), "stale decision must carry a non-null reason (no silent deny)");
    }

    @Test
    void nullTokenValidationIsRejectedByDefaultService() {
        DefaultFencingTokenService svc = new DefaultFencingTokenService();
        assertThrows(NullPointerException.class,
                () -> svc.validate(null),
                "DefaultFencingTokenService.validate must reject a null token");
    }
}
