package io.nop.ai.agent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
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
    private void renewOnceSafe(AgentSessionLifecycle.CancelHandle handle, String sessionId, String ownerId) {
        if (!runningExecutions.containsKey(sessionId)) {
            return;
        }
        try {
            boolean renewed = config.getSessionTakeoverLock().tryRenew(sessionId, ownerId, config.getLockLeaseMs());
            if (!renewed) {
                handleLeaseLost(handle, sessionId, ownerId);
            }
        } catch (RuntimeException e) {
            LOG.warn("DefaultAgentEngine: takeover lock renewal failed for sessionId={}, "
                    + "ownerId={} (will retry next interval): {}", sessionId, ownerId, e.toString());
        }
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

