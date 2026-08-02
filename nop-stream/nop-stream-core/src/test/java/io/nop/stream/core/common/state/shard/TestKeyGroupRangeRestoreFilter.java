/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 35: focused tests for {@link KeyGroupRangeRestoreFilter}, the entry-level
 * range filter that backs per-subtask partial restore on both the Memory and
 * RocksDB full-JSON snapshot paths.
 */
public class TestKeyGroupRangeRestoreFilter {

    private static final int MAX_P = 16;

    private static Map<String, Object> stateInfoWithValueEntries(String stateName, List<Object> keys) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", "java.lang.Long");
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
        data.put(stateName, stateInfoWithValueEntries(stateName, keys));
        return data;
    }

    @Test
    public void keyOwnedByRangeMatchesAssignToKeyGroup() {
        KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, 0);
        for (int i = 0; i < 100; i++) {
            String key = "k-" + i;
            int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
            assertEquals(range.contains(gid), KeyGroupRangeRestoreFilter.keyOwnedByRange(key, range, MAX_P),
                    "key " + key + " group " + gid);
        }
    }

    @Test
    public void nullRangeOrSingleGroupKeepsAll() {
        Map<String, Object> src = statesMap("count", List.of("a", "b", "c"));
        // null range => no filtering
        assertEquals(src, KeyGroupRangeRestoreFilter.filterKeyedStates(src, null, MAX_P));
        // maxParallelism=1 => single group, no filtering
        Map<String, Object> copy = KeyGroupRangeRestoreFilter.filterKeyedStates(src,
                new KeyGroupRange(0, 1), 1);
        assertEquals(3, entriesSize(copy, "count"));
    }

    @Test
    public void filterKeepsOnlyInRangeEntries() {
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            keys.add("key-" + i);
        }
        Map<String, Object> src = statesMap("count", keys);

        KeyGroupRange subtask0 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, 0);
        KeyGroupRange subtask1 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, 1);

        Map<String, Object> r0 = KeyGroupRangeRestoreFilter.filterKeyedStates(src, subtask0, MAX_P);
        Map<String, Object> r1 = KeyGroupRangeRestoreFilter.filterKeyedStates(src, subtask1, MAX_P);

        int kept0 = entriesSize(r0, "count");
        int kept1 = entriesSize(r1, "count");

        // The 4 subtask ranges partition [0,16) disjointly, so kept0+kept1..+kept3 == 50
        int total = kept0 + kept1;
        KeyGroupRange subtask2 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, 2);
        KeyGroupRange subtask3 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, 3);
        total += entriesSize(KeyGroupRangeRestoreFilter.filterKeyedStates(src, subtask2, MAX_P), "count");
        total += entriesSize(KeyGroupRangeRestoreFilter.filterKeyedStates(src, subtask3, MAX_P), "count");
        assertEquals(50, total, "4 subtask ranges must partition all keys");

        // kept0 must be exactly the keys whose group is in subtask0's range
        for (Object k : keys) {
            int gid = KeyGroupAssignment.assignToKeyGroup(k, MAX_P);
            if (subtask0.contains(gid)) {
                assertTrue(containsKey(r0, "count", k), "in-range key must survive filter: " + k);
            } else {
                assertFalse(containsKey(r0, "count", k), "out-of-range key must be filtered: " + k);
            }
        }
    }

    @Test
    public void unionOfRangePartitionsEqualsFullSet() {
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            keys.add("k" + i);
        }
        Map<String, Object> src = statesMap("agg", keys);

        // Rescale 4 -> 16: the 16 new subtask ranges must still partition all keys.
        int keptTotal = 0;
        for (int sub = 0; sub < 16; sub++) {
            KeyGroupRange r = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 16, sub);
            keptTotal += entriesSize(KeyGroupRangeRestoreFilter.filterKeyedStates(src, r, MAX_P), "agg");
        }
        assertEquals(80, keptTotal, "16 subtask ranges must partition all keys (rescale 4->16)");
    }

    @Test
    public void groupToSubtaskMappingStableAcrossParallelismChange() {
        // The same key's key-group id never changes for fixed maxParallelism; only
        // which subtask owns the group changes with parallelism.
        for (int i = 0; i < 40; i++) {
            String key = "stable-" + i;
            int gid4 = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
            int gid16 = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
            assertEquals(gid4, gid16, "key->group mapping must be maxParallelism-invariant");

            int sub4 = KeyGroupAssignment.assignKeyGroupToSubtask(gid4, MAX_P, 4);
            int sub16 = KeyGroupAssignment.assignKeyGroupToSubtask(gid4, MAX_P, 16);
            // group owned by subtask sub16 under p=16 must be inside sub16's range
            KeyGroupRange r16 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 16, sub16);
            assertTrue(r16.contains(gid4));
            // and the p=4 owner's range must also contain the group
            KeyGroupRange r4 = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub4);
            assertTrue(r4.contains(gid4));
        }
    }

    @SuppressWarnings("unchecked")
    private static int entriesSize(Map<String, Object> filtered, String stateName) {
        Map<String, Object> info = (Map<String, Object>) filtered.get(stateName);
        if (info == null) return 0;
        Object entries = info.get("entries");
        return entries instanceof List ? ((List<?>) entries).size() : 0;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsKey(Map<String, Object> filtered, String stateName, Object key) {
        Map<String, Object> info = (Map<String, Object>) filtered.get(stateName);
        if (info == null) return false;
        Object entries = info.get("entries");
        if (!(entries instanceof List)) return false;
        for (Object e : (List<?>) entries) {
            Object k = ((Map<String, Object>) e).get("key");
            if (java.util.Objects.equals(k, key)) return true;
        }
        return false;
    }
}
