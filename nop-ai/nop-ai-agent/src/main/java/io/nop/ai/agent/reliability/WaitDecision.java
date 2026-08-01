package io.nop.ai.agent.reliability;

import java.util.Objects;

/**
 * The result of {@link IWaitCoordinator#checkWait} — the decision returned
 * at the ReAct loop's condition registration point (design §13.1 Decision B/H).
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@link #none()} — no wait request exists for this session; the loop
 *       proceeds normally (the zero-regression path for the default
 *       {@link NoOpWaitCoordinator}).</li>
 *   <li>{@link #suspend(WaitCondition)} — a wait request exists and the
 *       condition is <b>not yet satisfied</b>; the loop must produce a
 *       WAIT_FOR checkpoint, set status=waiting, break reactLoop, and
 *       complete the future (thread released, session resident).</li>
 *   <li>{@link #proceed()} — a wait request exists but the condition is
 *       <b>already satisfied</b> (via {@code deliverWake} or timeout expiry);
 *       the loop must <b>skip suspend</b> and continue execution. This is
 *       the anti-re-suspend mechanism (Decision H): on wake re-entry (replay
 *       from reactLoop top), the registration point re-evaluates, finds the
 *       condition satisfied, and returns PROCEED so the session advances
 *       instead of re-suspending.</li>
 * </ul>
 */
public final class WaitDecision {

    public enum Action {
        NONE,
        SUSPEND,
        PROCEED
    }

    private final Action action;
    private final WaitCondition condition;

    private WaitDecision(Action action, WaitCondition condition) {
        this.action = action;
        this.condition = condition;
    }

    public static WaitDecision none() {
        return new WaitDecision(Action.NONE, null);
    }

    public static WaitDecision suspend(WaitCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("WaitDecision.suspend: condition must not be null");
        }
        return new WaitDecision(Action.SUSPEND, condition);
    }

    public static WaitDecision proceed() {
        return new WaitDecision(Action.PROCEED, null);
    }

    public Action getAction() {
        return action;
    }

    /**
     * @return the wait condition for a {@link Action#SUSPEND} decision;
     *         {@code null} for {@link Action#NONE} and {@link Action#PROCEED}.
     */
    public WaitCondition getCondition() {
        return condition;
    }

    @Override
    public String toString() {
        return "WaitDecision{action=" + action
                + (condition != null ? ", condition=" + condition : "")
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WaitDecision that = (WaitDecision) o;
        return action == that.action && Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, condition);
    }
}
