package io.nop.ai.agent.fencing;

import java.util.Objects;

/**
 * Immutable fencing token carrying the per-actor monotonic counter used to
 * guard against concurrent / stale writes (vision §5.1 Fencing Token protocol,
 * lines 252-267; glossary line 65).
 *
 * <p>A token bundles three pieces of information:
 * <ul>
 *   <li>{@code actorId} — the actor the token was issued for.</li>
 *   <li>{@code monotonicCounter} — a strictly-increasing counter; consumers
 *       reject any token whose counter is not greater than the highest counter
 *       they have already recorded (vision §5.1 rule 2-3).</li>
 *   <li>{@code issuedAt} — epoch millis at issue time, for observability /
 *       diagnostics only (it does not participate in the staleness decision;
 *       only the counter does).</li>
 * </ul>
 *
 * <p>Use the {@link #of} factory; the constructor is private. All fields are
 * final and the object is immutable, so a token may be safely shared across
 * threads.
 *
 * <p>See plan 235 (L4-fencing-token), Design Decision §4, vision §5.1.
 */
public final class FencingToken {

    private final String actorId;
    private final long monotonicCounter;
    private final long issuedAt;

    private FencingToken(String actorId, long monotonicCounter, long issuedAt) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.monotonicCounter = monotonicCounter;
        this.issuedAt = issuedAt;
    }

    /**
     * Build an immutable fencing token.
     *
     * @param actorId          the actor the token is issued for (non-null)
     * @param monotonicCounter the strictly-increasing counter
     * @param issuedAt         epoch millis at issue time
     * @return an immutable {@link FencingToken}
     */
    public static FencingToken of(String actorId, long monotonicCounter, long issuedAt) {
        return new FencingToken(actorId, monotonicCounter, issuedAt);
    }

    /**
     * @return the actor the token was issued for (non-null).
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * @return the strictly-increasing monotonic counter carried by this token.
     */
    public long getMonotonicCounter() {
        return monotonicCounter;
    }

    /**
     * @return epoch millis at issue time (observability only; not part of the
     *         staleness decision).
     */
    public long getIssuedAt() {
        return issuedAt;
    }

    @Override
    public String toString() {
        return "FencingToken{actorId='" + actorId + '\''
                + ", monotonicCounter=" + monotonicCounter
                + ", issuedAt=" + issuedAt + '}';
    }
}
