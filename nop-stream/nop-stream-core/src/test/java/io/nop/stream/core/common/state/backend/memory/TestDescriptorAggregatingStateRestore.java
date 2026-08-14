package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-01 (Decision: 方案 1 = live function reuse) — descriptor-path roundtrip
 * for the Memory backend: an aggregating state whose {@code AggregateFunction}
 * is a CAPTURING class (no no-arg constructor — e.g. the
 * {@code WindowOperatorBuilder.reduceFunctionAsAggregate} wrapper or any
 * lambda/anonymous class) must snapshot and restore successfully when the live
 * function is registered via
 * {@code IKeyedStateBackend#registerRestoreAggregateFunction}, and must FAIL
 * FAST (not silently degrade) when no live function is registered (the legacy
 * class-name reflection path cannot instantiate capturing classes).
 */
class TestDescriptorAggregatingStateRestore {

    /**
     * Capturing aggregate function with NO no-arg constructor — the reflection
     * path ({@code getDeclaredConstructor().newInstance()}) throws
     * NoSuchMethodException. The captured {@code offset} proves at restore time
     * that the LIVE instance (not a recreated one) is in use: the restored
     * state's result is the raw accumulated sum + offset.
     */
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

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setCurrentKey("k1");
        InternalAppendingState<String, Object, Long, long[], Long> state =
                backend.getInternalAppendingState(desc);
        state.setCurrentNamespace("w1");
        state.add(3L);
        state.add(4L);
        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot, "Snapshot must capture the accumulated window contents");
        backend.close();

        // Fresh backend simulating a job restart. WindowOperator registers its
        // live descriptor function before the deferred restore runs — mirror that.
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.registerRestoreAggregateFunction("window-contents", liveFn);
        restored.restoreState(snapshot);

        InternalAppendingState<String, Object, Long, long[], Long> restoredState =
                restored.getInternalAppendingState(desc);
        restored.setCurrentKey("k1");
        restoredState.setCurrentNamespace("w1");
        assertEquals(7L, restoredState.getAccumulator()[0],
                "Accumulated sum must be restored");

        // Wiring verification (Rule #23): the restored state's descriptor must
        // hold the LIVE function instance (identity), not a reflection-recreated
        // one — this is the operator-context handoff the fix establishes.
        assertTrue(restoredState instanceof MemoryInternalAggregatingState,
                "Memory backend must restore an internal aggregating state");
        MemoryInternalAggregatingState<?, ?, ?, ?, ?> memState =
                (MemoryInternalAggregatingState<?, ?, ?, ?, ?>) restoredState;
        assertSame(liveFn, memState.descriptor.getAggregateFunction(),
                "Restored descriptor must reuse the LIVE aggregate function instance");

        // Behavioral proof of live-instance usage: getResult applies the captured
        // offset (100) — a recreated instance could not carry it.
        assertEquals(107L, restoredState.get(),
                "Restored state must compute results with the live function's captured state");
        restored.close();
    }

    @Test
    void testRestoreWithoutLiveFunctionFailsFastForCapturingClass() throws Exception {
        CapturingSumAggregateFunction liveFn = new CapturingSumAggregateFunction(0L);
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("window-contents", liveFn, long[].class);

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setCurrentKey("k1");
        InternalAppendingState<String, Object, Long, long[], Long> state =
                backend.getInternalAppendingState(desc);
        state.setCurrentNamespace("w1");
        state.add(1L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        // No provider registered (legacy path): reflection cannot instantiate the
        // capturing class → restore must FAIL FAST with a clear error, never
        // silently produce an empty/broken state (No-Silent-No-Op rule #24).
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        assertThrows(StreamException.class, () -> restored.restoreState(snapshot),
                "Restore of a capturing aggregate function without a registered live "
                        + "function must fail fast (NoSuchMethodException → StreamException)");
        restored.close();
    }
}
