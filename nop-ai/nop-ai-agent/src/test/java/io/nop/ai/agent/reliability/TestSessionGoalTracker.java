package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 211 (L3-3) Phase 2 focused test for {@link SessionGoalTracker}
 * (Minimum Rules #25). Covers every detection path: PROGRESSING on new
 * signatures, STUCK when a signature repeats past the threshold, window
 * sliding evicting old signatures so STUCK clears, no-tool-call iterations
 * not affecting the assessment, multi-session isolation, anonymous-session
 * pass-through, and thread safety.
 */
public class TestSessionGoalTracker {

    private static IterationSnapshot iter(int n, String... signatures) {
        return new IterationSnapshot(n, List.of(signatures));
    }

    @Test
    void constructorRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionGoalTracker(0, 3),
                "windowSize must be >= 1");
        assertThrows(IllegalArgumentException.class,
                () -> new SessionGoalTracker(5, 0),
                "stuckThreshold must be >= 1");
    }

    @Test
    void defaultsMatchPlan() {
        SessionGoalTracker tracker = new SessionGoalTracker();
        assertEquals(SessionGoalTracker.DEFAULT_WINDOW_SIZE, tracker.getWindowSize());
        assertEquals(SessionGoalTracker.DEFAULT_STUCK_THRESHOLD, tracker.getStuckThreshold());
    }

    @Test
    void progressingWhenNewSignaturesAppear() {
        // Each iteration introduces a fresh signature → no repetition →
        // PROGRESSING throughout.
        SessionGoalTracker tracker = new SessionGoalTracker(5, 3);
        tracker.recordIteration("s1", iter(0, "read:{\"a\":1}"));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"));
        tracker.recordIteration("s1", iter(1, "write:{\"b\":2}"));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"));
        tracker.recordIteration("s1", iter(2, "list:{}"));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "Distinct signatures must never trip STUCK");
    }

    @Test
    void stuckWhenSameSignatureRepeatsPastThreshold() {
        SessionGoalTracker tracker = new SessionGoalTracker(5, 3);
        String sig = "read:{\"path\":\"/a\"}";
        tracker.recordIteration("s1", iter(0, sig));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "One occurrence is below the threshold");
        tracker.recordIteration("s1", iter(1, sig));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "Two occurrences are still below the threshold");
        tracker.recordIteration("s1", iter(2, sig));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("s1"),
                "Three occurrences meet the threshold → STUCK");
    }

    @Test
    void argsKeyOrderIsNormalisedSoSameArgsProduceSameSignature() {
        // The engine builds signatures with sorted args keys; the tracker
        // only compares the strings it receives. Verify the tracker treats
        // two identical signature strings (regardless of how they were built)
        // as the same repeating signature.
        SessionGoalTracker tracker = new SessionGoalTracker(5, 2);
        String sig = "read:{\"path\":\"/x\",\"mode\":\"r\"}";
        tracker.recordIteration("s1", iter(0, sig));
        tracker.recordIteration("s1", iter(1, sig));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("s1"),
                "Identical signature strings must be counted as repeats");
    }

    @Test
    void windowSlidingClearsStuckWhenOldSignaturesEvicted() {
        // windowSize=2, threshold=2: two identical signatures trip STUCK,
        // but a subsequent different signature evicts the oldest so the
        // repetition is no longer present → back to PROGRESSING.
        SessionGoalTracker tracker = new SessionGoalTracker(2, 2);
        String sig = "read:{\"a\":1}";
        tracker.recordIteration("s1", iter(0, sig));
        tracker.recordIteration("s1", iter(1, sig));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("s1"),
                "Two repeats within the window → STUCK");
        tracker.recordIteration("s1", iter(2, "write:{}"));
        // window is now [read, write] (oldest read evicted) → no repeat.
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "After sliding evicts the repeated signature, STUCK must clear");
    }

    @Test
    void noToolCallIterationDoesNotAffectAssessment() {
        SessionGoalTracker tracker = new SessionGoalTracker(5, 3);
        String sig = "read:{}";
        tracker.recordIteration("s1", iter(0, sig));
        // A no-tool-call iteration (empty signature list) leaves the window
        // unchanged — it is neither progress nor stuck evidence.
        tracker.recordIteration("s1", iter(1));
        tracker.recordIteration("s1", iter(2));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "Empty iterations must not count toward the repeat threshold");
        // Confirm the real signature still needs the same number of repeats.
        tracker.recordIteration("s1", iter(3, sig));
        tracker.recordIteration("s1", iter(4, sig));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("s1"),
                "The two empty iterations did not advance the count; sig now "
                        + "appears 3 times → STUCK");
    }

    @Test
    void multipleToolCallsPerIterationEachContributeSignatures() {
        SessionGoalTracker tracker = new SessionGoalTracker(5, 3);
        String sig = "read:{\"path\":\"/a\"}";
        // One iteration requesting the same tool twice contributes two entries.
        tracker.recordIteration("s1", iter(0, sig, sig));
        tracker.recordIteration("s1", iter(1, sig));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("s1"),
                "Three signature entries (two from one iteration) meet the threshold");
    }

    @Test
    void sessionsAreIsolated() {
        SessionGoalTracker tracker = new SessionGoalTracker(5, 2);
        String sig = "read:{}";
        // Session A repeats, session B does not.
        tracker.recordIteration("a", iter(0, sig));
        tracker.recordIteration("a", iter(1, sig));
        tracker.recordIteration("b", iter(0, "write:{}"));
        assertEquals(GoalAssessment.STUCK, tracker.assessGoal("a"),
                "Session A is stuck");
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("b"),
                "Session B is isolated and progressing");
    }

    @Test
    void untrackedAndAnonymousSessionsReportProgressing() {
        SessionGoalTracker tracker = new SessionGoalTracker(5, 2);
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("never-recorded"),
                "A never-recorded session must report PROGRESSING");
        tracker.recordIteration(null, iter(0, "read:{}"));
        tracker.recordIteration(null, iter(1, "read:{}"));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal(null),
                "Anonymous (null) sessions must not be tracked → PROGRESSING");
    }

    @Test
    void thresholdLargerThanWindowNeverTrips() {
        // stuckThreshold (3) > windowSize (2): no signature can ever repeat
        // enough times, so the assessment is always PROGRESSING.
        SessionGoalTracker tracker = new SessionGoalTracker(2, 3);
        tracker.recordIteration("s1", iter(0, "read:{}"));
        tracker.recordIteration("s1", iter(1, "read:{}"));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("s1"),
                "When threshold > windowSize, STUCK can never fire");
    }

    @Test
    void concurrentSessionsAreThreadSafe() throws Exception {
        // Many threads concurrently record/assess distinct sessions through
        // the same tracker instance. Thread safety requires per-session
        // state isolation without cross-session interference.
        SessionGoalTracker tracker = new SessionGoalTracker(10, 5);
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int t = 0; t < threads; t++) {
            final String session = "session-" + t;
            final String sig = "tool-" + t + ":{}";
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 5; i++) {
                        tracker.recordIteration(session, iter(i, sig));
                    }
                    GoalAssessment assessment = tracker.assessGoal(session);
                    assertEquals(GoalAssessment.STUCK, assessment,
                            "Each session repeated its own signature 5 times → STUCK: " + session);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("Concurrent session tracking failed", failure.get());
        }
    }
}
