package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Takeover-lock lease renewal (extracted from {@link DefaultAgentEngine},
 * MA4.2-05). Lazily creates the dedicated renewal scheduler, schedules the
 * per-session heartbeat, and aborts the local execution when the lease is
 * lost (preempted by another instance or expired) to prevent double-execution.
 */
public class SessionLockRenewal {
    private static final Logger LOG = LoggerFactory.getLogger(SessionLockRenewal.class);
    private final DefaultAgentEngineConfig config;
    private final java.util.concurrent.ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> runningExecutions;

    private ScheduledExecutorService lockRenewExecutor;
    private volatile boolean ownLockRenewExecutor;

    public SessionLockRenewal(DefaultAgentEngineConfig config,
                              java.util.concurrent.ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> runningExecutions) {
        this.config = config;
        this.runningExecutions = runningExecutions;
    }

    public void setLockRenewExecutor(ScheduledExecutorService lockRenewExecutor) {
        this.lockRenewExecutor = java.util.Objects.requireNonNull(lockRenewExecutor,
                "lockRenewExecutor must not be null");
        this.ownLockRenewExecutor = false;
    }

    public boolean isOwnLockRenewExecutor() {
        return ownLockRenewExecutor;
    }

    /**
     * Plan 2519 (裁定⑩ / 3.1-C7-012): bound on consecutive renewal failures
     * tolerated inside the transient exemption window before the local
     * execution is aborted via {@link #handleLeaseLost}. Zero {@code @cfg:}
     * keys are introduced (setter injection only); the default of 3 keeps the
     * plan 2496 insulation pin (2 injected Error rounds, then recovery) green.
     */
    private int maxConsecutiveRenewalFailures = 3;

    public int getMaxConsecutiveRenewalFailures() {
        return maxConsecutiveRenewalFailures;
    }

