/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 33 Phase 3: end-to-end validation of the Integer→Long state migration.
 *
 * <p>Exercises the full chain: {@code getState(ValueStateDescriptor<Integer>)} → snapshot →
 * {@link CompletedCheckpoint} → {@link LocalFileCheckpointStorage} persist (JSON via
 * {@link CheckpointSerDe}) → reload → {@code restoreState} → register migration →
 * {@code getState(ValueStateDescriptor<Long>)} → migration fires → values converted.
 *
 * <p>Covers both the {@link MemoryKeyedStateBackend} and {@link RocksDBKeyedStateBackend}.
 * A control test without a registered migration confirms the migration is explicit
 * (no silent degradation to fail-fast).
 */
class TestStateMigrationEndToEnd {

    private static final TaskLocation LOC = new TaskLocation("migration-job", "pipe", "v0", 0);

    /**
     * Integer→Long migration function used across all E2E scenarios.
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

    @TempDir
    File tempDir;

    /**
     * Builds a CompletedCheckpoint containing a single keyed state backend's snapshot
     * with Integer ValueState holding two keys.
     */
    private CompletedCheckpoint buildCheckpointWithIntegerState(IKeyedStateBackend<String> backend) throws Exception {
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("counter", Integer.class);
        backend.setCurrentKey("key1");
        backend.getState(desc).update(100);
        backend.setCurrentKey("key2");
        backend.getState(desc).update(200);

        StateSnapshot keyedSnapshot = backend.snapshotState();
        TaskStateSnapshot taskSnapshot = new TaskStateSnapshot(LOC);
        taskSnapshot.putKeyedState("backend-0", keyedSnapshot);

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(LOC, taskSnapshot);

        return CompletedCheckpoint.builder()
                .jobId("migration-job")
                .pipelineId("pipe")
                .checkpointId(1L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();
    }

    /**
     * Memory backend E2E: Integer checkpoint → JSON persist → reload → register migration →
     * restore → getState(Long) → values converted from Integer to Long.
     */
    @Test
    void memoryIntegerToLongMigrationFullRoundTrip() throws Exception {
        Path storageDir = tempDir.toPath().resolve("cp-storage");

        // 1. Build + persist Integer checkpoint
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);
        CompletedCheckpoint checkpoint = buildCheckpointWithIntegerState(backend);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        String path = storage.storeCheckPoint(checkpoint);
        byte[] persisted = Files.readAllBytes(Path.of(path));
        backend.close();

        // 2. Deserialize + restore into fresh memory backend with migration registered
        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(persisted);
        TaskStateSnapshot restoredTask = restored.getTaskStates().get(LOC);
        assertNotNull(restoredTask);
        Object rawKeyedState = restoredTask.getKeyedState("backend-0");
        assertNotNull(rawKeyedState);

        MemoryKeyedStateBackend<String> restoredBackend = new MemoryKeyedStateBackend<>(String.class);
        @SuppressWarnings("unchecked")
        StateSnapshot restoredSnapshot = new StateSnapshot((Map<String, Object>) rawKeyedState);
        restoredBackend.restoreState(restoredSnapshot);

        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", new IntegerToLongMigration("counter"));
        restoredBackend.setMigrationRegistry(components);

        // 3. getState with Long descriptor → migration fires, values converted
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        restoredBackend.setCurrentKey("key1");
        ValueState<Long> state = restoredBackend.getState(longDesc);
        assertEquals(Long.valueOf(100L), state.value());
        // Anti-hollow: type is genuinely Long
        assertEquals(Long.class, state.value().getClass());
        restoredBackend.setCurrentKey("key2");
        assertEquals(Long.valueOf(200L), restoredBackend.getState(longDesc).value());
        assertEquals(Long.class, restoredBackend.getState(longDesc).value().getClass());

        restoredBackend.close();
    }

    /**
     * Memory control test: no migration registered → fail-fast (proves migration is explicit).
     */
    @Test
    void memoryNoMigrationFailsFast() throws Exception {
        Path storageDir = tempDir.toPath().resolve("cp-storage-ctrl");

        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);
        CompletedCheckpoint checkpoint = buildCheckpointWithIntegerState(backend);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        String path = storage.storeCheckPoint(checkpoint);
        byte[] persisted = Files.readAllBytes(Path.of(path));
        backend.close();

        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(persisted);
        TaskStateSnapshot restoredTask = restored.getTaskStates().get(LOC);
        Object rawKeyedState = restoredTask.getKeyedState("backend-0");

