package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link IRetryPolicy} used as the shipped default when no
 * functional policy is registered (design
 * {@code nop-ai-agent-llm-layer.md} §7.3 NoRetry mode / plan 207 Phase 1).
 *
 * <p>{@link #shouldRetry} unconditionally returns {@link RetryOutcome#stop()}
 * — never retry, fail fast. This preserves the engine's pre-plan-207
 * zero-retry behaviour verbatim: the retry loop executes the LLM call exactly
 * once and propagates any exception as-is.
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour. Some call sites (deterministic, latency-sensitive
 * idempotent reads) deliberately want zero retry, and the shipped default
 * makes that the safe baseline. A functional policy is registered explicitly
 * via {@code DefaultAgentEngine.setRetryPolicy}. This mirrors the
 * {@code NoOpBudgetProvider} / {@code NoOpCheckpoint} / {@code NoOpContextCompactor}
 * sibling pass-through pattern.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoRetryPolicy implements IRetryPolicy {

    private static final NoRetryPolicy INSTANCE = new NoRetryPolicy();

    private NoRetryPolicy() {
    }

    /**
     * @return the singleton pass-through {@link IRetryPolicy} instance
     */
    public static IRetryPolicy noRetry() {
        return INSTANCE;
    }

    @Override
    public RetryOutcome shouldRetry(RetryContext context) {
        return RetryOutcome.stop();
    }
}
