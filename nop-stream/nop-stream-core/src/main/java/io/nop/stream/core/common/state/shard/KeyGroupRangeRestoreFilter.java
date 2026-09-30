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

/**
 * Stage 35: filters keyed-state snapshot entries so that a subtask only
 * restores the keys that fall inside its own {@link KeyGroupRange}.
 *
 * <p>The full-JSON snapshot path materializes every entry's <em>raw user key</em>
 * (un-prefixed), so the filter re-derives the key-group id via
 * {@link KeyGroupAssignment#assignToKeyGroup(Object, int)} and keeps the entry
 * only when the id is contained in the target range. This is the in-memory
 * equivalent of the RocksDB SST range scan (Stage 31 deferred): instead of a
 * byte-prefix range scan over SST files, the already-materialized JSON entries
 * are filtered in memory. Both paths converge on the same observable contract:
 * after restore the backend holds exactly the keys whose group is owned by the
 * target subtask.
 *
 * <p>ST-02: raw snapshot keys are JSON-native forms whose hash can differ from
 * the live typed key (Date/bean keys round-trip through JSON as maps). The
 * keyType-aware overloads re-materialize each key via
 * {@link StateKeyRematerializer#rematerializeKey} BEFORE computing its group,
 * so the filter decision matches the live {@code routeKey}/storage-key group.
 *
 * <p>The filter is purely a function of (snapshotData, targetRange,
 * maxParallelism) and performs no I/O, so it is unit-testable in isolation.
 */
public final class KeyGroupRangeRestoreFilter {

    private KeyGroupRangeRestoreFilter() {
    }

    /**
     * @return {@code true} if {@code rawKey}'s key-group id (computed under
     * {@code maxParallelism}) falls inside {@code range}.
     */
    public static boolean keyOwnedByRange(Object rawKey, KeyGroupRange range, int maxParallelism) {
        return keyOwnedByRange(rawKey, range, maxParallelism, null);
    }

    /**
     * ST-02: keyType-aware ownership check — {@code rawKey} is first
     * re-materialized to {@code keyType} (identity for String/primitive keys),
     * then hashed for its key-group.
     *
     * @return {@code true} if the re-materialized key's key-group id (computed
     * under {@code maxParallelism}) falls inside {@code range}.
     */
    public static boolean keyOwnedByRange(Object rawKey, KeyGroupRange range, int maxParallelism,
                                          Class<?> keyType) {
        if (range == null) {
            return true;
        }
        if (maxParallelism <= 1) {
            return true;
        }
        Object typedKey = StateKeyRematerializer.rematerializeKey(rawKey, keyType);
        int keyGroupId = KeyGroupAssignment.assignToKeyGroup(typedKey, maxParallelism);
        return range.contains(keyGroupId);
    }

    /**
     * Return a deep copy of {@code stateData} (the {@code states} map of a
     * keyed {@link io.nop.stream.core.common.state.backend.StateSnapshot})
     * where each state's {@code entries} list is reduced to the entries whose
     * raw key is owned by {@code range}. Non-entry metadata
     * ({@code stateType}/{@code valueType}/{@code schemaChecksum}/...) is
     * preserved verbatim, so the filtered snapshot remains restorable by the
     * existing SerDe.
     *
     * <p>{@code range == null} or {@code maxParallelism <= 1} returns a shallow
     * copy without filtering (whole-state restore, backward compatible).
     *
     * @param stateData      the {@code states} sub-map of a keyed StateSnapshot
     *                       ({@code snapshot.getStateData().get("states")})
     * @param range          target key-group range, or {@code null} for no filter
     * @param maxParallelism job-global key-group upper bound
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filterKeyedStates(Map<String, Object> stateData,
                                                         KeyGroupRange range, int maxParallelism) {
        return filterKeyedStates(stateData, range, maxParallelism, null);
    }

    /**
     * ST-02: keyType-aware variant — entry keys are re-materialized to
     * {@code keyType} before their group is derived, so the filter decision is
     * a function of the typed key exactly as the live write path computes it.
     *
     * @param stateData      the {@code states} sub-map of a keyed StateSnapshot
     *                       ({@code snapshot.getStateData().get("states")})
     * @param range          target key-group range, or {@code null} for no filter
     * @param maxParallelism job-global key-group upper bound
     * @param keyType        the snapshot's declared key class, or {@code null}
     *                       to skip re-materialization (legacy callers)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filterKeyedStates(Map<String, Object> stateData,
                                                         KeyGroupRange range, int maxParallelism,
                                                         Class<?> keyType) {
        if (stateData == null || stateData.isEmpty()) {
            return stateData;
        }
        if (range == null || maxParallelism <= 1) {
            return new LinkedHashMap<>(stateData);
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : stateData.entrySet()) {
            filtered.put(entry.getKey(),
                    filterStateInfo((Map<String, Object>) entry.getValue(), range, maxParallelism, keyType));
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> filterStateInfo(Map<String, Object> stateInfo,
                                                        KeyGroupRange range, int maxParallelism,
                                                        Class<?> keyType) {
        Map<String, Object> copy = new LinkedHashMap<>(stateInfo);
        Object entriesObj = copy.get("entries");
        if (!(entriesObj instanceof List)) {
            return copy;
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) entriesObj;
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            Object rawKey = e.get("key");
            if (keyOwnedByRange(rawKey, range, maxParallelism, keyType)) {
                kept.add(new LinkedHashMap<>(e));
            }
        }
        copy.put("entries", kept);
        return copy;
    }
}
