package io.nop.ai.agent.runtime.recovery;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.lang.IDestroyable;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-07/C3-020 regression (plan 2496 Phase 4, 30-pt table #24): the periodic
 * recovery scan must survive a Throwable (incl. Error) thrown by the scan body.
 * Pre-fix {@code scanOnceSafe} caught only {@code RuntimeException} — an Error
 * escaped the task body, the JDK {@code scheduleWithFixedDelay} round was
 * cancelled permanently and recovery went silently blind. Post-fix the guard
 * catches Throwable, keeps the chain re-armable and keeps the failure observable.
 *
 * <p>CRA A4 (plan 2498 Phase 4): {@code start()} binds
 * {@code executeOn(globalWorker())} — the recorded trigger dispatches the scan
 * round onto the real {@code nop-global-worker} pool, so round runs are
 * awaited bounded (the trigger returns before the body completes). Insulation
 * semantics are unchanged: the Throwable guard in {@code scanOnceSafe} plus
 * the R1-01 chain guard keep the chain alive and observable.
 */
public class TestScheduledRecoveryManagerInsulation {

    /** Recording stub: captures the scheduled periodic command for manual round runs. */
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicReference<Runnable> lastCommand = new AtomicReference<>();
        // CRA A4: one-shot trigger recording (the executeOn-bound registration path)
        final AtomicReference<Runnable> lastTrigger = new AtomicReference<>();
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            lastCommand.set(command);
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            lastTrigger.set(() -> {
                try {
                    callable.call();
                } catch (Exception e) {
                    throw io.nop.api.core.exceptions.NopException.adapt(e);
                }
            });
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
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
            return "test-recovery-recording-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
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

    /**
     * DataSource stub: the constructor's schema init gets a real H2 connection; every
     * later getConnection throws a deterministic Error — injecting the Error into the
     * first step of every scan round ({@code deleteStaleLocks}).
     */
    static final class ErrorAfterInitDataSource extends SimpleDataSource {
        private final SimpleDataSource delegate;
        private final AtomicBoolean errorArmed = new AtomicBoolean(false);

        ErrorAfterInitDataSource(SimpleDataSource delegate) {
            this.delegate = delegate;
        }

        void armError() {
            errorArmed.set(true);
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (errorArmed.get()) {
                throw new LinkageError("injected scan Error");
            }
            return delegate.getConnection();
        }
    }

    private ErrorAfterInitDataSource dataSource;
    private RecordingScheduler scheduler;
    private ScheduledRecoveryManager manager;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger managerLogger;

    @BeforeEach
    void setUp() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-recovery-insulation-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = new ErrorAfterInitDataSource(ds);
        scheduler = new RecordingScheduler();
        // Constructor runs initLockSchema() through the unwrapped leg of the stub.
        manager = new ScheduledRecoveryManager(dataSource, scheduler, 60);
        managerLogger = (Logger) LoggerFactory.getLogger(ScheduledRecoveryManager.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        managerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null && managerLogger != null) {
            managerLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        manager = null;
        dataSource = null;
    }

    private void awaitWarns(long expectedMinimum, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (warnCount() >= expectedMinimum) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(warnCount() >= expectedMinimum, message);
    }

    private long warnCount() {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("periodic scan failed"))
                .count();
    }

    @Test
    void testScanChainSurvivesInjectedErrorAndStaysObservable() {
        manager.start();
        Runnable round = scheduler.lastTrigger.get();
        assertTrue(round != null, "start must schedule the periodic scan (executeOn-bound trigger)");

        dataSource.armError();

        // Round 1 with injected Error: pre-insulation-fix the LinkageError propagated
        // out of the task body (killing the periodic round); post-fix it is caught in
        // the scan guard + observed. The round is dispatched on the worker pool, so
        // the warn is awaited bounded.
        assertDoesNotThrow(round::run, "the scan round trigger must not let an Error escape");
        awaitWarns(1, "the failed scan round must be observable via the warn log");

        // Round 2: the chain is still re-armable — a second failing round is also
        // contained (pre-fix the chain would already be dead after round 1).
        assertDoesNotThrow(round::run, "the periodic chain must remain alive after an Error round");
        awaitWarns(2, "each failing round must be observable");
    }
}
