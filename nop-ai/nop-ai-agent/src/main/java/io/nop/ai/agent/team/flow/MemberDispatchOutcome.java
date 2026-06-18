package io.nop.ai.agent.team.flow;

import java.util.Objects;

/**
 * Outcome of dispatching a single team task's {@link MemberDispatchPlan} via
 * {@link MemberFanOutDispatcher} (plan 245 / daemon dispatch parity).
 *
 * <p>This is the runtime-agnostic, nop-task-independent result of building the
 * fan-out future chain for one task, reducing the N member outcomes, and — on
 * reduction success — performing the single {@code completeTask} CAS. It is
 * the shared outcome type consumed by:
 * <ul>
 *   <li>the nop-task fan-out step variants
 *       ({@link BoundMemberFanOutStep} / {@link SpawnMemberFanOutStep} /
 *       {@link MixedMemberFanOutStep}), which adapt it into a
 *       {@code TaskStepReturn} + drive the {@link ExecutionRecorder};</li>
 *   <li>the daemon ({@code TeamTaskSchedulerDaemon}), which adapts it into
 *       the {@code SchedulerScanResult} counters + per-task store state.</li>
 * </ul>
 *
 * <h2>State model (honest, No Silent No-Op #24)</h2>
 * <ul>
 *   <li>{@link State#COMPLETED} — all N members satisfied the plan's
 *       {@link IReductionStrategy} AND the single {@code completeTask} CAS
 *       succeeded (task transitioned CLAIMED → COMPLETED). {@code cause} is
 *       null.</li>
 *   <li>{@link State#FAILED} — any member failed (engine exception /
 *       non-completed status / spawner NO_SPAWN / SPAWN_FAILED / null /
 *       threw), OR the reduction declined, OR the {@code completeTask} CAS
 *       lost. The task is LEFT IN CLAIMED (not abandoned — the recovery
 *       model is plan 240 reclaim, not terminal abandon). {@code cause} is
 *       non-null and describes the first failure.</li>
 * </ul>
 *
 * <p>Immutable. Use the {@link #completed()} / {@link #failed(Throwable)}
 * factories.
 *
 * <p>See plan 245 (daemon dispatch parity), design 裁定 3 / 4.
 */
public final class MemberDispatchOutcome {

    /**
     * Terminal state of a single task's fan-out dispatch.
     */
    public enum State {
        /** All N members completed + single completeTask CAS succeeded. */
        COMPLETED,
        /** Any failure: member failed / reduction declined / CAS lost. Task stays CLAIMED. */
        FAILED
    }

    private final State state;
    private final Throwable cause;

    private MemberDispatchOutcome(State state, Throwable cause) {
        this.state = Objects.requireNonNull(state, "state");
        this.cause = cause;
    }

    /**
     * @return a {@link State#COMPLETED} outcome (task transitioned CLAIMED → COMPLETED).
     */
    public static MemberDispatchOutcome completed() {
        return new MemberDispatchOutcome(State.COMPLETED, null);
    }

    /**
     * @param cause the first failure cause (non-null)
     * @return a {@link State#FAILED} outcome (task stays CLAIMED)
     */
    public static MemberDispatchOutcome failed(Throwable cause) {
        return new MemberDispatchOutcome(State.FAILED, Objects.requireNonNull(cause, "cause"));
    }

    /**
     * @return the terminal state.
     */
    public State getState() {
        return state;
    }

    /**
     * @return {@code true} when this outcome is a success ({@link State#COMPLETED}).
     */
    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    /**
     * @return the first failure cause when {@code state == FAILED};
     *         {@code null} when {@code state == COMPLETED}.
     */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "MemberDispatchOutcome{state=" + state
                + (cause != null ? ", cause=" + cause.getMessage() : "")
                + '}';
    }
}
