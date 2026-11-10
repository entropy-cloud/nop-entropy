/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumErrors;
import io.nop.message.debezium.DebeziumMessageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-03 + C1-002 carrier (plan 2558 裁定 B — REGISTRY 生命周期契约一次落地):
 * the {@link NopStreamOffsetBackingStore} registry lifecycle.
 *
 * <ol>
 *   <li>stop paired release is WRAPPER-level (single point = DebeziumEngineWrapper.stop()
 *       finally); DebeziumMessageSource.stop() does not release (no double counting);</li>
 *   <li>clearConnector is conditional: a live engine binding is rejected typed;</li>
 *   <li>orphan writes after the paired release are typed-rejected (never silently merged
 *       into a newer generation's bucket);</li>
 *   <li>the residency diagnostic metric is visible;</li>
 *   <li>soft-pin #5 negative face: setOffsets outside the recovery declaration window is
 *       typed-rejected — ADDED BEFORE the guard (先增后守), the :89-97 REPLACE pin file
 *       (TestNopStreamOffsetBackingStore) is untouched.</li>
 * </ol>
 */
@Timeout(120)
public class TestOffsetRegistryLifecycle {

    private static final String C = "rl-connector";

    @AfterEach
    void cleanup() {
        // releaseConnector is unconditional (stop-contract pairing face) — always cleans up
        NopStreamOffsetBackingStore.releaseConnector(C);
        NopStreamOffsetBackingStore.releaseConnector("rl-paired");
        NopStreamOffsetBackingStore.releaseConnector("rl-cond");
        NopStreamOffsetBackingStore.releaseConnector("rl-orphan");
        NopStreamOffsetBackingStore.releaseConnector("rl-res-a");
        NopStreamOffsetBackingStore.releaseConnector("rl-res-b");
        NopStreamOffsetBackingStore.releaseConnector("rl-window");
    }

    private static ByteBuffer bb(String s) {
        return ByteBuffer.wrap(s.getBytes());
    }

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

    // ---------------- ① stop paired release: wrapper-level, source no-release ----------------

    @Test
    void stopPairedReleaseIsWrapperLevelAndSourceDoesNotRelease() throws Exception {
        String connector = "rl-paired";
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(connector);
        store.setOffsets(Map.of(bb("p"), bb("o")));

        NoReleaseSource source = new NoReleaseSource(newConfig(connector), store);
        BlockingEngine engine = new BlockingEngine();
        source.injectedEngine = engine;
        source.injectOnCreate = engine;

        ICancellable subscription = source.subscribe(e -> {
        });
        awaitUntil("engine body must be running", () -> engine.ran.get());
        assertEquals(1, NopStreamOffsetBackingStore.residencySnapshot().get(connector),
                "the bucket is resident while the engine generation is bound");

        // source.stop() with a wrapper whose stop() is a deliberate NO-OP: the source must
        // NOT release the registry bucket itself (single release point = wrapper.stop()).
        source.stop();
        assertNotNull(NopStreamOffsetBackingStore.residencySnapshot().get(connector),
                "MA-03: DebeziumMessageSource.stop() does not release — the paired release "
                        + "is the wrapper's single-point obligation");

        // the real release happens at wrapper.stop() (its finally)
        ((NoReleaseWrapper) wrapperOf(source)).realStop();
        assertFalse(NopStreamOffsetBackingStore.residencySnapshot().containsKey(connector),
                "MA-03: wrapper.stop() is the paired-release single point — the bucket dies "
                        + "with the stop contract");
        // the old-generation instance is an orphan now: its writes are typed-rejected
        assertThrows(NopException.class, () -> store.setOffsets(Map.of(bb("p2"), bb("o2"))),
                "orphan generation writes must fail typed");
    }

    /** Wrapper whose stop() is a deliberate no-op — isolates the release obligation. */
    static class NoReleaseWrapper extends DebeziumEngineWrapper {
        private volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injected;

        NoReleaseWrapper(DebeziumConfig config, Consumer<ChangeEvent> consumer,
                         org.apache.kafka.connect.storage.OffsetBackingStore store) {
            super(config, consumer, store, null, null);
        }

        void inject(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            this.injected = engine;
        }

        @Override
        protected void runEngineWorker(
                DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
            // the real built engine is never run (no DB); the injected body drives the lifecycle
            super.runEngineWorker(injected);
        }

        @Override
        void closeEngineWithRetry() {
            closeEngineWithRetry(injected);
        }

        @Override
        public synchronized void stop() {
            // deliberate no-op: the source under test must not rely on wrapper.stop()
        }

        void realStop() {
            super.stop();
        }
    }

    static class NoReleaseSource extends DebeziumMessageSource {
        final DebeziumConfig configRef;
        volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injectedEngine;
        volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injectOnCreate;
        NoReleaseWrapper created;

        NoReleaseSource(DebeziumConfig config, org.apache.kafka.connect.storage.OffsetBackingStore store) {
            super(config, store);
            this.configRef = config;
        }

        @Override
        protected DebeziumEngineWrapper createEngineWrapper() {
            NoReleaseWrapper wrapper = new NoReleaseWrapper(configRef, this::dispatchEvent,
                    getOffsetStore());
            wrapper.inject(injectOnCreate);
            this.created = wrapper;
            return wrapper;
        }
    }

    private static DebeziumEngineWrapper wrapperOf(NoReleaseSource source) {
        return source.created;
    }


    // ---------------- ② conditional removal rejects live bindings ----------------

    @Test
    void clearConnectorRejectsLiveBindingTyped_C1_002() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector("rl-cond");
        store.setOffsets(Map.of(bb("k"), bb("v")));
        store.start();

        NopException ex = assertThrows(NopException.class,
                () -> NopStreamOffsetBackingStore.clearConnector("rl-cond"),
                "C1-002: removal against a live engine binding is rejected typed (pre-fix: "
                        + "unconditional remove split the live instances' writes)");
        assertEquals(DebeziumErrors.ERR_DEBEZIUM_OFFSET_STORE_LIVE_BINDING.getErrorCode(),
                ex.getErrorCode());
        assertNotNull(NopStreamOffsetBackingStore.residencySnapshot().get("rl-cond"),
                "the entry survives the rejected removal");
        assertEquals("v", new String(store.getOffsets().get(bb("k")).array()),
                "the live binding's data is intact");

        store.stop();
        // binding released at stop() — conditional removal is legal again
        NopStreamOffsetBackingStore.clearConnector("rl-cond");
        assertFalse(NopStreamOffsetBackingStore.residencySnapshot().containsKey("rl-cond"));
    }

    // ---------------- ③ orphan writes typed-rejected after paired release ----------------

    @Test
    void orphanWritesAreTypedRejectedAfterRelease() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector("rl-orphan");
        store.setOffsets(Map.of(bb("k"), bb("v")));

        // simulate the stop-contract paired release (wrapper.stop() calls this)
        NopStreamOffsetBackingStore.releaseConnector("rl-orphan");

        assertThrows(NopException.class, () -> store.setOffsets(Map.of(bb("k2"), bb("v2"))),
                "orphan setOffsets must fail typed (never silently merge into a newer bucket)");
        assertThrows(NopException.class, store::getOffsets,
                "orphan reads must fail explicitly too (ghost self-exposure)");
        assertThrows(NopException.class, () -> store.get(java.util.List.of(bb("k"))).get(),
                "orphan SPI reads must fail explicitly");
        assertThrows(NopException.class,
                () -> store.set(Map.of(bb("k2"), bb("v2")), null),
                "orphan SPI writes must fail explicitly");
        // a fresh generation gets a fresh bucket — no silent overwrite channel
        NopStreamOffsetBackingStore fresh = NopStreamOffsetBackingStore.forConnector("rl-orphan");
        assertEquals(0, fresh.getOffsets().size(),
                "the fresh generation starts from an empty bucket (AR-03 stale-resume abolished)");
    }

    // ---------------- ④ residency diagnostics ----------------

    @Test
    void residencyMetricIsVisible() {
        NopStreamOffsetBackingStore a = NopStreamOffsetBackingStore.forConnector("rl-res-a");
        a.setOffsets(Map.of(bb("k1"), bb("v1"), bb("k2"), bb("v2")));
        NopStreamOffsetBackingStore b = NopStreamOffsetBackingStore.forConnector("rl-res-b");
        b.setOffsets(Map.of(bb("k"), bb("v")));

        Map<String, Integer> residency = NopStreamOffsetBackingStore.residencySnapshot();
        assertEquals(2, residency.get("rl-res-a"));
        assertEquals(1, residency.get("rl-res-b"));

        NopStreamOffsetBackingStore.releaseConnector("rl-res-a");
        residency = NopStreamOffsetBackingStore.residencySnapshot();
        assertFalse(residency.containsKey("rl-res-a"),
                "buckets die at the stop-contract paired release (growth upper bound = "
                        + "concurrently active connector names, C5-001)");
        assertEquals(1, residency.get("rl-res-b"));
    }

    // ---------------- ⑤ soft-pin #5: recovery declaration window ----------------

    /**
     * 窗外 typed 拒绝负向断言（先增后守，plan 2558 裁定 M③）：setOffsets 在任一绑定实例
     * start() 之后（恢复声明窗封窗）必须 typed 拒绝。REPLACE 契约本体（:89-97 现状钉）零改写。
     */
    @Test
    void setOffsetsOutsideRecoveryWindowIsTypedRejected_softPin5() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector("rl-window");
        // in-window: the legal restore path (forConnector binding → setOffsets before start)
        store.setOffsets(Map.of(bb("p1"), bb("o1")));

        store.start();

        NopException ex = assertThrows(NopException.class,
                () -> store.setOffsets(Map.of(bb("p2"), bb("o2"))),
                "soft-pin #5: a mid-run restore (any binding instance started) is typed-rejected — "
                        + "it would roll the running engine's fresh writes back to recovery values");
        assertEquals(DebeziumErrors.ERR_DEBEZIUM_OFFSET_RECOVERY_WINDOW_CLOSED.getErrorCode(),
                ex.getErrorCode());
        // the running data is untouched by the rejected restore
        assertEquals("o1", new String(store.getOffsets().get(bb("p1")).array()));

        store.stop();
    }

    /** Live, closeable engine body (TestDebeziumEngineStopContract shape). */
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
        public void close() {
            release.countDown();
        }
    }
}
