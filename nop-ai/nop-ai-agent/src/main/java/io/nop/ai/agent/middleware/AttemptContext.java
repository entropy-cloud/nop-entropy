package io.nop.ai.agent.middleware;

import io.nop.ai.api.chat.ErrorClassification;

/**
 * Strongly-typed per-attempt context carried by {@link io.nop.ai.agent.hook.HookContext}
 * when execution-level ({@link MiddlewareScope#EXECUTION}) middleware fires
 * (W3-1, decision D2).
 *
 * <p>An execution-level middleware reads this to learn <b>which attempt</b> is
 * running, <b>whether it is a retry</b>, and <b>how the previous attempt was
 * classified</b> — exactly the information that must be re-evaluated on every
 * retry ("resurrection retry" semantics). It is {@code null} on session-level
 * middleware invocations (where attempt is not a meaningful concept).
 *
 * <p>Design rationale: attempt is a transient value scoped to the retry-loop
 * iteration, not a property of the whole execution. It therefore lives on
 * {@code HookContext} (the per-invocation carrier) rather than on
 * {@code AgentExecutionContext} (which is per-request). Strong typing (vs. the
 * {@code HookContext.data} map) matches the nop convention and lets middleware
 * authors discover the contract via the type system.
 */
public final class AttemptContext {

    private final int attempt;
    private final boolean retry;
    private final ErrorClassification lastErrorClassification;

    /**
     * @param attempt                 the current attempt number (0-based)
     * @param lastErrorClassification the error classification produced by the
     *                                previous attempt ({@code null} on the first
     *                                attempt, or when the previous attempt had
     *                                no error classification)
     */
    public AttemptContext(int attempt, ErrorClassification lastErrorClassification) {
        this.attempt = attempt;
        this.retry = attempt > 0;
        this.lastErrorClassification = lastErrorClassification;
    }

    /**
     * @return the current attempt number (0-based; 0 = first attempt)
     */
    public int getAttempt() {
        return attempt;
    }

    /**
     * @return {@code true} if this is a retry attempt (attempt &gt; 0)
     */
    public boolean isRetry() {
        return retry;
    }

    /**
     * @return the error classification of the previous attempt, or {@code null}
     *         on the first attempt / when the previous attempt had no error
     *         classification. A middleware uses this to decide whether a retry
     *         is worthwhile (e.g. skip if the previous attempt was
     *         NON_TRANSIENT).
     */
    public ErrorClassification getLastErrorClassification() {
        return lastErrorClassification;
    }
}
