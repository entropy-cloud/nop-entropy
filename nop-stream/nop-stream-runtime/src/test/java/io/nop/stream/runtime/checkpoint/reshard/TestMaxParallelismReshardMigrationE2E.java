/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.reshard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 37 E2E: the {@code maxParallelism} reshard migration tool
 * ({@link MaxParallelismReshardMigration}).
 *
 * <p>Exercises the full offline path: stage a keyed savepoint at
 * {@code maxParallelism=128} (parallelism=4) → persist → run reshard to
 * {@code 256} → reload the new savepoint → assert key conservation, new-group
 * routing, and stamped ownership under the new {@code maxParallelism}. Then
 * restores each new subtask's slice into a fresh keyed backend (memory + rocksdb)
 * and verifies the aggregate result equals the original input — proving the
 * reshard produced restorable, correct data laid out for the new
 * {@code maxParallelism} (not a hollow copy).
 *
 * <p>Covers the plan's Exit Criteria: 迁移正确性单测 (128→256 + 256→128),
 * restore 正确性 E2E (memory + rocksdb), 端到端 (read→reshard→write→reload),
 * 边界 (empty keyed state, operator-only no-op, old==new fail-fast), and
 * 无静默跳过 (unknown structure fails fast).
 */
class TestMaxParallelismReshardMigrationE2E {

    private static final int OLD_MAX_P = 128;
    private static final int PARALLELISM = 4;
    private static final String STATE_NAME = "count";
    private static final String KEYED_KEY = "operator-1-keyed";
    private static final String VERTEX = "kv-vertex";

    @TempDir
    Path tempDir;

