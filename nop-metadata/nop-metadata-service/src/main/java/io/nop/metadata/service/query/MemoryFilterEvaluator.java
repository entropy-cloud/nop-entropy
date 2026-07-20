/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存 TreeBean 求值器（plan 2026-07-18-0900-2 D11.3：跨库 JOIN 聚合内存路径 having 过滤）。
 *
 * <p>用于跨库内存 GROUP BY 路径的 {@code having} 过滤——聚合后的 group 行（{@code List<Map<String,Object>>}，
 * key 为大写化 alias）按 having TreeBean 递归求值，仅保留满足条件的 group。
 *
 * <p>op 集合与 SQL 路径对齐：eq/ne/gt/ge/lt/le/like/in/between/is-null/not-null + 组合 and/or/not。
 * 未知 op → 显式失败（{@link MetaAggregationExecutor#ERR_AGGR_HAVING_UNSUPPORTED_OP}）。
 *
 * <p>**类型强转（R1 M2）**：聚合值可能 Long/Double/BigDecimal/Integer/null，用户字面量可能 Integer/String/BigDecimal →
 * 比较前 Number 统一转 BigDecimal；非 Number 类型走 Comparable.compareTo（不静默转 0）。
 *
 * <p>**大小写匹配（R1 m2）**：叶子条件 name 与 group 行 key 比对 case-insensitive（与 safeAlias 大写化对齐）。
 *
 * <p>**name 反查**：name（用户选定的 measure/dimension name）→ alias（safeAlias 大写化）经调用方传入的 nameToAlias 反查表；
 * 未命中 → 显式失败（{@link MetaAggregationExecutor#ERR_AGGR_HAVING_UNKNOWN_NAME}）。
 */
final class MemoryFilterEvaluator {
    private final TreeBean having;
    private final Map<String, String> nameToAlias;
    private final NopMetaTable table;
    private final List<String> measureNames;
    private final List<String> dimensionNames;

    MemoryFilterEvaluator(TreeBean having, Map<String, String> nameToAlias, NopMetaTable table,
                          List<String> measureNames, List<String> dimensionNames) {
        this.having = having;
        this.nameToAlias = nameToAlias;
        this.table = table;
        this.measureNames = measureNames;
        this.dimensionNames = dimensionNames;
    }

    /** 按求值结果过滤行（不修改原列表，返回新列表）。 */
    List<Map<String, Object>> filter(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (evaluate(having, row)) {
                out.add(row);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluate(TreeBean node, Map<String, Object> row) {
        // plan 2026-07-18-1500-2：多列算术 having 在跨库内存路径显式失败（对齐 D12.2）
        // 检测点选在 evaluate 入口（R2 NEW-4）：错误上下文更清晰（含 op + name）。
        // 多列算术 leaf 经 setAttr/getAttr 承载 expr 属性；命中即拒绝，不静默跳过 / 不静默返回 false。
        Object exprAttr = node.getAttr(MetaAggregationExecutor.HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", exprAttr.toString());
        }
        String op = node.getTagName();
        if (op == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNSUPPORTED_OP)
                    .param("op", String.valueOf(op));
        }
        switch (op) {
            case FilterBeanConstants.FILTER_OP_AND:
                return evalAll(node.getChildren(), row);
            case FilterBeanConstants.FILTER_OP_OR:
                return evalAny(node.getChildren(), row);
            case FilterBeanConstants.FILTER_OP_NOT:
                List<TreeBean> notChildren = node.getChildren();
                if (notChildren == null || notChildren.isEmpty()) {
                    return true;
                }
                return !evaluate(notChildren.get(0), row);
            case FilterBeanConstants.FILTER_OP_EQ:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) == 0;
            case FilterBeanConstants.FILTER_OP_NE:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) != 0;
            case FilterBeanConstants.FILTER_OP_GT:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) > 0;
            case FilterBeanConstants.FILTER_OP_GE:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) >= 0;
            case FilterBeanConstants.FILTER_OP_LT:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) < 0;
            case FilterBeanConstants.FILTER_OP_LE:
                return compareValues(getRowValue(node, row), getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE)) <= 0;
            case FilterBeanConstants.FILTER_OP_LIKE: {
                Object rowVal = getRowValue(node, row);
                Object literal = getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE);
                if (rowVal == null || literal == null) {
                    return false;
                }
                String s = String.valueOf(rowVal);
                String pattern = String.valueOf(literal).replace("%", ".*").replace("_", ".");
                return s.matches(pattern);
            }
            case FilterBeanConstants.FILTER_OP_IN: {
                Object rowVal = getRowValue(node, row);
                Object literal = getLiteral(node, FilterBeanConstants.FILTER_ATTR_VALUE);
                if (!(literal instanceof Collection) && !(literal instanceof Object[])) {
                    throw new NopException(NopMetadataErrors.ERR_FILTER_IN_VALUE_NOT_COLLECTION)
                            .param("name", String.valueOf(node.getAttr(FilterBeanConstants.FILTER_ATTR_NAME)));
                }
                Collection<?> coll = (literal instanceof Collection)
                        ? (Collection<?>) literal
                        : java.util.Arrays.asList((Object[]) literal);
                for (Object v : coll) {
                    if (compareValues(rowVal, v) == 0) {
                        return true;
                    }
                }
                return false;
            }
            case FilterBeanConstants.FILTER_OP_BETWEEN: {
                Object rowVal = getRowValue(node, row);
                Object min = node.getAttr(FilterBeanConstants.FILTER_ATTR_MIN);
                Object max = node.getAttr(FilterBeanConstants.FILTER_ATTR_MAX);
                if (min == null && max == null) {
                    throw new NopException(NopMetadataErrors.ERR_FILTER_BETWEEN_MISSING_BOUNDS)
                            .param("name", String.valueOf(node.getAttr(FilterBeanConstants.FILTER_ATTR_NAME)));
                }
                if (min != null && compareValues(rowVal, min) < 0) {
                    return false;
                }
                if (max != null && compareValues(rowVal, max) > 0) {
                    return false;
                }
                return true;
            }
            case FilterBeanConstants.FILTER_OP_IS_NULL:
                return getRowValue(node, row) == null;
            case FilterBeanConstants.FILTER_OP_NOT_NULL:
                return getRowValue(node, row) != null;
            case FilterBeanConstants.FILTER_OP_ALWAYS_TRUE:
                return true;
            case FilterBeanConstants.FILTER_OP_ALWAYS_FALSE:
                return false;
            default:
                throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNSUPPORTED_OP)
                        .param("op", op)
                        .param("name", String.valueOf(node.getAttr(FilterBeanConstants.FILTER_ATTR_NAME)));
        }
    }

    private boolean evalAll(List<TreeBean> children, Map<String, Object> row) {
        if (children == null || children.isEmpty()) {
            return true;
        }
        for (TreeBean c : children) {
            if (!evaluate(c, row)) {
                return false;
            }
        }
        return true;
    }

    private boolean evalAny(List<TreeBean> children, Map<String, Object> row) {
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (TreeBean c : children) {
            if (evaluate(c, row)) {
                return true;
            }
        }
        return false;
    }

    /** 取叶子条件的聚合值（按 name 反查 alias，从 row case-insensitive 取值）。 */
    private Object getRowValue(TreeBean node, Map<String, Object> row) {
        Object nameObj = node.getAttr(FilterBeanConstants.FILTER_ATTR_NAME);
        if (nameObj == null || nameObj.toString().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_FILTER_MISSING_FIELD)
                    .param("op", String.valueOf(node.getTagName()));
        }
        String name = nameObj.toString();
        String alias = nameToAlias.get(name);
        if (alias == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name)
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        return getCaseInsensitive(row, alias);
    }

    private static Object getLiteral(TreeBean node, String attr) {
        return node.getAttr(attr);
    }

    /** 大小写不敏感取值（聚合行 key 已大写化，但防御性匹配）。 */
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

    /**
     * 比较两值：Number → BigDecimal 统一比较；其他 → {@link Comparable}。null 处理：null 视为小于一切（与 SQL NULL 语义近似）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        BigDecimal ab = toBigDecimal(a);
        BigDecimal bb = toBigDecimal(b);
        if (ab != null && bb != null) {
            return ab.compareTo(bb);
        }
        if (a instanceof Comparable && b.getClass().isInstance(a)) {
            return ((Comparable) a).compareTo(b);
        }
        if (b instanceof Comparable && a.getClass().isInstance(b)) {
            return -((Comparable) b).compareTo(a);
        }
        // 类型不可比较时回退到字符串比较（不静默失败）
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    /** 值→BigDecimal 转换（Integer/Long/Double/Float/BigDecimal/BigInteger 等）。非数值返回 null。 */
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

    /** 测试可访问的求值入口（仅用于单元测试，避免直接构造内部类）。 */
    static boolean evaluateForTest(TreeBean having, Map<String, String> nameToAlias, NopMetaTable table,
                                     List<String> measureNames, List<String> dimensionNames, Map<String, Object> row) {
        return new MemoryFilterEvaluator(having, nameToAlias, table, measureNames, dimensionNames).evaluate(having, row);
    }

    /** 测试可访问的过滤入口。 */
    static List<Map<String, Object>> filterForTest(TreeBean having, Map<String, String> nameToAlias, NopMetaTable table,
                                                    List<String> measureNames, List<String> dimensionNames,
                                                    List<Map<String, Object>> rows) {
        return new MemoryFilterEvaluator(having, nameToAlias, table, measureNames, dimensionNames).filter(rows);
    }
}
