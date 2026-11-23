/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-012 carrier (plan 2558 裁定 G / 反向钉#3 后置面): the {@link DebeziumConfig}
 * extraProperties getter returns a FROZEN view ({@code Map.copyOf}).
 *
 * <p>Prerequisite (钉①, executed BEFORE this fix): the four live-map setups in
 * {@code TestDebeziumEngineProperties} :181/:187/:207/:218 were rewritten to the config API
 * (addExtraProperty) — no test may mutate through the getter view anymore.
 *
 * <p>Freeze semantics: read-side view only (no lifecycle hook, no state flag) —「装配期专用」
 * is a documented-only contract; null values are skipped at the write entries + WARN because
 * {@code Map.copyOf} rejects null values; the backing field stays a serializable concurrent
 * map so a write racing a frozen snapshot cannot throw ConcurrentModificationException
 * (checkpoint persistence unaffected — see the round-trip below and
 * {@code TestDebeziumConfigSerializable}).
 */
public class TestDebeziumConfigFrozen {

    @Test
    void getterReturnsFrozenView_mutationAttemptsThrow() {
        DebeziumConfig config = new DebeziumConfig();
        config.addExtraProperty("k", "v");

        Map<String, String> view = config.getExtraProperties();
        assertEquals("v", view.get("k"));

        assertThrows(UnsupportedOperationException.class, () -> view.put("k2", "v2"),
                "the getter view must be frozen (C1-012: pre-fix the live HashMap escaped "
                        + "through the getter and external mutation bypassed every invariant)");
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertThrows(UnsupportedOperationException.class, () -> view.remove("k"));

        // successive getter calls are independent snapshots
        config.addExtraProperty("k3", "v3");
        assertEquals(2, config.getExtraProperties().size());
        assertEquals(1, view.size(), "an earlier snapshot view is unaffected by later writes");
    }

    @Test
    void writeEntriesSkipNullValuesWithDeclaredDegradation() {
        DebeziumConfig config = new DebeziumConfig();
        config.addExtraProperty("good", "v");
        config.addExtraProperty("nullValue", null);
        config.addExtraProperty(null, "v2");

        Map<String, String> view = config.getExtraProperties();
        assertEquals("v", view.get("good"));
        assertTrue(!view.containsKey("nullValue"),
                "null values are skipped at the write entry (Map.copyOf rejects null values "
                        + "with NPE — the write path degrades with WARN instead)");
        assertEquals(1, view.size());
    }

    @Test
    void setExtraPropertiesCopiesAndSkipsNullValues() {
        DebeziumConfig config = new DebeziumConfig();
        Map<String, String> external = new HashMap<>();
        external.put("a", "1");
        external.put("b", null);
        config.setExtraProperties(external);

        // the internal field is a copy — mutating the external map afterwards must not leak
        external.put("a", "mutated");
        assertEquals("1", config.getExtraProperties().get("a"));
        assertEquals(1, config.getExtraProperties().size());
    }

    /** Concurrent-read anchor: readers via the frozen view never see a torn state. */
    @Test
    void concurrentWritesAndFrozenReadsAreAnchored() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        int readers = 4;
        int perRound = 500;
        int total = readers * perRound;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch done = new CountDownLatch(readers + 1);
        // Worker-thread exceptions are swallowed by the executor unless collected — pre-fix
        // the CME from Map.copyOf(HashMap) surfaced only as stderr noise while the test stayed
        // green. Capture them so the frozen-read contract is actually asserted.
        ConcurrentLinkedQueue<Throwable> workerFailures = new ConcurrentLinkedQueue<>();
        try {
            // single assembly-time writer thread (装配期专用: setup is sequential by contract;
            // the anchor is the READ side — every snapshot is a coherent frozen copy)
            pool.execute(() -> {
                try {
                    for (int i = 0; i < total; i++) {
                        config.addExtraProperty("w." + i, "v");
                    }
                } catch (Throwable e) {
                    workerFailures.add(e);
                } finally {
                    done.countDown();
                }
            });
            for (int r = 0; r < readers; r++) {
                pool.execute(() -> {
                    try {
                        for (int i = 0; i < perRound; i++) {
                            Map<String, String> snapshot = config.getExtraProperties();
                            long nonNull = snapshot.values().stream()
                                    .filter(v -> v.startsWith("v") && v.length() >= 1).count();
                            assertEquals(snapshot.size(), nonNull,
                                    "a frozen snapshot never contains a torn entry");
                        }
                    } catch (Throwable e) {
                        workerFailures.add(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "the anchor must settle");
        } finally {
            pool.shutdownNow();
        }
        assertTrue(workerFailures.isEmpty(),
                "frozen reads must never fail while a write races: " + workerFailures);
        assertEquals(total, config.getExtraProperties().size(),
                "all assembly writes land in the backing field");
    }

    /** Serialization bless face: the backing field stays a serializable concurrent map. */
    @Test
    void serializationRoundTripStillGreen() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("frozen-cdc");
        config.addExtraProperty("custom.key", "custom.value");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(config);
        oos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        DebeziumConfig restored = (DebeziumConfig) ois.readObject();

        assertEquals("custom.value", restored.getExtraProperties().get("custom.key"),
                "the frozen getter must not break the checkpoint-persistence round trip");
        assertThrows(UnsupportedOperationException.class,
                () -> restored.getExtraProperties().put("x", "y"),
                "the restored config's view is frozen too");
        assertDoesNotThrow(() -> restored.addExtraProperty("post", "restore"),
                "write entries stay open after deserialization");
        assertEquals("restore", restored.getExtraProperties().get("post"));
    }
}
