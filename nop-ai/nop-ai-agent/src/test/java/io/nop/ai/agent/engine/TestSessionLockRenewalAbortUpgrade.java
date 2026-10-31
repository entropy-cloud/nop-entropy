package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (裁定⑩ / 3.1-C7-012, 红⑩): takeover-lock renewal failures must
 * escalate to the lease-lost abort instead of warning forever.
 *
 * <ul>
 *   <li>A root-cause SQLException (DB unreachable — connection refused /
 *       recoverable) means the lease state is UNKNOWN, which under fencing is
 *       treated as possibly lost (fail-safe): the local execution must abort.
 *       Pre-fix state (red): the Throwable guard only WARNed and the session
 *       kept executing through an asymmetric partition (infinite tolerance).</li>
 *   <li>Transient failures (SQLTimeoutException / lock-wait timeout semantics)
 *       stay inside the counted exemption window and a success resets the
 *       counter; exceeding the configured bound aborts.</li>
 *   <li>The plan 2496 insulation pin (Error rounds never abort) stays green:
 *       non-SQL failures are counted, not immediately fatal.</li>
 * </ul>
 */
public class TestSessionLockRenewalAbortUpgrade {

    /** Lock stub whose tryRenew always throws the given throwable. */
    static class ThrowingLock implements ISessionTakeoverLock {
        final Throwable failure;
        final AtomicInteger renewAttempts = new AtomicInteger();

        ThrowingLock(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            renewAttempts.incrementAndGet();
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            throw (RuntimeException) failure;
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return true;
        }

        @Override
        public boolean isHeld(String sessionId) {
            return false;
        }
    }

    /** Lock stub: one transient failure, one success, one transient failure. */
    static class TransientSuccessTransientLock implements ISessionTakeoverLock {
        final AtomicInteger renewAttempts = new AtomicInteger();

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            int attempt = renewAttempts.incrementAndGet();
            if (attempt == 1 || attempt == 3) {
                throw new NopAiAgentException("wrapped transient",
                        new SQLTimeoutException("lock wait timeout, attempt=" + attempt));
            }
            return true;
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return true;
        }

