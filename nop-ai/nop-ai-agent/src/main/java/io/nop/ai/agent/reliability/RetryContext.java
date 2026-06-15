package io.nop.ai.agent.reliability;

/**
 * Retry context passed to {@link IRetryPolicy#shouldRetry(RetryContext)}
 * (design {@code nop-ai-agent-llm-layer.md} §7.2 / plan 207 / L3-2).
 *
 * <p>Carries the information a retry policy needs to decide RETRY / STOP /
 * FALLBACK for a failed LLM call:
 * <ul>
 *   <li>{@code attempt} — the zero-based index of the attempt that just
 *       failed (0 = the first call before any retry). The policy uses this
 *       to enforce max-attempts and to compute exponential backoff.</li>
 *   <li>{@code lastError} — the {@link Throwable} thrown by the failed
 *       {@code chatService.call(...)}. Carried so the policy (and the
 *       retry-loop caller) can rethrow the original failure on STOP.</li>
 *   <li>{@code errorClassification} — the {@link ErrorClassification} the
 *       caller derived from {@code lastError} (e.g. via
 *       {@link LlmErrorClassifier}). The policy keys its RETRY/STOP
 *       decision on this classification rather than on raw exception
 *       introspection.</li>
 *   <li>{@code hasStreamedContent} — whether the failed call had already
 *       streamed content to the caller (design §7.4 streaming guard).
 *       The current LLM call path is non-streaming, so this is always
 *       {@code false} in this plan (Non-Goal — actual streaming wiring is
 *       a successor). It is carried here so a future streaming path can
 *       pass {@code true} and a policy can downgrade FALLBACK to STOP
 *       without changing the interface.</li>
 * </ul>
 *
 * <p>This is an immutable data carrier. The retry loop constructs a fresh
 * instance per failed attempt.
 */
public final class RetryContext {

    private final int attempt;
    private final Throwable lastError;
    private final ErrorClassification errorClassification;
    private final boolean hasStreamedContent;

    public RetryContext(int attempt,
                        Throwable lastError,
                        ErrorClassification errorClassification,
                        boolean hasStreamedContent) {
        if (attempt < 0) {
            throw new IllegalArgumentException("RetryContext attempt must not be negative: " + attempt);
        }
        if (errorClassification == null) {
            throw new IllegalArgumentException("RetryContext errorClassification must not be null");
        }
        this.attempt = attempt;
        this.lastError = lastError;
        this.errorClassification = errorClassification;
        this.hasStreamedContent = hasStreamedContent;
    }

    public int getAttempt() {
        return attempt;
    }

    public Throwable getLastError() {
        return lastError;
    }

    public ErrorClassification getErrorClassification() {
        return errorClassification;
    }

    public boolean isHasStreamedContent() {
        return hasStreamedContent;
    }
}
