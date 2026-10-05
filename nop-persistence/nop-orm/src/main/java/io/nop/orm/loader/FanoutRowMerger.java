/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.impl.SingleColumnRow;
import io.nop.orm.eql.SqlOrderByOrdinal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * fanout 归并器（plan 2321，执行引擎设计 §3.4 归并语义分级）。
 *
 * <p>四级语义：无序拼接（concat）/ ORDER BY 内存排序（排序键经 Phase 1 orderBy→投影序数
 * 映射从组装行取值，未投影已在资格判定显式失败）/ 裸 count 求和（isBareCount）/ 浅分页
 * （每分片下推 offset+limit 后归并截断 [offset, offset+limit)，总数=各分片 count 求和，
 * 由独立的裸 count fanout 查询承载）。正确性能完整保住的显式做，需要深水区机制的显式拒
 * （GROUP BY/HAVING/DISTINCT/非count聚合/FOR UPDATE/UNION 由资格判定拦截）。</p>
 */
final class FanoutRowMerger {

    private FanoutRowMerger() {
    }

    /**
     * 无 ORDER BY：各分片结果按分片顺序拼接。
     */
    static List<IDataRow> mergeConcat(List<List<IDataRow>> shardRows) {
        if (shardRows.size() == 1)
            return shardRows.get(0);
        List<IDataRow> merged = new ArrayList<>();
        for (List<IDataRow> rows : shardRows) {
            merged.addAll(rows);
        }
        return merged;
    }

    /**
     * ORDER BY：全量拉回后内存排序产出真序。排序键经 orderBy→投影序数映射从组装行取值
     * （与 JdbcQueryExecutor.transformRow 同一取值通道）。排序稳定（TimSort），
     * 同键行保持分片顺序，跨分片结果确定性可复现。
     *
     * <p>空序缺省（nullsFirst 未声明）：null 视为最大值——asc 时 null 在后、desc 时在前
     * （H2/Postgres/Oracle 惯例）；显式 NULLS FIRST/LAST 覆盖缺省。</p>
     */
    static List<IDataRow> mergeSorted(List<List<IDataRow>> shardRows, List<SqlOrderByOrdinal> ordinals) {
        List<IDataRow> merged = mergeConcat(shardRows);
        Comparator<IDataRow> comparator = buildComparator(ordinals);
        merged.sort(comparator);
        return merged;
    }

    static Comparator<IDataRow> buildComparator(List<SqlOrderByOrdinal> ordinals) {
        List<Comparator<IDataRow>> comparators = new ArrayList<>(ordinals.size());
        for (SqlOrderByOrdinal ordinal : ordinals) {
            comparators.add(rowComparator(ordinal));
        }
        return (a, b) -> {
            for (Comparator<IDataRow> comparator : comparators) {
                int cmp = comparator.compare(a, b);
                if (cmp != 0)
                    return cmp;
            }
            return 0;
        };
    }

    private static Comparator<IDataRow> rowComparator(SqlOrderByOrdinal ordinal) {
        int index = ordinal.getOrdinal();
        boolean asc = ordinal.isAsc();
        Boolean nullsFirst = ordinal.getNullsFirst();
        return (a, b) -> compareValues(a.getObject(index), b.getObject(index), asc, nullsFirst);
    }

    static int compareValues(Object a, Object b, boolean asc, Boolean nullsFirst) {
        if (a == null && b == null)
            return 0;
        if (a == null || b == null) {
            // 缺省 null 视为最大值（asc: null 在后 / desc: null 在前）；显式三态覆盖
            boolean first = nullsFirst != null ? nullsFirst : !asc;
            if (a == null)
                return first ? -1 : 1;
            return first ? 1 : -1;
        }
        int cmp = ((Comparable<Object>) a).compareTo(b);
        return asc ? cmp : -cmp;
    }

    /**
     * 裸 count：各分片 count 求和，返回单行单列结果行。
     */
    static IDataRow mergeBareCount(List<List<IDataRow>> shardRows, IDataSetMeta meta) {
        long total = 0;
        for (List<IDataRow> rows : shardRows) {
            for (IDataRow row : rows) {
                total += ((Number) row.getObject(0)).longValue();
            }
        }
        return new SingleColumnRow(meta, total);
    }

    /**
     * 浅分页截断：归并（含排序）后取 [offset, offset+limit) 区间。每分片已下推
     * offset+limit 前缀（全局第 k 名必在每分片前 offset+limit 名内，正确性成立）。
     */
    static List<IDataRow> truncateToRange(List<IDataRow> rows, LongRangeBean range) {
        if (range == null)
            return rows;
        long offset = range.getOffset();
        long limit = range.getLimit();
        if (offset >= rows.size())
            return Collections.emptyList();
        int end = (int) Math.min(rows.size(), offset + limit);
        return new ArrayList<>(rows.subList((int) offset, end));
    }

    /**
     * 每分片下推范围：取各分片前 offset+limit 名（浅分页）；无分页返回 null（不截断）。
     */
    static LongRangeBean perShardRange(LongRangeBean range) {
        if (range == null)
            return null;
        return LongRangeBean.of(0, range.getOffset() + range.getLimit());
    }
}
