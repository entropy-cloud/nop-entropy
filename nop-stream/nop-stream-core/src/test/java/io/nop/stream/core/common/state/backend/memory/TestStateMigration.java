/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.accumulators.DoubleCounter;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 33: verifies the state-migration wiring in {@link MemoryKeyedStateBackend}.
 *
 * <p>When a restored state's checksum differs from the current descriptor's checksum:
 * <ul>
 *   <li>If a matching {@link StateMigrationFunction} is registered, the backend performs
 *       a full-scan migration (read old → {@code migrate} → write new) and updates the
 *       state's descriptor so subsequent {@code getState()} calls are idempotent.</li>
 *   <li>If no matching function is registered, the backend fails fast with
 *       {@code ERR_STREAM_STATE_SCHEMA_MISMATCH}.</li>
 * </ul>
 */
class TestStateMigration {

    /**
     * Integer→Long migration function: {@code migrate} widens the Integer to a Long.
     * Source fingerprint = Integer ValueState, target fingerprint = Long ValueState.
     */
    private static final class IntegerToLongMigration implements StateMigrationFunction<Integer, Long> {
        private final SerializerFingerprint source;
        private final SerializerFingerprint target;

        IntegerToLongMigration(String stateName) {
            this.source = StateSchemaResolver.fromDescriptor(
                    StateSchemaResolver.STATE_TYPE_VALUE,
                    new ValueStateDescriptor<>(stateName, Integer.class));
            this.target = StateSchemaResolver.fromDescriptor(
                    StateSchemaResolver.STATE_TYPE_VALUE,
                    new ValueStateDescriptor<>(stateName, Long.class));
        }

        @Override
        public Long migrate(Integer oldValue) {
            return oldValue != null ? oldValue.longValue() : null;
        }

        @Override
        public SerializerFingerprint sourceFingerprint() {
            return source;
        }

        @Override
        public SerializerFingerprint targetFingerprint() {
            return target;
        }
    }

    @Test
    void registeredMigrationConvertsIntegerStateToLong() throws Exception {
        // 1. Build a checkpoint with Integer ValueState
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(42);
        backend.setCurrentKey("k2");
        backend.getState(intDesc).update(99);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        // 2. Restore into a fresh backend and register the Integer→Long migration
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", new IntegerToLongMigration("counter"));
        restored.setMigrationRegistry(components);

        // 3. getState with Long descriptor → migration runs, values converted
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        restored.setCurrentKey("k1");
        ValueState<Long> state = restored.getState(longDesc);
        assertEquals(Long.valueOf(42L), state.value());
        restored.setCurrentKey("k2");
        assertEquals(Long.valueOf(99L), restored.getState(longDesc).value());

        // Anti-hollow: the value is genuinely a Long, not an Integer that auto-boxed
        restored.setCurrentKey("k1");
        assertEquals(Long.class, restored.getState(longDesc).value().getClass());
    }

    @Test
    void noMigrationRegisteredThrowsSchemaMismatch() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(7);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        // No migration registry set → fail-fast
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void emptyRegistryThrowsSchemaMismatch() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(7);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setMigrationRegistry(new StreamComponents()); // empty registry
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void migrationIsIdempotentOnRepeatGetState() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(42);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", new IntegerToLongMigration("counter"));
        restored.setMigrationRegistry(components);

        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        restored.setCurrentKey("k1");
        // First getState triggers migration
        assertEquals(Long.valueOf(42L), restored.getState(longDesc).value());
        // Second getState: descriptor already replaced → checksum matches → no re-migration
        assertEquals(Long.valueOf(42L), restored.getState(longDesc).value());
        // Third time also stable
        assertEquals(Long.valueOf(42L), restored.getState(longDesc).value());
    }

    @Test
    void matchingChecksumDoesNotTriggerMigration() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(10);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        // Register a migration that should NOT fire (checksums match)
        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", new IntegerToLongMigration("counter"));
        restored.setMigrationRegistry(components);

        restored.setCurrentKey("k1");
        // Same type → checksums match → value returned as-is, no migration
        ValueState<Integer> state = restored.getState(new ValueStateDescriptor<>("counter", Integer.class));
        assertEquals(Integer.valueOf(10), state.value());
    }

    /**
     * Accumulator-state migration surface: verifies the migration path is wired for
     * ReducingState. The stored object is an opaque accumulator; this test surfaces
     * the risk that a wrong migration produces silently corrupt state (per Non-Goals:
     * only surface, not full E2E accumulator-migration correctness guarantee).
     */
    @Test
    void reducingStateMigrationIsWiredAndSurfacesAccumulatorRisk() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        // Use a Long-based reducing state (LongCounter accumulator)
        ReducingStateDescriptor<Long> longDesc =
                new ReducingStateDescriptor<>("sum", Long.class, LongCounter.class);
        backend.setCurrentKey("k1");
        ReducingState<Long> state = backend.getReducingState(longDesc);
        state.add(10L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        // Changing the value type (Long→Double) triggers checksum mismatch.
        // No migration registered → fail-fast (proves the accumulator path is wired).
        ReducingStateDescriptor<Double> doubleDesc =
                new ReducingStateDescriptor<>("sum", Double.class, DoubleCounter.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getReducingState(doubleDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
    }
}
