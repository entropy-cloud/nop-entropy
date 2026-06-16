package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 211 (L3-3) Phase 1 focused test for {@link NoOpGoalTracker} (Minimum
 * Rules #25). Verifies the shipped default unconditionally reports PROGRESSING
 * for every session and treats {@code recordIteration} as an explicit no-op
 * that does not change the assessment — preserving the engine's pre-plan-211
 * behaviour (no STUCK abort ever fires).
 */
public class TestNoOpGoalTracker {

    @Test
    void noOpReturnsSingletonViaFactory() {
        IGoalTracker a = NoOpGoalTracker.noOp();
        IGoalTracker b = NoOpGoalTracker.noOp();
        assertSame(a, b, "noOp() must return the same singleton instance");
    }

    @Test
    void assessGoalAlwaysProgressingForAnySession() {
        IGoalTracker tracker = NoOpGoalTracker.noOp();
        // A session that was never recorded must still report PROGRESSING (no
        // state is ever tracked by the pass-through default).
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("session-a"),
                "NoOpGoalTracker must report PROGRESSING for an untracked session");
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("session-b"),
                "NoOpGoalTracker must report PROGRESSING for any session");
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal(null),
                "NoOpGoalTracker must report PROGRESSING for anonymous (null) sessions");
    }

    @Test
    void recordIterationIsExplicitNoOpAndNeverCausesStuck() {
        IGoalTracker tracker = NoOpGoalTracker.noOp();
        // Recording many identical-signature iterations must be an explicit
        // no-op: the assessment stays PROGRESSING no matter how many repeats
        // are recorded. This proves recordIteration is a real pass-through
        // (not an empty placeholder for required behaviour) but correctly
        // discards the data by design.
        List<String> repeatedSignature = List.of("read_file:{\"path\":\"/a\"}");
        for (int i = 0; i < 100; i++) {
            tracker.recordIteration("session-a", new IterationSnapshot(i, repeatedSignature));
        }
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("session-a"),
                "NoOpGoalTracker must never report STUCK after any number of recorded iterations");

        tracker.recordIteration("session-a", new IterationSnapshot(0, List.of()));
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("session-a"),
                "NoOpGoalTracker must report PROGRESSING after recording an empty-signature iteration");
    }

    @Test
    void recordingDoesNotLeakAcrossSessions() {
        IGoalTracker tracker = NoOpGoalTracker.noOp();
        // Recording many iterations on session A must not affect session B at
        // all — the pass-through default maintains no per-session state.
        for (int i = 0; i < 50; i++) {
            tracker.recordIteration("session-a", new IterationSnapshot(i, List.of("tool:{}")));
        }
        assertEquals(GoalAssessment.PROGRESSING, tracker.assessGoal("session-b"),
                "NoOpGoalTracker must isolate sessions (no cross-session state leak)");
    }
}
