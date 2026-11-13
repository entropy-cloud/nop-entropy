package io.nop.message.debezium;

import io.debezium.engine.DebeziumEngine;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C10-001 four scenarios + C6-002 (MA-05 三面归一) + C6-006 + C8-004 dispatch face
 * (plan 2503 Phase 2):
 *
 * <ol>
 *   <li>ghost subscription must not survive a failed engine start, and a recovered start
 *       must not double-deliver to the ghost (C6-002 / C3-003 / C1-006);</li>
 *   <li>poison-event two polarities — asserted at wrapper level in
 *       {@code TestDebeziumEngineWrapperPoisonEndpoint_C3_002_C8_004} and at the dispatch
 *       level here (delivery failures enter the shared poison-event endpoint);</li>
 *   <li>concurrent subscribe×stop interleave adjudicated deterministically: after stop(),
 *       subscribe is rejected typed (C3-003 interleaving B);</li>
 *   <li>same-action double subscribe: cancelling the STALE handle must not delete the live
 *       sibling registration nor stop the engine (C6-006, WI23 identity semantics).</li>
 * </ol>
 *
 * <p>Baseline-compilable tests (startFailureLeavesNoGhostSubscription_C6_002,
 * cancelStaleHandleDoesNotStopLiveSiblingOrEngine_C6_006,
 * subscribeAfterStopRejectedTyped_C3_003) use only pre-existing API surface, so their RED
 * evidence is the true live baseline. The post-fix-only tests assert against seams introduced
 * by the fix itself; their red argument is by mutation/revert of the production hunks
 * (recorded in the plan execution log, per the shared-fixture red-argument rule of the
 * Phase 2 exit criteria).
 */
public class TestDebeziumMessageSource {

    /** Full mysql-ish config: buildable by the embedded engine without connecting (C4-004 shape). */
    static DebeziumConfig buildableConfig(String name) {
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

    /** Config that fails fast inside buildProperties (no connector type) — no engine is built. */
    static DebeziumConfig failingConfig(String name) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(name);
        return config;
    }

    static ChangeEvent event(String table) {
        return new ChangeEvent(
                new ChangeEventMetadata("mysql", "srv", "db", null, table, "data"),
                "c", null, Map.of("id", 1), Map.of("id", 1), 0L);
    }

