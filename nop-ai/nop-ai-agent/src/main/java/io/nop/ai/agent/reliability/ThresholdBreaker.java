package io.nop.ai.agent.reliability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Functional {@link ICircuitBreaker} implementing the three-state
 * consecutive-failure circuit-breaker state machine (design
 * {@code nop-ai-agent-reliability.md} §5.1 / plan 210 / L3-1).
 *
 * <p>Per model identity (the {@code provider:model} composite key built by
 * {@code ReActAgentExecutor.buildModelKey}) the breaker maintains an
 * independent state machine:
 * <ul>
 *   <li><b>CLOSED</b> (initial) — all calls allowed. Each recorded failure
 *       increments a consecutive-failure counter; when the counter reaches
 *       {@code failureThreshold} the breaker transitions to <b>OPEN</b>.</li>
 *   <li><b>OPEN</b> — all calls rejected ({@code allowCall} returns false).
 *       The transition is lazy: no background timer is started. The next
 *       {@link #allowCall(String)} after {@code cooldownMs} has elapsed since
 *       the OPEN transition transitions the breaker to <b>HALF_OPEN</b> and
 *       admits that caller as the single probe.</li>
 *   <li><b>HALF_OPEN</b> — exactly one probe call is admitted (the first
 *       caller that wins the probe slot); concurrent callers are rejected as
 *       if still OPEN. A successful probe ({@link #recordSuccess})
 *       transitions back to CLOSED and clears the failure counter; a failed
 *       probe ({@link #recordFailure}) transitions back to OPEN and restarts
 *       the cooldown clock.</li>
 * </ul>
 *
 * <p><b>Thread safety</b>: state is tracked per model-key in a
 * {@link ConcurrentHashMap}. Each entry's state-machine transitions are
 * guarded by synchronizing on the entry object, so concurrent callers to the
 * <i>same</i> model-key see consistent state, while concurrent callers to
 * <i>different</i> model-keys proceed in parallel. {@link #getState} reads
 * the {@code volatile} state field without locking for diagnostics (a
 * slightly-stale snapshot of just the state is acceptable; the
 * decision-making methods {@link #allowCall} / {@link #recordSuccess} /
 * {@link #recordFailure} always take the lock and see a consistent
 * snapshot).
 *
 * <p><b>Invariants</b>:
 * <ul>
 *   <li>A breaker that has never recorded any outcome for a model-key
 *       reports CLOSED and allows calls (the healthy default).</li>
 *   <li>The failure counter only counts <i>consecutive</i> failures: a
 *       success at any point resets it to zero. Therefore a retry cycle
 *       that ultimately succeeds (transient failures followed by success)
 *       does not accumulate debt — the breaker only trips on failures with
 *       no intervening success.</li>
 *   <li>The {@link CircuitState} public enum stays at three states. The
 *       probe-in-flight exclusivity of HALF_OPEN is enforced by an internal
 *       boolean flag per entry, not by a fourth public state.</li>
 * </ul>
 *
 * <p>State is in-memory only (per breaker instance). Persistence /
 * cross-process sharing is a Non-Goal successor (design §11 deferred).
 */
public final class ThresholdBreaker implements ICircuitBreaker {

    /** Default consecutive-failure threshold before the breaker trips. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    /** Default cooldown in milliseconds before a tripped breaker probes. */
    public static final long DEFAULT_COOLDOWN_MS = 60_000L;

    private final int failureThreshold;
    private final long cooldownMs;
    private final ConcurrentMap<String, BreakerEntry> entries = new ConcurrentHashMap<>();

    /**
     * Construct a breaker with the default threshold (3) and cooldown (60s).
     */
    public ThresholdBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * @param failureThreshold consecutive failures required to trip the
     *                         breaker (must be &gt;= 1)
     * @param cooldownMs      milliseconds the breaker stays OPEN before
     *                        admitting a HALF_OPEN probe (must be &gt;= 0;
     *                        0 = probe on the very next call after tripping)
     */
    public ThresholdBreaker(int failureThreshold, long cooldownMs) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "ThresholdBreaker failureThreshold must be >= 1: " + failureThreshold);
        }
        if (cooldownMs < 0) {
            throw new IllegalArgumentException(
                    "ThresholdBreaker cooldownMs must be >= 0: " + cooldownMs);
        }
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    @Override
    public boolean allowCall(String modelKey) {
        if (modelKey == null) {
            throw new IllegalArgumentException("modelKey must not be null");
        }
        BreakerEntry entry = entries.computeIfAbsent(modelKey, k -> new BreakerEntry());
        synchronized (entry) {
            switch (entry.state) {
                case CLOSED:
                    return true;
                case OPEN:
                    // Lazy cooldown check: no background timer. If the cooldown
                    // has elapsed, transition to HALF_OPEN and admit this caller
                    // as the single probe.
                    if (System.currentTimeMillis() - entry.openedAt >= cooldownMs) {
                        entry.state = CircuitState.HALF_OPEN;
                        entry.probeInFlight = true;
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    // A probe is in flight: only the probe caller is admitted;
                    // concurrent callers are rejected as if still OPEN.
                    if (!entry.probeInFlight) {
                        entry.probeInFlight = true;
                        return true;
                    }
                    return false;
                default:
                    throw new IllegalStateException("Unknown circuit state: " + entry.state);
            }
        }
    }

    @Override
    public CircuitState getState(String modelKey) {
        if (modelKey == null) {
            throw new IllegalArgumentException("modelKey must not be null");
        }
        BreakerEntry entry = entries.get(modelKey);
        // An untracked model-key has never recorded any outcome → CLOSED.
        return entry != null ? entry.state : CircuitState.CLOSED;
    }

    @Override
    public void recordSuccess(String modelKey) {
        if (modelKey == null) {
            throw new IllegalArgumentException("modelKey must not be null");
        }
        BreakerEntry entry = entries.get(modelKey);
        if (entry == null) {
            // Success on an untracked key: nothing to reset (already CLOSED).
            return;
        }
        synchronized (entry) {
            switch (entry.state) {
                case CLOSED:
                    entry.consecutiveFailures = 0;
                    return;
                case HALF_OPEN:
                    // Probe succeeded → reset to CLOSED, clear the counter,
                    // release the probe slot.
                    entry.state = CircuitState.CLOSED;
                    entry.consecutiveFailures = 0;
                    entry.probeInFlight = false;
                    return;
                case OPEN:
                    // Defensive: a success was reported while OPEN (the call
                    // should have been rejected). Reset the counter; leave the
                    // state as OPEN — the operator-configured cooldown still
                    // gates the next probe.
                    entry.consecutiveFailures = 0;
                    return;
                default:
                    throw new IllegalStateException("Unknown circuit state: " + entry.state);
            }
        }
    }

    @Override
    public void recordFailure(String modelKey) {
        if (modelKey == null) {
            throw new IllegalArgumentException("modelKey must not be null");
        }
        BreakerEntry entry = entries.computeIfAbsent(modelKey, k -> new BreakerEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            switch (entry.state) {
                case CLOSED:
                    entry.consecutiveFailures++;
                    if (entry.consecutiveFailures >= failureThreshold) {
                        entry.state = CircuitState.OPEN;
                        entry.openedAt = now;
                    }
                    return;
                case HALF_OPEN:
                    // Probe failed → back to OPEN, restart the cooldown clock,
                    // release the probe slot.
                    entry.state = CircuitState.OPEN;
                    entry.openedAt = now;
                    entry.probeInFlight = false;
                    return;
                case OPEN:
                    // Defensive: a failure was reported while OPEN (the call
                    // should have been rejected). Leave the state as OPEN;
                    // do not extend the cooldown (the failure is spurious).
                    return;
                default:
                    throw new IllegalStateException("Unknown circuit state: " + entry.state);
            }
        }
    }

    /**
     * Per-model-key mutable state holder. All fields are mutated under the
     * entry's monitor (synchronized on the entry instance). The
     * {@code volatile state} field is also read lock-free by
     * {@link #getState} for diagnostics.
     */
    private static final class BreakerEntry {
        volatile CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures = 0;
        long openedAt = 0L;
        boolean probeInFlight = false;
    }
}
