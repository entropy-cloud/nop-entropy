/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存多键比较器（plan 2026-07-18-0900-2 D11.3：跨库 JOIN 聚合内存路径 orderBy 排序）。
 *
 * <p>用于跨库内存 GROUP BY 路径的 {@code orderBy} 排序——聚合后的 group 行（{@code List<Map<String,Object>>}，
 * key 为大写化 alias）按 {@code List<OrderFieldBean>} 逐字段排序。
 *
 * <p>每个 {@link OrderFieldBean#getName()} 经 nameToAlias 反查为 alias（大写化）；未命中 →
 * {@link MetaAggregationExecutor#ERR_AGGR_ORDER_BY_UNKNOWN_NAME} 显式失败（不静默跳过该排序字段）。
 *
 * <p>**类型强转（R1 M2）**：聚合值 Number → BigDecimal 统一比较；非 Number → {@link Comparable}。
 *
 * <p>**null 策略（{@code nullsFirst}）**：{@code nullsFirst=true} → null 排前；{@code nullsFirst=false} → null 排后；
 * 未指定时按 SQL 方言惯例（H2/PostgreSQL 默认 ASC nulls last、DESC nulls first），此处采用 {@code !desc}（与
 * {@link OrderFieldBean#shouldNullsFirst()} 一致）。
 */
final class MemoryOrderByComparator {
    private MemoryOrderByComparator() {
    }

    /**
     * 按 {@code orderBy} 排序（返回新列表，不修改原列表）。
     */
    static List<Map<String, Object>> sort(List<Map<String, Object>> rows, List<OrderFieldBean> orderBy,
                                            Map<String, String> nameToAlias, NopMetaTable table,
                                            List<String> measureNames, List<String> dimensionNames) {
        // 预解析 orderBy：name → alias，构造 (alias, desc, nullsFirst) 元组列表
        List<SortKey> keys = new ArrayList<>(orderBy.size());
        for (OrderFieldBean f : orderBy) {
            String name = f.getName();
            String alias = nameToAlias.get(name);
            if (alias == null) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", String.valueOf(name))
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            boolean nullsFirst = f.getNullsFirst() != null ? f.getNullsFirst() : !f.isDesc();
            keys.add(new SortKey(alias, f.isDesc(), nullsFirst));
        }

        List<Map<String, Object>> out = new ArrayList<>(rows);
        out.sort(new KeyComparator(keys));
        return out;
    }

    private static final class SortKey {
        final String alias;
        final boolean desc;
        final boolean nullsFirst;

        SortKey(String alias, boolean desc, boolean nullsFirst) {
            this.alias = alias;
            this.desc = desc;
            this.nullsFirst = nullsFirst;
        }
    }

    private static final class KeyComparator implements Comparator<Map<String, Object>> {
        private final List<SortKey> keys;

        KeyComparator(List<SortKey> keys) {
            this.keys = keys;
        }

        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            for (SortKey k : keys) {
                Object va = getCaseInsensitive(a, k.alias);
                Object vb = getCaseInsensitive(b, k.alias);
                int cmp = compareWithNulls(va, vb, k.nullsFirst);
                if (cmp != 0) {
                    return k.desc ? -cmp : cmp;
                }
            }
            return 0;
        }

        private static int compareWithNulls(Object a, Object b, boolean nullsFirst) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return nullsFirst ? -1 : 1;
            }
            if (b == null) {
                return nullsFirst ? 1 : -1;
            }
            BigDecimal ab = toBigDecimal(a);
            BigDecimal bb = toBigDecimal(b);
            if (ab != null && bb != null) {
                return ab.compareTo(bb);
            }
            if (a instanceof Comparable && b.getClass().isInstance(a)) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                int c = ((Comparable) a).compareTo(b);
                return c;
            }
            if (b instanceof Comparable && a.getClass().isInstance(b)) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                int c = ((Comparable) b).compareTo(a);
                return -c;
            }
            // 类型不可比较时回退到字符串比较
            return String.valueOf(a).compareTo(String.valueOf(b));
        }

        private static BigDecimal toBigDecimal(Object v) {
            if (v instanceof BigDecimal) {
                return (BigDecimal) v;
            }
            if (v instanceof java.math.BigInteger) {
                return new BigDecimal((java.math.BigInteger) v);
            }
            if (v instanceof Number) {
                return BigDecimal.valueOf(((Number) v).doubleValue());
            }
            return null;
        }

        private static Object getCaseInsensitive(Map<String, Object> map, String key) {
            if (map == null || key == null) {
                return null;
            }
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    /** 测试可访问的排序入口（仅用于单元测试）。 */
    static List<Map<String, Object>> sortForTest(List<Map<String, Object>> rows, List<OrderFieldBean> orderBy,
                                                   Map<String, String> nameToAlias, NopMetaTable table,
                                                   List<String> measureNames, List<String> dimensionNames) {
        return sort(rows, orderBy, nameToAlias, table, measureNames, dimensionNames);
    }

    /** 测试用：构造单字段 alias→alias 反查表的便捷方法。 */
    static Map<String, String> identityNameToAlias(List<String> names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String n : names) {
            map.put(n, n);
        }
        return map;
    }
}
