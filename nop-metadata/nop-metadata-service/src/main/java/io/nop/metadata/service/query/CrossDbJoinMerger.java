
package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 跨库拼接合并逻辑（plan 300 Phase 3 提取）。
 * <p>
 * 负责跨 querySpace 的 JOIN 结果集应用层内存合并（D5），包括键匹配、NULL 语义（AR-05）、
 * 类型一致性校验（AR-05）、命名空间 Anti-Hollow 校验（D1.4）和分页截断。
 */
class CrossDbJoinMerger {
    private final int maxCrossDbRows;

    CrossDbJoinMerger() {
        this(CrossDbConfigHolder.maxCrossDbRows);
    }

    CrossDbJoinMerger(int maxCrossDbRows) {
        this.maxCrossDbRows = maxCrossDbRows;
    }

    /**
     * 应用层内存合并（D5）：右表按 join key 建索引，左表逐行匹配。
     */
    List<Map<String, Object>> crossDbMerge(NopMetaTableJoin join, List<Map<String, Object>> leftRows,
                                           List<Map<String, Object>> rightRows, Long limit, Long offset) {
        checkSizeLimit(leftRows.size(), "left", join.getJoinId());
        checkSizeLimit(rightRows.size(), "right", join.getJoinId());

        String leftField = join.getLeftField();
        String rightField = join.getRightField();
        String alias = join.getAlias();
        if (alias == null || alias.trim().isEmpty()) {
            alias = "right";
        }
        String joinType = join.getJoinType();
        String joinId = join.getJoinId();

        requireFieldInRowKeys(leftField, leftRows, "left", joinId);
        requireFieldInRowKeys(rightField, rightRows, "right", joinId);

        verifyCrossDbKeyTypeConsistency(join, leftRows, leftField, rightRows, rightField);

        Map<String, List<Map<String, Object>>> rightIndex = new HashMap<>();
        for (Map<String, Object> r : rightRows) {
            Object rawKey = getCaseInsensitive(r, rightField);
            if (rawKey == null) {
                continue;
            }
            String key = stringKey(rawKey);
            rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        Set<String> leftKeys = computeLeftKeys(leftRows);

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> l : leftRows) {
            Object rawLeftKey = getCaseInsensitive(l, leftField);
            if (rawLeftKey == null) {
                if (_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
                    merged.add(mergeRow(l, null, alias, leftKeys));
                    checkMergedSizeLimit(merged.size(), joinId);
                }
                continue;
            }
            String key = stringKey(rawLeftKey);
            List<Map<String, Object>> matches = rightIndex.get(key);
            if (matches != null && !matches.isEmpty()) {
                for (Map<String, Object> r : matches) {
                    merged.add(mergeRow(l, r, alias, leftKeys));
                    checkMergedSizeLimit(merged.size(), joinId);
                }
            } else if (_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
                merged.add(mergeRow(l, null, alias, leftKeys));
                checkMergedSizeLimit(merged.size(), joinId);
            }
        }

        return truncate(merged, limit, offset);
    }

