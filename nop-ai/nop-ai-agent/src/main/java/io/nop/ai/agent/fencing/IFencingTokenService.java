package io.nop.ai.agent.fencing;

/**
 * Central monotonic-counter decision gateway for concurrent-write protection
 * (vision §5.1 Fencing Token protocol, lines 252-267; vision §10 line 462 —
 * the engine layer depends on this interface name; plan 235 delivers the
 * foundational primitive slice).
 *
 * <p>A consumer that guards a write path (a successor such as scope_claim
 * coordination, Compaction / snapshot writes per vision §6.2, branch-affinity
 * registration, or ResourceGuard fencing integration) calls {@link #issue} to
 * obtain a fresh {@link FencingToken} for an actor, then calls
 * {@link #validate} before mutating shared state, supplying the token the actor
 * attached to the operation. The service returns an immutable
 * {@link FencingTokenDecision}; the enforcement point fail-fast throws
 * {@link io.nop.ai.agent.engine.NopAiAgentException} / broadcasts a
 * {@code conflict_alert} on a stale result (Design Decision §6 — reaction is a
 * consumer responsibility; the primitive returns a decision, not an exception).
 *
 * <h2>Operation semantics (vision §5.1 rules 2-3)</h2>
 * <ul>
 *   <li>{@code issue(actorId)} — atomically increments the per-actor monotonic
 *       counter and returns a new {@link FencingToken}. Consecutive calls for
 *       the same actor return strictly increasing counters (first issue = 1,
 *       then 2, 3, ...). No two concurrent issues for the same actor ever
 *       return the same counter.</li>
 *   <li>{@code validate(token)} — compares {@code token.monotonicCounter}
 *       against the per-actor recorded high-water:
 *     <ul>
 *       <li>{@code presented > recorded} → {@code valid} decision and the
 *           recorded high-water is atomically updated to {@code presented}
 *           (only-if-greater, so concurrent validators cannot regress the
 *           high-water).</li>
 *       <li>{@code presented <= recorded} → {@code stale} decision with a
 *           non-null reason; the recorded high-water is <em>not</em> updated
 *           (monotonic, never regresses).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Shipped defaults</h2>
 * <ul>
 *   <li>{@link NoOpFencingTokenService} — singleton shipped default,
 *       {@code validate} always returns {@code valid}. This is the
 *       functional-impls' constructor default so that consumers see zero
 *       behaviour regression unless an integrator wires a
 *       {@link DefaultFencingTokenService} (Design Decision §2). The NoOp
 *       valid is an explicit decision object, not a swallowed null / silent
 *       skip (Minimum Rules #24).</li>
 *   <li>{@link DefaultFencingTokenService} — functional in-memory CAS
 *       implementation (per-actor {@link java.util.concurrent.atomic.AtomicLong}
 *       counters). Single-JVM concurrent-write protection; DB-backed
 *       cross-process CAS is a successor (Non-Goal; vision §2.3 rejects the
 *       multi-process model, single JVM + Virtual Thread is the baseline).</li>
 * </ul>
 *
 * <p>No engine-top-level {@code setFencingTokenService} field is added in this
 * plan: there is no wired consumer yet (all consumers are successors), so
 * engine-top-level wiring is reserved for the first consumer (scope_claim /
 * Compaction) successor (Design Decision §8).
 *
 * <p>See plan 235 (L4-fencing-token), Design Decisions §1 / §2 / §3 / §5 / §6 /
 * §7 / §8, and vision §5.1 / §10.
 */
public interface IFencingTokenService {

    /**
     * Atomically increment the per-actor monotonic counter and return a new
     * {@link FencingToken} carrying the new counter.
     *
     * @param actorId the actor to issue a token for (non-null)
     * @return a new immutable {@link FencingToken} whose counter is strictly
     *         greater than the previous token issued for the same actor (first
     *         issue = 1); never {@code null} (Minimum Rules #24)
     */
    FencingToken issue(String actorId);

    /**
     * Adjudicate a presented {@link FencingToken} against the per-actor
     * recorded high-water. Returns an immutable {@link FencingTokenDecision};
     * never {@code null}. Does <em>not</em> throw on a stale token — the
     * enforcement-point reaction is a consumer responsibility (Design Decision
     * §6).
     *
     * @param token the token presented by the actor (non-null)
     * @return a {@link FencingTokenDecision}; {@code valid} (high-water
     *         atomically updated to {@code presented}) when
     *         {@code presented > recorded}, {@code stale} (high-water not
     *         updated) otherwise
     */
    FencingTokenDecision validate(FencingToken token);
}
