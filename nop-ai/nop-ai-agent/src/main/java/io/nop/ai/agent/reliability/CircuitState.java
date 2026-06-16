package io.nop.ai.agent.reliability;

/**
 * Three-state circuit-breaker state machine (design
 * {@code nop-ai-agent-reliability.md} §5.1 / plan 210 / L3-1).
 *
 * <ul>
 *   <li>{@link #CLOSED} — the model is healthy; all calls are allowed.
 *       Failures increment a consecutive-failure counter; reaching the
 *       configured threshold transitions to {@link #OPEN}.</li>
 *   <li>{@link #OPEN} — the model is considered broken; calls are rejected
 *       (fail fast). After the configured cooldown elapses the breaker lazily
 *       transitions to {@link #HALF_OPEN} on the next
 *       {@link ICircuitBreaker#allowCall(String)} probe.</li>
 *   <li>{@link #HALF_OPEN} — a single probe call is allowed through to test
 *       whether the model has recovered. A successful probe resets the breaker
 *       to {@link #CLOSED} (failure counter cleared); a failed probe returns
 *       the breaker to {@link #OPEN} (cooldown restarted).</li>
 * </ul>
 *
 * <p>The public enum stays at three states on purpose (design §5.1). The
 * probe-in-flight exclusivity of {@link #HALF_OPEN} (only the first concurrent
 * caller is allowed through as the probe; subsequent callers are rejected as
 * if still {@link #OPEN}) is enforced by an internal synchronisation
 * primitive inside {@code ThresholdBreaker}, not by a fourth public state.
 */
public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
