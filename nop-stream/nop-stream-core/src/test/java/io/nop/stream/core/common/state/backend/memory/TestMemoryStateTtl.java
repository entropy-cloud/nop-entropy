/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.time.Duration;
import java.util.Map;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 verification for keyed-state TTL on the memory backend: lazy eviction, write-
 * after-expiry freshness, OnCreateAndWrite vs OnReadAndWrite, restore survival, snapshot
 * exclusion, and checkpoint round-trip. Uses a controllable clock so no real time passes.
 */
class TestMemoryStateTtl {

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

    private static MemoryKeyedStateBackend<String> newBackend(FakeClock clock) {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlTimeProvider(clock);
        return backend;
    }

    private static StateTtlConfig ttl(Duration d, StateTtlUpdateType type) {
        return StateTtlConfig.newBuilder(d).setUpdateType(type).build();
    }

    @Test
    void valueStateLazyEvictionReturnsDefaultAndRemovesEntry() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(42);
        assertEquals(42, state.value());

        clock.advance(150); // expired
        assertEquals(0, state.value(), "expired entry should return default");

        // lazy eviction must remove the storage entry (double cleanup), not just hide it.
        // A snapshot taken immediately must contain zero entries for this state.
        StateSnapshot snapshot = backend.snapshotState();
        @SuppressWarnings("unchecked")
        Map<String, Object> statesMap = (Map<String, Object>) snapshot.getStateData().get("states");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> entries =
                (java.util.List<Map<String, Object>>) ((Map<String, Object>) statesMap.get("v")).get("entries");
        assertTrue(entries.isEmpty(),
                "lazy eviction must have removed the storage entry, so the snapshot is empty");
    }

    @Test
    void writeAfterExpiryHasNoStaleData() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(100), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(1);
        clock.advance(150); // expired
        state.update(2);    // write new value after expiry
        assertEquals(2, state.value(), "no stale residual from the expired entry");
    }

    @Test
    void onCreateAndWriteDoesNotRefreshOnRead() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(7);
        clock.advance(60);
        assertEquals(7, state.value()); // read at t=60
        clock.advance(60);              // t=120, past the original write window
        assertEquals(0, state.value(), "read must not refresh TTL under OnCreateAndWrite");
    }

    @Test
    void onReadAndWriteRefreshesOnRead() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100), StateTtlUpdateType.OnReadAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(7);                 // write at t=0, ts=0
        clock.advance(60);               // t=60
        assertEquals(7, state.value());  // read refreshes -> ts=60
        clock.advance(90);               // t=150, 150-60=90 <= 100 still within window
        assertEquals(7, state.value());  // read refreshes -> ts=150
        clock.advance(101);              // t=251, 251-150=101 > 100 -> expired
        assertEquals(0, state.value(), "entry must expire past the last refreshed window");
    }

    @Test
    void disabledTtlBehavesLikeNoTtl() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        // explicitly disabled config
        desc.setTtlConfig(StateTtlConfig.DISABLED);

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(5);
        clock.advance(1_000_000); // a long time — should NOT expire because TTL is disabled
        assertEquals(5, state.value());

        // No performance/behaviour regression: a no-TTL descriptor behaves identically.
        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v2", Integer.class, 0);
        ValueState<Integer> plain = backend.getState(plainDesc);
        backend.setCurrentKey("k2");
        plain.update(9);
        clock.advance(1_000_000);
        assertEquals(9, plain.value());
    }

    @Test
    void mapStateWholeMapExpiresAsUnit() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        MapStateDescriptor<String, Integer> desc = new MapStateDescriptor<>("m", String.class, Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));

        MapState<String, Integer> state = backend.getMapState(desc);
        backend.setCurrentKey("k1");
        state.put("a", 1);
        state.put("b", 2);
        assertEquals(1, state.get("a"));
        clock.advance(60); // whole map expired
        assertNull(state.get("a"), "expired map returns null for any key");
        assertNull(state.get("b"));
        assertTrue(state.isEmpty());
    }

    @Test
    void listAndReducingAndAggregatingLazyEviction() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);

        ListStateDescriptor<String> listDesc = new ListStateDescriptor<>("l", String.class);
        listDesc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));
        ListState<String> list = backend.getListState(listDesc);

        ReducingStateDescriptor<Long> reducingDesc =
                new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);
        reducingDesc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));
        ReducingState<Long> reducing = backend.getReducingState(reducingDesc);

        AggregatingStateDescriptor<Long, long[], Long> aggDesc =
                new AggregatingStateDescriptor<>("a", new SumAggregateFunction(), long[].class);
        aggDesc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));
        AggregatingState<Long, Long> agg = backend.getAggregatingState(aggDesc);

        backend.setCurrentKey("k1");
        list.add("x");
        reducing.add(10L);
        agg.add(5L);
        clock.advance(60); // all expired

        assertFalse(list.get().iterator().hasNext(), "expired list state is empty");
        assertNull(reducing.get(), "expired reducing state is null");
        assertNull(agg.get(), "expired aggregating state is null");
    }

    @Test
    void snapshotExcludesExpiredEntries() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("alive");
        state.update(1);
        backend.setCurrentKey("dead");
        state.update(2);
        clock.advance(60); // both written at t=0, now both expired... write alive again
        backend.setCurrentKey("alive");
        state.update(3);   // refresh alive at t=60
        // "dead" is now expired, "alive" is fresh

        StateSnapshot snapshot = backend.snapshotState();
        MemoryKeyedStateBackend<String> restored = newBackend(new FakeClock());
        restored.restoreState(snapshot);

        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v", Integer.class, 0);
        ValueState<Integer> restoredState = restored.getState(plainDesc);
        restored.setCurrentKey("alive");
        assertEquals(3, restoredState.value(), "fresh entry survives snapshot");
        restored.setCurrentKey("dead");
        // "dead" was excluded from the snapshot; the restored state therefore has no value
        // for it. The restored descriptor carries a null default (SerDe does not persist
        // defaults), so value() returns null.
        assertNull(restoredState.value(), "expired entry excluded from snapshot");
    }

    @Test
    void ttlSurvivesCheckpointRestore() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(99);

        StateSnapshot snapshot = backend.snapshotState();

        // Restore into a fresh backend with its own clock, and re-supply the TTL descriptor.
        FakeClock clock2 = new FakeClock();
        clock2.now = 1_000_000; // restore happens "much later" in wall-clock terms
        MemoryKeyedStateBackend<String> restored = newBackend(clock2);
        restored.restoreState(snapshot);

        ValueState<Integer> restoredState = restored.getState(desc);
        restored.setCurrentKey("k1");
        assertEquals(99, restoredState.value(),
                "restored entry is granted a fresh TTL window on first access");

        clock2.advance(150); // now past the restored window
        // Restored descriptor carries a null default (SerDe does not persist defaults),
        // so the expired read returns null. The key point: TTL is still ACTIVE after
        // restore — the previously-readable value (99) is gone.
        assertNull(restoredState.value(), "TTL remains active after restore");
    }

    @Test
    void ttlStateCheckpointRoundTripPreservesUserValues() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofHours(1), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("a");
        state.update(11);
        backend.setCurrentKey("b");
        state.update(22);

        StateSnapshot snapshot = backend.snapshotState();
        MemoryKeyedStateBackend<String> restored = newBackend(new FakeClock());
        restored.restoreState(snapshot);

        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v", Integer.class, 0);
        ValueState<Integer> rs = restored.getState(plainDesc);
        restored.setCurrentKey("a");
        assertEquals(11, rs.value());
        restored.setCurrentKey("b");
        assertEquals(22, rs.value(),
                "user values round-trip correctly; sidecar timestamps never corrupt serialization");
    }

    @Test
    void snapshotOfFullyExpiredStateIsEmpty() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(50), StateTtlUpdateType.OnCreateAndWrite));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(1);
        clock.advance(60); // expired

        StateSnapshot snapshot = backend.snapshotState();
        // snapshot produced but the state had exactly one (now expired) entry → 0 entries
        @SuppressWarnings("unchecked")
        Map<String, Object> statesMap = (Map<String, Object>) snapshot.getStateData().get("states");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> entries =
                (java.util.List<Map<String, Object>>) ((Map<String, Object>) statesMap.get("v")).get("entries");
        assertTrue(entries.isEmpty(), "expired entries must not appear in the snapshot");
    }

    public static class SumAggregateFunction implements AggregateFunction<Long, long[], Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[]{0L};
        }

        @Override
        public long[] add(Long value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public Long getResult(long[] accumulator) {
            return accumulator[0];
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }
}
