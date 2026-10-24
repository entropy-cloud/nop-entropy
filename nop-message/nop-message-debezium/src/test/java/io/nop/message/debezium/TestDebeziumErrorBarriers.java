package io.nop.message.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-009 barriers ④⑤⑦ regression (plan 2496 Phase 4, Debezium face):
 *
 * <ul>
 *   <li>④ the engine worker guard must cover Throwable — an Error escaping
 *       {@code engine.run()} must still fire {@code notifyEngineFailure} so the registered
 *       FAILURE_LISTENERS learn the CDC stream died (pre-fix the Exception-only catch was
 *       bypassed and the nop-stream side task waited on the latch forever);</li>
 *   <li>⑤ the per-subscriber dispatch guard must cover Throwable — one failing subscriber
 *       must not kill event delivery to the remaining subscribers;</li>
 *   <li>⑦ the failure-listener guard must cover Throwable — a listener that itself throws
 *       an Error must not propagate out of {@code notifyEngineFailure}.</li>
 * </ul>
 */
public class TestDebeziumErrorBarriers {

    /** Fake engine whose run() throws a deterministic Error. */
    static class ThrowingEngine implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final AtomicBoolean ran = new AtomicBoolean(false);

        @Override
        public void run() {
            ran.set(true);
            throw new LinkageError("injected engine Error");
        }

        @Override
        public void close() {
        }
    }

    /** Testable wrapper exposing the protected worker body with an injected engine ref. */
    static class TestableEngineWrapper extends DebeziumEngineWrapper {
        final AtomicBoolean runningFlag;

        TestableEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> consumer,
                              AtomicBoolean runningFlag) {
            super(config, consumer);
            this.runningFlag = runningFlag;
        }

        void driveWorkerWith(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            runEngineWorker(engine);
        }
    }

    private final List<Throwable> reported = new CopyOnWriteArrayList<>();
    private final CountDownLatch failureReported = new CountDownLatch(1);
    private final AtomicBoolean runningFlag = new AtomicBoolean(false);
    private TestableEngineWrapper wrapper;
    private static final String CONNECTOR = "test-error-barrier-connector";

    @BeforeEach
    void setUp() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        wrapper = new TestableEngineWrapper(config, event -> {
        }, runningFlag);
        DebeziumEngineWrapper.registerFailureListener(CONNECTOR, error -> {
            reported.add(error);
            failureReported.countDown();
        });
    }

    @AfterEach
    void tearDown() {
        DebeziumEngineWrapper.unregisterFailureListener(CONNECTOR);
    }

    @Test
    void engineWorkerErrorStillNotifiesFailureListeners() throws Exception {
        ThrowingEngine engine = new ThrowingEngine();

        // Pre-fix the LinkageError escaped the Exception-only worker catch and
        // notifyEngineFailure never fired (FAILURE_LISTENERS silent — the terminal-death
        // invisibility face of C3-009). Post-fix the notification is mandatory.
        wrapper.driveWorkerWith(engine);

        assertTrue(engine.ran.get(), "the injected engine run must actually have been reached");
        assertTrue(failureReported.await(5, TimeUnit.SECONDS),
                "a terminal engine death on an Error must reach the FAILURE_LISTENERS");
        assertTrue(reported.get(0) instanceof LinkageError,
                "the reported failure must be the injected Error itself");
        assertEquals(false, runningFlag.get(),
                "the running flag must be reset by the worker finally block");
    }

    @Test
    void listenerThrowingErrorDoesNotPropagate() {
        DebeziumEngineWrapper.registerFailureListener("throwing-listener", error -> {
            throw new LinkageError("injected listener Error");
        });
        try {
            // Pre-fix the listener's Error escaped notifyEngineFailure (Exception-only
            // guard) into the engine worker thread.
            DebeziumEngineWrapper.notifyEngineFailure("throwing-listener",
                    new IllegalStateException("engine-boom"));
        } finally {
            DebeziumEngineWrapper.unregisterFailureListener("throwing-listener");
        }
    }

    @Test
    void dispatchEventErrorIsContainedPerSubscriber() throws Exception {
        DebeziumMessageSource source = new DebeziumMessageSource(new DebeziumConfig());
        AtomicBoolean secondReceived = new AtomicBoolean(false);

        // Mark the source started reflectively — otherwise the first subscribe() would
        // build and run the real embedded engine (no broker in unit scope). With the
        // flag set, subscribe() exercises the real registration path without the engine.
        Field startedField = DebeziumMessageSource.class.getDeclaredField("started");
        startedField.setAccessible(true);
        startedField.setBoolean(source, true);

        source.subscribe(event -> {
            throw new LinkageError("injected subscriber Error");
        });
        source.subscribe(event -> secondReceived.set(true));

        ChangeEvent event = new ChangeEvent(new ChangeEventMetadata(
                "test-connector", "test-server", "db", "schema", "t", "data"),
                "c", null, Map.of("id", 1), Map.of("id", 1), System.currentTimeMillis());

        // Pre-fix the first subscriber's LinkageError escaped the Exception-only guard,
        // aborted the dispatch loop and killed engine.run() upstream.
        source.dispatchEvent(event);

        assertTrue(secondReceived.get(),
                "the remaining subscriber must still receive the event after one subscriber's Error");
    }
}