        MemoryKeyedStateBackend<String> restoredBackend = new MemoryKeyedStateBackend<>(String.class);
        @SuppressWarnings("unchecked")
        StateSnapshot restoredSnapshot = new StateSnapshot((Map<String, Object>) rawKeyedState);
        restoredBackend.restoreState(restoredSnapshot);
        // No migration registered

        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restoredBackend.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        restoredBackend.close();
    }

    /**
     * RocksDB backend E2E: Integer checkpoint → JSON persist → reload → register migration →
     * restore → getState(Long) → values converted from Integer to Long.
     */
    @Test
    void rocksdbIntegerToLongMigrationFullRoundTrip() throws Exception {
        Path storageDir = tempDir.toPath().resolve("cp-storage-rdb");
        Path origDbDir = tempDir.toPath().resolve("orig-db");

        // 1. Build + persist Integer checkpoint
        RocksDBStateBackend stateBackend = new RocksDBStateBackend(origDbDir.toString(), 1);
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);
        CompletedCheckpoint checkpoint = buildCheckpointWithIntegerState(backend);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        String path = storage.storeCheckPoint(checkpoint);
        byte[] persisted = Files.readAllBytes(Path.of(path));
        backend.close();

        // 2. Deserialize + restore into fresh rocksdb backend with migration registered
        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(persisted);
        TaskStateSnapshot restoredTask = restored.getTaskStates().get(LOC);
        assertNotNull(restoredTask);
        Object rawKeyedState = restoredTask.getKeyedState("backend-0");
        assertNotNull(rawKeyedState);

        Path restoreDbDir = tempDir.toPath().resolve("restore-db");
        RocksDBKeyedStateBackend<String> restoredBackend =
                new RocksDBKeyedStateBackend<>(restoreDbDir.toString(), String.class, 1, null);
        @SuppressWarnings("unchecked")
        StateSnapshot restoredSnapshot = new StateSnapshot((Map<String, Object>) rawKeyedState);
        restoredBackend.restoreState(restoredSnapshot);

        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", new IntegerToLongMigration("counter"));
        restoredBackend.setMigrationRegistry(components);

        // 3. getState with Long descriptor → migration fires, values converted
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        restoredBackend.setCurrentKey("key1");
        ValueState<Long> state = restoredBackend.getState(longDesc);
        assertEquals(Long.valueOf(100L), state.value());
        // Anti-hollow: type is genuinely Long
        assertEquals(Long.class, state.value().getClass());
        restoredBackend.setCurrentKey("key2");
        assertEquals(Long.valueOf(200L), restoredBackend.getState(longDesc).value());
        assertEquals(Long.class, restoredBackend.getState(longDesc).value().getClass());

        restoredBackend.close();
    }

    /**
     * RocksDB control test: no migration registered → fail-fast.
     */
    @Test
    void rocksdbNoMigrationFailsFast() throws Exception {
        Path storageDir = tempDir.toPath().resolve("cp-storage-rdb-ctrl");
        Path origDbDir = tempDir.toPath().resolve("orig-db-ctrl");

        RocksDBStateBackend stateBackend = new RocksDBStateBackend(origDbDir.toString(), 1);
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);
        CompletedCheckpoint checkpoint = buildCheckpointWithIntegerState(backend);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        String path = storage.storeCheckPoint(checkpoint);
        byte[] persisted = Files.readAllBytes(Path.of(path));
        backend.close();

        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(persisted);
        TaskStateSnapshot restoredTask = restored.getTaskStates().get(LOC);
        Object rawKeyedState = restoredTask.getKeyedState("backend-0");

        Path restoreDbDir = tempDir.toPath().resolve("restore-db-ctrl");
        RocksDBKeyedStateBackend<String> restoredBackend =
                new RocksDBKeyedStateBackend<>(restoreDbDir.toString(), String.class, 1, null);
        @SuppressWarnings("unchecked")
        StateSnapshot restoredSnapshot = new StateSnapshot((Map<String, Object>) rawKeyedState);
        restoredBackend.restoreState(restoredSnapshot);
        // No migration registered

        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);
        StreamException ex = assertThrows(StreamException.class, () -> restoredBackend.getState(longDesc));
        assertEquals(ERR_STREAM_STATE_SCHEMA_MISMATCH.getErrorCode(), ex.getErrorCode());
        restoredBackend.close();
    }
}