    static void setStarted(DebeziumMessageSource source, boolean value) {
        try {
            Field started = DebeziumMessageSource.class.getDeclaredField("started");
            started.setAccessible(true);
            started.setBoolean(source, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private final List<AutoCloseable> cleanups = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        for (AutoCloseable cleanup : cleanups) {
            try {
                cleanup.close();
            } catch (Exception e) {
                // test teardown only
            }
        }
    }

    // ---------------- C6-002: ghost subscription on failed start ----------------

    @Test
    void startFailureLeavesNoGhostSubscription_C6_002() {
        DebeziumMessageSource source = new DebeziumMessageSource(failingConfig("ghost-src"));
        List<ChangeEvent> received = new CopyOnWriteArrayList<>();

        // buildProperties fails typed (no connector type) — no real engine touched
        assertThrows(NopException.class, () -> source.subscribe(received::add),
                "the start failure must propagate to the subscribe caller");
        assertEquals(0, source.getSubscriptionCount(),
                "pre-fix the registration survived the failed start as a ghost the caller "
                        + "can never cancel (C6-002)");
    }

    // ---------------- C6-006: identity deregistration ----------------

    @Test
    void cancelStaleHandleDoesNotStopLiveSiblingOrEngine_C6_006() {
        DebeziumMessageSource source = new DebeziumMessageSource(buildableConfig("c6-006-src"));
        setStarted(source, true);
        List<ChangeEvent> received = new CopyOnWriteArrayList<>();

        // the SAME action reference — the C6-006 defect shape (baseline put-overwrite)
        Consumer<ChangeEvent> action = received::add;
        ICancellable first = source.subscribe(action);
        ICancellable second = source.subscribe(action);

        // cancelling the STALE handle: pre-fix it deleted the CURRENT registration (action
        // key) and stopped the live engine under it
        first.cancel("stale-handle");

        assertEquals(1, source.getSubscriptionCount(),
                "the live sibling registration must survive");
        assertTrue(source.isStarted(),
                "cancelling the stale handle must not stop the active engine (pre-fix this was false)");
        source.dispatchEvent(event("t"));
        assertEquals(1, received.size(),
                "the surviving sibling subscription receives the dispatch (identity semantics: "
                        + "each subscription is delivered once)");
    }

    // ---------------- C3-003 interleaving B: subscribe after stop ----------------

    @Test
    void subscribeAfterStopRejectedTyped_C3_003() {
        DebeziumMessageSource source = new DebeziumMessageSource(failingConfig("stopped-src"));
        setStarted(source, true);
        source.stop();

        NopException ex = assertThrows(NopException.class, () -> source.subscribe(e -> {
        }), "pre-fix subscribe resurrected a fresh engine after stop (C3-003 interleaving B)");
        assertEquals("message source already stopped: subscribe is rejected; create a new source",
                ex.getParam("detail"),
                "the rejection must be the stop-rejection, not an incidental engine failure");
        assertEquals(0, source.getSubscriptionCount(),
                "no registration may survive the typed rejection");
    }

    // ---------------- C8-004: dispatch failure enters the poison endpoint ----------------

    @Test
    void dispatchFailureEntersPoisonEndpointPerSubscription_C8_004() {
        DebeziumMessageSource source = new DebeziumMessageSource(buildableConfig("c8-004-src"));
        setStarted(source, true);
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        List<ChangeEvent> okReceived = new CopyOnWriteArrayList<>();
        AtomicInteger deliveries = new AtomicInteger();

        // failing subscriber: fails on deliveries 1 and 3, succeeds on 2 (attempt reset proof)
        source.subscribe(e -> {
            if (deliveries.incrementAndGet() != 2) {
                throw new IllegalStateException("handler-boom-" + deliveries.get());
            }
        }, failures::add);
        source.subscribe(okReceived::add, null);

        ChangeEvent evt = event("t");
        source.dispatchEvent(evt); // failure 1
        source.dispatchEvent(evt); // success (attempt reset)
        source.dispatchEvent(evt); // failure 2 — attempt must be 1 again, not 2

        assertEquals(2, failures.size(),
                "delivery failures must enter the poison-event endpoint (pre-fix: silent "
                        + "LOG-and-return while the engine advanced the offset)");
        ConsumeFailure failure = failures.get(0);
        assertEquals("srv.t", failure.getTopic(), "topic = event destination (D12 field mapping)");
        assertEquals("mysql@t", failure.getLocation(),
                "location = connector + source offset key (D12 field mapping)");
        assertEquals(1, failure.getAttempt(),
                "attempt resets to 1 after an intervening success (D12 attempt semantics)");
        assertEquals(1, failures.get(1).getAttempt());
        assertSame(evt, failure.getMessage());
        assertTrue(failure.getThrowable() instanceof IllegalStateException);
        assertEquals(3, okReceived.size(),
                "all three dispatches reach the sibling subscriber — failures are contained "
                        + "per subscription");
        assertEquals(2, source.getPoisonEventCount(),
                "the poison-event counter counts every endpoint drop");
    }

    @Test
    void throwingFailureHandlerDoesNotKillDispatchLoop_C8_004() {
        DebeziumMessageSource source = new DebeziumMessageSource(buildableConfig("cb-throw-src"));
        setStarted(source, true);
        List<ChangeEvent> received = new CopyOnWriteArrayList<>();

        source.subscribe(e -> {
            throw new IllegalStateException("handler-boom");
        }, failure -> {
            throw new LinkageError("injected callback Error");
        });
        source.subscribe(received::add);

        assertDoesNotThrow(() -> source.dispatchEvent(event("t")),
                "a throwing failure handler is guarded (WI16 dispatch discipline)");
        assertEquals(1, received.size(), "the sibling dispatch loop must survive");
    }

    // ---------------- C6-002: no ghost double-delivery after recovered start ----------------

    /**
     * Start fails once, then recovers (emulated healthy engine — no real connector). The
     * first caller's handler must NEVER receive events: its subscribe threw and it can hold
     * no cancellable handle. Pre-fix the ghost registration stayed in the map and the
     * recovered engine double-delivered to it.
     */
    @Test
    void noGhostDoubleDeliveryAfterRecoveredStart_C6_002() throws Exception {
        RecoveringSource source = new RecoveringSource(buildableConfig("recovering-src"));
        List<ChangeEvent> ghostReceived = new CopyOnWriteArrayList<>();
        List<ChangeEvent> healthyReceived = new CopyOnWriteArrayList<>();

        assertThrows(NopException.class, () -> source.subscribe(ghostReceived::add));
        source.subscribe(healthyReceived::add); // recovery path: start succeeds now

        source.dispatchEvent(event("t"));

        assertEquals(1, healthyReceived.size(), "the recovered subscription receives the event");
        assertEquals(0, ghostReceived.size(),
                "the failed subscribe must not leave a ghost receiver (pre-fix double delivery)");
    }

    /** Start fails on the first attempt, then emulates a healthy engine via reflection. */
    static class RecoveringSource extends DebeziumMessageSource {
        int attempts;

        RecoveringSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        protected synchronized void startEngineIfNeeded() {
            if (attempts++ == 0) {
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)
                        .param("name", getConfig().getName());
            }
            setStarted(this, true);
        }
    }

    // ---------------- MA-02 ③: wiring — engine death flips started, listener 必达 ----------------

    @Test
    void engineTerminalDeathFlipsStartedAndReachesListener_C3_002() throws Exception {
        String connector = "wiring-src";
        DebeziumConfig config = buildableConfig(connector);
        List<Throwable> staticListenerFailures = new CopyOnWriteArrayList<>();
        DebeziumEngineWrapper.registerFailureListener(connector, staticListenerFailures::add);
        cleanups.add(() -> DebeziumEngineWrapper.unregisterFailureListener(connector));

        WiringSource source = new WiringSource(config);
        cleanups.add(source::stop);

        // real chain (rule 23): subscribe → startEngineIfNeeded → createEngineWrapper →
        // wrapper.start() → dedicated carrier → worker death injection
        source.subscribe(e -> {
        });

        // started 翻转发生在专用 carrier 线程上——事件驱动等待，不读竞态快照
        //（合并修订 2026-11-13：模块全量跑下后段测试的 GC/JIT 压力曾使立即断言假红）
        awaitUntil("engine start must flip the started flag", source::isStarted);
        assertNotNull(source.created, "the wrapper must come from createEngineWrapper");
        assertTrue(source.created.throwingEngine.ran.get(),
                "the injected engine body must actually have run on the carrier");

        awaitUntil("a terminal engine death must flip source.isStarted() (MA-02 ③: no lying)",
                () -> !source.isStarted());
        assertEquals(1, staticListenerFailures.size(),
                "the R5-CON-03 static failure listener must still be reached (protocol intact)");
        assertTrue(staticListenerFailures.get(0) instanceof LinkageError);
    }

    static void awaitUntil(String message, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        assertTrue(false, message);
    }

    /** Real wrapper whose worker body runs an injected throwing engine instead of the built one. */
    static class ProbeWrapper extends DebeziumEngineWrapper {
        final ThrowingEngine throwingEngine = new ThrowingEngine();

        ProbeWrapper(DebeziumConfig config, Consumer<ChangeEvent> consumer,
                     OffsetBackingStore store, Consumer<ConsumeFailure> poison,
                     Consumer<Throwable> terminal) {
            super(config, consumer, store, poison, terminal);
        }

        @Override
        protected void runEngineWorker(
                DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
            // the real built engine is never run (no connection attempt); the worker death
            // path under test is the production runEngineWorker body
            super.runEngineWorker(throwingEngine);
        }
    }

    /** Source whose wrapper is a probe, wired with the PRODUCTION callbacks verbatim. */
    static class WiringSource extends DebeziumMessageSource {
        ProbeWrapper created;

        WiringSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        protected DebeziumEngineWrapper createEngineWrapper() {
            created = new ProbeWrapper(getConfig(), this::dispatchEvent, getOffsetStore(),
                    poisonEventReporter(), terminalDeathListener());
            return created;
        }
    }

    /** Deterministic engine: run() throws once entered. */
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
}
