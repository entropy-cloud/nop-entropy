/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2511 C8-001 / C10-013 (adjudication ⑥): per-record last-emitted position
 * protocol — the checkpoint offset must never run ahead of what the source actually
 * emitted downstream.
 *
 * <p>Red ⑤ carrier: {@link #snapshotOffsetEqualsLastEmittedUnderQueueSkew} — pre-fix
 * the reader lifted the engine offset-store value into the split snapshot; with the
 * bounded queue in-flight (≤256 records) the snapshot ran ahead of the emitted
 * boundary and a crash silently dropped the in-flight records.
 */
public class TestDebeziumLastEmittedOffsets_C8_001 {

    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("debezium-c8-001-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.walk(outputDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
                // teardown best-effort
            }
        });
    }

    // ==================== helpers ====================

    static ChangeEventMetadata mysqlMeta(long pos) {
        return new ChangeEventMetadata("mysql", "srv-1", "db", null, "tbl", "data",
                pos, "binlog-0001", null, null);
    }

    static ChangeEventMetadata lsnMeta(long lsn) {
        return new ChangeEventMetadata("postgresql", "srv-1", "db", "public", "tbl", "data",
                null, null, lsn, null);
    }

    static ChangeEventMetadata commitLsnMeta(long commitLsn) {
        return new ChangeEventMetadata("sqlserver", "srv-1", "db", "dbo", "tbl", "data",
                null, null, null, commitLsn);
    }

    static ChangeEvent event(ChangeEventMetadata meta, Map<String, Object> key) {
        return new ChangeEvent(meta, "c", null, Map.of("id", 1), key, 0L);
    }

    /** Marker entry decode: positions carried by a synthesized offset map. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> decodeMarker(Map<String, String> offsetMap) throws Exception {
        String markerValue = offsetMap.get(markerKey());
        assertNotNull(markerValue, "synthesized map must carry the last-emitted marker");
        return (Map<String, Object>) io.nop.core.lang.json.JsonTool.parseNonStrict(
                new String(Base64.getDecoder().decode(markerValue), StandardCharsets.UTF_8));
    }

    static String markerKey() {
        return Base64.getEncoder().encodeToString(
                "{\"nop.cdc.last-emitted\":true}".getBytes(StandardCharsets.UTF_8));
    }

    static String b64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== tracker unit faces ====================

    @Test
    void positionExtractionFollowsKeyFormPriority() {
        LastEmittedOffsetTracker tracker = new LastEmittedOffsetTracker();
        tracker.onEmitted(event(lsnMeta(100), Map.of("id", 1)));
        tracker.onEmitted(event(commitLsnMeta(200), Map.of("id", 2)));
        tracker.onEmitted(event(mysqlMeta(300), Map.of("id", 3)));

        Map<String, Map<String, Object>> positions = tracker.snapshotPositions();
        Map<String, Object> fields = positions.get("srv-1");
        assertNotNull(fields);
        // the LAST emitted event's key form wins (the table stores the latest position)
        assertEquals(300L, ((Number) fields.get("pos")).longValue());
        assertEquals("binlog-0001", fields.get("file"));

        TreeMap<String, String> map = tracker.synthesizeOffsetMap();
        assertEquals(2, map.size(), "marker + one engine-shaped entry per server");
        assertTrue(map.containsKey(markerKey()));
    }

    @Test
    void nullMetadataOrIncompleteKeyFormFailsTyped() {
        LastEmittedOffsetTracker tracker = new LastEmittedOffsetTracker();
        // metadata == null (converter source block missing)
        StreamException ex1 = assertThrows(StreamException.class,
                () -> tracker.onEmitted(new ChangeEvent(null, "c", null, Map.of(), null, 0L)));
        assertTrue(String.valueOf(ex1.getParam(
                        io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL)).contains("metadata is null"));

        // MySQL key form incomplete: pos without file
        ChangeEventMetadata incomplete = new ChangeEventMetadata("mysql", "srv-1", "db", null,
                "tbl", "data", 42L, null, null, null);
        StreamException ex2 = assertThrows(StreamException.class,
                () -> tracker.onEmitted(event(incomplete, Map.of("id", 1))));
        assertTrue(String.valueOf(ex2.getParam(
                        io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL)).contains("no usable position"));

        // no silent fallback: the failed event left no position behind
        assertFalse(tracker.hasPositions());
    }

    @Test
    void restoreSeedsNumericMinAcrossServersAndSplits() {
        LastEmittedOffsetTracker tracker = new LastEmittedOffsetTracker();

        LastEmittedOffsetTracker split1 = new LastEmittedOffsetTracker();
        split1.onEmitted(event(lsnMeta(30), Map.of("id", 1)));
        tracker.seedFromOffsetMap(split1.synthesizeOffsetMap());

        LastEmittedOffsetTracker split2 = new LastEmittedOffsetTracker();
        split2.onEmitted(event(lsnMeta(20), Map.of("id", 2)));
        tracker.seedFromOffsetMap(split2.synthesizeOffsetMap());

        // numeric min = conservative replay (duplicate-not-lose), NOT "take the first one"
        assertEquals(20L, ((Number) tracker.snapshotPositions().get("srv-1").get("lsn")).longValue());

        // legacy engine-format maps (no marker) are ignored, never mis-seeded
        Map<String, String> legacy = new LinkedHashMap<>();
        legacy.put(b64("{\"partition\":\"p\"}"), b64("{\"pos\":999}"));
        tracker.seedFromOffsetMap(legacy);
        assertEquals(20L, ((Number) tracker.snapshotPositions().get("srv-1").get("lsn")).longValue());
    }

    // ==================== FLIP-27 reader face (red ⑤ carrier) ====================

    /**
     * Red ⑤ carrier: queue-skew injection — the engine handler has returned for more
     * records than the reader emitted (queue in-flight), and the engine-store offset is
     * FRESHER than the last-emitted boundary. Post-fix the split snapshot carries
     * exactly the last-emitted boundary; pre-fix it carried the store value (loss
     * window). Also covers kill-recover: restore re-seeds from the snapshot boundary.
     */
    @Test
    void snapshotOffsetEqualsLastEmittedUnderQueueSkew() throws Exception {
        DebeziumConfig cfg = TestDebeziumCdcSource.config("cdc", "db.a");
        TestDebeziumCdcSource.StubbedReader reader = new TestDebeziumCdcSource.StubbedReader(cfg, null, null);
        reader.setContextForTest(TestDebeziumCdcSource.readerCtx(0, 1));
        reader.start();
        reader.addSplits(List.of(new DebeziumTableSplit("db.a")));
        // the engine runner thread publishes the queue context asynchronously — wait for it
        long deadline = System.currentTimeMillis() + 5000;
        while (reader.queueCtx.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertNotNull(reader.queueCtx.get(), "stub run must expose the queue context");

        // engine store holds a FRESHER offset than anything emitted (queue skew form)
        reader.offsets.put("stale-engine-key", "fresher-engine-offset");

        // emit 3 records through the bounded queue; drain exactly 2 of them —
        // 1 stays in-flight, the store value stays ahead of the emitted boundary
        reader.queueCtx.get().collect(event(lsnMeta(100), Map.of("id", 100)));
        reader.queueCtx.get().collect(event(lsnMeta(101), Map.of("id", 101)));
        reader.queueCtx.get().collect(event(lsnMeta(102), Map.of("id", 102)));
        assertNotNull(reader.pollNext().orElse(null), "emit record 1");
        assertNotNull(reader.pollNext().orElse(null), "emit record 2");

        List<DebeziumTableSplit> snapshot = reader.snapshotState(9L);
        assertEquals(1, snapshot.size());
        DebeziumTableSplit split = snapshot.get(0);
        assertTrue(split.isOffsetCarrying(), "positions exist: snapshot must carry the last-emitted map");
        Map<String, Object> positions = decodeMarker(split.getOffsetMap());
        assertEquals(101L, ((Number) ((Map<String, Object>) positions.get("srv-1")).get("lsn")).longValue(),
                "snapshot offset == last-EMITTED boundary (store's fresher value must not leak)");
        assertFalse(split.getOffsetMap().containsKey("stale-engine-key"),
                "the engine-store entry must not ride the snapshot");

        // kill-recover: restore from the snapshot — the tracker re-seeds at the
        // snapshot boundary (conservative min across splits)
        TestDebeziumCdcSource.StubbedReader recovered =
                new TestDebeziumCdcSource.StubbedReader(cfg, null, null);
        recovered.restoreState(List.of(split));
        assertRestoredBoundary(recovered, 101L);

        reader.close();
    }

    static void assertRestoredBoundary(TestDebeziumCdcSource.StubbedReader recovered, long expected) throws Exception {
        var snapshot = recovered.snapshotState(11L);
        Map<String, Object> positions = decodeMarker(snapshot.get(0).getOffsetMap());
        assertEquals(expected, ((Number) ((Map<String, Object>) positions.get("srv-1")).get("lsn")).longValue(),
                "restored boundary must equal the snapshot's last-emitted position");
    }

    // ==================== legacy function face ====================

    /**
     * Legacy {@link DebeziumCdcSourceFunction} face (same adjudication): the collect-直连
     * path records the last-emitted position AFTER the downstream collect returns, and
     * snapshotState snapshots that boundary — not the engine store's (fresher) value.
     * Recovery from the snapshot re-seeds the boundary (kill-recover).
     */
    @Test
    void legacyFunctionSnapshotsLastEmittedNotStoreValue() throws Exception {
        String connector = "cdc-legacy-c8";
        List<String> emittedKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger emitIndex = new java.util.concurrent.atomic.AtomicInteger();

        DebeziumConfig cfg = TestDebeziumCdcSource.config(connector, "db.a");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(cfg) {
            @Override
            protected DebeziumMessageSource createMessageSource(
                    DebeziumConfig c, NopStreamOffsetBackingStore offsetStore) {
                return new PositionEmittingMockSource(c, offsetStore, emittedKeys, emitIndex);
            }
        };
        source.initializeState(null);

        java.util.List<ChangeEvent> collected = new java.util.concurrent.CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<ChangeEvent> ctx = new SourceFunction.SourceContext<ChangeEvent>() {
            @Override
            public void collect(ChangeEvent element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
                collect(element);
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

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception expectedOnCancel) {
                // cancel-driven exit
            }
        });
        runner.setDaemon(true);
        runner.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (collected.size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(collected.size() >= 3, "mock source must emit at least 3 events");

        // snapshot INSIDE the race window (before kill): the store has already advanced
        // past the last collected record (idx+1 commit per emit), but the snapshot must
        // equal the last-EMITTED boundary
        var snapshot = source.snapshotState(1L);
        Map<String, String> offsetMap =
                (Map<String, String>) snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
        Map<String, Object> positions = decodeMarker(offsetMap);
        assertEquals((long) (collected.size() - 1),
                ((Number) ((Map<String, Object>) positions.get(connector)).get("lsn")).longValue(),
                "legacy snapshot == last-emitted boundary (store value is fresher)");

        source.cancel();
        runner.join(5000);

        // kill-recover: restore the snapshot map into a fresh function — the tracker
        // re-seeds at the boundary; the recovered snapshot stays at the boundary even
        // though the (restored) store may hold fresher bytes
        NopStreamOffsetBackingStore.clearConnector(connector);
        DebeziumCdcSourceFunction recovered = new DebeziumCdcSourceFunction(cfg) {
            @Override
            protected DebeziumMessageSource createMessageSource(
                    DebeziumConfig c, NopStreamOffsetBackingStore offsetStore) {
                return new PositionEmittingMockSource(c, offsetStore, emittedKeys, emitIndex);
            }
        };
        TaskStateSnapshotLikeAdapter.restoreInto(recovered, offsetMap);
        var recoveredSnapshot = recovered.snapshotState(2L);
        Map<String, String> recoveredMap =
                (Map<String, String>) recoveredSnapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
        Map<String, Object> recoveredPositions = decodeMarker(recoveredMap);
        assertEquals((long) (collected.size() - 1),
                ((Number) ((Map<String, Object>) recoveredPositions.get(connector)).get("lsn")).longValue(),
                "recovered boundary must equal the checkpointed last-emitted position");
        NopStreamOffsetBackingStore.clearConnector(connector);
    }

    /** Initializes a function from a serialized CDC offset map (checkpoint restore seam). */
    static final class TaskStateSnapshotLikeAdapter {
        static void restoreInto(DebeziumCdcSourceFunction function, Map<String, String> offsetMap)
                throws Exception {
            io.nop.stream.core.checkpoint.TaskStateSnapshot state =
                    new io.nop.stream.core.checkpoint.TaskStateSnapshot(
                            new io.nop.stream.core.checkpoint.TaskLocation("", "", "", 0));
            state.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY, offsetMap);
            function.initializeState(state);
        }
    }

    /**
     * Minimal mock source emitting events with REAL position metadata (lsn = record
     * index) and committing a FRESHER next-index to the store after each dispatch —
     * the exact store-leads-emission skew the protocol closes.
     */
    static final class PositionEmittingMockSource extends DebeziumMessageSource {
        private final List<String> emittedKeys;
        private final java.util.concurrent.atomic.AtomicInteger emitIndex;
        private volatile boolean cancelled;
        private volatile Thread emitter;

        PositionEmittingMockSource(DebeziumConfig config, NopStreamOffsetBackingStore offsetStore,
                                   List<String> emittedKeys,
                                   java.util.concurrent.atomic.AtomicInteger emitIndex) {
            super(config, offsetStore);
            this.emittedKeys = emittedKeys;
            this.emitIndex = emitIndex;
        }

        @Override
        public ICancellable subscribe(java.util.function.Consumer<ChangeEvent> action) {
            NopStreamOffsetBackingStore store = (NopStreamOffsetBackingStore) getOffsetStore();
            int startIdx = 0;
            if (store != null) {
                for (Map.Entry<java.nio.ByteBuffer, java.nio.ByteBuffer> e : store.getOffsets().entrySet()) {
                    String key = new String(e.getKey().array(), StandardCharsets.UTF_8);
                    String value = new String(e.getValue().array(), StandardCharsets.UTF_8);
                    if (value.startsWith("{")) {
                        // last-emitted marker / engine-shape JSON entry: resume after the
                        // recorded lsn (the mock models a position-resuming connector)
                        try {
                            long lsn = ((Number) ((Map<?, ?>) io.nop.core.lang.json.JsonTool
                                    .parseNonStrict(value)).get("lsn")).longValue();
                            startIdx = (int) lsn + 1;
                        } catch (Exception ignored) {
                            // not a JSON offset value: skip
                        }
                    } else if ("__idx__".equals(key)) {
                        startIdx = Integer.parseInt(value);
                    }
                }
            }
            final int from = startIdx;
            emitter = new Thread(() -> {
                int i = from;
                while (!cancelled) {
                    int idx = i++;
                    ChangeEventMetadata meta = new ChangeEventMetadata(connectorName(), connectorName(),
                            "db", null, "tbl", "data", null, null, (long) idx, null);
                    Map<String, Object> key = new LinkedHashMap<>();
                    key.put("id", idx);
                    ChangeEvent event = new ChangeEvent(meta, "c",
                            java.util.Collections.emptyMap(),
                            java.util.Collections.singletonMap("id", idx), key, 0L);
                    emittedKeys.add("rec-" + idx);
                    action.accept(event);
                    // store commit AFTER the dispatch: fresher than the emitted boundary
                    if (store != null) {
                        store.setOffsets(java.util.Collections.singletonMap(
                                java.nio.ByteBuffer.wrap("__idx__".getBytes(StandardCharsets.UTF_8)),
                                java.nio.ByteBuffer.wrap(String.valueOf(idx + 1)
                                        .getBytes(StandardCharsets.UTF_8))));
                    }
                    try {
                        Thread.sleep(30);
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

        private static String connectorName() {
            return "cdc-legacy-c8";
        }
    }
}
