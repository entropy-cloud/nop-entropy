package io.nop.ai.agent.runtime.recovery;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA A4 regression (plan 2498 Phase 4, design matrix A4 row): the recovery
 * scan is a multi-query DB sweep — {@code start()} must bind
 * {@code executeOn(GlobalExecutors.globalWorker())} on the injected carrier so
 * the scan body executes on the worker pool and never occupies timer threads.
 *
 * <p>Real-chain wiring proof: a real {@code GlobalExecutors.globalTimer()} is
 * injected as the carrier, a real (short-interval) periodic registration is
 * performed through {@code start()}, and the thread executing the scan body is
 * captured. Pre-fix the body ran directly on the timer thread
 * ({@code nop-global-timer-*}); post-fix it runs on {@code nop-global-worker-*}.
 * The DB sweep is stubbed at {@code scanOnce} (thread capture only) — the
 * scheduling/H2 data behaviour is covered by the parallel
 * {@code TestScheduledRecoveryManager*} classes.
 */
public class TestScheduledRecoveryManagerWorkerOffload_A4 {

    @Test
    void scanBodyRunsOnWorkerPoolNotTimerThread_A4() throws Exception {
        DataSource dataSource = h2DataSource();
        CountDownLatch scanned = new CountDownLatch(1);
        AtomicReference<String> scanThread = new AtomicReference<>();

        ScheduledRecoveryManager manager = new ScheduledRecoveryManager(
                dataSource, GlobalExecutors.globalTimer(), 1) {
            @Override
            public RecoveryScanResult scanOnce() {
                scanThread.set(Thread.currentThread().getName());
                scanned.countDown();
                return RecoveryScanResult.empty();
            }
        };

        manager.start();
        try {
            assertTrue(scanned.await(20, TimeUnit.SECONDS),
                    "the periodic recovery scan must fire with the 1s interval");
        } finally {
            manager.stop();
        }

        String threadName = scanThread.get();
        assertNotNull(threadName, "the scan body must have executed");
        assertTrue(threadName.startsWith("nop-global-worker"),
                "the scan body must execute on the platform worker pool, got: " + threadName);
        assertFalse(threadName.startsWith("nop-global-timer"),
                "the scan body must never execute on the timer thread, got: " + threadName);
    }

    private DataSource h2DataSource() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-recovery-offload-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }
}