    public void setMaxConsecutiveRenewalFailures(int maxConsecutiveRenewalFailures) {
        if (maxConsecutiveRenewalFailures <= 0) {
            throw new NopAiAgentException("maxConsecutiveRenewalFailures must be positive, got: "
                    + maxConsecutiveRenewalFailures);
        }
        this.maxConsecutiveRenewalFailures = maxConsecutiveRenewalFailures;
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    synchronized ScheduledExecutorService getLockRenewExecutor() {
        if (lockRenewExecutor == null) {
            lockRenewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nop-ai-agent-lock-renew");
                t.setDaemon(true);
                return t;
            });
            ownLockRenewExecutor = true;
        }
        return lockRenewExecutor;
    }
    public ScheduledFuture<?> startLockRenewal(AgentSessionLifecycle.CancelHandle handle, String sessionId, String ownerId) {
        if (config.getLockRenewIntervalMs() <= 0) {
            // Explicit opt-out: renewal disabled, lease behaves as pure
            // passive TTL (backward-compatible escape hatch).
            return null;
        }
        ScheduledExecutorService exec = getLockRenewExecutor();
        return exec.scheduleWithFixedDelay(() -> renewOnceSafe(handle, sessionId, ownerId),
                config.getLockRenewIntervalMs(), config.getLockRenewIntervalMs(), TimeUnit.MILLISECONDS);
    }
    // package-private for the abort-upgrade regression test (plan 2519 红⑩)
    void renewOnceSafe(AgentSessionLifecycle.CancelHandle handle, String sessionId, String ownerId) {
        if (!runningExecutions.containsKey(sessionId)) {
            return;
        }
        try {
            boolean renewed = config.getSessionTakeoverLock().tryRenew(sessionId, ownerId, config.getLockLeaseMs());
            if (!renewed) {
                handleLeaseLost(handle, sessionId, ownerId);
                return;
            }
            // Plan 2519 (裁定⑩): a successful renewal proves the lease is
            // (again) known-good — reset the consecutive-failure counter.
            // (handle is null only for probes; production always carries one.)
            if (handle != null) {
                handle.renewalFailureCount.set(0);
            }
        } catch (Throwable e) {
            // MA-07/C4-007 (plan 2496 Phase 4): the guard must cover Throwable, not just
            // RuntimeException. The catch keeps the periodic chain alive
            // (JDK re-arms on the next interval) and keeps the failure observable.
            // Plan 2519 (裁定⑩ / 3.1-C7-012): the guard is no longer the terminal
            // state. Failures are classified by unwrapping the cause chain to the
            // root cause (never by the outer wrapper type — DbSessionTakeoverLock
            // wraps every SQLException in a NopAiAgentException, so outer-type
            // classification would be constant "unknown" and mis-kill):
            // - root cause is a recoverable/other SQLException (connection refused /
            //   dropped / DB unreachable) => the lease state is UNKNOWN, which under
            //   fencing is treated as possibly lost (fail-safe: self-abort beats
            //   double-execution in an asymmetric partition) => immediate abort.
            // - root cause is a transient SQL exception (lock-wait timeout and
            //   other retryable semantics) or a non-SQL failure (Error, runtime
            //   exception) => counted inside the exemption window; the window bound
            //   is maxConsecutiveRenewalFailures — exceeding it aborts (no infinite
            //   WARN, which was the pre-fix defect face). The bound (3) stays above
            //   the plan 2496 insulation pin (2 injected Error rounds + recovery),
            //   so the plan 2496 guard semantics are preserved zero-regressed.
            boolean leaseStateUnknown = isLeaseStateUnknownFailure(e);
            int failures = handle == null ? 1 : handle.renewalFailureCount.incrementAndGet();
            if (leaseStateUnknown || failures >= maxConsecutiveRenewalFailures) {
                LOG.warn("nop.ai.agent.lease-renew-abort: takeover lock renewal failed "
                        + "consecutively for sessionId={}, ownerId={}, consecutiveFailures={}, "
                        + "leaseStateUnknown={} — escalating to lease-lost abort (lease state "
                        + "unknown is treated as possibly lost, fail-safe)", sessionId, ownerId,
                        failures, leaseStateUnknown, e);
                handleLeaseLost(handle, sessionId, ownerId);
            } else {
                LOG.warn("DefaultAgentEngine: takeover lock renewal failed for sessionId={}, "
                        + "ownerId={} (will retry next interval, consecutiveFailures={}/{}): {}",
                        sessionId, ownerId, failures, maxConsecutiveRenewalFailures, e.toString());
            }
        }
    }

    /**
     * Plan 2519 (裁定⑩): classification predicate. Unwraps the full cause chain
     * to the root cause (self-referential chains are guarded) and reports
     * whether the failure means the lease state is unknowable (DB unreachable):
     * any root {@link SQLException} other than a retryable
     * {@link SQLTransientException} (lock-wait timeout and similar retryable
     * semantics — {@link SQLRecoverableException} is explicitly NOT transient
     * here: a lost connection means the lease is unknown). Classification never
     * short-circuits on the outer wrapper type.
     */
    static boolean isLeaseStateUnknownFailure(Throwable e) {
        Throwable root = e;
        Throwable cause = root.getCause();
        while (cause != null && cause != root) {
            root = cause;
            cause = root.getCause();
        }
        if (root instanceof SQLException) {
            return !(root instanceof SQLTransientException)
                    || root instanceof SQLRecoverableException;
        }
        // Non-SQL failures (Error, unexpected RuntimeException) do not prove the
        // lease is lost, but neither do they prove it is held — they stay inside
        // the counted exemption window (bounded by maxConsecutiveRenewalFailures).
        return false;
    }
    public void handleLeaseLost(AgentSessionLifecycle.CancelHandle handle, String sessionId, String ownerId) {
        LOG.warn("DefaultAgentEngine: takeover lease lost for sessionId={}, ownerId={} "
                + "(preempted by another instance or expired) — aborting local execution "
                + "to prevent double-execution", sessionId, ownerId);
        AgentExecutionContext ctx = handle.context;
        ctx.setLeaseLost(true);
        ctx.setCancelRequested(true);
        ctx.setCancelReason("takeover lease lost");
        Thread t = handle.thread;
        if (t != null) {
            t.interrupt();
        }
    }
    public static void cancelLockRenewalQuietly(Future<?> renewHandle) {
        if (renewHandle == null) {
            return;
        }
        try {
            renewHandle.cancel(false);
        } catch (RuntimeException e) {
            LOG.warn("DefaultAgentEngine: failed to cancel lock renewal task: {}", e.toString());
        }
    }
}

