package io.nop.ai.agent.fencing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Functional {@link IFencingTokenService} that enforces per-actor monotonic
 * counter concurrent-write protection via in-memory CAS (plan 235, Design
 * Decision §3).
 *
 * <h2>State (two independent per-actor counters)</h2>
 * <ul>
 *   <li><b>issue counter</b> — the next counter to hand out. {@link #issue}
 *       atomically increments it ({@link AtomicLong#incrementAndGet}), so
 *       consecutive / concurrent issues for the same actor always return
 *       strictly increasing counters with no duplicates (first issue = 1, then
 *       2, 3, ...).</li>
 *   <li><b>recorded high-water</b> — the highest counter a {@link #validate}
 *       call has accepted so far. {@link #validate} atomically compares the
 *       presented counter against it and updates it only-if-greater via a
 *       compare-and-set loop, so concurrent validators cannot regress the
 *       high-water (no lost update).</li>
 * </ul>
 *
 * <p>The two counters are independent: {@code issue} generates tokens,
 * {@code validate} records the high-water of accepted tokens. This matches the
 * fencing-token usage pattern (an actor issues a token, a consumer validates
 * it) and the vision §5.1 recovery rule (on recovery the issue counter is
 * reset to DB-max + 1 — a DB-backed successor concern; in-memory the issue
 * counter simply starts at 0 and the state is lost on process restart).
 *
 * <h2>Adjudication flow (per {@code validate} call)</h2>
 * <ol>
 *   <li>Read the per-actor recorded high-water (defaulting to {@code 0} when
 *       no token has ever been validated for the actor).</li>
 *   <li>{@code presented > recorded} → CAS-update the high-water to
 *       {@code presented} (retrying on CAS race); return a {@code valid}
 *       decision whose {@code recordedCounter} is the pre-update high-water.
 *       The CAS loop guarantees the high-water only ever moves forward.</li>
 *   <li>{@code presented <= recorded} → return a {@code stale} decision with a
 *       non-null reason; the high-water is <em>not</em> updated (monotonic).
 *       Not a silent skip (Minimum Rules #24).</li>
 * </ol>
 *
 * <p>Single-JVM baseline: this protects against concurrent writes from actors
 * running on Virtual Threads / platform threads inside one JVM. DB-backed
 * cross-process CAS ({@code UPDATE ... SET counter = ? WHERE counter = ?},
 * vision §5.1 line 267) is a successor (Non-Goal; vision §2.3 rejects the
 * multi-process model).
 *
 * <p>See plan 235 (L4-fencing-token), Design Decisions §3 / §7, vision §5.1.
 */
public final class DefaultFencingTokenService implements IFencingTokenService {

    private final ConcurrentHashMap<String, AtomicLong> issueCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> recordedHighWater = new ConcurrentHashMap<>();

    @Override
    public FencingToken issue(String actorId) {
        AtomicLong counter = issueCounters.computeIfAbsent(actorId, k -> new AtomicLong(0L));
        long next = counter.incrementAndGet(); // first issue -> 1, then 2, 3, ...
        return FencingToken.of(actorId, next, System.currentTimeMillis());
    }

    @Override
    public FencingTokenDecision validate(FencingToken token) {
        AtomicLong recorded = recordedHighWater.computeIfAbsent(token.getActorId(), k -> new AtomicLong(0L));
        long presented = token.getMonotonicCounter();
        // CAS loop: atomically read the high-water and conditionally update it
        // only-if-greater, so concurrent validators cannot regress it (no lost
        // update). The `current` captured in each iteration is the value the
        // decision is based on.
        while (true) {
            long current = recorded.get();
            if (presented > current) {
                if (recorded.compareAndSet(current, presented)) {
                    // High-water advanced current -> presented; the decision is
                    // valid against the pre-update high-water `current`.
                    return FencingTokenDecision.valid(token.getActorId(), presented, current);
                }
                // CAS raced: another validator moved the high-water; re-read.
            } else {
                // Stale (presented <= current); do not update the high-water.
                return FencingTokenDecision.stale(token.getActorId(), presented, current);
            }
        }
    }
}
