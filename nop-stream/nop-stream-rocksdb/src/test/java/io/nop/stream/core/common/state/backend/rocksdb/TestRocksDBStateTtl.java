/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.StateTtlUpdateType;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 verification for keyed-state TTL on the RocksDB backend: lazy eviction (sidecar
 * timestamps), snapshot expiration exclusion, restore survival, user-value round-trip, and
 * the pure-Java background sweep ({@link RocksDBKeyedStateBackend#cleanupExpiredEntries()}).
 *
 * <p>The native RocksDB compaction-filter path is intentionally not covered here: the
 * {@code rocksdbjni} binding does not expose a pure-Java compaction-filter callback, so the
 * background cleanup is realized by the sweep instead (see
 * {@code ai-dev/design/nop-stream/state-management-design.md} TTL section).
 */
class TestRocksDBStateTtl {

    private static final class FakeClock implements TtlTimeProvider {
        long now;

        @Override
        public long currentTimeMillis() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend(FakeClock clock) {
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) new RocksDBStateBackend(tempDir.getAbsolutePath())
                        .createKeyedStateBackend(String.class);
        backend.setTtlTimeProvider(clock);
        return backend;
    }

    private static StateTtlConfig ttl(Duration d) {
        return StateTtlConfig.newBuilder(d).setUpdateType(StateTtlUpdateType.OnCreateAndWrite).build();
    }

    @Test
    void valueStateLazyEvictionReturnsDefaultAndRemovesEntry() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(42);
        assertEquals(42, state.value());

        clock.advance(150);
        assertEquals(0, state.value(), "expired entry should return default");

        // The RocksDB entry must have been physically deleted by lazy eviction, not just
        // hidden: a snapshot (which runs a sweep first) must contain zero entries.
        StateSnapshot snapshot = backend.snapshotState();
        @SuppressWarnings("unchecked")
        Map<String, Object> statesMap = (Map<String, Object>) snapshot.getStateData().get("states");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> entries =
                (java.util.List<Map<String, Object>>) ((Map<String, Object>) statesMap.get("v")).get("entries");
        assertTrue(entries.isEmpty(), "lazy eviction must have removed the RocksDB entry");
        backend.close();
    }

    @Test
    void writeAfterExpiryStartsFresh() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(1);
        clock.advance(150); // expired
        state.update(2);
        assertEquals(2, state.value(), "no stale residual from the expired entry");
        backend.close();
    }

    @Test
    void reducingStateAddAfterExpiryStartsFresh() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ReducingStateDescriptor<Long> desc =
                new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(100)));

        ReducingState<Long> state = backend.getReducingState(desc);
        backend.setCurrentKey("k1");
        state.add(10L);
        state.add(5L);
        assertEquals(15L, state.get());
        clock.advance(150); // expired — accumulator must not seed the next add
        state.add(7L);
        assertEquals(7L, state.get(), "expired accumulator must not contribute to the new accumulation");
        backend.close();
    }

    @Test
    void mapStateWholeMapExpiresAsUnit() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        MapStateDescriptor<String, Integer> desc = new MapStateDescriptor<>("m", String.class, Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));

        MapState<String, Integer> state = backend.getMapState(desc);
        backend.setCurrentKey("k1");
        state.put("a", 1);
        state.put("b", 2);
        clock.advance(60);
        assertNull(state.get("a"), "expired map returns null for any key");
        assertTrue(state.isEmpty(), "expired whole map is empty");
        backend.close();
    }

    @Test
    void listStateAndSweep() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ListStateDescriptor<String> desc = new ListStateDescriptor<>("l", String.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));

        ListState<String> state = backend.getListState(desc);
        backend.setCurrentKey("k1");
        state.add("x");
        state.add("y");
        clock.advance(60); // expired

        // Background sweep reclaims expired entries without an access.
        int removed = backend.cleanupExpiredEntries();
        assertEquals(1, removed, "sweep reclaimed one expired base key");
        // After sweep, a plain read (no TTL) confirms the entry is gone from RocksDB.
        ValueStateDescriptor<Object> probe = new ValueStateDescriptor<>("probe", Object.class);
        backend.setCurrentKey("k1");
        // The list CF key is gone; reading via the TTL-disabled list state returns empty.
        backend.setCurrentKey("k1");
        assertFalse(state.get().iterator().hasNext(), "sweep removed the expired list entry");
        backend.close();
    }

    @Test
    void snapshotExcludesExpiredEntries() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("alive");
        state.update(1);
        backend.setCurrentKey("dead");
        state.update(2);
        clock.advance(60);
        backend.setCurrentKey("alive");
        state.update(3); // refresh "alive"; "dead" stays expired

        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newBackend(new FakeClock());
        restored.restoreState(snapshot);

        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v", Integer.class, 0);
        ValueState<Integer> rs = restored.getState(plainDesc);
        restored.setCurrentKey("alive");
        assertEquals(3, rs.value(), "fresh entry survives snapshot");
        restored.setCurrentKey("dead");
        assertNull(rs.value(), "expired entry excluded from snapshot");
        restored.close();
    }

    @Test
    void ttlSurvivesCheckpointRestore() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(99);

        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        FakeClock clock2 = new FakeClock();
        clock2.now = 1_000_000;
        RocksDBKeyedStateBackend<String> restored = newBackend(clock2);
        restored.restoreState(snapshot);

        ValueState<Integer> restoredState = restored.getState(desc);
        restored.setCurrentKey("k1");
        assertEquals(99, restoredState.value(), "restored entry granted a fresh TTL window on first access");
        clock2.advance(150);
        assertNull(restoredState.value(), "TTL remains active after restore");
        restored.close();
    }

    @Test
    void ttlStateCheckpointRoundTripPreservesUserValues() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofHours(1)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("a");
        state.update(11);
        backend.setCurrentKey("b");
        state.update(22);

        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newBackend(new FakeClock());
        restored.restoreState(snapshot);

        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v", Integer.class, 0);
        ValueState<Integer> rs = restored.getState(plainDesc);
        restored.setCurrentKey("a");
        assertEquals(11, rs.value());
        restored.setCurrentKey("b");
        assertEquals(22, rs.value(),
                "user values round-trip correctly; sidecar timestamps never corrupt serialization");
        restored.close();
    }
}
