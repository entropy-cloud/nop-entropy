/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2-C3-014 residual five (plan 2503 Phase 3): the stop contract upgraded from「已发出」to
 * 「已终止」on the real wrapper.
 *
 * <ul>
 *   <li>① STARTING_TASKS window: {@code engine.close()} rejected with IllegalStateException
 *       is retried within a bounded budget until the engine terminates; exhaustion fails
 *       typed and leaves stop retryable (pre-fix: single attempt swallowed, one-shot latch
 *       engaged, permanent ghost);</li>
 *   <li>③ C4-006: a worker lambda entering after the stop request must not drive the
 *       engine;</li>
 *   <li>④ C4-005: a stuck engine is escalated to carrier {@code shutdownNow()} and the
 *       residual ghost is declared by explicit ERROR;</li>
 *   <li>⑤ the termination latch is the queryable terminality signal — the source-level stop
 *       chain (cancel → source.stop → wrapper.stop) ends with the engine actually
 *       terminated and the signal observable.</li>
 * </ul>
 *
 * <p>Tests drive {@code runEngineWorker} with injected fake engines on the REAL dedicated
 * carrier (the {@code TestDebeziumEngineDedicatedCarrier_C4_004} probe shape); the 2498
 * carrier contract is a zero-regression pin and is not modified here.
 */
@Timeout(120)
public class TestDebeziumEngineStopContract_C3_014 {

