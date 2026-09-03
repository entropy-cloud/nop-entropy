package io.nop.stream.rocksdb;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-01 (Decision: 方案 1 = live function reuse) — descriptor-path roundtrip
 * for the RocksDB backend: an aggregating state whose {@code AggregateFunction}
 * is a CAPTURING class (no no-arg constructor) must snapshot and restore
 * successfully when the live function is registered via
 * {@code IKeyedStateBackend#registerRestoreAggregateFunction}, and must FAIL
 * FAST without a provider (the class-name reflection path cannot instantiate
 * capturing classes).
 */
class TestRocksDBDescriptorAggregatingStateRestore {

    @TempDir
    File tempDir;

    private static final class CapturingSumAggregateFunction implements AggregateFunction<Long, long[], Long> {
        private static final long serialVersionUID = 1L;

        private final long offset;

        CapturingSumAggregateFunction(long offset) {
            this.offset = offset;
        }

        @Override
        public long[] createAccumulator() {
            return new long[]{0};
        }

        @Override
        public long[] add(Long value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public Long getResult(long[] accumulator) {
            return accumulator[0] + offset;
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }

    @Test
    void testInternalAggregatingStateRoundtripUsesLiveFunction() throws Exception {
        CapturingSumAggregateFunction liveFn = new CapturingSumAggregateFunction(100L);
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("window-contents", liveFn, long[].class);

        RocksDBStateBackend factory = new RocksDBStateBackend(tempDir.getAbsolutePath());
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
        backend.setCurrentKey("k1");
        InternalAppendingState<String, Object, Long, long[], Long> state =
                backend.getInternalAppendingState(desc);
        state.setCurrentNamespace("w1");
        state.add(3L);
        state.add(4L);
        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot, "Snapshot must capture the accumulated window contents");
        backend.close();

        // Fresh backend simulating a job restart; WindowOperator registers its
        // live descriptor function before the deferred restore runs.
        RocksDBKeyedStateBackend<String> restored =
                (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
        restored.registerRestoreAggregateFunction("window-contents", liveFn);
        restored.restoreState(snapshot);

        InternalAppendingState<String, Object, Long, long[], Long> restoredState =
                restored.getInternalAppendingState(desc);
        restored.setCurrentKey("k1");
        restoredState.setCurrentNamespace("w1");
        assertEquals(7L, restoredState.getAccumulator()[0],
                "Accumulated sum must be restored");

        // Behavioral proof of live-instance reuse (wiring verification, Rule #23):
        // getResult applies the captured offset (100) — a reflection-recreated
        // instance could not carry it (and cannot even be created).
        assertEquals(107L, restoredState.get(),
                "Restored state must compute results with the live function's captured state");
        restored.close();
    }

    @Test
    void testRestoreWithoutLiveFunctionFailsFastForCapturingClass() throws Exception {
        CapturingSumAggregateFunction liveFn = new CapturingSumAggregateFunction(0L);
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("window-contents", liveFn, long[].class);

        RocksDBStateBackend factory = new RocksDBStateBackend(tempDir.getAbsolutePath());
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
        backend.setCurrentKey("k1");
        InternalAppendingState<String, Object, Long, long[], Long> state =
                backend.getInternalAppendingState(desc);
        state.setCurrentNamespace("w1");
        state.add(1L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored =
                (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
        assertThrows(StreamException.class, () -> restored.restoreState(snapshot),
                "Restore of a capturing aggregate function without a registered live "
                        + "function must fail fast (NoSuchMethodException → StreamException)");
        restored.close();
    }
}
