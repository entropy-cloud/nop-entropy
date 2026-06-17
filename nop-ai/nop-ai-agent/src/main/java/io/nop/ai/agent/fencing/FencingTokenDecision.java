package io.nop.ai.agent.fencing;

/**
 * Immutable result of an {@link IFencingTokenService#validate} adjudication
 * (plan 235, vision §5.1 rules 2-3). Carries the valid/stale verdict plus the
 * actorId / presentedCounter / recordedCounter context that produced it, so an
 * enforcement point (a successor consumer such as scope_claim coordination,
 * Compaction, or ResourceGuard fencing integration) can include a precise
 * English reason when it fail-fast throws
 * {@link io.nop.ai.agent.engine.NopAiAgentException} or broadcasts a
 * {@code conflict_alert} on a stale result.
 *
 * <h2>Semantics (vision §5.1 rules 2-3)</h2>
 * <ul>
 *   <li>{@code valid == true} ⇒ {@code presentedCounter &gt; recordedCounter}
 *       at decision time, and the service has atomically updated its recorded
 *       high-water to {@code presentedCounter}. {@code reason} is {@code null}
 *       (no reason needed for a valid token). {@code recordedCounter} carries
 *       the pre-update high-water value the token was compared against, so a
 *       reader can reconstruct the comparison that was made
 *       ({@code presentedCounter &gt; recordedCounter}).</li>
 *   <li>{@code valid == false} (stale) ⇒ {@code presentedCounter &lt;= recordedCounter},
 *       the recorded high-water was <em>not</em> updated, and {@code reason}
 *       is non-null (Minimum Rules #24 — a stale verdict always explains why,
 *       no silent deny).</li>
 * </ul>
 *
 * <p>The primitive deliberately returns a decision object instead of throwing:
 * the enforcement-point reaction (throw / broadcast / reject) is a consumer
 * responsibility, and different consumers have different reaction strategies
 * (Design Decision §6 — consistent with {@code IResourceGuard.checkConcurrent}
 * → {@code QuotaDecision} and {@code ITeamAclChecker.checkAccess} →
 * {@code TeamAclDecision}).
 *
 * <p>Use the {@link #valid} / {@link #stale} factories; the constructor is
 * private so the valid⇒null-reason / stale⇒non-null-reason invariant is
 * enforced.
 *
 * <p>See plan 235 (L4-fencing-token), Design Decision §6 / §7.
 */
public final class FencingTokenDecision {

    private final boolean valid;
    private final String actorId;
    private final long presentedCounter;
    private final long recordedCounter;
    private final String reason;

    private FencingTokenDecision(boolean valid, String actorId, long presentedCounter,
                                 long recordedCounter, String reason) {
        this.valid = valid;
        this.actorId = actorId;
        this.presentedCounter = presentedCounter;
        this.recordedCounter = recordedCounter;
        this.reason = reason;
    }

    /**
     * Build a valid decision. {@code reason} is forced to {@code null}.
     * {@code recordedCounter} should be the pre-update high-water value the
     * token was compared against (i.e. the value for which
     * {@code presentedCounter &gt; recordedCounter} held).
     *
     * @param actorId          the actor the validated token belongs to
     * @param presentedCounter the counter the token presented
     * @param recordedCounter  the pre-update recorded high-water against which
     *                         the token was compared
     * @return an immutable valid decision (reason is null)
     */
    public static FencingTokenDecision valid(String actorId, long presentedCounter, long recordedCounter) {
        return new FencingTokenDecision(true, actorId, presentedCounter, recordedCounter, null);
    }

    /**
     * Build a stale decision. A non-null English reason is auto-generated
     * citing the presented / recorded counters, so the enforcement point can
     * surface it verbatim (Minimum Rules #24).
     *
     * @param actorId          the actor the validated token belongs to
     * @param presentedCounter the counter the token presented
     * @param recordedCounter  the recorded high-water that was not exceeded
     * @return an immutable stale decision (reason is non-null)
     */
    public static FencingTokenDecision stale(String actorId, long presentedCounter, long recordedCounter) {
        return new FencingTokenDecision(false, actorId, presentedCounter, recordedCounter,
                "fencing token stale: presented " + presentedCounter
                        + " <= recorded " + recordedCounter);
    }

    /**
     * @return {@code true} if the token was strictly newer than the recorded
     *         high-water (and the high-water was updated).
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * @return {@code true} if the token was stale
     *         ({@code presentedCounter <= recordedCounter}). Convenience
     *         inverse of {@link #isValid()}.
     */
    public boolean isStale() {
        return !valid;
    }

    /**
     * @return the actor the validated token belongs to.
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * @return the counter the token presented.
     */
    public long getPresentedCounter() {
        return presentedCounter;
    }

    /**
     * @return for a valid decision, the pre-update recorded high-water the
     *         token was compared against; for a stale decision, the recorded
     *         high-water that was not exceeded.
     */
    public long getRecordedCounter() {
        return recordedCounter;
    }

    /**
     * @return the staleness reason, or {@code null} when {@link #isValid()}.
     */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "FencingTokenDecision{valid=" + valid
                + ", actorId=" + (actorId != null ? "'" + actorId + "'" : "null")
                + ", presentedCounter=" + presentedCounter
                + ", recordedCounter=" + recordedCounter
                + ", reason=" + (reason != null ? "'" + reason + "'" : "null") + '}';
    }
}
