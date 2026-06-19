package io.nop.ai.agent.runtime.lock;

/**
 * Opt-in cross-process session takeover lock. When wired into
 * {@code DefaultAgentEngine} (via {@code setSessionTakeoverLock}), this lock
 * prevents two JVM instances sharing the same backing store from
 * simultaneously restoring and executing the same crashed/pending session
 * (double-execution correctness gap, plan 221 / L4-8-P4).
 *
 * <p>The shipped default is {@link NoOpSessionTakeoverLock} — single-process
 * deployments keep relying on the engine's in-process
 * {@code runningExecutions.putIfAbsent} guard, so engine behaviour is
 * unchanged unless a functional lock (e.g. {@link DbSessionTakeoverLock})
 * is explicitly wired. No-op is an explicit "no cross-process lock needed"
 * semantic, not a silent no-op (Minimum Rules #24).
 *
 * <h2>CAS acquire semantics</h2>
 * {@link #tryAcquire} is the atomic compare-and-swap primitive:
 * <ul>
 *   <li>No prior lock for {@code sessionId} → INSERT succeeds → acquire
 *       succeeds ({@code true}).</li>
 *   <li>Prior lock held by a different owner with {@code LOCK_EXPIRES_AT}
 *       still in the future → INSERT/UPDATE rejected → acquire fails
 *       ({@code false}). Failure is a normal control-flow signal, not an
 *       exception — the caller decides fail-fast (entry points) vs skip
 *       ({@code restorePendingSessions}) based on context.</li>
 *   <li>Prior lock held by the <em>same</em> owner → acquire is treated as
 *       a renew/refresh ({@code true}).</li>
 *   <li>Prior lock expired ({@code LOCK_EXPIRES_AT <= now}) regardless of
 *       owner → stale lock is preempted by the new owner ({@code true}).</li>
 * </ul>
 *
 * <h2>Conditional release</h2>
 * {@link #release} only releases a lock held by the given {@code ownerId}
 * — never another owner's lock. Returns {@code true} when the lock was
 * actually held by {@code ownerId} and has been removed; {@code false}
 * when no such owner-held lock exists (already released, expired and
 * preempted, or never acquired). This prevents a misbehaving (or stale)
 * instance from accidentally releasing a fresh lock a different instance
 * legitimately acquired.
 *
 * <h2>Lease / TTL expiry</h2>
 * Each successful acquire sets {@code LOCK_EXPIRES_AT = now + leaseMs}.
 * If the lock holder crashes, the lock auto-expires when {@code now}
 * crosses {@code LOCK_EXPIRES_AT}; the next {@link #tryAcquire} (any
 * owner) preempts the stale lock. During normal execution the engine
 * auto-renews the lease via {@link #tryRenew} at
 * {@code lockRenewIntervalMs} (plan 273) so a long-running agent's lease
 * does not expire mid-execution. There is no background sweeper thread
 * for stale locks in this interface (passive TTL expiry is the
 * fail-safe; {@code ScheduledRecoveryManager.deleteStaleLocks} is an
 * optional deployment-layer complement).
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls. DB-backed
 * implementations achieve this via atomic SQL CAS operations (INSERT ...
 * ON CONFLICT / MERGE, conditional UPDATE/DELETE with affected-row counts).
 *
 * <p>See plan 221 and design {@code nop-ai-agent-actor-runtime-vision.md}
 * §6.3 / §10 Phase 4.
 */
public interface ISessionTakeoverLock {

    /**
     * Atomically acquire (or renew) the takeover lock for the given
     * session. See class-level CAS semantics for the full truth table.
     *
     * @param sessionId the persistent session identity; never null
     * @param ownerId   the acquiring owner identity (typically the engine
     *                  {@code instanceId} UUID); never null
     * @param leaseMs   the lease duration in milliseconds; the lock
     *                  auto-expires {@code leaseMs} after a successful
     *                  acquire. Must be {@code > 0}
     * @return {@code true} if the lock is now held by {@code ownerId};
     *         {@code false} if an active lock is held by a different owner
     *         (never throws for lock contention — the caller decides
     *         fail-fast vs skip)
     */
    boolean tryAcquire(String sessionId, String ownerId, long leaseMs);

    /**
     * Conditionally release the lock — only releases a lock held by
     * {@code ownerId} for {@code sessionId}. Never releases another
     * owner's lock.
     *
     * @param sessionId the persistent session identity; never null
     * @param ownerId   the owner identity that originally acquired the
     *                  lock; never null
     * @return {@code true} if a lock held by {@code ownerId} was removed;
     *         {@code false} if no such owner-held lock exists
     */
    boolean release(String sessionId, String ownerId);

    /**
     * Check whether an <em>active</em> lock (not expired) is currently
     * held by any owner for {@code sessionId}. Used by
     * {@code restorePendingSessions} to skip sessions already being
     * processed by another instance (reduces wasted restore attempts and
     * log noise). Does not distinguish owners — a session locked by this
     * instance also reports {@code true}.
     *
     * @param sessionId the persistent session identity; never null
     * @return {@code true} if an active (non-expired) lock exists for
     *         {@code sessionId}; {@code false} otherwise
     */
    boolean isHeld(String sessionId);

    /**
     * Conditionally renew (extend the lease of) a lock held by
     * {@code ownerId}. Only succeeds if {@code ownerId} currently holds
     * an active lock for {@code sessionId}; otherwise returns
     * {@code false}.
     *
     * <p>The engine auto-calls this during execution: once an execution
     * starts (after {@link #tryAcquire} succeeds), the engine schedules a
     * periodic {@code tryRenew} at {@code lockRenewIntervalMs} (default
     * 10min — a safe fraction of the default 30min lease) so a long-
     * running agent's lease does not expire mid-execution and get
     * preempted by another JVM instance (double-execution prevention,
     * plan 273). When {@code tryRenew} returns {@code false} (lease lost
     * / preempted), the engine aborts the local execution and marks the
     * session {@code failed}.
     *
     * @param sessionId the persistent session identity; never null
     * @param ownerId   the owner identity that originally acquired the
     *                  lock; never null
     * @param leaseMs   the new lease duration in milliseconds (extends
     *                  {@code LOCK_EXPIRES_AT} to {@code now + leaseMs}).
     *                  Must be {@code > 0}
     * @return {@code true} if the lease was extended;
     *         {@code false} if {@code ownerId} does not currently hold
     *         an active lock for {@code sessionId}
     */
    boolean tryRenew(String sessionId, String ownerId, long leaseMs);
}