    static DebeziumConfig newConfig(String name) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(name);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");
        config.setDatabasePassword("secret");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("_tmp/cdc-" + name + "/schema-history.dat");
        return config;
    }

    /** Engine whose run() blocks until close() releases it (a live, closeable engine). */
    static class BlockingEngine implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final AtomicBoolean ran = new AtomicBoolean(false);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void run() {
            ran.set(true);
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() throws IOException {
            release.countDown();
        }
    }

    /**
     * STARTING_TASKS-window engine: the first close() is rejected with the dependency's
     * IllegalStateException; the second one succeeds and releases the body.
     */
    static class StartingTasksEngine extends BlockingEngine {
        final AtomicInteger closeAttempts = new AtomicInteger();

        @Override
        public void close() throws IOException {
            if (closeAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException(
                        "Cannot request task start-up and shutdown at the same time (STARTING_TASKS)");
            }
            release.countDown();
        }
    }

    /** Stuck engine: close() succeeds but the body ignores it and survives interrupts. */
    static class StuckEngine implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final AtomicBoolean interruptedFlag = new AtomicBoolean(false);
        final CountDownLatch entered = new CountDownLatch(1);

        @Override
        public void run() {
            entered.countDown();
            while (true) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interruptedFlag.set(true);
                }
            }
        }

        @Override
        public void close() {
            // cooperative close is a no-op for this stuck engine
        }
    }

    /** Always-ISE engine: the STARTING_TASKS window never clears within any budget. */
    static class AlwaysRejectingEngine implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final AtomicInteger closeAttempts = new AtomicInteger();

        @Override
        public void run() {
            // block until (never) released
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            closeAttempts.incrementAndGet();
            throw new IllegalStateException("STARTING_TASKS never clears");
        }
    }

    /** Probe wrapper: swaps the injected engine into the real worker body on the real carrier. */
    static class StopProbeWrapper extends DebeziumEngineWrapper {
        private volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injected;

        StopProbeWrapper(DebeziumConfig config) {
            this(config, event -> {
            }, null, null);
        }

        StopProbeWrapper(DebeziumConfig config, Consumer<ChangeEvent> consumer,
                         Consumer<ConsumeFailure> poison, Consumer<Throwable> terminal) {
            super(config, consumer, null, poison, terminal);
        }

        void inject(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            this.injected = engine;
        }

        @Override
        protected void runEngineWorker(
                DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
            super.runEngineWorker(injected);
        }

        @Override
        void closeEngineWithRetry() {
            // the probe's lifecycle engine is the injected fake (the real built engine is
            // never run); the production retry logic below is what this exercises
            closeEngineWithRetry(injected);
        }
    }

    /** Direct-drive seam for the worker-entry guard (TestDebeziumErrorBarriers shape). */
    static class DriveProbeWrapper extends DebeziumEngineWrapper {
        DriveProbeWrapper(DebeziumConfig config) {
            super(config, event -> {
            });
        }

        void driveWorkerWith(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            runEngineWorker(engine);
        }
    }

    static void awaitUntil(String message, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        assertTrue(false, message);
    }

    // ---------------- ①: STARTING_TASKS window — bounded close retry ----------------

    @Test
    void startingTasksWindowStopRetriesCloseUntilTerminated_C3_014() throws Exception {
        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig("c3-014-ise"));
        StartingTasksEngine engine = new StartingTasksEngine();
        wrapper.inject(engine);
        try {
            wrapper.start();
            awaitUntil("engine body must be running before stop",
                    wrapper::isRunning);

            // pre-fix: the single close() threw ISE, was swallowed, and the one-shot latch
            // engaged — permanent ghost. Post-fix: bounded retry until termination.
            wrapper.stop();

            assertEquals(2, engine.closeAttempts.get(),
                    "close must be retried within the STARTING_TASKS budget (pre-fix: 1 attempt, swallowed)");
            assertTrue(wrapper.isTerminated(),
                    "the stop contract ends with the engine actually terminated (⑤)");
            assertFalse(wrapper.isRunning());
        } finally {
            wrapper.stop();
        }
    }

    @Test
    void startTasksCloseFailureIsTypedAndStopStaysRetryable_C3_014() {
        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig("c3-014-typed"));
        AlwaysRejectingEngine engine = new AlwaysRejectingEngine();
        wrapper.inject(engine);
        // shrink the latch/escalation budgets; the carrier budget (2498 constant) still
        // applies — this test calls the close unit directly to stay fast
        wrapper.terminationWaitMillis = 50;
        wrapper.escalationWaitMillis = 50;

        NopException ex = assertThrows(NopException.class, () -> wrapper.closeEngineWithRetry(engine),
                "STARTING_TASKS budget exhaustion must fail typed (rule 24: never swallowed)");
        assertTrue(engine.closeAttempts.get() >= 2,
                "the retry budget must produce multiple close attempts");
        assertEquals("engine.close() rejected with IllegalStateException past the STARTING_TASKS "
                + "retry budget; the engine may be a ghost", ex.getParam("detail"));
    }

    // ---------------- ③: worker-entry guard (C4-006) ----------------

    @Test
    void workerEntryGuardSkipsEngineAfterStop_C3_014() {
        DriveProbeWrapper wrapper = new DriveProbeWrapper(newConfig("c3-014-guard"));
        // arms the guard (gated stop: never started)
        wrapper.stop();

        BlockingEngine engine = new BlockingEngine();
        wrapper.driveWorkerWith(engine);

        assertFalse(engine.ran.get(),
                "a worker entering after the stop request must not drive the engine (C4-006; "
                        + "pre-fix the queued worker opened a fresh engine after stop)");
        assertFalse(wrapper.isRunning(), "the skipped worker is not a running engine");
    }

    // ---------------- ④: stuck engine escalation + ghost declaration ----------------

    @Test
    void stuckEngineEscalatesToShutdownNowAndDeclaresGhost_C3_014() throws Exception {
        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig("c3-014-stuck"));
        StuckEngine engine = new StuckEngine();
        wrapper.inject(engine);
        wrapper.terminationWaitMillis = 200;
        wrapper.escalationWaitMillis = 200;
        try {
            wrapper.start();
            assertTrue(engine.entered.await(10, TimeUnit.SECONDS),
                    "the stuck engine body must enter");

            wrapper.stop();

            assertTrue(engine.interruptedFlag.get(),
                    "the stuck engine must have been interrupted by the carrier shutdownNow "
                            + "escalation (④; pre-fix: no escalation)");
            assertFalse(wrapper.isTerminated(),
                    "the residual ghost is declared: the engine never terminated");
        } finally {
            wrapper.stop();
        }
    }

    // ---------------- ⑤: stop chain entry-to-exit (source → wrapper → terminated) ----------------

    @Test
    void stopChainEndsWithEngineTerminatedAndSignalQueryable_C3_014() throws Exception {
        DebeziumConfig config = newConfig("c3-014-chain");
        ChainSource source = new ChainSource(config);
        BlockingEngine engine = new BlockingEngine();
        source.injectedEngine = engine;

        ICancellable subscription = source.subscribe(e -> {
        });
        awaitUntil("the engine body must run after subscribe", () -> engine.ran.get());
        assertTrue(source.isStarted());

        // real stop chain: cancel → (empty map) → source.stop() → wrapper.stop()
        subscription.cancel("chain-stop");

        assertFalse(source.isStarted());
        assertTrue(source.isEngineTerminated(),
                "the stop chain must end with the engine TERMINATED and the signal queryable "
                        + "through the source (⑤; the four nop-stream stop faces poll this)");
        assertTrue(wrapperOf(source).isTerminated());
    }

    static class ChainSource extends DebeziumMessageSource {
        final DebeziumConfig configRef;
        volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injectedEngine;
        DebeziumEngineWrapper created;

        ChainSource(DebeziumConfig config) {
            super(config, null);
            this.configRef = config;
        }

        @Override
        protected DebeziumEngineWrapper createEngineWrapper() {
            // production wiring verbatim (rule 23): the source's own callbacks
            StopProbeWrapper wrapper = new StopProbeWrapper(configRef, this::dispatchEvent,
                    poisonEventReporter(), terminalDeathListener());
            wrapper.inject(injectedEngine);
            this.created = wrapper;
            return wrapper;
        }
    }

    private static DebeziumEngineWrapper wrapperOf(ChainSource source) {
        return source.created;
    }

}