    /** Build a keyed ValueState stateData map for the given (key -> value) entries. */
    private static Map<String, Object> buildKeyedStateData(Map<String, Long> entries) {
        List<Map<String, Object>> entryList = new ArrayList<>();
        for (Map.Entry<String, Long> e : entries.entrySet()) {
            Map<String, Object> en = new LinkedHashMap<>();
            en.put("namespace", "_default_");
            en.put("key", e.getKey());
            en.put("value", e.getValue());
            entryList.add(en);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", "java.lang.Long");
        info.put("schemaChecksum", "long-checksum");
        info.put("schemaVersion", 1);
        info.put("entries", entryList);

        Map<String, Object> states = new LinkedHashMap<>();
        states.put(STATE_NAME, info);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyType", "java.lang.String");
        data.put("states", states);
        return data;
    }

    /** Stage a real on-disk checkpoint at OLD_MAX_P / PARALLELISM, keys sharded by old owner subtask. */
    private String stageSavepoint(Map<String, Long> allKeys, String jobId, String pipelineId) {
        // Partition keys by their old (maxP=128, p=4) owner subtask.
        List<List<Map.Entry<String, Long>>> perSubtask = new ArrayList<>();
        for (int s = 0; s < PARALLELISM; s++) perSubtask.add(new ArrayList<>());
        for (Map.Entry<String, Long> e : allKeys.entrySet()) {
            int gid = KeyGroupAssignment.assignToKeyGroup(e.getKey(), OLD_MAX_P);
            int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, OLD_MAX_P, PARALLELISM);
            perSubtask.get(sub).add(e);
        }

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < PARALLELISM; s++) {
            TaskLocation loc = new TaskLocation(jobId, pipelineId, VERTEX, s);
            Map<String, Long> subEntries = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : perSubtask.get(s)) subEntries.put(e.getKey(), e.getValue());
            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_KEY, buildKeyedStateData(subEntries));
            taskStates.put(loc, ts);
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId).pipelineId(pipelineId).checkpointId(1L)
                .triggerTimestamp(1000L).completedTimestamp(2000L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("old-store").toString());
        return storage.storeCheckPoint(checkpoint);
    }

    private Map<String, Long> buildKeys(int count) {
        Map<String, Long> keys = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) keys.put("reshard-key-" + i, (long) (i + 1));
        return keys;
    }

    @Test
    void reshardUp128to256_conservesKeysAndRestoresUnderNewMaxParallelism_memory() throws Exception {
        Map<String, Long> allKeys = buildKeys(300);
        String oldPath = stageSavepoint(allKeys, "up-job", "up-pipe");
        Path outBase = tempDir.resolve("out-up");

        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                oldPath, OLD_MAX_P, 256, outBase.toString());

        assertEquals(OLD_MAX_P, result.getOldMaxParallelism());
        assertEquals(256, result.getNewMaxParallelism());
        assertEquals(allKeys.size(), result.totalKeyedEntries(),
                "per-state key count conserved across migration");
        assertTrue(result.getKeyCountByState().containsKey(STATE_NAME));

        // Reload the new savepoint from disk (end-to-end: read old -> reshard -> write -> read new).
        CompletedCheckpoint reshaped = CheckpointSerDe.deserializeCheckpoint(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(result.getNewSavepointPath())
                        .resolve("1.checkpoint")));
        assertNotNull(reshaped);

        // Restore each new subtask slice into a fresh memory backend (maxP=256)
        // and collect (key -> value) via getState. The union must equal the input.
        Map<String, Long> restored = restoreAndCollectMemory(reshaped, 256);
        assertEquals(allKeys, restored, "restored aggregate must equal original input (memory)");

        // Prove the reshard took effect under the NEW maxParallelism: at least one
        // key must be owned by a different subtask than under the old maxParallelism.
        assertAtLeastOneKeyMovedSubtask(reshaped, 256);
    }

    @Test
    void reshardDown256to128_conservesKeys() throws Exception {
        // Stage at maxP=256, reshard down to 128.
        int oldMax = 256;
        Map<String, Long> allKeys = buildKeys(300);
        String oldPath = stageSavepointAt(allKeys, "dn-job", "dn-pipe", oldMax);

        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                oldPath, oldMax, 128, tempDir.resolve("out-dn").toString());
        assertEquals(allKeys.size(), result.totalKeyedEntries(), "downscale reshard conserves keys");

        CompletedCheckpoint reshaped = CheckpointSerDe.deserializeCheckpoint(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(result.getNewSavepointPath())
                        .resolve("1.checkpoint")));
        Map<String, Long> restored = restoreAndCollectMemory(reshaped, 128);
        assertEquals(allKeys, restored, "restored aggregate must equal original input (downscale, memory)");
    }

    @Test
    void restoreCorrectness_rocksdb_afterReshard() throws Exception {
        Map<String, Long> allKeys = buildKeys(150);
        String oldPath = stageSavepoint(allKeys, "rdb-job", "rdb-pipe");
        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                oldPath, OLD_MAX_P, 256, tempDir.resolve("out-rdb").toString());

        CompletedCheckpoint reshaped = CheckpointSerDe.deserializeCheckpoint(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(result.getNewSavepointPath())
                        .resolve("1.checkpoint")));

        Map<String, Long> restored = restoreAndCollectRocksDB(reshaped, 256);
        assertEquals(allKeys, restored, "restored aggregate must equal original input (rocksdb)");
    }

    @Test
    void emptyKeyedState_migratesWithoutLoss() throws Exception {
        // Stage a vertex with an empty keyed state (no entries).
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < PARALLELISM; s++) {
            TaskLocation loc = new TaskLocation("empty-job", "empty-pipe", VERTEX, s);
            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_KEY, buildKeyedStateData(new LinkedHashMap<>()));
            taskStates.put(loc, ts);
        }
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("empty-job").pipelineId("empty-pipe").checkpointId(1L)
                .triggerTimestamp(1L).completedTimestamp(2L)
                .checkpointType(CheckpointType.SAVEPOINT).taskStates(taskStates).build();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("empty-store").toString());
        String path = storage.storeCheckPoint(checkpoint);

        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                path, OLD_MAX_P, 256, tempDir.resolve("out-empty").toString());
        assertEquals(0, result.totalKeyedEntries(), "empty keyed state stays empty (no loss)");
    }

    @Test
    void operatorOnlyJob_isExplicitNoOpWithWarning() throws Exception {
        // A job with only operator (non-keyed) state: reshard is structural no-op for it.
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < PARALLELISM; s++) {
            TaskLocation loc = new TaskLocation("op-job", "op-pipe", VERTEX, s);
            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putOperatorState("op-list", java.util.List.of("a", "b"));
            taskStates.put(loc, ts);
        }
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("op-job").pipelineId("op-pipe").checkpointId(1L)
                .triggerTimestamp(1L).completedTimestamp(2L)
                .checkpointType(CheckpointType.SAVEPOINT).taskStates(taskStates).build();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("op-store").toString());
        String path = storage.storeCheckPoint(checkpoint);

        ReshardMigrationResult result = MaxParallelismReshardMigration.migrate(
                path, OLD_MAX_P, 256, tempDir.resolve("out-op").toString());
        assertEquals(0, result.totalKeyedEntries());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no keyed state") || w.contains("operator")),
                "operator-only job must record an explicit no-op warning: " + result.getWarnings());
        // operator state survived intact (4 subtasks x 1 op state each).
        assertEquals(PARALLELISM, result.getOperatorStateCount());
    }

    @Test
    void oldEqualsNewMaxParallelism_failsFast() throws Exception {
        Map<String, Long> allKeys = buildKeys(50);
        String oldPath = stageSavepoint(allKeys, "eq-job", "eq-pipe");
        StreamException ex = assertThrows(StreamException.class, () ->
                MaxParallelismReshardMigration.migrate(oldPath, OLD_MAX_P, OLD_MAX_P,
                        tempDir.resolve("out-eq").toString()));
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void unknownKeyedStructure_failsFastNoSilentDrop() throws Exception {
        // A keyed value that is an opaque string (not StateSnapshot / stateData map).
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        TaskLocation loc = new TaskLocation("bad-job", "bad-pipe", VERTEX, 0);
        TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
        ts.putKeyedState(KEYED_KEY, "opaque-non-map-value");
        taskStates.put(loc, ts);
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("bad-job").pipelineId("bad-pipe").checkpointId(1L)
                .triggerTimestamp(1L).completedTimestamp(2L)
                .checkpointType(CheckpointType.SAVEPOINT).taskStates(taskStates).build();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("bad-store").toString());
        String path = storage.storeCheckPoint(checkpoint);

        assertThrows(StreamException.class, () ->
                MaxParallelismReshardMigration.migrate(path, OLD_MAX_P, 256,
                        tempDir.resolve("out-bad").toString()),
                "unknown keyed structure must fail-fast, not be silently dropped");
    }

    @Test
    void stampedOwnershipUsesNewMaxParallelism() throws Exception {
        Map<String, Long> allKeys = buildKeys(120);
        String oldPath = stageSavepoint(allKeys, "own-job", "own-pipe");

        // Read the old checkpoint and reshard in memory: the returned checkpoint
        // stamps each subtask's ownership under the NEW maxParallelism. (The on-disk
        // `.checkpoint` format persists the redistribution but re-derives ownership
        // metadata at restore time from the execution plan — consistent with how the
        // platform treats all savepoints — so ownership is verified on the in-memory
        // result. The persisted key layout is verified by the restore E2E above.)
        CompletedCheckpoint old = CheckpointSerDe.deserializeCheckpoint(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(oldPath)));
        MaxParallelismReshardMigration.Resharded resharded =
                MaxParallelismReshardMigration.reshardCheckpoint(old, OLD_MAX_P, 256);
        CompletedCheckpoint reshaped = resharded.getCheckpoint();
        int newMaxP = 256;

        for (TaskStateSnapshot ts : reshaped.getTaskStates().values()) {
            assertTrue(ts instanceof io.nop.stream.core.checkpoint.TaskEpochSnapshot,
                    "migrated subtask snapshots must be TaskEpochSnapshot with materialized ownership");
            io.nop.stream.core.checkpoint.TaskEpochSnapshot epoch =
                    (io.nop.stream.core.checkpoint.TaskEpochSnapshot) ts;
            assertEquals(newMaxP, epoch.getMaxParallelism(),
                    "stamped maxParallelism must be the NEW value");
            assertEquals(PARALLELISM, epoch.getParallelism());
            assertTrue(epoch.isKeyGroupOwnershipMaterialized());
            KeyGroupRange range = epoch.getKeyGroupRange();
            assertNotNull(range);
            // Range consistent with computeKeyGroupRangeForSubtaskIndex under new maxParallelism
            KeyGroupRange expected = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                    newMaxP, PARALLELISM, ts.getTaskLocation().getTaskIndex());
            assertEquals(expected, range);
        }
    }

    // ---- helpers ----

    private String stageSavepointAt(Map<String, Long> allKeys, String jobId, String pipelineId, int oldMaxP) {
        List<List<Map.Entry<String, Long>>> perSubtask = new ArrayList<>();
        for (int s = 0; s < PARALLELISM; s++) perSubtask.add(new ArrayList<>());
        for (Map.Entry<String, Long> e : allKeys.entrySet()) {
            int gid = KeyGroupAssignment.assignToKeyGroup(e.getKey(), oldMaxP);
            int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, oldMaxP, PARALLELISM);
            perSubtask.get(sub).add(e);
        }
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < PARALLELISM; s++) {
            TaskLocation loc = new TaskLocation(jobId, pipelineId, VERTEX, s);
            Map<String, Long> subEntries = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : perSubtask.get(s)) subEntries.put(e.getKey(), e.getValue());
            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_KEY, buildKeyedStateData(subEntries));
            taskStates.put(loc, ts);
        }
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId).pipelineId(pipelineId).checkpointId(1L)
                .triggerTimestamp(1L).completedTimestamp(2L)
                .checkpointType(CheckpointType.SAVEPOINT).taskStates(taskStates).build();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.resolve("old-store-" + oldMaxP).toString());
        return storage.storeCheckPoint(checkpoint);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> keyedStateDataOf(TaskStateSnapshot ts) {
        Object v = ts.getKeyedStates().get(KEYED_KEY);
        if (v instanceof StateSnapshot) {
            return ((StateSnapshot) v).getStateData();
        }
        return (Map<String, Object>) v;
    }

    /**
     * Anti-hollow: in the migrated savepoint, at least one key must be owned by
     * a subtask different from its owner under the OLD maxParallelism — proving
     * the tool recomputed groups under the NEW maxParallelism rather than
     * copying the old layout verbatim.
     */
    @SuppressWarnings("unchecked")
    private void assertAtLeastOneKeyMovedSubtask(CompletedCheckpoint reshaped, int newMaxP) {
        boolean moved = false;
        for (TaskStateSnapshot ts : reshaped.getTaskStates().values()) {
            int subtaskIndex = ts.getTaskLocation().getTaskIndex();
            Map<String, Object> stateData = keyedStateDataOf(ts);
            if (stateData == null) continue;
            Object statesObj = stateData.get("states");
            if (!(statesObj instanceof Map)) continue;
            Object infoObj = ((Map<String, Object>) statesObj).get(STATE_NAME);
            if (!(infoObj instanceof Map)) continue;
            Object entries = ((Map<String, Object>) infoObj).get("entries");
            if (!(entries instanceof List)) continue;
            for (Object e : (List<?>) entries) {
                Object k = ((Map<String, Object>) e).get("key");
                int oldGroup = KeyGroupAssignment.assignToKeyGroup(k, OLD_MAX_P);
                int oldSubtask = KeyGroupAssignment.assignKeyGroupToSubtask(oldGroup, OLD_MAX_P, PARALLELISM);
                if (oldSubtask != subtaskIndex) {
                    moved = true;
                    break;
                }
            }
            if (moved) break;
        }
        assertTrue(moved, "reshard must relocate at least one key to a different subtask under "
                + "the new maxParallelism (else the tool is a hollow copy). newMaxP=" + newMaxP);
        assertNotEquals(OLD_MAX_P, newMaxP);
    }

    private Map<String, Long> restoreAndCollectMemory(CompletedCheckpoint reshaped, int newMaxP) throws Exception {
        return restoreAndCollect(reshaped, newMaxP, true, null);
    }

    private Map<String, Long> restoreAndCollectRocksDB(CompletedCheckpoint reshaped, int newMaxP) throws Exception {
        return restoreAndCollect(reshaped, newMaxP, false,
                tempDir.resolve("rdb-restore-" + System.nanoTime()).toString());
    }

    /**
     * Restore each migrated subtask slice into a fresh backend (memory or rocksdb)
     * configured with the NEW maxParallelism and the subtask's range, then
     * re-snapshot the backend and read back (key -> value). The union across
     * subtasks must equal the original input — exercising the real backend
     * restore/snapshot code paths for both backends and proving the migrated
     * layout is restorable under the new maxParallelism.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Long> restoreAndCollect(CompletedCheckpoint reshaped, int newMaxP,
                                                boolean memory, String rdbPath) throws Exception {
        Map<String, Long> collected = new TreeMap<>();

        for (TaskStateSnapshot ts : reshaped.getTaskStates().values()) {
            int subtaskIndex = ts.getTaskLocation().getTaskIndex();
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                    newMaxP, PARALLELISM, subtaskIndex);
            Map<String, Object> stateData = keyedStateDataOf(ts);
            assertNotNull(stateData, "migrated subtask must carry keyed state data");
            StateSnapshot snapshot = new StateSnapshot(stateData);

            StateSnapshot restored;
            if (memory) {
                MemoryKeyedStateBackend<String> mem = new MemoryKeyedStateBackend<>(String.class, newMaxP);
                mem.setTargetKeyGroupRange(range);
                mem.restoreState(snapshot);
                restored = mem.snapshotState();
                mem.close();
            } else {
                RocksDBKeyedStateBackend<String> rdb =
                        new RocksDBKeyedStateBackend<>(rdbPath + "-" + subtaskIndex, String.class, newMaxP, null);
                rdb.setTargetKeyGroupRange(range);
                rdb.restoreState(snapshot);
                restored = rdb.snapshotState();
                rdb.close();
            }

            // Read entries from the re-snapshot (reflection-free key enumeration).
            Map<String, Object> rsData = restored.getStateData();
            Object rsStates = rsData.get("states");
            if (rsStates instanceof Map) {
                Object infoObj = ((Map<String, Object>) rsStates).get(STATE_NAME);
                if (infoObj instanceof Map) {
                    Object entries = ((Map<String, Object>) infoObj).get("entries");
                    if (entries instanceof List) {
                        for (Object e : (List<?>) entries) {
                            Map<String, Object> entry = (Map<String, Object>) e;
                            Object k = entry.get("key");
                            Object v = entry.get("value");
                            if (k != null) {
                                collected.put(k.toString(), ((Number) v).longValue());
                            }
                        }
                    }
                }
            }
        }
        return collected;
    }
}

