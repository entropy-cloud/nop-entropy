package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link ICircuitBreaker} used as the shipped default when no
 * functional breaker is registered (design
 * {@code nop-ai-agent-reliability.md} §3.3 / §5.1 / plan 210 Phase 1 / L3-1).
 *
 * <p>{@link #allowCall} unconditionally returns {@code true} and
 * {@link #getState} unconditionally reports {@link CircuitState#CLOSED}, so
 * every call is allowed through and the breaker never trips. This preserves
 * the engine's pre-plan-210 zero-circuit-breaking behaviour verbatim: the
 * ReAct loop's outer check passes, the result-recording calls are explicit
 * no-ops, and no exception is thrown to reject a call.
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour. Some deployments deliberately want zero
 * circuit-breaking (deterministic replay, latency-sensitive paths, or
 * environments where every model failure must surface immediately to the
 * operator), and the shipped default makes that the safe baseline. A
 * functional breaker ({@code ThresholdBreaker}) is registered explicitly via
 * {@code DefaultAgentEngine.setCircuitBreaker}. This mirrors the
 * {@code NoRetryPolicy} / {@code NoOpCheckpoint} /
 * {@code NoOpBudgetProvider} sibling pass-through pattern.
 *
 * <p>{@link #recordSuccess} and {@link #recordFailure} are <b>explicit
 * no-ops</b>: they are not empty method bodies used as placeholders for
 * required behaviour (Minimum Rules #24). The correct shipped behaviour of a
 * pass-through breaker is to discard the recorded outcome, because the
 * breaker maintains no per-model state by design.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class AlwaysClosed implements ICircuitBreaker {

    private static final AlwaysClosed INSTANCE = new AlwaysClosed();

    private AlwaysClosed() {
    }

    /**
     * @return the singleton pass-through {@link ICircuitBreaker} instance
     */
    public static ICircuitBreaker alwaysClosed() {
        return INSTANCE;
    }

    @Override
    public boolean allowCall(String modelKey) {
        return true;
    }

    @Override
    public CircuitState getState(String modelKey) {
        return CircuitState.CLOSED;
    }

    @Override
    public void recordSuccess(String modelKey) {
        // Explicit no-op: the pass-through default maintains no per-model
        // state, so a recorded success is discarded by design. This is not
        // an empty placeholder for required behaviour — the correct
        // behaviour of a pass-through breaker is to ignore outcome reports.
        // A functional breaker (ThresholdBreaker) resets its failure counter.
    }

    @Override
    public void recordFailure(String modelKey) {
        // Explicit no-op: see recordSuccess. The correct behaviour of a
        // pass-through breaker is to ignore outcome reports; a functional
        // breaker increments its failure counter and may trip to OPEN.
    }
}