        @Override
        public boolean isHeld(String sessionId) {
            return false;
        }
    }

    private final ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> runningExecutions =
            new ConcurrentHashMap<>();

    private ListAppender<ILoggingEvent> logAppender;
    private Logger renewalLogger;

    @BeforeEach
    void attachAppender() {
        renewalLogger = (Logger) LoggerFactory.getLogger(SessionLockRenewal.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        renewalLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        if (renewalLogger != null && logAppender != null) {
            renewalLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private SessionLockRenewal newRenewal(DefaultAgentEngineConfig config,
                                          AtomicInteger leaseLostCount) {
        return new SessionLockRenewal(config, runningExecutions) {
            @Override
            public void handleLeaseLost(AgentSessionLifecycle.CancelHandle handle,
                                        String sessionId, String ownerId) {
                leaseLostCount.incrementAndGet();
                // do NOT call super: the probe handle has no execution context/thread
            }
        };
    }

    private void awaitAttempts(AtomicInteger renewAttempts, int target) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (renewAttempts.get() < target && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(renewAttempts.get() >= target,
                "renewal chain must keep running: attempts=" + renewAttempts.get());
    }

    /**
     * 红⑩ defect-state red: a root-cause SQLException (DB unreachable) used to be
     * WARNed forever while the session kept executing (asymmetric partition,
     * fencing defeated). Post-fix: the first lease-state-unknown failure escalates
     * to the lease-lost abort path.
     */
    @Test
    void testSqlConnectFailureEscalatesToLeaseLostAbort() throws Exception {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(20);
        ThrowingLock lock = new ThrowingLock(new NopAiAgentException("renew failed",
                new SQLException("connection refused")));
        config.setSessionTakeoverLock(lock);

        AtomicInteger leaseLostCount = new AtomicInteger();
        SessionLockRenewal watched = newRenewal(config, leaseLostCount);

        String sessionId = "session-sql-abort";
        runningExecutions.put(sessionId, new AgentSessionLifecycle.CancelHandle(null, null));
        java.util.concurrent.ScheduledFuture<?> renewTask =
                watched.startLockRenewal(null, sessionId, "owner-1");
        try {
            awaitAttempts(lock.renewAttempts, 1);
            long deadline = System.currentTimeMillis() + 10_000;
            while (leaseLostCount.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(leaseLostCount.get() >= 1,
                    "root-cause SQLException (DB unreachable) must escalate to the "
                            + "lease-lost abort instead of infinite WARN tolerance");

            // The abort is observable under the named metric key.
            long abortLogs = logAppender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("nop.ai.agent.lease-renew-abort"))
                    .count();
            assertTrue(abortLogs >= 1, "abort must be logged under nop.ai.agent.lease-renew-abort");
        } finally {
            SessionLockRenewal.cancelLockRenewalQuietly(renewTask);
        }
    }

    /**
     * Transient (retryable) failures stay inside the exemption window, a success
     * resets the consecutive counter, and exceeding the bound aborts.
     */
    @Test
    void testTransientFailureExemptionWindowBoundAndReset() throws Exception {
        // (a) 2 consecutive transient failures with bound 2 => abort fires.
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(20);
        // never recovers: fail every round transiently
        ThrowingLock transientForever = new ThrowingLock(new NopAiAgentException("wrapped transient",
                new SQLTimeoutException("lock wait timeout")));
        config.setSessionTakeoverLock(transientForever);

        AtomicInteger leaseLostCountA = new AtomicInteger();
        SessionLockRenewal watchedA = newRenewal(config, leaseLostCountA);
        watchedA.setMaxConsecutiveRenewalFailures(2);
        String sessionIdA = "session-transient-bound";
        AgentSessionLifecycle.CancelHandle handleA = new AgentSessionLifecycle.CancelHandle(null, null);
        runningExecutions.put(sessionIdA, handleA);
        java.util.concurrent.ScheduledFuture<?> taskA =
                watchedA.startLockRenewal(handleA, sessionIdA, "owner-1");
        try {
            awaitAttempts(transientForever.renewAttempts, 2);
            long deadline = System.currentTimeMillis() + 10_000;
            while (leaseLostCountA.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(leaseLostCountA.get() >= 1,
                    "exceeding the transient exemption-window bound must abort "
                            + "(no infinite WARN)");
            // The first transient round must have stayed inside the window
            // (counted warn, not abort) — the counted-retry warn is observable.
            long countedWarns = logAppender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("consecutiveFailures=1/2"))
                    .count();
            assertTrue(countedWarns >= 1,
                    "the first transient failure must be warned inside the exemption window");
        } finally {
            SessionLockRenewal.cancelLockRenewalQuietly(taskA);
        }
    }

    /**
     * A successful renewal resets the consecutive-failure counter: transient,
     * success, transient never escalates (bound 2).
     */
    @Test
    void testSuccessResetsConsecutiveFailureCounter() throws Exception {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(20);
        TransientSuccessTransientLock lock = new TransientSuccessTransientLock();
        config.setSessionTakeoverLock(lock);

        AtomicInteger leaseLostCount = new AtomicInteger();
        SessionLockRenewal watched = newRenewal(config, leaseLostCount);
        watched.setMaxConsecutiveRenewalFailures(2);
        String sessionId = "session-transient-reset";
        AgentSessionLifecycle.CancelHandle handle3 = new AgentSessionLifecycle.CancelHandle(null, null);
        runningExecutions.put(sessionId, handle3);
        java.util.concurrent.ScheduledFuture<?> task =
                watched.startLockRenewal(handle3, sessionId, "owner-1");
        try {
            awaitAttempts(lock.renewAttempts, 3);
            Thread.sleep(100);
            assertEquals(0, leaseLostCount.get(),
                    "a successful renewal must reset the consecutive counter: "
                            + "transient-success-transient never escalates");
        } finally {
            SessionLockRenewal.cancelLockRenewalQuietly(task);
        }
    }

    /**
     * Classification unit pins: unwrap to root cause; never classify by the
     * outer wrapper type; SQLRecoverableException is lease-unknown even though
     * it extends SQLTransientException.
     */
    @Test
    void testCauseChainUnwrapClassification() {
        NopAiAgentException wrappedSql = new NopAiAgentException("renew failed",
                new SQLException("connection refused"));
        assertTrue(SessionLockRenewal.isLeaseStateUnknownFailure(wrappedSql),
                "root SQLException (connect failure) is lease-state-unknown");

        NopAiAgentException wrappedTransient = new NopAiAgentException("renew failed",
                new SQLTimeoutException("lock wait timeout"));
        assertFalse(SessionLockRenewal.isLeaseStateUnknownFailure(wrappedTransient),
                "root SQLTransientException (retryable) stays inside the exemption window");

        NopAiAgentException wrappedRecoverable = new NopAiAgentException("renew failed",
                new SQLRecoverableException("connection lost"));
        assertTrue(SessionLockRenewal.isLeaseStateUnknownFailure(wrappedRecoverable),
                "SQLRecoverableException (lost connection) is lease-state-unknown");

        assertFalse(SessionLockRenewal.isLeaseStateUnknownFailure(new RuntimeException("boom")),
                "non-SQL failures are counted, not immediately fatal (plan 2496 guard preserved)");

        // deeply nested cause chain is still unwrapped to the root
        RuntimeException deep = new RuntimeException("level0",
                new RuntimeException("level1", new SQLException("root db down")));
        assertTrue(SessionLockRenewal.isLeaseStateUnknownFailure(deep));
    }
}
