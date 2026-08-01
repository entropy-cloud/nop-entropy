package io.nop.ai.agent.reliability;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Functional {@link IWaitCoordinator} implementation (design §13.1 Decisions
 * C/D/G/H). Manages per-session wait conditions in-memory with an injectable
 * clock for deterministic testing.
 *
 * <p><b>Condition re-evaluation (Decision H)</b>: {@link #checkWait} is the
 * heart of the anti-re-suspend mechanism. On each call it re-evaluates whether
 * the condition is satisfied:
 * <ul>
 *   <li>If satisfied (via {@link #deliverWake} or timeout expiry per
 *       {@link #isConditionSatisfied}): returns {@link WaitDecision#proceed()}
 *       and consumes the wait — the registration point skips suspend and
 *       continues execution.</li>
 *   <li>If not satisfied: returns {@link WaitDecision#suspend(WaitCondition)}
 *       — the registration point suspends.</li>
 *   <li>If no wait request exists: returns {@link WaitDecision#none()}.</li>
 * </ul>
 *
 * <p><b>Timeout scheduling (Decision C)</b>: when a {@link WaitCondition.Type#TIMEOUT}
 * condition is registered and a {@link IScheduledExecutor} is wired, a delayed
 * task calls {@link #deliverWake} at the deadline. This ensures the condition
 * is marked satisfied even without an external wake call.
 *
 * <p><b>Testable clock</b>: time is read from an injectable {@link LongSupplier}
 * (default {@code System::currentTimeMillis}), avoiding the
 * {@code System.currentTimeMillis()} anti-pattern (design §13.4 Decision D
 * ruling applied to WAIT_FOR). Tests inject a controllable time source.
 */
public class DefaultWaitCoordinator implements IWaitCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWaitCoordinator.class);

    private final ConcurrentMap<String, WaitState> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final IScheduledExecutor scheduler;

    /**
     * Per-session wait state: the condition, whether it has been satisfied
     * (via deliverWake), and an optional wake payload.
     */
    private static final class WaitState {
        volatile WaitCondition condition;
        volatile boolean satisfied;
        volatile Object wakePayload;
    }

    /**
     * Create a coordinator with the system clock and no timeout scheduling.
     */
    public DefaultWaitCoordinator() {
        this(System::currentTimeMillis, null);
    }

    /**
     * Create a coordinator with an injectable clock and optional scheduler.
     *
     * @param clock    the time source for timeout evaluation; never null
     * @param scheduler the scheduler for timeout-condition wake delivery;
     *                  may be null (timeout conditions will only be satisfied
     *                  by explicit deliverWake or by clock advancement on the
     *                  next checkWait call)
     */
    public DefaultWaitCoordinator(LongSupplier clock, IScheduledExecutor scheduler) {
        this.clock = clock != null ? clock : System::currentTimeMillis;
        this.scheduler = scheduler;
    }

    @Override
    public void requestWait(String sessionId, WaitCondition condition) {
        WaitState state = new WaitState();
        state.condition = condition;
        state.satisfied = false;
        sessions.put(sessionId, state);

        if (condition.getType() == WaitCondition.Type.TIMEOUT && scheduler != null) {
            long delay = Math.max(0, condition.getDeadlineMs() - clock.getAsLong());
            scheduler.schedule(() -> {
                deliverWake(sessionId, null);
                return null;
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public WaitDecision checkWait(String sessionId) {
        WaitState state = sessions.get(sessionId);
        if (state == null || state.condition == null) {
            return WaitDecision.none();
        }
        if (state.satisfied || isConditionSatisfied(state.condition)) {
            WaitCondition consumed = state.condition;
            state.condition = null;
            state.satisfied = false;
            LOG.debug("checkWait: condition satisfied for session={} type={} → PROCEED",
                    sessionId, consumed.getType());
            return WaitDecision.proceed();
        }
        return WaitDecision.suspend(state.condition);
    }

    @Override
    public void deliverWake(String sessionId, Object payload) {
        sessions.computeIfPresent(sessionId, (k, state) -> {
            state.satisfied = true;
            state.wakePayload = payload;
            LOG.debug("deliverWake: session={} condition marked satisfied", sessionId);
            return state;
        });
    }

    @Override
    public boolean isWaiting(String sessionId) {
        WaitState state = sessions.get(sessionId);
        return state != null && state.condition != null && !state.satisfied;
    }

    /**
     * Evaluate whether a condition is satisfied based on its type and the
     * current time. TIMEOUT conditions are satisfied when the deadline has
     * passed. EVENT and USER_INPUT conditions are satisfied only by explicit
     * {@link #deliverWake} (not by time).
     */
    private boolean isConditionSatisfied(WaitCondition condition) {
        if (condition.getType() == WaitCondition.Type.TIMEOUT) {
            return clock.getAsLong() >= condition.getDeadlineMs();
        }
        return false;
    }
}
