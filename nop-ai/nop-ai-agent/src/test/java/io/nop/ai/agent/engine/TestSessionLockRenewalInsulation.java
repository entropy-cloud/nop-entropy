package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-07/C4-007 regression (plan 2496 Phase 4, 30-pt table #23): the takeover-lock
 * renewal heartbeat must survive a Throwable (incl. Error) thrown by the lock
 * backend. A renewal loop killed by an Error escapes the JDK
 * {@code scheduleWithFixedDelay} contract — the round is cancelled, never
 * rescheduled, the lease silently expires while local execution continues and
 * another instance can preempt the session (double-execution hazard, the
 * safety-critical face of the MA-07 family).
 *
 * <p>Named regression obligation (plan 2496 Phase 4 checklist): the
 * {@code SessionLockRenewal} site must have a test that injects an Error and
 * asserts (a) the renewal chain stays alive, (b) the failure is observable,
 * (c) lease-lost abort is NOT triggered by the Error round.
 */
public class TestSessionLockRenewalInsulation {

    /**
     * Lock stub that throws a deterministic Error for the first N renew attempts,
     * then renews normally — simulating e.g. an OOM/NoClassDefFoundError burst
     * inside the DB lock path followed by recovery.
     */
    static class ErrorInjectingLock implements ISessionTakeoverLock {
        final int errorRounds;
        final AtomicInteger renewAttempts = new AtomicInteger();

        ErrorInjectingLock(int errorRounds) {
            this.errorRounds = errorRounds;
        }

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            int attempt = renewAttempts.incrementAndGet();
            if (attempt <= errorRounds) {
                throw new LinkageError("injected renewal Error, attempt=" + attempt);
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

    /**
     * Named regression: Error 注入后续期调度链存活、失败可观测、不误触发 lease-lost 中止。
     */
    @Test
    void testLockRenewalChainSurvivesInjectedErrorAndStaysObservable() throws Exception {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(30);
        ErrorInjectingLock lock = new ErrorInjectingLock(2);
        config.setSessionTakeoverLock(lock);

        AtomicInteger leaseLostCount = new AtomicInteger();
        SessionLockRenewal watched = new SessionLockRenewal(config, runningExecutions) {
            @Override
            public void handleLeaseLost(AgentSessionLifecycle.CancelHandle handle,
                                        String sessionId, String ownerId) {
                leaseLostCount.incrementAndGet();
                super.handleLeaseLost(handle, sessionId, ownerId);
            }
        };

        String sessionId = "session-error-renew";
        runningExecutions.put(sessionId, new AgentSessionLifecycle.CancelHandle(null, null));

        // The lazily created single-thread renewal executor drives the periodic chain;
        // wait for it to pass the two injected Error rounds and renew again.
        java.util.concurrent.ScheduledFuture<?> handle =
                watched.startLockRenewal(null, sessionId, "owner-1");
        try {
            // The renewal must survive TWO injected Error rounds and keep renewing:
            // pre-fix the first LinkageError killed the periodic round permanently
            // (renewAttempts would stall at 1).
            long deadline = System.currentTimeMillis() + 10_000;
            while (lock.renewAttempts.get() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(lock.renewAttempts.get() >= 3,
                    "renewal chain must survive the injected Errors and re-arm: attempts="
                            + lock.renewAttempts.get());

            // Failure must be observable — the widened guard logs the Error rounds.
            long warnCount = logAppender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("takeover lock renewal failed"))
                    .count();
            assertTrue(warnCount >= 2,
                    "each Error round must be observable via the renewal warn log: warns=" + warnCount);

            // An Error round is a transport failure, not a lease loss: the abort path
            // (session double-execution guard) must NOT fire.
            assertEquals(0, leaseLostCount.get(),
                    "Error-injected renewal failure must not be mistaken for lease loss");
        } finally {
            SessionLockRenewal.cancelLockRenewalQuietly(handle);
        }
    }
}
