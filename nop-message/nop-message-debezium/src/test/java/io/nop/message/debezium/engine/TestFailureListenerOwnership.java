/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumErrors;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-10 carrier (plan 2558 裁定 F — two-arg conditional removal, zero API change) plus the
 * MA-09 P3 four sub-face pins (same-file sweep):
 *
 * <ol>
 *   <li>conditional removal: unregistering a FOREIGN live handle is rejected typed and the
 *       foreign listener stays registered; removing from an empty slot is idempotent;</li>
 *   <li>registering a DIFFERENT live listener replaces it (semantics unchanged, R404) and
 *       emits a WARN — the generation observation face;</li>
 *   <li>the wrapper stop path carries a remove-by-name fallback (the wrapper cannot see the
 *       external owner's handle);</li>
 *   <li>concurrent register/unregister/notify races never explode (C10-002).</li>
 * </ol>
 *
 * <p>MA-09 P3 sub-faces pinned at the bottom: start-failure resets startRequested;
 * start-after-stop one-shot; worker-death idempotent close; quiesce join stuck WARN
 * refinement.
 */
@Timeout(180)
public class TestFailureListenerOwnership {

    private static final String NAME = "fl-owner";

    @AfterEach
    void cleanUp() {
        DebeziumEngineWrapper.unregisterFailureListener(NAME);
        DebeziumEngineWrapper.unregisterFailureListener("fl-other");
    }

    private static DebeziumConfig newConfig(String name) {
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

    // ---------------- ① conditional removal rejects foreign handles ----------------

    @Test
    void conditionalRemovalRejectsForeignListenerKeepsSlot() {
        List<Throwable> received = new CopyOnWriteArrayList<>();
        Consumer<Throwable> owner = received::add;
        Consumer<Throwable> foreign = t -> {
        };

        DebeziumEngineWrapper.registerFailureListener(NAME, owner);

        NopException ex = assertThrows(NopException.class,
                () -> DebeziumEngineWrapper.unregisterFailureListener(NAME, foreign),
                "MA-10: a foreign handle must not be able to clear another owner's slot");
        assertEquals(DebeziumErrors.ERR_DEBEZIUM_LISTENER_FOREIGN_HANDLE.getErrorCode(),
                ex.getErrorCode());

        // the owner's listener survives the rejected removal
        DebeziumEngineWrapper.notifyEngineFailure(NAME, new IllegalStateException("boom"));
        assertEquals(1, received.size(), "the foreign removal must not have touched the slot");

        // the owner removes its own handle; further removals are idempotent no-ops
        assertDoesNotThrow(() -> DebeziumEngineWrapper.unregisterFailureListener(NAME, owner));
        assertDoesNotThrow(() -> DebeziumEngineWrapper.unregisterFailureListener(NAME, owner),
                "removing from an empty slot is an idempotent no-op, not a rejection");
        DebeziumEngineWrapper.notifyEngineFailure(NAME, new IllegalStateException("after"));
        assertEquals(1, received.size(), "no delivery after the owner's own removal");
    }

    // ---------------- ② REPLACE of a different live listener: WARN + semantics kept ----------------

    @Test
    void replaceDifferentLiveListenerWarnsAndTakesEffect() {
        Logger wrapperLogger = (Logger) LoggerFactory.getLogger(DebeziumEngineWrapper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        wrapperLogger.addAppender(appender);
        try {
            List<Throwable> first = new CopyOnWriteArrayList<>();
            List<Throwable> second = new CopyOnWriteArrayList<>();

            DebeziumEngineWrapper.registerFailureListener(NAME, first::add);
            // different instance, same name: replace takes effect (semantics unchanged, R404)
            DebeziumEngineWrapper.registerFailureListener(NAME, second::add);

            RuntimeException failure = new RuntimeException("terminal");
            DebeziumEngineWrapper.notifyEngineFailure(NAME, failure);

            assertTrue(second.contains(failure), "the replacement listener takes effect");
            assertFalse(first.contains(failure), "the replaced listener no longer receives");

            boolean warnSeen = appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(msg -> msg.contains("failure-listener-replace") && msg.contains(NAME));
            assertTrue(warnSeen, "MA-10: replacing a DIFFERENT live listener must WARN "
                    + "(generation observation face)");
        } finally {
            wrapperLogger.detachAppender(appender);
        }
    }

    // ---------------- ③ stop 兜底 remove-by-name ----------------

    @Test
    void stopCarriesRemoveByNameFallback() throws Exception {
        List<Throwable> received = new CopyOnWriteArrayList<>();
        DebeziumEngineWrapper.registerFailureListener(NAME, received::add);

        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig(NAME));
        BlockingEngine engine = new BlockingEngine();
        wrapper.inject(engine);
        wrapper.start();
        assertTrue(engine.entered.await(10, TimeUnit.SECONDS), "engine body must enter");
        wrapper.stop();

        // the slot was cleared by the stop fallback even though nobody unregistered
        Consumer<Throwable> foreign = t -> {
        };
        assertDoesNotThrow(
                () -> DebeziumEngineWrapper.unregisterFailureListener(NAME, foreign),
                "stop 兜底 remove-by-name: the slot is empty afterwards (a foreign-handle "
                        + "removal from an empty slot is an idempotent no-op, not a rejection)");
        DebeziumEngineWrapper.notifyEngineFailure(NAME, new IllegalStateException("after-stop"));
        assertTrue(received.isEmpty(), "no delivery after the stop fallback cleared the slot");
    }

    // ---------------- ④ concurrent notify race (C10-002) ----------------

    @Test
    void concurrentRegisterUnregisterNotifyDoesNotExplode_C10_002() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            int iterations = 300;
            CountDownLatch done = new CountDownLatch(6);
            for (int t = 0; t < 6; t++) {
                final int id = t;
                pool.execute(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            Consumer<Throwable> listener = evt -> {
                            };
                            DebeziumEngineWrapper.registerFailureListener(NAME, listener);
                            // notify must always see either null or a REGISTERED listener —
                            // never a half-state
                            DebeziumEngineWrapper.notifyEngineFailure(NAME,
                                    new RuntimeException("r-" + id + "-" + i));
                            // one-arg (owner cleanup / stop fallback) and two-arg
                            // (conditional) removals interleave freely
                            DebeziumEngineWrapper.unregisterFailureListener(NAME);
                            DebeziumEngineWrapper.unregisterFailureListener(NAME, listener);
                        }
                    } catch (Throwable e) {
                        // a two-arg removal losing the race to a one-arg removal may hit the
                        // empty slot (idempotent) or the foreign slot (typed) — both are
                        // DECLARED outcomes; nothing else may escape
                        if (!(e instanceof NopException)) {
                            throw e;
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "the race must settle");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------- MA-09 P3 four sub-face pins ----------------

    /** start-failure resets startRequested: the next start() retries instead of no-op'ing. */
    @Test
    void startFailureResetsStartRequested_MA09() {
        // config without connectorType: buildProperties fails typed INSIDE start()
        DebeziumEngineWrapper wrapper = new DebeziumEngineWrapper(new DebeziumConfig(), null);
        assertThrows(NopException.class, wrapper::start, "the first start fails typed");
        // pre-fix: startRequested stayed set and the second start() silently returned
        // (「already running」gate) — post-fix it retries the build and fails typed again
        assertThrows(NopException.class, wrapper::start,
                "MA-09: a failed start must reset startRequested so the next start() retries");
    }

    /** start-after-stop one-shot contract. */
    @Test
    void startAfterStopIsOneShotNoOp_MA09() throws Exception {
        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig(NAME + "-oneshot"));
        BlockingEngine engine = new BlockingEngine();
        wrapper.inject(engine);
        wrapper.start();
        assertTrue(engine.entered.await(10, TimeUnit.SECONDS));
        wrapper.stop();
        assertTrue(wrapper.isTerminated());

        assertDoesNotThrow(wrapper::start,
                "start after a completed stop contract is a declared no-op (one-shot), "
                        + "never a fresh engine");
        assertFalse(wrapper.isRunning(), "the one-shot no-op must not resurrect the engine");
    }

    /** worker-death idempotent close: a failing close on the death path is contained. */
    @Test
    void workerDeathCloseIsIdempotentTolerant_MA09() {
        DriveProbeWrapper wrapper = new DriveProbeWrapper(newConfig(NAME + "-close"));
        assertDoesNotThrow(() -> wrapper.driveWorkerWith(new ExplodingCloseEngine()),
                "MA-09: the worker-death path must tolerate (WARN) a failing engine close — "
                        + "nobody dies because of the close attempt");
    }

    /** quiesce join stuck WARN refinement: body exited (quiesce) vs body still running. */
    @Test
    void quiesceJoinStuckWarnDistinguishesBodyExited_MA09() throws Exception {
        Logger wrapperLogger = (Logger) LoggerFactory.getLogger(DebeziumEngineWrapper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        wrapperLogger.addAppender(appender);
        StopProbeWrapper wrapper = new StopProbeWrapper(newConfig(NAME + "-quiesce"));
        ExitingEngine engine = new ExitingEngine();
        wrapper.inject(engine);
        try {
            wrapper.start();
            assertTrue(engine.entered.await(10, TimeUnit.SECONDS), "engine body must enter");

            // park the carrier with a trailing task BEFORE the body exits: after the body
            // exits the carrier join cannot complete within the budget → the WARN must
            // say「quiesce lag」, not「still running」(a parked daemon thread is the price
            // of simulating the lag; shutdown() does not interrupt it)
            ExecutorService carrier = wrapper.getEngineExecutor();
            assertNotNull(carrier);
            carrier.execute(() -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            engine.exit.countDown();

            wrapper.stop();

            boolean quiesceWarn = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(msg -> msg.contains("quiesce lag"));
            assertTrue(quiesceWarn, "MA-09: a stuck join after the body exited must be "
                    + "reported as quiesce lag (refined WARN wording)");
            assertFalse(appender.list.stream()
                            .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                            .map(ILoggingEvent::getFormattedMessage)
                            .anyMatch(msg -> msg.contains("still running")),
                    "the body-exited join must not be reported as a live-engine hazard");
        } finally {
            wrapperLogger.detachAppender(appender);
        }
    }

    static void awaitUntil(String message, java.util.function.BooleanSupplier condition)
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

    // ---------------- probe machinery (TestDebeziumEngineStopContract shape) ----------------

    static class BlockingEngine implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final AtomicBoolean ran = new AtomicBoolean(false);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);

        @Override
        public void run() {
            ran.set(true);
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            release.countDown();
        }
    }

    /** Engine that exits immediately and whose close() always fails (MA-09 tolerance face). */
    static class ExplodingCloseEngine
            implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        @Override
        public void run() {
            // natural death
        }

        @Override
        public void close() throws IOException {
            throw new IOException("injected close failure");
        }
    }

    /** Engine that exits only when released (for the quiesce join refinement). */
    static class ExitingEngine
            implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch exit = new CountDownLatch(1);

        @Override
        public void run() {
            entered.countDown();
            try {
                exit.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            exit.countDown();
        }
    }

    static class StopProbeWrapper extends DebeziumEngineWrapper {
        private volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injected;

        StopProbeWrapper(DebeziumConfig config) {
            super(config, event -> {
            }, null, null, null);
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
            closeEngineWithRetry(injected);
        }
    }

    static class DriveProbeWrapper extends DebeziumEngineWrapper {
        DriveProbeWrapper(DebeziumConfig config) {
            super(config, event -> {
            });
        }

        void driveWorkerWith(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            runEngineWorker(engine);
        }
    }
}
