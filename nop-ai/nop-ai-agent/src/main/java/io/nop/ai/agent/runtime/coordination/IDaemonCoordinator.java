package io.nop.ai.agent.runtime.coordination;

/**
 * Opt-in cross-process team-level scan-lease coordination for the
 * multi-instance {@code TeamTaskSchedulerDaemon} deployment (plan 242 /
 * {@code L4-cross-process-daemon-coordination}).
 *
 * <p><b>Why this interface exists</b>. A multi-instance deployment of the
 * scheduler daemon (N JVM processes sharing the same backing store) suffers
 * from redundant scan load: every instance periodically scans <em>all</em>
 * active teams, rebuilding the topology and re-running the ready query on
 * the same task set the other N-1 instances are also scanning.
 * {@code claimTask}'s DB-level CAS (plan 227 / 240) already provides the
 * <em>correctness floor</em> — concurrent claims on the same task resolve
 * to exactly one winner — so this coordination layer is a
 * <b>scan-load optimization</b>, not a correctness precondition.
 *
 * <p>Coordination granularity is <b>per-team scan lease</b> (design 裁定 2):
 * before scanning a team, the daemon asks the coordinator for that team's
 * short-lived scan lease. If another instance already holds an active lease
 * for the team, this instance skips it (reducing redundant DB reads +
 * topology builds + claim CAS contention). N instances therefore tend to
 * spread across different teams rather than duplicating the whole scan.
 *
 * <p>The shipped default is {@link NoOpDaemonCoordinator} — single-process
 * deployments are unchanged (every {@code tryAcquireScanLease} returns
 * {@code true} = full scan), which is an explicit "no cross-process
 * coordination needed" semantic, not a silent no-op (Minimum Rules #24).
 * Integrators opt in by wiring {@link DbDaemonCoordinator} via
 * {@code TeamTaskSchedulerDaemon.setDaemonCoordinator}.
 *
 * <p>Symmetric to {@link io.nop.ai.agent.runtime.lock.ISessionTakeoverLock}'s
 * lease CAS pattern but lives in a separate contract: different domain
 * (scan coordination vs session takeover), different key space (teamId vs
 * sessionId), and different lease semantics (scan-cycle level vs 30-min
 * session takeover). Naming isolation keeps the two leases evolvable
 * independently (design 裁定 3).
 *
 * <h2>CAS acquire semantics (truth table)</h2>
 * {@link #tryAcquireScanLease} is the atomic compare-and-swap primitive:
 * <ul>
 *   <li>No prior lease for {@code teamId} → INSERT succeeds → acquire
 *       succeeds ({@code true}).</li>
 *   <li>Prior lease held by a <em>different</em> owner with
 *       {@code EXPIRES_AT} still in the future → UPDATE rejected → acquire
 *       fails ({@code false}). Failure is a normal control-flow signal
 *       (the caller skips this team and records it as
 *       {@code skippedCoordinatedTeams}), never an exception.</li>
 *   <li>Prior lease held by the <em>same</em> owner → acquire is treated as
 *       a renew ({@code true}).</li>
 *   <li>Prior lease expired ({@code EXPIRES_AT <= now}) regardless of
 *       owner → stale lease is preempted by the new owner ({@code true}).</li>
 * </ul>
 *
 * <h2>Conditional release</h2>
 * {@link #releaseScanLease} only releases a lease held by the given
 * {@code ownerId} — never another owner's lease. Returns {@code true} when
 * the lease was actually held by {@code ownerId} and has been removed;
 * {@code false} when no such owner-held lease exists (already released,
 * expired and preempted, or never acquired). This prevents a stale instance
 * from accidentally freeing a fresh lease a different instance just
 * acquired.
 *
 * <p>The daemon normally calls {@code releaseScanLease} in a {@code finally}
 * block after a team's scan completes — the active release is the
 * fast-failover path (the next instance can immediately take the lease
 * rather than waiting for TTL). If the holder crashes without releasing,
 * the lease auto-expires when {@code now} crosses {@code EXPIRES_AT} — the
 * passive fail-safe.
 *
 * <h2>Lease / TTL expiry</h2>
 * Each successful acquire sets {@code EXPIRES_AT = now + leaseMs}. The
 * caller chooses {@code leaseMs} — the {@code TeamTaskSchedulerDaemon}
 * default is {@code 6 * scanIntervalSec * 1000} (design 裁定 8), giving a
 * worst-case single-team scan (including a synchronous agent execute
 * join) ample headroom while bounding failover latency to ~30s on the
 * default 5s scan cadence.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls. DB-backed
 * implementations achieve this via atomic SQL CAS operations
 * (PK uniqueness + conditional UPDATE/DELETE with affected-row counts).
 *
 * <h2>Honest failure contract (Minimum Rules #24)</h2>
 * <ul>
 *   <li>{@code tryAcquireScanLease == false} is an <b>explicit coordination
 *       signal</b> (another instance owns the team's scan lease), not a
 *       silent skip — the daemon records it in
 *       {@code skippedCoordinatedTeams}.</li>
 *   <li>Any SQLException / DB error is wrapped in
 *       {@link io.nop.ai.agent.engine.NopAiAgentException} — never
 *       swallowed.</li>
 *   <li>{@link NoOpDaemonCoordinator} unconditionally returns {@code true}
 *       on acquire — an explicit "no coordination" semantic, not a silent
 *       placeholder.</li>
 * </ul>
 *
 * <p><b>Correctness floor is unaffected</b>: even if this coordination
 * layer fails completely (lease table dropped, all acquires return
 * {@code false}, or NoOp is wired), {@code claimTask} CAS still guarantees
 * no double-dispatch (plan 227 / 240, design 裁定 6).
 *
 * <p>See plan 242 and design
 * {@code nop-ai-agent-cross-process-daemon-coordination.md}.
 */
