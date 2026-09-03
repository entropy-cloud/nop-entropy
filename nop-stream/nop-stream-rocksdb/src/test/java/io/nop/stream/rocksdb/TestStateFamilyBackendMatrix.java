package io.nop.stream.rocksdb;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 0830-3 Phase 1: state-family x backend round-trip matrix. For every keyed
 * state family supported by the live backends, snapshot produced on backend A
 * restores on backend B for all four {memory, rocksdb} direction combinations.
 * Existing per-family round-trip tests (TestRocksDBSnapshotRestore 21 cases,
 * TestStateSnapshotRoundTrip, TestMemoryKeyedStateBackendSnapshotRestore) are
 * referenced, not rewritten; this matrix closes the systematic family x backend
 * cross-product gap named by the plan's Current Baseline.
 *
 * <p>TTL variants use an injected clock: an entry whose TTL window has elapsed
 * at snapshot time must be excluded from the snapshot, live entries must survive
 * restore (RK-4 semantics: repeated getState must not reset the window).
 */
class TestStateFamilyBackendMatrix {

    @TempDir
    File tempDir;

    private int dbSeq = 0;

    public static class SumAggregate implements AggregateFunction<Long, Long, Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Long value, Long accumulator) {
            return accumulator + value;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    private static final class FakeClock implements TtlTimeProvider {
        final AtomicLong now = new AtomicLong(1_000_000);

        @Override
        public long currentTimeMillis() {
            return now.get();
        }

        void advance(long ms) {
            now.addAndGet(ms);
        }
    }

    private final FakeClock clock = new FakeClock();

    private IInternalStateBackend<String> newBackend(String kind) {
        if ("memory".equals(kind)) {
            MemoryStateBackend backend = new MemoryStateBackend();
            @SuppressWarnings("unchecked")
            IInternalStateBackend<String> keyed =
                    (IInternalStateBackend<String>) backend.createKeyedStateBackend(String.class);
            ((MemoryKeyedStateBackend<String>) keyed).setTtlTimeProvider(clock);
            return keyed;
        }
        File dir = new File(tempDir, "rocks-" + (dbSeq++));
        dir.mkdirs();
        RocksDBKeyedStateBackend<String> backend =
                new RocksDBKeyedStateBackend<>(dir.getAbsolutePath(), String.class, 1, null);
        backend.setTtlTimeProvider(clock);
        return backend;
    }

    /** Populates one state of every family on {@code backend} and takes a snapshot. */
    private StateSnapshot populateAllFamilies(IInternalStateBackend<String> backend) throws Exception {
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("mx-value", Long.class)).update(11L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("mx-value", Long.class)).update(22L);

        backend.setCurrentKey("k1");
        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("mx-map", String.class, Long.class));
        ms.put("a", 1L);
        ms.put("b", 2L);

        backend.setCurrentKey("k1");
        ListState<Long> ls = backend.getListState(new ListStateDescriptor<>("mx-list", Long.class));
        ls.add(1L);
        ls.add(2L);

        InternalListState<String, TimeWindow, Long> ils =
                backend.getInternalListState(new ListStateDescriptor<>("mx-internal-list", Long.class));
        ils.setCurrentNamespace(new TimeWindow(1000, 2000));
        ils.add(4L);

        backend.setCurrentKey("k1");
        ReducingState<Long> rs = backend.getReducingState(
                new ReducingStateDescriptor<>("mx-reducing", Long.class, LongCounter.class));
        rs.add(5L);
        rs.add(7L);

        backend.setCurrentKey("k1");
        AggregatingState<Long, Long> as = backend.getAggregatingState(
                new AggregatingStateDescriptor<>("mx-aggregating", new SumAggregate(), Long.class));
        as.add(3L);
        as.add(4L);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> ias =
                backend.getInternalAppendingState(new AggregatingStateDescriptor<>(
                        "mx-internal-aggregating", new SumAggregate(), Long.class));
        ias.setCurrentNamespace(new TimeWindow(0, 100));
        ias.add(6L);

        InternalAppendingState<String, TimeWindow, Long, Long, Long> aps =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("mx-appending", Long.class, LongCounter.class));
        aps.setCurrentNamespace(new TimeWindow(0, 100));
        aps.add(5L);
        aps.add(7L);

        return backend.snapshotState();
    }

    private void assertAllFamiliesRestored(IInternalStateBackend<String> backend) throws Exception {
        // aggregating restore prefers the live function; register before first getState
        backend.registerRestoreAggregateFunction("mx-aggregating", new SumAggregate());
        backend.registerRestoreAggregateFunction("mx-internal-aggregating", new SumAggregate());

        backend.setCurrentKey("k1");
        assertEquals(Long.valueOf(11L), backend.getState(new ValueStateDescriptor<>("mx-value", Long.class)).value());
        backend.setCurrentKey("k2");
        assertEquals(Long.valueOf(22L), backend.getState(new ValueStateDescriptor<>("mx-value", Long.class)).value());

        backend.setCurrentKey("k1");
        MapState<String, Long> ms = backend.getMapState(
                new MapStateDescriptor<>("mx-map", String.class, Long.class));
        assertEquals(Long.valueOf(1L), ms.get("a"));
        assertEquals(Long.valueOf(2L), ms.get("b"));

        ListState<Long> ls = backend.getListState(new ListStateDescriptor<>("mx-list", Long.class));
        int n = 0;
        for (Long ignored : ls.get()) {
            n++;
        }
        assertEquals(2, n);

        InternalListState<String, TimeWindow, Long> ils =
                backend.getInternalListState(new ListStateDescriptor<>("mx-internal-list", Long.class));
        ils.setCurrentNamespace(new TimeWindow(1000, 2000));
        n = 0;
        for (Long ignored : ils.get()) {
            n++;
        }
        assertEquals(1, n);

        backend.setCurrentKey("k1");
        assertEquals(Long.valueOf(12L), backend.getReducingState(
                new ReducingStateDescriptor<>("mx-reducing", Long.class, LongCounter.class)).get());

        assertEquals(Long.valueOf(7L), backend.getAggregatingState(
                new AggregatingStateDescriptor<>("mx-aggregating", new SumAggregate(), Long.class)).get());

        InternalAppendingState<String, TimeWindow, Long, Long, Long> ias =
                backend.getInternalAppendingState(new AggregatingStateDescriptor<>(
                        "mx-internal-aggregating", new SumAggregate(), Long.class));
        ias.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(6L), ias.get());

        InternalAppendingState<String, TimeWindow, Long, Long, Long> aps =
                backend.getInternalAppendingState(
                        new ReducingStateDescriptor<>("mx-appending", Long.class, LongCounter.class));
        aps.setCurrentNamespace(new TimeWindow(0, 100));
        assertEquals(Long.valueOf(12L), aps.get());
    }

    // ==================== family x backend matrix ====================

    private void runMatrix(String from, String to) throws Exception {
        IInternalStateBackend<String> producer = newBackend(from);
        StateSnapshot snapshot = populateAllFamilies(producer);
        if (producer instanceof RocksDBKeyedStateBackend) {
            ((RocksDBKeyedStateBackend<String>) producer).close();
        }

        IInternalStateBackend<String> consumer = newBackend(to);
        consumer.restoreState(snapshot);
        assertAllFamiliesRestored(consumer);
        if (consumer instanceof RocksDBKeyedStateBackend) {
            ((RocksDBKeyedStateBackend<String>) consumer).close();
        }
    }

    @Test
    void matrixMemoryToMemory() throws Exception {
        runMatrix("memory", "memory");
    }

    @Test
    void matrixMemoryToRocks() throws Exception {
        runMatrix("memory", "rocks");
    }

    @Test
    void matrixRocksToRocks() throws Exception {
        runMatrix("rocks", "rocks");
    }

    @Test
    void matrixRocksToMemory() throws Exception {
        runMatrix("rocks", "memory");
    }

    // ==================== TTL variants (per backend) ====================

    private void runTtlVariant(String kind, boolean repeatedGetStateMode) throws Exception {
        IInternalStateBackend<String> backend = newBackend(kind);
        StateTtlConfig ttl = StateTtlConfig.newBuilder(Duration.ofSeconds(10)).build();

        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("ttl-value", Long.class);
        desc.setTtlConfig(ttl);

        // timeline: dead written at t=0; live written at t=15s; snapshot at t=17s.
        // dead age 17s > 10s ttl -> expired; live age 2s <= 10s -> alive.
        //
        // repeatedGetStateMode=true exercises getState() per access (RK-4: an unchanged
        // TTL config must keep the accumulated sidecar — pinned on rocksdb today). The
        // memory backend currently rebinds a fresh sidecar on every getState (the
        // RK-4 core-side twin); its Phase 3 fix will flip this flag for memory too.
        ValueState<Long> handle = null;
        if (!repeatedGetStateMode) {
            handle = backend.getState(desc);
        }

        backend.setCurrentKey("dead");
        (repeatedGetStateMode ? backend.getState(desc) : handle).update(2L);
        clock.advance(15_000);

        backend.setCurrentKey("live");
        (repeatedGetStateMode ? backend.getState(desc) : handle).update(1L);
        clock.advance(2_000);

        StateSnapshot snapshot = backend.snapshotState();

        IInternalStateBackend<String> restored = newBackend(kind);
        restored.restoreState(snapshot);

        ValueStateDescriptor<Long> restoredDesc = new ValueStateDescriptor<>("ttl-value", Long.class);
        restoredDesc.setTtlConfig(ttl);
        restored.setCurrentKey("live");
        assertEquals(Long.valueOf(1L), restored.getState(restoredDesc).value());
        restored.setCurrentKey("dead");
        assertNull(restored.getState(restoredDesc).value(),
                "entry expired at snapshot time must not survive the round-trip");

        if (backend instanceof RocksDBKeyedStateBackend) {
            ((RocksDBKeyedStateBackend<String>) backend).close();
        }
        if (restored instanceof RocksDBKeyedStateBackend) {
            ((RocksDBKeyedStateBackend<String>) restored).close();
        }
    }

    @Test
    void ttlVariantMemory() throws Exception {
        // RK-4 core twin fixed in Phase 3 (applyTtl keeps an unchanged TTL
        // context): memory now exercises the repeated-getState mode too.
        runTtlVariant("memory", true);
    }

    @Test
    void ttlVariantRocks() throws Exception {
        runTtlVariant("rocks", true);
    }

    // ==================== namespace variants ====================

    @Test
    void timeWindowNamespaceSurvivesCrossBackendRoundTrip() throws Exception {
        IInternalStateBackend<String> mem = newBackend("memory");
        InternalListState<String, TimeWindow, Long> ils =
                mem.getInternalListState(new ListStateDescriptor<>("ns-list", Long.class));
        ils.setCurrentNamespace(new TimeWindow(100, 200));
        ils.add(1L);
        ils.setCurrentNamespace(new TimeWindow(300, 400));
        ils.add(2L);
        StateSnapshot snapshot = mem.snapshotState();

        IInternalStateBackend<String> rocks = newBackend("rocks");
        rocks.restoreState(snapshot);
        InternalListState<String, TimeWindow, Long> restored =
                rocks.getInternalListState(new ListStateDescriptor<>("ns-list", Long.class));
        restored.setCurrentNamespace(new TimeWindow(100, 200));
        int first = 0;
        for (Long ignored : restored.get()) {
            first++;
        }
        restored.setCurrentNamespace(new TimeWindow(300, 400));
        int second = 0;
        for (Long ignored : restored.get()) {
            second++;
        }
        assertEquals(1, first);
        assertEquals(1, second);
        ((RocksDBKeyedStateBackend<String>) rocks).close();
    }
}