    /**
     * MA7.4-04：合并产物（笛卡尔积）上限——两侧各 ≤ maxCrossDbRows 时乘积最坏
     * maxCrossDbRows^2 行入内存，需在合并过程中逐行计数并快速失败。
     */
    private void checkMergedSizeLimit(int mergedRows, String joinId) {
        if (mergedRows > maxCrossDbRows) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_CROSS_DB_SIZE_LIMIT)
                    .param("joinId", joinId).param("side", "merged").param("rows", mergedRows)
                    .param("limit", maxCrossDbRows);
        }
    }

    private void checkSizeLimit(int rows, String side, String joinId) {
        if (rows > maxCrossDbRows) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_CROSS_DB_SIZE_LIMIT)
                    .param("joinId", joinId).param("side", side).param("rows", rows)
                    .param("limit", maxCrossDbRows);
        }
    }

    /**
     * Verify that all non-null key values on each side have compatible Java types.
     * Iterates all rows (bounded by maxCrossDbRows check in crossDbMerge).
     * If a side has no non-null keys or only one side has keys, validation passes
     * (no cross-side type comparison possible).
     *
     * <p>AR-20b（plan 2026-08-06-1228-1 Phase 2）：整型族（Byte/Short/Integer/Long/BigInteger）
     * 跨类型视为兼容——merge 匹配走 {@code stringKey}（{@code String.valueOf}），整型等值键必然同串
     * （{@code Long.toString(1)="1"=Integer.toString(1)}），精确类比较是过度防护（INT vs BIGINT
     * 跨库 join 数值相等被误拒）；非整型不匹配（如 BigDecimal vs Integer、String vs Integer）维持拒绝——
     * {@code BigDecimal("1.0")="1.0" vs Integer 1="1"} 数值等但不同串、{@code Float 0.1f vs Double 0.1}
     * 同串但数值不等，放宽会静默失配/错配。
     */
    private void verifyCrossDbKeyTypeConsistency(NopMetaTableJoin join,
                                                 List<Map<String, Object>> leftRows, String leftField,
                                                 List<Map<String, Object>> rightRows, String rightField) {
        Class<?> leftType = firstNonNullKeyType(leftRows, leftField);
        Class<?> rightType = firstNonNullKeyType(rightRows, rightField);
        if (leftType == null || rightType == null) {
            return;
        }
        if (!leftType.equals(rightType) && !(isIntegerFamily(leftType) && isIntegerFamily(rightType))) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH)
                    .param("joinId", join.getJoinId())
                    .param("leftType", leftType.getName())
                    .param("rightType", rightType.getName());
        }
    }

    /** 整型族：数值等值经 stringKey 必然同串，跨类型（INT vs BIGINT）视为兼容（AR-20b）。 */
    private static boolean isIntegerFamily(Class<?> type) {
        return type == Byte.class || type == Short.class || type == Integer.class
                || type == Long.class || type == java.math.BigInteger.class;
    }

    /**
     * Return the Java type of the first non-null key value in the column.
     * Validates that ALL non-null values in that column share the same type
     * (integer-family mixed values are accepted, AR-20b).
     * Returns null if no non-null key is found.
     */
    private static Class<?> firstNonNullKeyType(List<Map<String, Object>> rows, String field) {
        if (rows == null) return null;
        Class<?> resultType = null;
        for (Map<String, Object> r : rows) {
            Object v = getCaseInsensitive(r, field);
            if (v == null) continue;
            Class<?> type = v.getClass();
            if (resultType == null) {
                resultType = type;
            } else if (!resultType.equals(type)) {
                if (isIntegerFamily(resultType) && isIntegerFamily(type)) {
                    // 单列内整型族混型（如 Integer + Long）→ 兼容（stringKey 匹配语义下数值等值成立）
                    continue;
                }
                throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH)
                        .param("leftType", resultType.getName())
                        .param("rightType", type.getName());
            }
        }
        return resultType;
    }

    private void requireFieldInRowKeys(String field, List<Map<String, Object>> rows, String side, String joinId) {
        if (field == null || rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, Object> sample = rows.get(0);
        if (!containsKeyIgnoreCase(sample, field)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_NAMESPACE_MISMATCH)
                    .param("joinId", joinId).param("side", side)
                    .param("field", field).param("rowKeys", sample.keySet());
        }
    }

    static Object getCaseInsensitive(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean containsKeyIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return false;
        }
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> computeLeftKeys(List<Map<String, Object>> leftRows) {
        if (leftRows == null || leftRows.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> row : leftRows) {
            keys.addAll(row.keySet());
        }
        return keys;
    }

    static Map<String, Object> mergeRow(Map<String, Object> left, Map<String, Object> right,
                                         String alias, Set<String> leftKeys) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        if (left != null) {
            row.putAll(left);
        }
        if (right != null) {
            for (Map.Entry<String, Object> e : right.entrySet()) {
                String k = e.getKey();
                if (leftKeys != null && leftKeys.contains(k)) {
                    k = alias + "_" + k;
                }
                row.put(k, e.getValue());
            }
        }
        return row;
    }

    private static String stringKey(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    List<Map<String, Object>> truncate(List<Map<String, Object>> rows, Long limit, Long offset) {
        int from = 0;
        if (offset != null && offset > 0) {
            if (offset > Integer.MAX_VALUE) {
                throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE).param("offset", offset);
            }
            from = offset.intValue();
        }
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            // AR-09（plan 2026-08-06-0553-3 Phase 1）：负 limit 显式拒绝（defense-in-depth，
            // 入口归一化已拒绝，此处兜底直接调用本方法的路径）
            if (limit < 0) {
                throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID).param("limit", limit);
            }
            if (limit > Integer.MAX_VALUE) {
                throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE).param("limit", limit);
            }
            // AR-09：long 运算防 int 溢出（from + limit 溢出为负 → subList 裸 IllegalArgumentException）
            to = (int) Math.min(rows.size(), (long) from + limit);
        }
        return new ArrayList<>(rows.subList(from, to));
    }
}
