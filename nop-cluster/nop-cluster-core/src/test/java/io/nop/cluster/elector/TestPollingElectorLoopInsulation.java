package io.nop.cluster.elector;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-004 + CL-011/CL-014 regression (plan 2496 Phase 4, 30-pt table #14 explicit
 * admission — the platform insulation layer has zero automatic coverage over the one-shot
 * self-continuing election chain, so the site-side fix lives in
 * {@link AbstractPollingLeaderElector}).
 *
 * <ul>
 *   <li>Error escaping {@code checkElection()} must not kill the loop silently: the guard
 *       reports it with the business identifier ({@code nop.err.cluster.leader-elector.loop-broken}
 *       + elector class + clusterId, the CL-014 obligation) and re-arms (CL-011 backoff);</li>
 *   <li>execution-leg rejection (the audited kill path: shared worker pool Abort) completes
 *       the round future exceptionally — the centralized {@code whenComplete} observation
 *       must alarm with the business identifier and re-arm;</li>
 *   <li>the subclass tail re-arm contract must keep producing exactly one chain (no
 *       double-scheduling from the base-class {@code finally} guard);</li>
 *   <li>the pending round future is retained ({@code getPendingCheckForObservation()}),
 *       no longer discarded at the schedule site.</li>
 * </ul>
 */
@Timeout(30)
public class TestPollingElectorLoopInsulation {

    /** Production-shaped elector: re-arms from the checkElection tail like SysDao/Jdbc. */
    static class TailRearmElector extends AbstractPollingLeaderElector {
        final AtomicInteger rounds = new AtomicInteger();
        final AtomicInteger errorRounds = new AtomicInteger();
        volatile int failFirstNRounds = 0;

        @Override
        public void restartElection() {
            onRestartElection();
        }

        @Override
        protected Void checkElection() {
            // Tail placement mirrors SysDaoLeaderElector/JdbcLeaderElector: the re-arm is
            // OUTSIDE the body's try/catch, so an Error escaping the body skips the tail
            // (the pre-fix silent kill path the base guard must close).
            if (rounds.incrementAndGet() <= failFirstNRounds) {
                errorRounds.incrementAndGet();
                throw new LinkageError("injected election Error");
            }
            if (!isStopping()) {
                scheduleCheck();
            }
            return null;
        }
    }

    private static final AtomicInteger TIMER_SEQ = new AtomicInteger();

    private DefaultScheduledExecutor timer;
    private TailRearmElector elector;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger electorLogger;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer(
                "test-elector-loop-timer-" + TIMER_SEQ.incrementAndGet());
        elector = new TailRearmElector();
        elector.setScheduledExecutor(timer);
        elector.setCheckIntervalMs(50);
        electorLogger = (Logger) LoggerFactory.getLogger(AbstractPollingLeaderElector.class);
        logAppender = new ListAppender<>();
        // The round-future observation callback runs on the common pool (async by design),
        // so alarms can be appended while a test thread samples brokenLogs(). Logback's
        // ListAppender uses a plain ArrayList — swap in a concurrent list to keep reads
        // race-free instead of intermittently throwing ConcurrentModificationException.
        logAppender.list = new CopyOnWriteArrayList<>();
        logAppender.start();
        electorLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (elector != null) {
            elector.stop();
        }
        if (timer != null) {
            timer.destroy();
        }
        if (logAppender != null && electorLogger != null) {
            electorLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private long brokenLogs() {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("nop.err.cluster.leader-elector.loop-broken"))
                .count();
    }

    @Test
    void testErrorEscapeReframesLoopAndReportsWithBusinessIdentifier() throws Exception {
        elector.failFirstNRounds = 2;
        elector.start();

        assertTrue(elector.getPendingCheckForObservation() != null,
                "the pending round future must be retained, not discarded at the schedule site");

        long deadline = System.currentTimeMillis() + 15_000;
        while (elector.rounds.get() < 4 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        // CL-011: the loop survived two injected Errors and keeps electing — pre-fix the
        // first LinkageError escaped the subclass catch(Exception) and the chain died.
        assertTrue(elector.rounds.get() >= 4,
                "the election loop must re-arm after injected Errors: rounds=" + elector.rounds.get());
        assertTrue(elector.errorRounds.get() >= 2);

        // CL-014: the broken-link signal carries the business identifier.
        assertTrue(brokenLogs() >= 2,
                "each Error escape must be reported via nop.err.cluster.leader-elector.loop-broken");
        boolean hasBusinessId = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("elector=TailRearmElector")
                        && e.getFormattedMessage().contains("clusterId="));
        assertTrue(hasBusinessId, "the broken-link report must carry elector + clusterId");
    }

    @Test
    void testExecutionLegRejectionAlarmsAndRearms() throws Exception {
        // Execution-leg rejection stub: schedule() itself succeeds (timer leg), but every
        // round future completes exceptionally — the audited C4-004 kill path (shared
        // worker pool Abort), which pre-fix left the loop dead with only the executor's
        // generic error log.
        RecordingRejectionScheduler rejector = new RecordingRejectionScheduler();
        TailRearmElector rejected = new TailRearmElector();
        rejected.setScheduledExecutor(rejector);
        rejected.setCheckIntervalMs(50);
        try {
            rejected.start();

            // whenComplete alarm + backoff re-arm: after the first rejected round a new
            // round must be scheduled (delay 1s), and the business-identified alarm fired.
            long deadline = System.currentTimeMillis() + 10_000;
            while (rejector.scheduleCount.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(rejector.scheduleCount.get() >= 2,
                    "a rejected round must trigger the backoff re-arm: schedules="
                            + rejector.scheduleCount.get());
            assertTrue(brokenLogs() >= 1,
                    "the rejection must be reported via nop.err.cluster.leader-elector.loop-broken");
        } finally {
            rejected.stop();
            rejector.destroy();
        }
    }

    @Test
    void testSubclassTailRearmKeepsSingleChain() throws Exception {
        elector.start();

        // Sample chain growth twice: with the tail re-arm contract the guarded finally
        // must NOT add a second parallel chain (schedules ≈ rounds, never 2x).
        long deadline = System.currentTimeMillis() + 5_000;
        while (elector.rounds.get() < 5 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        int rounds1 = elector.rounds.get();
        Thread.sleep(300);
        int rounds2 = elector.rounds.get();
        assertTrue(rounds2 > rounds1, "the loop must keep polling at the configured cadence");
        // Each round schedules exactly one successor; the initial kick adds one.
        assertTrue(rounds2 <= 40, "round growth must stay on the single chain (no fork): "
                + "rounds=" + rounds2);
    }

    /** Schedule stub whose every round future completes exceptionally (execution leg). */
    static class RecordingRejectionScheduler implements io.nop.commons.concurrent.executor.IScheduledExecutor,
            io.nop.commons.lang.IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger();
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public java.util.concurrent.Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                                     long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            CompletableFuture<V> future = new CompletableFuture<>();
            future.completeExceptionally(new RejectedExecutionException("injected execution-leg rejection"));
            return future;
        }

        @Override
        public java.util.concurrent.Future<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                                  long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }

        @Override
        public boolean isDestroyed() {
            return destroyed.get();
        }

        @Override
        public String getName() {
            return "test-rejection-scheduler";
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
