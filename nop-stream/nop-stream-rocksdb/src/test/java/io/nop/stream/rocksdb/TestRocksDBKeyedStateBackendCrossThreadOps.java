/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.StateTtlUpdateType;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01, W-9 victim-side pin (finding 1.2-C10-006, cross-unit anchor of
 * 1.1-C3-009; plan 2534 ruling D.2 item 7): a keyed backend whose writer thread and
 * its TTL sidecar bookkeeping are raced by a snapshot/sweep thread must not corrupt
 * state or throw.
 *
 * <p>Thread A continuously advances the current key and writes values (so the TTL
 * sidecar {@code TtlContext.timestamps} map gains new entries); thread B repeatedly
 * calls {@link RocksDBKeyedStateBackend#snapshotState()} (which sweeps first) and
 * {@link RocksDBKeyedStateBackend#cleanupExpiredEntries()}. Pre-fix the sidecar was a
 * plain {@code HashMap}: put-while-iterating threw {@link
 * java.util.ConcurrentModificationException} deterministically under this hammer.
 * Post-fix the sidecar is a {@code ConcurrentHashMap}: weakly-consistent iteration is
 * safe, no exception escapes, and every snapshotted key is a key the writer wrote
 * (snapshot key set ⊆ written key set; no torn never-written keys appear).
 */
class TestRocksDBKeyedStateBackendCrossThreadOps {

    private static final class FakeClock implements TtlTimeProvider {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend() {
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) new RocksDBStateBackend(tempDir.getAbsolutePath())
                        .createKeyedStateBackend(String.class);
        backend.setTtlTimeProvider(new FakeClock());
        backend.setIncrementalCheckpointEnabled(false);
        return backend;
    }

    private static StateTtlConfig ttl() {
        // TTL must be ENABLED so recordWrite() actually feeds the sidecar map — but
        // long enough that nothing expires during the hammer (sweep iterates, deletes none).
        return StateTtlConfig.newBuilder(Duration.ofHours(1))
                .setUpdateType(StateTtlUpdateType.OnCreateAndWrite).build();
    }

    @Test
    void writerAndSnapshotSweepThreadsDoNotCorruptStateOrThrow() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl());
        ValueState<Integer> state = backend.getState(desc);

        Set<String> writtenKeys = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        CountDownLatch writerDone = new CountDownLatch(1);

        // Writer thread: continuously advance the current key and write. Growing keys
        // keep the sidecar map under structural modification for the whole window
        // (pre-fix CME surface; post-fix weakly-consistent iteration).
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 20_000; i++) {
                    String key = "k" + i;
                    backend.setCurrentKey(key);
                    state.update(i);
                    writtenKeys.add(key);
                }
            } catch (Throwable t) {
                readerFailure.compareAndSet(null, t);
            } finally {
                writerDone.countDown();
            }
        }, "r401-rocksdb-writer");
        writer.start();

        // Snapshot/sweep thread: until the writer finishes, keep sweeping the TTL
        // sidecar and taking full snapshots. Any escaping exception (CME, native
        // error, state corruption) is captured and fails the pin.
        int sweeps = 0;
        int snapshots = 0;
        List<String> snapshotKeys = new ArrayList<>();
        try {
            while (!writerDone.await(5, TimeUnit.MILLISECONDS)) {
                backend.cleanupExpiredEntries();
                sweeps++;
                if (sweeps % 8 == 0) {
                    StateSnapshot snapshot = backend.snapshotState();
                    snapshots++;
                    Map<String, Object> statesMap =
                            (Map<String, Object>) snapshot.getStateData().get("states");
                    if (statesMap != null) {
                        Object vState = statesMap.get("v");
                        if (vState instanceof Map) {
                            Object entries = ((Map<?, ?>) vState).get("entries");
                            if (entries instanceof List) {
                                for (Object e : (List<?>) entries) {
                                    if (e instanceof Map) {
                                        Object key = ((Map<?, ?>) e).get("key");
                                        if (key != null) {
                                            snapshotKeys.add(String.valueOf(key));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            readerFailure.compareAndSet(null, t);
        }
        writer.join(30_000);

        if (readerFailure.get() != null) {
            throw new AssertionError("cross-thread keyed-state ops failed", readerFailure.get());
        }
        assertEquals(0, writerDone.getCount(), "writer thread must finish its workload");
        assertTrue(sweeps > 0, "the sweep thread must actually have run");
        assertTrue(snapshots > 0, "the snapshot path must actually have run");

        // Snapshot key set ⊆ written key set: a torn snapshot must never surface a
        // key the writer never wrote.
        for (String key : snapshotKeys) {
            if (!writtenKeys.contains(key)) {
                throw new AssertionError("snapshot surfaced a never-written key: " + key);
            }
        }
        // Post-fix sanity: the writer's own last write must be visible and consistent.
        backend.setCurrentKey("k0");
        assertEquals(0, state.value());
    }
}
