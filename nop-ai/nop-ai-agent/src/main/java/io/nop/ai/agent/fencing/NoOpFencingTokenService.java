package io.nop.ai.agent.fencing;

/**
 * Shipped no-op default for {@link IFencingTokenService} (plan 235, Design
 * Decision §2).
 *
 * <p>{@link #validate} <strong>always returns {@code valid}</strong> — an
 * explicit valid decision. This is the backward-compatible shipped default so
 * that integrators see zero behaviour regression unless they explicitly wire a
 * {@link DefaultFencingTokenService}: every operation that previously went
 * through with no fencing check now still goes through, with no extra overhead.
 * The valid return value explicitly encodes "fencing not enabled → no
 * restriction added" — it is not a swallowed null / silent skip (Minimum Rules
 * #24).
 *
 * <p>{@link #issue} returns a non-null placeholder {@link FencingToken}
 * (carrying the caller's actorId, counter {@code 0}, and the current epoch ms).
 * The counter is {@code 0} to signal the intentional disabled-mode (the
 * placeholder is never meant to participate in a real strictly-increasing
 * sequence — {@code validate} accepts it anyway because fencing is disabled);
 * a real counter is only produced by {@link DefaultFencingTokenService}. This
 * keeps {@code issue} contract-faithful (returns non-null) without inventing a
 * fake monotonic sequence in disabled mode.
 *
 * <p><b>Why valid and not stale?</b> The NoOp semantics is "fencing enforcement
 * not enabled = do not add any restriction". The {@code valid} return value
 * explicitly encodes "permitted" — consistent with {@code NoOpResourceGuard}
 * (always allow) / {@code NoOpTeamAclChecker} (always allow) /
 * {@code NoOpSessionTakeoverLock} (always acquire) shipped-default patterns.
 *
 * <p>See plan 235 (L4-fencing-token), Design Decision §2.
 */
public final class NoOpFencingTokenService implements IFencingTokenService {

    private static final NoOpFencingTokenService INSTANCE = new NoOpFencingTokenService();

    private NoOpFencingTokenService() {
    }

    /**
     * @return the singleton NoOp service instance
     */
    public static NoOpFencingTokenService noOp() {
        return INSTANCE;
    }

    @Override
    public FencingToken issue(String actorId) {
        // Placeholder token: counter 0 signals intentional disabled-mode (no
        // fake monotonic sequence is invented). Non-null, carrying the caller's
        // actorId verbatim (Minimum Rules #24 — not a silent null return).
        return FencingToken.of(actorId, 0L, System.currentTimeMillis());
    }

    @Override
    public FencingTokenDecision validate(FencingToken token) {
        // Explicit valid: "fencing not enabled → no restriction added". The
        // recordedCounter is 0 because the NoOp maintains no high-water state;
        // the token's presented counter is accepted regardless. Not a silent
        // skip — the decision object is returned and the caller proceeds
        // normally (Minimum Rules #24).
        return FencingTokenDecision.valid(token.getActorId(), token.getMonotonicCounter(), 0L);
    }
}
