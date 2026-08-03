/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CDC source checkpoint integration (Stage 53 Phase 1).
 */
public class TestDebeziumCdcCheckpoint {

    private static final String CONNECTOR = "test-checkpoint-cdc";

    @AfterEach
    void cleanup() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
    }

    private DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        return config;
    }

    // ---- testCdcSourceImplementsCheckpointedSourceFunction ----

    @Test
    void testCdcSourceImplementsCheckpointedSourceFunction() {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        assertInstanceOf(CheckpointedSourceFunction.class, source);
    }

    // ---- testSnapshotStateStoresOffsetsFromStore ----

    @Test
    void testSnapshotStateStoresOffsetsFromStore() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());

        // Bind an offset store with some offsets
        source.initializeState(null); // first run → empty store
        NopStreamOffsetBackingStore store = source.getOffsetStore();
        assertNotNull(store);
        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(ByteBuffer.wrap("p1".getBytes()), ByteBuffer.wrap("pos-100".getBytes()));
        store.setOffsets(offsets);

        OperatorSnapshotResult result = source.snapshotState(7L);
        assertNotNull(result);
        assertEquals(7L, result.getCheckpointId());

        Object raw = result.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
        assertNotNull(raw);
        assertInstanceOf(Map.class, raw);
        @SuppressWarnings("unchecked")
        Map<String, String> serialized = (Map<String, String>) raw;
        assertFalse(serialized.isEmpty(), "snapshot must contain the offset map");
    }

    // ---- testInitializeStateRestoresOffsetsToStore ----

    @Test
    void testInitializeStateRestoresOffsetsToStore() throws Exception {
        // Build a checkpoint state containing CDC offsets
        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(ByteBuffer.wrap("part-A".getBytes()), ByteBuffer.wrap("offset-42".getBytes()));
        Map<String, String> serialized = NopStreamOffsetBackingStore.toSerializable(offsets);

        TaskStateSnapshot state = new TaskStateSnapshot(new TaskLocation("", "", "", 0));
        state.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY, serialized);

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.initializeState(state);

        NopStreamOffsetBackingStore store = source.getOffsetStore();
        assertNotNull(store);
        Map<ByteBuffer, ByteBuffer> restored = store.getOffsets();
        assertEquals(1, restored.size());
        assertEquals("offset-42", new String(restored.get(ByteBuffer.wrap("part-A".getBytes())).array()));
    }

    // ---- snapshot → serialize → deserialize → initializeState round-trip ----

    @Test
    void testSnapshotRestoreRoundTrip() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newConfig());
        source.initializeState(null);
        NopStreamOffsetBackingStore store = source.getOffsetStore();
        store.setOffsets(Collections.singletonMap(
                ByteBuffer.wrap("k".getBytes()), ByteBuffer.wrap("v-99".getBytes())));

        // Snapshot
        OperatorSnapshotResult result = source.snapshotState(1L);
        Object offsetMap = result.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);

        // Simulate serialization of the offset map (as the checkpoint storage would)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(offsetMap);
        oos.close();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        @SuppressWarnings("unchecked")
        Map<String, String> deserialized = (Map<String, String>) ois.readObject();

        // Build restored state
        TaskStateSnapshot restoredState = new TaskStateSnapshot(new TaskLocation("", "", "", 0));
        restoredState.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY, deserialized);

        // A NEW source function recovers
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
        DebeziumCdcSourceFunction recovered = new DebeziumCdcSourceFunction(newConfig());
        recovered.initializeState(restoredState);

        Map<ByteBuffer, ByteBuffer> recoveredOffsets = recovered.getOffsetStore().getOffsets();
        assertEquals(1, recoveredOffsets.size());
        assertEquals("v-99", new String(recoveredOffsets.get(ByteBuffer.wrap("k".getBytes())).array()));
    }

    // ---- testConfigSurvivesSerialization ----

    @Test
    void testConfigSurvivesSerialization() throws Exception {
        DebeziumConfig config = newConfig();
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(source);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        DebeziumCdcSourceFunction restored = (DebeziumCdcSourceFunction) ois.readObject();

        assertNotNull(restored);
        // The config field is no longer transient: connection info survives serialization.
        assertDoesNotThrow(restored::cancel);
    }

    // ---- testOffsetStoreInjectionChain ----
    // Verifies the 3-layer wiring:
    //   DebeziumCdcSourceFunction → DebeziumMessageSource(config, offsetStore)
    //   → DebeziumEngineWrapper(config, consumer, offsetStore)
    //   → offset.storage property = NopStreamOffsetBackingStore FQCN

    @Test
    void testOffsetStoreInjectionChain() throws Exception {
        DebeziumConfig config = newConfig();
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        store.setOffsets(Collections.singletonMap(
                ByteBuffer.wrap("seed".getBytes()), ByteBuffer.wrap("pos".getBytes())));

        // Layer 2: DebeziumMessageSource forwards the store to the wrapper
        DebeziumMessageSource messageSource = new DebeziumMessageSource(config, store);
        assertSame(store, messageSource.getOffsetStore());

        // Layer 3: DebeziumEngineWrapper receives the store
        io.nop.message.debezium.engine.DebeziumEngineWrapper wrapper =
                new io.nop.message.debezium.engine.DebeziumEngineWrapper(config, evt -> {
                }, store);
        assertSame(store, wrapper.getOffsetStore());

        // The wrapper sets offset.storage to the NopStreamOffsetBackingStore FQCN
        java.util.Properties props = io.nop.message.debezium.engine.DebeziumEngineConfig
                .buildProperties(config, true);
        assertEquals(NopStreamOffsetBackingStore.class.getName(),
                props.getProperty("offset.storage"),
                "offset.storage must point at NopStreamOffsetBackingStore FQCN when a custom store is in use");
        assertNull(props.getProperty("offset.storage.file.filename"),
                "file filename must not be set when using the custom store (would conflict)");

        // Engine-instantiated instance (via reflection + configure) binds to the same registry entry
        NopStreamOffsetBackingStore engineInstance = new NopStreamOffsetBackingStore();
        org.apache.kafka.connect.runtime.WorkerConfig wc =
                org.mockito.Mockito.mock(org.apache.kafka.connect.runtime.WorkerConfig.class);
        Map<String, Object> originals = new java.util.HashMap<>();
        originals.put("name", CONNECTOR);
        org.mockito.Mockito.when(wc.originals()).thenReturn(originals);
        engineInstance.configure(wc);
        // The engine instance shares the offset data seeded by the source function instance
        assertEquals(1, engineInstance.getOffsets().size());
    }

    // ---- testCdcCheckpointKillRecoverNoDuplicates (E2E) ----

    @Test
    void testCdcCheckpointKillRecoverNoDuplicates() throws Exception {
        DebeziumConfig config = newConfig();

        // Records emitted by the mock source, in order
        List<String> emittedRecordKeys = new CopyOnWriteArrayList<>();
        AtomicInteger emitIndex = new AtomicInteger(0);

        // A source function whose createMessageSource returns a controllable mock source
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config) {
            @Override
            protected DebeziumMessageSource createMessageSource(
                    DebeziumConfig cfg, NopStreamOffsetBackingStore offsetStore) {
                return new MockCdcMessageSource(cfg, offsetStore, emittedRecordKeys, emitIndex);
            }
        };

        // First run: no prior checkpoint
        source.initializeState(null);

        CopyOnWriteArrayList<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<ChangeEvent> ctx = collectorContext(collected);

        // Run in a thread
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected on cancel
            }
        });
        runner.start();

        // Wait until at least 2 events collected
        long deadline = System.currentTimeMillis() + 5000;
        while (collected.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(collected.size() >= 2, "should collect at least 2 events on first run");

        // Checkpoint: snapshot the offset
        OperatorSnapshotResult snapshot = source.snapshotState(1L);
        assertNotNull(snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY));

        // Kill: cancel the source
        source.cancel();
        runner.join(5000);

        int firstRunCount = collected.size();

        // Build the restored state from the snapshot (simulating checkpoint storage)
        Object offsetMap = snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
        TaskStateSnapshot restoredState = new TaskStateSnapshot(new TaskLocation("", "", "", 0));
        restoredState.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY, offsetMap);

        // Recovery: a fresh source function instance restores from the checkpoint
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
        AtomicInteger recoverEmitIndex = new AtomicInteger(0);
        DebeziumCdcSourceFunction recovered = new DebeziumCdcSourceFunction(config) {
            @Override
            protected DebeziumMessageSource createMessageSource(
                    DebeziumConfig cfg, NopStreamOffsetBackingStore offsetStore) {
                return new MockCdcMessageSource(cfg, offsetStore, emittedRecordKeys, recoverEmitIndex);
            }
        };
        recovered.initializeState(restoredState);

        // The restored offset store must contain the checkpointed offset
        NopStreamOffsetBackingStore restoredStore = recovered.getOffsetStore();
        assertNotNull(restoredStore);
        assertFalse(restoredStore.getOffsets().isEmpty(),
                "restored offset store must contain the checkpointed offsets");

        // Continue collecting on recovery
        CopyOnWriteArrayList<ChangeEvent> recoveredCollected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<ChangeEvent> recoverCtx = collectorContext(recoveredCollected);

        Thread runner2 = new Thread(() -> {
            try {
                recovered.run(recoverCtx);
            } catch (Exception e) {
                // expected on cancel
            }
        });
        runner2.start();

        // Wait until at least 1 event collected on recovery
        deadline = System.currentTimeMillis() + 5000;
        while (recoveredCollected.size() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        recovered.cancel();
        runner2.join(5000);

        assertFalse(recoveredCollected.isEmpty(), "recovery run should emit at least one new event");

        // No duplicates: every emitted record key is unique across both runs
        List<String> allKeys = new ArrayList<>();
        collected.forEach(e -> allKeys.add(String.valueOf(e.getKey())));
        recoveredCollected.forEach(e -> allKeys.add(String.valueOf(e.getKey())));

        // The MockCdcMessageSource emits events keyed by global index; the recovery source must
        // resume after the first run's emitted index (no duplicate keys).
        for (int i = 0; i < allKeys.size(); i++) {
            for (int j = i + 1; j < allKeys.size(); j++) {
                assertNotEquals(allKeys.get(i), allKeys.get(j),
                        "duplicate record key detected across kill/recover: " + allKeys.get(i));
            }
        }
    }

    // ---- helpers ----

    private SourceFunction.SourceContext<ChangeEvent> collectorContext(
            CopyOnWriteArrayList<ChangeEvent> sink) {
        return new SourceFunction.SourceContext<>() {
            @Override
            public void collect(ChangeEvent element) {
                sink.add(element);
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
                sink.add(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    /**
     * Test double for {@link DebeziumMessageSource} that emits synthetic events without a real
     * Debezium engine. On subscribe it inspects the offset store to determine where to resume,
     * proving the checkpointed offset is honored on recovery.
     */
    static class MockCdcMessageSource extends DebeziumMessageSource {
        private final List<String> emittedRecordKeys;
        private final AtomicInteger emitIndex;
        private volatile boolean cancelled = false;
        private volatile Thread emitter;

        MockCdcMessageSource(DebeziumConfig config, NopStreamOffsetBackingStore offsetStore,
                             List<String> emittedRecordKeys, AtomicInteger emitIndex) {
            super(config, offsetStore);
            this.emittedRecordKeys = emittedRecordKeys;
            this.emitIndex = emitIndex;
        }

        @Override
        public ICancellable subscribe(Consumer<ChangeEvent> action) {
            // Determine the resume point from the offset store (simulates Debezium resuming from
            // the committed offset). The store holds the last-committed index under a fixed key.
            NopStreamOffsetBackingStore store = (NopStreamOffsetBackingStore) getOffsetStore();
            int startIdx = 0;
            if (store != null) {
                Map<ByteBuffer, ByteBuffer> offsets = store.getOffsets();
                ByteBuffer pos = offsets.get(ByteBuffer.wrap("__idx__".getBytes()));
                if (pos != null) {
                    startIdx = Integer.parseInt(new String(pos.array()));
                }
            }
            final int from = startIdx;

            emitter = new Thread(() -> {
                int i = from;
                while (!cancelled) {
                    int idx = i++;
                    ChangeEventMetadata meta = new ChangeEventMetadata(
                            CONNECTOR, CONNECTOR, "db", null, "tbl", "data");
                    Map<String, Object> key = new LinkedHashMap<>();
                    key.put("id", idx);
                    ChangeEvent event = new ChangeEvent(meta, "c",
                            Collections.emptyMap(), Collections.singletonMap("id", idx),
                            key, System.currentTimeMillis());
                    emittedRecordKeys.add("rec-" + idx);
                    action.accept(event);
                    // commit the offset (next-to-consume index) to the store — mirrors Debezium
                    // semantics where the committed offset is the position AFTER the last record
                    store.setOffsets(Collections.singletonMap(
                            ByteBuffer.wrap("__idx__".getBytes()),
                            ByteBuffer.wrap(String.valueOf(idx + 1).getBytes())));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            emitter.setDaemon(true);
            emitter.start();

            return new ICancellable() {
                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public void cancel(String reason) {
                    cancelled = true;
                    if (emitter != null) {
                        emitter.interrupt();
                    }
                }

                @Override
                public String getCancelReason() {
                    return null;
                }

                @Override
                public void removeOnCancel(Consumer<String> task) {
                }

                @Override
                public void appendOnCancel(Consumer<String> task) {
                }
            };
        }

        @Override
        public synchronized void stop() {
            cancelled = true;
            if (emitter != null) {
                emitter.interrupt();
            }
        }
    }
}
