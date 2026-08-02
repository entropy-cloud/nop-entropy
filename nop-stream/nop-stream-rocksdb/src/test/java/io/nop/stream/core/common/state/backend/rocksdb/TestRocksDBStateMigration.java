/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.nio.file.Path;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 33: verifies the state-migration wiring in {@link RocksDBKeyedStateBackend}.
 * Mirrors {@code TestStateMigration} (memory) for the RocksDB backend: a registered
 * migration function converts Integer ValueState to Long on restore; without a
 * registered function the backend fails fast.
 */
class TestRocksDBStateMigration {

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

    @TempDir
    File tempDir;

    @Test
    void registeredMigrationConvertsIntegerStateToLong() throws Exception {
        // 1. Build a checkpoint with Integer ValueState
        Path origDir = tempDir.toPath().resolve("orig");
        RocksDBKeyedStateBackend<String> backend =
                new RocksDBKeyedStateBackend<>(origDir.toString(), String.class, 1, null);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(42);
        backend.setCurrentKey("k2");
        backend.getState(intDesc).update(99);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        // 2. Restore into a fresh backend and register the Integer→Long migration
        Path restoreDir = tempDir.toPath().resolve("restored");
        RocksDBKeyedStateBackend<String> restored =
                new RocksDBKeyedStateBackend<>(restoreDir.toString(), String.class, 1, null);
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

        // Anti-hollow: the value is genuinely a Long
        restored.setCurrentKey("k1");
        assertEquals(Long.class, restored.getState(longDesc).value().getClass());
        restored.close();
    }

    @Test
    void noMigrationRegisteredThrowsSchemaMismatch() throws Exception {
        Path origDir = tempDir.toPath().resolve("orig");
        RocksDBKeyedStateBackend<String> backend =
                new RocksDBKeyedStateBackend<>(origDir.toString(), String.class, 1, null);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(7);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        Path restoreDir = tempDir.toPath().resolve("restored");
        RocksDBKeyedStateBackend<String> restored =
                new RocksDBKeyedStateBackend<>(restoreDir.toString(), String.class, 1, null);
        restored.restoreState(snapshot);

        // No migration registry set → fail-fast
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restored.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        restored.close();
    }

    @Test
    void migrationIsIdempotentOnRepeatGetState() throws Exception {
        Path origDir = tempDir.toPath().resolve("orig");
        RocksDBKeyedStateBackend<String> backend =
                new RocksDBKeyedStateBackend<>(origDir.toString(), String.class, 1, null);
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("k1");
        backend.getState(intDesc).update(42);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        Path restoreDir = tempDir.toPath().resolve("restored");
        RocksDBKeyedStateBackend<String> restored =
                new RocksDBKeyedStateBackend<>(restoreDir.toString(), String.class, 1, null);
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
        restored.close();
    }
}
