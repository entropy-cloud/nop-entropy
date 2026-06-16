package io.nop.ai.agent.reliability;

/**
 * Layer 3 extension point for circuit-breaking a model/provider that is
 * failing consecutively (design {@code nop-ai-agent-reliability.md} §3.3
 * platform layer / §5.1 three-state breaker / plan 210 / L3-1).
 *
 * <p>The ReAct loop's single-LLM-call point consults the breaker at two
 * distinct layers around the retry loop (plan 210 Phase 1 adjudication):
 * <ul>
 *   <li><b>Outer check</b> — before entering the retry loop, the breaker is
 *       asked whether the <i>primary</i> model (the one
 *       {@code IModelRouter.route()} returned for this iteration) may be
 *       called. A {@code false} return means the circuit is OPEN and the
 *       ReAct loop fails fast with a {@code NopAiAgentException} (no silent
 *       skip — Minimum Rules #24). Circuit-breaking and retry are orthogonal:
 *       retry handles transient failures within a single call cycle; the
 *       breaker handles consecutive-failure patterns that span call cycles.</li>
 *   <li><b>Result recording</b> — every attempt outcome is reported back so
 *       the breaker can update its consecutive-failure counter:
 *       {@link #recordFailure(String)} is called inside the retry loop at the
 *       catch-block entry (before RETRY/STOP/FALLBACK branching) and again on
 *       the non-exception failure path ({@code !response.isSuccess()});
 *       {@link #recordSuccess(String)} is called once after the retry loop
 *       completes with a successful response (which may be the fallback
 *       model).</li>
 * </ul>
 *
 * <p>The breaker tracks state per <i>model identity</i> — the
 * {@code provider:model} composite key built by
 * {@code ReActAgentExecutor.buildModelKey(ChatOptions)}. A breaker tripping
 * on model A does not prevent calls to a healthy model B (the fallback-chain
 * scenario), so the key parameter is mandatory on every method.
 *
 * <p><b>Pass-through semantics of the shipped default</b>: {@link AlwaysClosed}
 * unconditionally reports {@link CircuitState#CLOSED}, returns {@code true}
 * from {@link #allowCall(String)}, and treats {@link #recordSuccess} /
 * {@link #recordFailure} as explicit no-ops. This preserves the engine's
 * pre-plan-210 zero-circuit-breaking behaviour verbatim, so wiring the
 * breaker is a zero-regression change. A functional breaker
 * ({@code ThresholdBreaker}) is registered explicitly via
 * {@code DefaultAgentEngine.setCircuitBreaker}.
 *
 * <p><b>Contract for implementations</b>: all methods must accept a non-null
 * {@code modelKey} (callers always pass the composite key built by
 * {@code buildModelKey}). {@link #allowCall} must never return {@code null}.
 * Implementations must be safe to call from the ReAct loop's execution
 * thread (stateless like {@link AlwaysClosed}, or properly synchronised like
 * {@code ThresholdBreaker}).
 */
public interface ICircuitBreaker {

    /**
     * Decide whether a call to the model identified by {@code modelKey} may
     * proceed. Called by the ReAct loop before entering the retry loop
     * (outer layer, primary model only).
     *
     * <p>Return values:
     * <ul>
     *   <li>{@code true} — the call may proceed (breaker is CLOSED or this
     *       caller won the HALF_OPEN probe slot).</li>
     *   <li>{@code false} — the breaker is OPEN (or the HALF_OPEN probe slot
     *       was taken by another concurrent caller); the ReAct loop must
     *       fail fast rather than issue the call.</li>
     * </ul>
     *
     * @param modelKey non-null composite model identity ({@code provider:model})
     * @return whether the call is allowed; never {@code null}
     */
    boolean allowCall(String modelKey);

    /**
     * Query the breaker's current state for the given model. Used for
     * diagnostics and by the ReAct loop to enrich the failure exception when
     * a call is rejected.
     *
     * @param modelKey non-null composite model identity ({@code provider:model})
     * @return the breaker state; never {@code null}. A breaker that has never
     *         recorded any outcome for {@code modelKey} reports CLOSED.
     */
    CircuitState getState(String modelKey);

    /**
     * Record that a call to {@code modelKey} succeeded. Called by the ReAct
     * loop once after the retry loop completes with a successful response
     * (which may be the fallback model if a FALLBACK switch happened).
     *
     * <p>Semantics: resets the consecutive-failure counter to zero; if the
     * breaker is HALF_OPEN, transitions to CLOSED.
     *
     * @param modelKey non-null composite model identity ({@code provider:model})
     */
    void recordSuccess(String modelKey);

    /**
     * Record that a call to {@code modelKey} failed. Called by the ReAct
     * loop inside the retry loop's catch-block entry (before
     * RETRY/STOP/FALLBACK branching, and before any FALLBACK switches the
     * tracked model key) and again on the non-exception failure path
     * ({@code !response.isSuccess()}).
     *
     * <p>Semantics: increments the consecutive-failure counter; when the
     * counter reaches the configured threshold, transitions CLOSED → OPEN;
     * if the breaker is HALF_OPEN, transitions back to OPEN (probe failed,
     * cooldown restarted).
     *
     * @param modelKey non-null composite model identity ({@code provider:model})
     */
    void recordFailure(String modelKey);
}
