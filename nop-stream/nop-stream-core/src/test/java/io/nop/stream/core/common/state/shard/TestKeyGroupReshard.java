/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 37: focused tests for {@link KeyGroupReshard}, the pure redistribution
 * logic that backs the {@code maxParallelism} reshard migration. Mirrors
 * {@link TestKeyGroupRangeRestoreFilter} (which covers the parallelism-only
 * rescale) but asserts the essential reshard difference: a {@code maxParallelism}
 * change <em>moves keys between groups</em>, so every entry is re-hashed under
 * the new {@code maxParallelism} and re-routed to its new owner subtask.
 */
public class TestKeyGroupReshard {

    private static Map<String, Object> valueStateInfo(String stateName, List<Object> keys) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", "java.lang.Long");
        info.put("schemaChecksum", "abc");
        info.put("schemaVersion", 1);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object k : keys) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("namespace", "_default_");
            e.put("key", k);
            e.put("value", 1L);
            entries.add(e);
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> statesMap(String stateName, List<Object> keys) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(stateName, valueStateInfo(stateName, keys));
        return data;
    }

    private static List<Object> keys(int n) {
        List<Object> ks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ks.add("reshard-key-" + i);
        }
        return ks;
    }

    @SuppressWarnings("unchecked")
    private static int entryCount(Map<String, Object> statesMap, String stateName) {
        Map<String, Object> info = (Map<String, Object>) statesMap.get(stateName);
        if (info == null) return 0;
        Object entries = info.get("entries");
        return entries instanceof List ? ((List<?>) entries).size() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> collectKeys(Map<Integer, Map<String, Object>> redistributed, String stateName) {
        List<Object> all = new ArrayList<>();
        for (Map<String, Object> statesMap : redistributed.values()) {
            Map<String, Object> info = (Map<String, Object>) statesMap.get(stateName);
            if (info == null) continue;
            Object entries = info.get("entries");
            if (entries instanceof List) {
                for (Object e : (List<?>) entries) {
                    all.add(((Map<String, Object>) e).get("key"));
                }
            }
        }
        return all;
    }

    @Test
    public void reshardUpConservesKeysAndRoutesByNewMaxParallelism() {
        int oldMaxP = 128;
        int newMaxP = 256;
        int parallelism = 4;
        List<Object> ks = keys(200);
        Map<String, Object> global = statesMap("count", ks);

        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(global, newMaxP, parallelism);

        // Conservation: every new subtask exists; union of keys == input keys (no loss/dup).
        assertEquals(parallelism, redistributed.size(), "all new subtasks present (even if empty)");
        List<Object> allKeys = collectKeys(redistributed, "count");
        assertEquals(ks.size(), allKeys.size(), "no keys lost or duplicated");
        Set<Object> inputSet = new HashSet<>(ks);
        Set<Object> outputSet = new HashSet<>(allKeys);
        assertEquals(inputSet, outputSet, "key set must be conserved (order-independent)");

        // Routing correctness: each subtask holds exactly the keys whose group
        // (under the NEW maxParallelism) falls in its own range.
        for (int s = 0; s < parallelism; s++) {
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(newMaxP, parallelism, s);
            Map<String, Object> info = (Map<String, Object>) redistributed.get(s).get("count");
            Object entries = info == null ? null : info.get("entries");
            int size = entries instanceof List ? ((List<?>) entries).size() : 0;
            if (entries instanceof List) {
                for (Object e : (List<?>) entries) {
                    Object k = ((Map<String, Object>) e).get("key");
                    int gid = KeyGroupAssignment.assignToKeyGroup(k, newMaxP);
                    assertTrue(range.contains(gid),
                            "key " + k + " group " + gid + " not in subtask " + s + " range " + range);
                }
            }
            // size must equal the number of input keys whose new group lands in this range
            int expected = 0;
            for (Object k : ks) {
                if (range.contains(KeyGroupAssignment.assignToKeyGroup(k, newMaxP))) expected++;
            }
            assertEquals(expected, size, "subtask " + s + " key count must match range membership");
        }
    }

    @Test
    public void reshardDownConservesKeys() {
        // 256 -> 128 (reverse direction)
        int newMaxP = 128;
        int parallelism = 4;
        List<Object> ks = keys(200);
        Map<String, Object> global = statesMap("agg", ks);

        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(global, newMaxP, parallelism);

        List<Object> allKeys = collectKeys(redistributed, "agg");
        assertEquals(ks.size(), allKeys.size(), "downscale reshard must conserve keys");
    }

    @Test
    public void reshardActuallyMovesKeysVersusOldMaxParallelism() {
        // Anti-hollow: prove the reshard recomputed groups under the NEW
        // maxParallelism (not the old). There must exist at least one key whose
        // owner subtask differs between old (128) and new (256) routing; if none
        // differ the reshard would be a no-op copy (hollow).
        int oldMaxP = 128;
        int newMaxP = 256;
        int parallelism = 4;
        List<Object> ks = keys(500);
        Map<String, Object> global = statesMap("count", ks);

        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(global, newMaxP, parallelism);

        boolean moved = false;
        for (int s = 0; s < parallelism; s++) {
            Map<String, Object> info = (Map<String, Object>) redistributed.get(s).get("count");
            Object entries = info == null ? null : info.get("entries");
            if (!(entries instanceof List)) continue;
            for (Object e : (List<?>) entries) {
                Object k = ((Map<String, Object>) e).get("key");
                int oldSub = KeyGroupAssignment.assignKeyGroupToSubtask(
                        KeyGroupAssignment.assignToKeyGroup(k, oldMaxP), oldMaxP, parallelism);
                if (oldSub != s) {
                    moved = true;
                    break;
                }
            }
            if (moved) break;
        }
        assertTrue(moved, "reshard must move at least one key to a different subtask "
                + "under the new maxParallelism (else it is a hollow no-op copy)");
        assertNotEquals(oldMaxP, newMaxP);
    }

    @Test
    public void reshardPreservesStateInfoMetadata() {
        Map<String, Object> global = statesMap("count", keys(10));
        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(global, 256, 4);
        for (Map<String, Object> statesMap : redistributed.values()) {
            Map<String, Object> info = (Map<String, Object>) statesMap.get("count");
            if (info == null) continue;
            assertEquals("ValueState", info.get("stateType"));
            assertEquals("java.lang.Long", info.get("valueType"));
            assertEquals("abc", info.get("schemaChecksum"));
            assertEquals(1, info.get("schemaVersion"));
        }
    }

    @Test
    public void emptyStatesReturnsAllEmptySubtasks() {
        Map<String, Object> empty = new LinkedHashMap<>();
        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(empty, 256, 4);
        assertEquals(4, redistributed.size());
        for (Map<String, Object> statesMap : redistributed.values()) {
            assertTrue(statesMap.isEmpty());
        }
    }

    @Test
    public void nullGlobalStatesReturnsAllEmptySubtasks() {
        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(null, 256, 4);
        assertEquals(4, redistributed.size());
    }

    @Test
    public void entryWithoutKeyFailsFast() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("namespace", "_default_");
        // no "key" field
        bad.put("value", 1L);
        entries.add(bad);
        info.put("entries", entries);
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("count", info);
        assertThrows(IllegalStateException.class,
                () -> KeyGroupReshard.redistributeStates(global, 256, 4),
                "entry without key must fail-fast, not be silently dropped");
    }

    @Test
    public void unknownStateTypeFailsFast() {
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("count", "not-a-state-info-map");
        assertThrows(IllegalStateException.class,
                () -> KeyGroupReshard.redistributeStates(global, 256, 4),
                "non-map state info must fail-fast");
    }

    @Test
    public void missingEntriesListFailsFast() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        // no "entries"
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("count", info);
        assertThrows(IllegalStateException.class,
                () -> KeyGroupReshard.redistributeStates(global, 256, 4),
                "state without entries list must fail-fast");
    }

    @Test
    public void invalidArgsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupReshard.redistributeStates(new LinkedHashMap<>(), 0, 4));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupReshard.redistributeStates(new LinkedHashMap<>(), 256, 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupReshard.redistributeStates(new LinkedHashMap<>(), 16, 32));
    }

    @Test
    public void countKeyedEntriesHandlesNullAndNested() {
        assertEquals(0, KeyGroupReshard.countKeyedEntries(null));
        assertEquals(0, KeyGroupReshard.countKeyedEntries(new LinkedHashMap<>()));
        assertEquals(5, KeyGroupReshard.countKeyedEntries(statesMap("count", keys(5))));
    }
}
