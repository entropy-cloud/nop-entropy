/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 35: per-subtask KeyGroupRange partial restore on the Memory backend.
 *
 * <p>Anti-hollow: a full snapshot containing keys spread across all key groups is
 * restored into several backends, each carrying a different target
 * {@link KeyGroupRange}. After restore each backend must hold <em>only</em> the
 * keys whose group it owns — not the full set. This proves the range filter is
 * actually applied during restore rather than the backend silently loading
 * everything.
 *
 * <p>Also covers scale-up (one source snapshot split across N new subtasks) and
 * scale-down (multiple source snapshots merged into one new subtask).
 */
public class TestKeyGroupRangeBackendRestore {

    private static final int MAX_P = 16;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    private static Map<String, Long> writeKeys(MemoryKeyedStateBackend<String> backend, int count) throws Exception {
        ValueState<Long> state = backend.getState(COUNT);
        Map<String, Long> written = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = "range-key-" + i;
            long val = 1000L + i;
            backend.setCurrentKey(key);
            state.update(val);
            written.put(key, val);
        }
        return written;
    }

    private static Long readValue(MemoryKeyedStateBackend<String> backend, String key) throws Exception {
        backend.setCurrentKey(key);
        return backend.getState(COUNT).value();
    }

    @Test
    public void rangeRestoreKeepsOnlyOwnedKeys_scaleUp_4_to_16() throws Exception {
        // One source snapshot taken at parallelism=4 semantics (all keys together).
        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, MAX_P);
        Map<String, Long> allKeys = writeKeys(source, 60);
        StateSnapshot snapshot = source.snapshotState();
        source.close();

        // Restore into 16 new subtask backends, each with its own KeyGroupRange.
        int keptTotal = 0;
        for (int sub = 0; sub < 16; sub++) {
            MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 16, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            int kept = 0;
            for (String key : allKeys.keySet()) {
                int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
                Long val = readValue(backend, key);
                if (range.contains(gid)) {
                    assertEquals(allKeys.get(key), val, "owned key " + key + " must be restored on subtask " + sub);
                    kept++;
                } else {
                    assertEquals(null, val, "non-owned key " + key + " must NOT be restored on subtask " + sub);
                }
            }
            keptTotal += kept;
            backend.close();
        }
        assertEquals(60, keptTotal, "16 subtask ranges must partition all 60 keys (scale-up)");
    }

    @Test
    public void rangeRestoreMergesMultipleSnapshots_scaleDown_16_to_4() throws Exception {
        // Build 16 source snapshots, each containing a disjoint slice of keys (the
        // keys that source subtask would own under parallelism=16).
        java.util.List<java.util.Map<String, Object>> sourceDataMaps = new java.util.ArrayList<>();
        java.util.List<java.util.List<String>> keysPerSubtask = new java.util.ArrayList<>();
        for (int sub = 0; sub < 16; sub++) {
            keysPerSubtask.add(new java.util.ArrayList<>());
        }
        // Route each key to its p=16 owner subtask.
        java.util.Map<String, Long> allKeys = new LinkedHashMap<>();
        for (int i = 0; i < 60; i++) {
            String key = "down-key-" + i;
            long val = 2000L + i;
            allKeys.put(key, val);
            int sub = KeyGroupAssignment.assignKeyGroupToSubtask(
                    KeyGroupAssignment.assignToKeyGroup(key, MAX_P), MAX_P, 16);
            keysPerSubtask.get(sub).add(key);
        }
        for (int sub = 0; sub < 16; sub++) {
            MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, MAX_P);
            ValueState<Long> state = backend.getState(COUNT);
            for (String key : keysPerSubtask.get(sub)) {
                backend.setCurrentKey(key);
                state.update(allKeys.get(key));
            }
            StateSnapshot snap = backend.snapshotState();
            sourceDataMaps.add(snap != null ? snap.getStateData() : new LinkedHashMap<>());
            backend.close();
        }

        // Merge all source snapshots into one (the executor's buildRescaledTaskState
        // does exactly this union before a single filtered restore).
        java.util.Map<String, Object> mergedStates = new LinkedHashMap<>();
        for (java.util.Map<String, Object> data : sourceDataMaps) {
            Object statesObj = data.get("states");
            if (!(statesObj instanceof java.util.Map)) continue;
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> statesMap = (java.util.Map<String, Object>) statesObj;
            for (java.util.Map.Entry<String, Object> e : statesMap.entrySet()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> info = (java.util.Map<String, Object>) e.getValue();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> acc = (java.util.Map<String, Object>) mergedStates.get(e.getKey());
                if (acc == null) {
                    mergedStates.put(e.getKey(), new LinkedHashMap<>(info));
                } else {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> entries = (java.util.List<Object>) info.get("entries");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> accEntries = (java.util.List<Object>) acc.get("entries");
                    accEntries.addAll(entries);
                }
            }
        }
        java.util.Map<String, Object> mergedData = new LinkedHashMap<>();
        mergedData.put("states", mergedStates);
        StateSnapshot mergedSnapshot = new StateSnapshot(mergedData);

        // Scale down to 4 subtasks. Each new subtask restores the merged snapshot with
        // its own KeyGroupRange (spanning several old subtasks).
        int keptTotal = 0;
        for (int sub = 0; sub < 4; sub++) {
            MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(mergedSnapshot);

            for (String key : allKeys.keySet()) {
                int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
                Long val = readValue(backend, key);
                if (range.contains(gid)) {
                    assertEquals(allKeys.get(key), val,
                            "owned key " + key + " must be merged on new subtask " + sub);
                    keptTotal++;
                } else {
                    assertEquals(null, val, "non-owned key " + key + " must NOT appear on new subtask " + sub);
                }
            }
            backend.close();
        }
        assertEquals(60, keptTotal, "4 new subtasks must merge all 60 keys (scale-down)");
    }

    @Test
    public void noRangeRestoresAllBackwardCompatible() throws Exception {
        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, MAX_P);
        Map<String, Long> allKeys = writeKeys(source, 20);
        StateSnapshot snapshot = source.snapshotState();
        source.close();

        // No target range => full restore (backward compatible).
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, MAX_P);
        backend.restoreState(snapshot);
        for (Map.Entry<String, Long> e : allKeys.entrySet()) {
            assertEquals(e.getValue().longValue(), readValue(backend, e.getKey()));
        }
        backend.close();
    }

    @Test
    public void emptyRangeRestoresNothing() throws Exception {
        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, MAX_P);
        writeKeys(source, 20);
        StateSnapshot snapshot = source.snapshotState();
        source.close();

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, MAX_P);
        backend.setTargetKeyGroupRange(new KeyGroupRange(0, 0)); // empty range
        backend.restoreState(snapshot);
        // No key should be restored: every key reads null.
        for (int i = 0; i < 20; i++) {
            assertEquals(null, readValue(backend, "range-key-" + i));
        }
        backend.close();
    }

    @Test
    public void singleArgConstructorDefaultsToOneGroup() {
        // The single-arg constructor is the non-keyed default (maxParallelism=1);
        // keyed jobs must pass an explicit maxParallelism.
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        assertEquals(1, backend.getMaxParallelism());
        backend.close();
    }
}