public interface IDaemonCoordinator {

    /**
     * Atomically acquire (or renew) the scan lease for the given team. See
     * the class-level CAS truth table for the full semantics.
     *
     * <p><b>Non-exception control flow</b>: a {@code false} return means
     * "another instance holds an active lease for this team" — the caller
     * should skip the team (recording it as
     * {@code skippedCoordinatedTeams}), <em>not</em> treat it as an error.
     * DB-level errors, by contrast, propagate as
     * {@link io.nop.ai.agent.engine.NopAiAgentException}.
     *
     * @param teamId  the team whose scan lease to acquire; never null
     * @param ownerId the acquiring daemon instance identity (the daemon's
     *                {@code daemonOwnerId}, unique per instance); never null
     * @param leaseMs the lease duration in milliseconds; the lease
     *                auto-expires {@code leaseMs} after a successful
     *                acquire. Must be {@code > 0}
     * @return {@code true} if the lease is now held by {@code ownerId};
     *         {@code false} if an active lease is held by a different
     *         owner (never throws for lease contention)
     */
    boolean tryAcquireScanLease(String teamId, String ownerId, long leaseMs);

    /**
     * Conditionally release the scan lease — only releases a lease held by
     * {@code ownerId} for {@code teamId}. Never releases another owner's
     * lease.
     *
     * <p>Normally called by the daemon in a {@code finally} block after a
     * team's scan completes (active release = fast failover). A {@code false}
     * return means the lease was no longer ours (already expired and
     * preempted, or never acquired) — the daemon LOG.warn's and continues;
     * it does <em>not</em> affect the scan's task results (the scan ran
     * regardless, and {@code claimTask} CAS is the correctness floor).
     *
     * @param teamId  the team whose lease to release; never null
     * @param ownerId the owner identity that originally acquired the
     *                lease; never null
     * @return {@code true} if a lease held by {@code ownerId} was removed;
     *         {@code false} if no such owner-held lease exists
     */
    boolean releaseScanLease(String teamId, String ownerId);

    /**
     * Check whether an <em>active</em> lease (not expired) is currently
     * held by any owner for {@code teamId}. Used for observability / tests
     * to assert the cross-process coordination state. Does not distinguish
     * owners — a lease held by this instance also reports {@code true}.
     *
     * @param teamId the team whose lease state to check; never null
     * @return {@code true} if an active (non-expired) lease exists for
     *         {@code teamId}; {@code false} otherwise
     */
    boolean isScanLeaseActive(String teamId);
}
