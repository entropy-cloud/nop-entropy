/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTableJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨库拼接合并逻辑（plan 300 Phase 3 提取）。
 * <p>
 * 负责跨 querySpace 的 JOIN 结果集应用层内存合并（D5），包括键匹配、NULL 语义（AR-05）、
 * 类型一致性校验（AR-05）、命名空间 Anti-Hollow 校验（D1.4）和分页截断。
 */
class CrossDbJoinMerger {
    private final int maxCrossDbRows;

    CrossDbJoinMerger() {
        this(10000);
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

        Set<String> leftKeys = leftRows.isEmpty() ? Collections.emptySet() : new HashSet<>(leftRows.get(0).keySet());

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> l : leftRows) {
            Object rawLeftKey = getCaseInsensitive(l, leftField);
            if (rawLeftKey == null) {
                if (_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
                    merged.add(mergeRow(l, null, alias, leftKeys));
                }
                continue;
            }
            String key = stringKey(rawLeftKey);
            List<Map<String, Object>> matches = rightIndex.get(key);
            if (matches != null && !matches.isEmpty()) {
                for (Map<String, Object> r : matches) {
                    merged.add(mergeRow(l, r, alias, leftKeys));
                }
            } else if (_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
                merged.add(mergeRow(l, null, alias, leftKeys));
            }
        }

        return truncate(merged, limit, offset);
    }

    private void checkSizeLimit(int rows, String side, String joinId) {
        if (rows > maxCrossDbRows) {
            throw new NopException(NopMetadataErrors.ERR_JOIN_CROSS_DB_SIZE_LIMIT)
                    .param("joinId", joinId).param("side", side).param("rows", rows)
                    .param("limit", maxCrossDbRows);
        }
    }

    private void verifyCrossDbKeyTypeConsistency(NopMetaTableJoin join,
                                                 List<Map<String, Object>> leftRows, String leftField,
                                                 List<Map<String, Object>> rightRows, String rightField) {
        Object leftSample = firstNonNullKey(leftRows, leftField);
        Object rightSample = firstNonNullKey(rightRows, rightField);
        if (leftSample == null || rightSample == null) {
            return;
        }
        if (!leftSample.getClass().equals(rightSample.getClass())) {
            throw new NopException(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH)
                    .param("joinId", join.getJoinId())
                    .param("leftType", leftSample.getClass().getName())
                    .param("rightType", rightSample.getClass().getName());
        }
    }

    private static Object firstNonNullKey(List<Map<String, Object>> rows, String field) {
        if (rows == null) return null;
        for (Map<String, Object> r : rows) {
            Object v = getCaseInsensitive(r, field);
            if (v != null) return v;
        }
        return null;
    }

    private void requireFieldInRowKeys(String field, List<Map<String, Object>> rows, String side, String joinId) {
        if (field == null || rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, Object> sample = rows.get(0);
        if (!containsKeyIgnoreCase(sample, field)) {
            throw new NopException(NopMetadataErrors.ERR_JOIN_NAMESPACE_MISMATCH)
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
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE).param("offset", offset);
            }
            from = offset.intValue();
        }
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            if (limit > Integer.MAX_VALUE) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE).param("limit", limit);
            }
            to = Math.min(rows.size(), from + limit.intValue());
        }
        return new ArrayList<>(rows.subList(from, to));
    }
}
