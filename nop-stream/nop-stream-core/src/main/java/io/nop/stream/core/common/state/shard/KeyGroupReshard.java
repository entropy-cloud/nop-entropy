/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 37: pure redistribution logic for a {@code maxParallelism} reshard
 * migration. This is the {@code maxParallelism}-change counterpart of
 * {@link KeyGroupRangeRestoreFilter} (which handles the {@code parallelism}-only
 * rescale).
 *
 * <p><b>Essential difference from Stage 35 rescale</b>: when only
 * {@code parallelism} changes, the key&rarr;group mapping is unchanged (it
 * depends solely on {@code maxParallelism}); only the group&rarr;subtask
 * ownership shifts, so {@link KeyGroupRangeRestoreFilter} can filter entries by
 * range under the same {@code maxParallelism}. When {@code maxParallelism}
 * changes, the key&rarr;group mapping <em>itself</em> changes, so every entry's
 * raw key must be re-hashed under the <em>new</em> {@code maxParallelism} and
 * re-routed to its new owner subtask.
 *
 * <p>This class operates on the same JSON-level {@code states} sub-map
 * structure ({@code stateName -> stateInfo} where each {@code stateInfo} carries
 * an {@code entries} list of {@code {namespace, key, value/...}}) that
 * {@link KeyGroupRangeRestoreFilter} consumes. It is purely a function of
 * (globalStates, newMaxParallelism, newParallelism) and performs no I/O, so it
 * is unit-testable in isolation. The I/O wrapper ({@code MaxParallelismReshardMigration}
 * in nop-stream-runtime) reads/writes the savepoint and assembles the per-vertex
 * global pool before delegating here.
 *
 * <p><b>Anti-hollow contract</b>: entries lacking a {@code key} field, or state
 * infos lacking an {@code entries} list, cause fail-fast — they are never
 * silently dropped.
 */
public final class KeyGroupReshard {

    private KeyGroupReshard() {
    }

    /**
     * Partition a <em>global</em> keyed {@code states} map (the union of every
     * key across all old subtasks, per state name) into per-new-subtask
     * {@code states} maps, recomputing each entry's key-group under
     * {@code newMaxParallelism} and routing it to the owning subtask under
     * {@code newParallelism}.
     *
     * @param globalStates     the {@code states} sub-map of a keyed StateSnapshot,
     *                         with each state's {@code entries} being the global
     *                         union of keys ({@code stateName -> stateInfo})
     * @param newMaxParallelism new job-global key-group upper bound (&ge; 1)
     * @param newParallelism    new per-vertex subtask count
     *                         (&ge; 1, &le; {@code newMaxParallelism})
     * @return {@code subtaskIndex -> states-map} where each states-map holds
     *         only the entries whose recomputed group is owned by that subtask;
     *         every returned subtask (including empty ones) carries the same
     *         state-info metadata as the input so the result is restorable
     * @throws IllegalArgumentException on any invalid argument
     * @throws IllegalStateException    if an entry lacks a {@code key} field
     *                                  (fail-fast, no silent drop)
     */
    @SuppressWarnings("unchecked")
    public static Map<Integer, Map<String, Object>> redistributeStates(
            Map<String, Object> globalStates, int newMaxParallelism, int newParallelism) {
        if (newMaxParallelism < 1) {
            throw new IllegalArgumentException("newMaxParallelism must be at least 1: " + newMaxParallelism);
        }
        if (newParallelism < 1) {
            throw new IllegalArgumentException("newParallelism must be at least 1: " + newParallelism);
        }
        if (newParallelism > newMaxParallelism) {
            throw new IllegalArgumentException(
                    "newParallelism (" + newParallelism + ") must not exceed newMaxParallelism ("
                            + newMaxParallelism + ")");
        }

        Map<Integer, Map<String, Object>> result = new TreeMap<>();
        for (int s = 0; s < newParallelism; s++) {
            result.put(s, new LinkedHashMap<>());
        }

        if (globalStates == null || globalStates.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> stateEntry : globalStates.entrySet()) {
            String stateName = stateEntry.getKey();
            if (!(stateEntry.getValue() instanceof Map)) {
                throw new IllegalStateException(
                        "KeyGroupReshard: state '" + stateName + "' is not a state-info map (no migration "
                                + "of unknown state types; fail-fast instead of silent drop)");
            }
            Map<String, Object> stateInfo = (Map<String, Object>) stateEntry.getValue();
            Object entriesObj = stateInfo.get("entries");
            if (!(entriesObj instanceof List)) {
                throw new IllegalStateException(
                        "KeyGroupReshard: state '" + stateName + "' has no 'entries' list (fail-fast "
                                + "instead of silent drop)");
            }
            List<Map<String, Object>> entries = (List<Map<String, Object>>) entriesObj;

            // Bucket entries by new owner subtask; each subtask gets its own copy
            // of the state-info metadata with its (reduced) entries list.
            Map<Integer, List<Map<String, Object>>> bucketedBySubtask = new TreeMap<>();
            for (Map<String, Object> e : entries) {
                Object rawKey = e.get("key");
                if (rawKey == null && !e.containsKey("key")) {
                    throw new IllegalStateException(
                            "KeyGroupReshard: state '" + stateName + "' has an entry without a 'key' "
                                    + "field (fail-fast instead of silent drop)");
                }
                int groupId = KeyGroupAssignment.assignToKeyGroup(rawKey, newMaxParallelism);
                int subtaskIndex = KeyGroupAssignment.assignKeyGroupToSubtask(groupId, newMaxParallelism, newParallelism);
                bucketedBySubtask.computeIfAbsent(subtaskIndex, k -> new ArrayList<>()).add(new LinkedHashMap<>(e));
            }

            for (Map.Entry<Integer, List<Map<String, Object>>> b : bucketedBySubtask.entrySet()) {
                Map<String, Object> infoCopy = new LinkedHashMap<>(stateInfo);
                infoCopy.put("entries", b.getValue());
                result.get(b.getKey()).put(stateName, infoCopy);
            }
        }
        return result;
    }

    /**
     * Count the total number of keyed entries (keys) across a {@code states}
     * sub-map. Used for the migration's key-conservation invariant.
     *
     * @param states the {@code states} sub-map of a keyed StateSnapshot
     *               ({@code stateName -> stateInfo})
     * @return total entry count across all states
     */
    @SuppressWarnings("unchecked")
    public static int countKeyedEntries(Map<String, Object> states) {
        if (states == null || states.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Object infoObj : states.values()) {
            if (!(infoObj instanceof Map)) {
                continue;
            }
            Object entries = ((Map<String, Object>) infoObj).get("entries");
            if (entries instanceof List) {
                total += ((List<?>) entries).size();
            }
        }
        return total;
    }
}
