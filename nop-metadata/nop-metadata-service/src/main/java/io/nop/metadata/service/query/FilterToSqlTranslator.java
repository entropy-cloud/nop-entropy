package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 平台 TreeBean filter 树 → SQL WHERE 片段 + PreparedStatement 绑定参数的翻译器（架构基线 §4.4 D1）。
 *
 * <p>复用 §2.7.1 D3 标识符注入防护：列名/字段名为 SQL 标识符，拼接前必须通过 {@link #IDENTIFIER_PATTERN}
 * 白名单校验（与 {@code MetaQualityRuleExecutor} 同正则）；比较值（eq/gt/in/between 等的 value）使用
 * PreparedStatement 参数绑定（返回 {@link #params} 列表，调用方按序 setX）。
 *
 * <p>首版支持的 TreeBean 标准叶子条件（架构基线 §4.4 D1 + Non-Blocking Follow-up「复杂 filter 求值」
 * 限定首版标准叶子条件）：eq/ne/gt/ge/lt/le/like/in/between/is-null/not-null + 组合条件 and/or/not。
 * 其余 op（如 contains/startsWith）首版显式失败抛 inline ErrorCode（不静默跳过、不伪造）。
 *
 * <p>无状态，可在多 BizModel 间共享实例。
 */
public class FilterToSqlTranslator {

    /** SQL 标识符白名单：字母/下划线开头，后跟字母/数字/下划线。与 MetaQualityRuleExecutor 一致，防注入。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    static final ErrorCode ERR_FILTER_INVALID_IDENTIFIER =
            ErrorCode.define("metadata.query-filter-invalid-identifier",
                    "Filter field name does not match identifier whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    "identifier");
    static final ErrorCode ERR_FILTER_UNSUPPORTED_OP =
            ErrorCode.define("metadata.query-filter-unsupported-op",
                    "Filter op not supported in first version: {op}", "op");
    static final ErrorCode ERR_FILTER_MISSING_FIELD =
            ErrorCode.define("metadata.query-filter-missing-field",
                    "Filter leaf condition missing 'name' attr (field name): {op}", "op");
    static final ErrorCode ERR_FILTER_MISSING_VALUE =
            ErrorCode.define("metadata.query-filter-missing-value",
                    "Filter leaf condition missing 'value' attr: {op} name={name}", "op", "name");
    static final ErrorCode ERR_FILTER_IN_VALUE_NOT_COLLECTION =
            ErrorCode.define("metadata.query-filter-in-not-collection",
                    "Filter 'in'/'notIn' value must be a collection: name={name}", "name");
    static final ErrorCode ERR_FILTER_BETWEEN_MISSING_BOUNDS =
            ErrorCode.define("metadata.query-filter-between-missing-bounds",
                    "Filter 'between' requires min and/or max attrs: name={name}", "name");
    /**
     * fieldResolver 命中失败（plan 2026-07-18-0900-2：having 引用未选定的 measure/dimension name）→ 显式失败。
     * 调用方应优先在反查表构建阶段用更具体的 {@code ERR_AGGR_HAVING_UNKNOWN_NAME} 失败；本 ErrorCode 为防御性兜底。
     */
    static final ErrorCode ERR_FILTER_FIELD_RESOLVER_MISS =
            ErrorCode.define("metadata.query-filter-field-resolver-miss",
                    "Filter field resolver returned no SQL expression for name (likely unknown measure/dimension "
                            + "in having/orderBy): {op} name={name}", "op", "name");

    /** 翻译结果：{@code sql} 为 WHERE 片段（不含 "WHERE" 关键字，无 filter 时为 null），{@code params} 为绑定参数。 */
    public static final class TranslatedFilter {
        private final String sql;
        private final List<Object> params;

        TranslatedFilter(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        /** WHERE 片段 SQL（无 "WHERE" 关键字）；无 filter 时为 null。 */
        public String getSql() {
            return sql;
        }

        /** 绑定参数列表（按 SQL 中 ? 出现顺序）；无 filter 时为空列表。 */
        public List<Object> getParams() {
            return params;
        }
    }

    /**
     * 翻译 filter 树为 WHERE 片段 + 参数。
     *
     * @param filter TreeBean filter 树（与 §2.5.2 D1 MetaTableFilter.definition 同结构）；null/无子节点返回空翻译
     * @return 翻译结果（{@code sql} 为 null 表示无 WHERE）
     */
    public TranslatedFilter translate(TreeBean filter) {
        return translate(filter, null);
    }

    /**
     * 翻译 filter 树为 WHERE/HAVING 片段 + 参数（plan 2026-07-18-0900-2：having 聚合后过滤支持）。
     *
     * <p>当 {@code fieldResolver} 非空时，叶子条件的 {@code name} 经回调解析为 SQL 表达式（如 {@code SUM(AMOUNT)}），
     * **跳过 {@link #validateIdentifier}**（因聚合表达式含括号会触发白名单失败）；该表达式中的列名已在 measure/dimension
     * 加载时经白名单校验。{@code fieldResolver} 为空时维持既有 {@code requireField + validateIdentifier} 行为。
     *
     * <p>递归 and/or/not 逻辑完全复用既有 {@code joinChildren}/{@code translateNot}。既有 {@link #translate(TreeBean)}
     * 行为不变（委托 {@code translate(filter, null)}）。
     *
     * @param filter        TreeBean filter 树；null/无子节点返回空翻译
     * @param fieldResolver 叶子条件 name → SQL 表达式的回调（非空时跳过标识符白名单）；可空
     * @return 翻译结果（{@code sql} 为 null 表示无 WHERE/HAVING）
     */
    public TranslatedFilter translate(TreeBean filter, Function<String, String> fieldResolver) {
        if (filter == null) {
            return new TranslatedFilter(null, new ArrayList<>());
        }
        List<Object> params = new ArrayList<>();
        String sql = translateNode(filter, params, fieldResolver);
        return new TranslatedFilter(sql, params);
    }

    private String translateNode(TreeBean node, List<Object> params, Function<String, String> fieldResolver) {
        String op = node.getTagName();
        if (op == null) {
            throw new NopException(ERR_FILTER_UNSUPPORTED_OP).param("op", String.valueOf(op));
        }
        switch (op) {
            case FilterBeanConstants.FILTER_OP_AND:
                return joinChildren(node, " AND ", params, fieldResolver, true);
            case FilterBeanConstants.FILTER_OP_OR:
                return joinChildren(node, " OR ", params, fieldResolver, true);
            case FilterBeanConstants.FILTER_OP_NOT:
                return translateNot(node, params, fieldResolver);
            case FilterBeanConstants.FILTER_OP_EQ:
            case FilterBeanConstants.FILTER_OP_NE:
            case FilterBeanConstants.FILTER_OP_GT:
            case FilterBeanConstants.FILTER_OP_GE:
            case FilterBeanConstants.FILTER_OP_LT:
            case FilterBeanConstants.FILTER_OP_LE:
            case FilterBeanConstants.FILTER_OP_LIKE:
                return translateComparison(op, node, params, fieldResolver);
            case FilterBeanConstants.FILTER_OP_IN:
                return translateIn(node, params, fieldResolver, false);
            case FilterBeanConstants.FILTER_OP_NOT_IN:
                return translateIn(node, params, fieldResolver, true);
            case FilterBeanConstants.FILTER_OP_BETWEEN:
                return translateBetween(node, params, fieldResolver);
            case FilterBeanConstants.FILTER_OP_IS_NULL:
                return translateNullCheck(node, fieldResolver, "IS NULL");
            case FilterBeanConstants.FILTER_OP_NOT_NULL:
                return translateNullCheck(node, fieldResolver, "IS NOT NULL");
            case FilterBeanConstants.FILTER_OP_ALWAYS_TRUE:
                return "1=1";
            case FilterBeanConstants.FILTER_OP_ALWAYS_FALSE:
                return "1=0";
            default:
                // 首版不支持的 op → 显式失败（不静默跳过、不伪造）
                throw new NopException(ERR_FILTER_UNSUPPORTED_OP).param("op", op);
        }
    }

    private String joinChildren(TreeBean node, String sep, List<Object> params,
                                 Function<String, String> fieldResolver, boolean wrapIfMulti) {
        List<TreeBean> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            // and/or 无子节点 → 视为无过滤（返回恒真，避免拼出空括号）；调用方拼 WHERE 时会忽略 null/空
            return null;
        }
        List<String> parts = new ArrayList<>(children.size());
        for (TreeBean child : children) {
            String part = translateNode(child, params, fieldResolver);
            if (part != null) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String joined = String.join(sep, parts);
        return wrapIfMulti ? "(" + joined + ")" : joined;
    }

    private String translateNot(TreeBean node, List<Object> params, Function<String, String> fieldResolver) {
        List<TreeBean> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return null;
        }
        // not 仅取第一个子条件（标准语义）
        String inner = translateNode(children.get(0), params, fieldResolver);
        if (inner == null) {
            return null;
        }
        return "NOT (" + inner + ")";
    }

    private String translateComparison(String op, TreeBean node, List<Object> params,
                                        Function<String, String> fieldResolver) {
        String col = requireField(node, fieldResolver);
        String sqlOp = sqlOpOf(op);
        Object value = node.getAttr(FilterBeanConstants.FILTER_ATTR_VALUE);
        if (value == null && !hasAttr(node, FilterBeanConstants.FILTER_ATTR_VALUE)) {
            throw new NopException(ERR_FILTER_MISSING_VALUE)
                    .param("op", op).param("name", col);
        }
        params.add(value);
        return col + " " + sqlOp + " ?";
    }

    private String translateIn(TreeBean node, List<Object> params, Function<String, String> fieldResolver,
                                boolean negated) {
        String col = requireField(node, fieldResolver);
        Object value = node.getAttr(FilterBeanConstants.FILTER_ATTR_VALUE);
        if (value == null && !hasAttr(node, FilterBeanConstants.FILTER_ATTR_VALUE)) {
            throw new NopException(ERR_FILTER_MISSING_VALUE)
                    .param("op", node.getTagName()).param("name", col);
        }
        if (!(value instanceof java.util.Collection) && !(value instanceof Object[])) {
            throw new NopException(ERR_FILTER_IN_VALUE_NOT_COLLECTION).param("name", col);
        }
        java.util.Collection<?> coll;
        if (value instanceof java.util.Collection) {
            coll = (java.util.Collection<?>) value;
        } else {
            coll = java.util.Arrays.asList((Object[]) value);
        }
        if (coll.isEmpty()) {
            // in 空集 → 恒假（SQL IN () 不合法，故翻译为 1=0 / not in 空集 → 恒真）
            return negated ? "1=1" : "1=0";
        }
        StringBuilder sb = new StringBuilder(col);
        sb.append(negated ? " NOT IN (" : " IN (");
        boolean first = true;
        for (Object v : coll) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("?");
            params.add(v);
        }
        sb.append(")");
        return sb.toString();
    }

    private String translateBetween(TreeBean node, List<Object> params, Function<String, String> fieldResolver) {
        String col = requireField(node, fieldResolver);
        Object min = node.getAttr(FilterBeanConstants.FILTER_ATTR_MIN);
        Object max = node.getAttr(FilterBeanConstants.FILTER_ATTR_MAX);
        if (min == null && max == null) {
            throw new NopException(ERR_FILTER_BETWEEN_MISSING_BOUNDS).param("name", col);
        }
        StringBuilder sb = new StringBuilder();
        if (min != null && max != null) {
            sb.append(col).append(" BETWEEN ? AND ?");
            params.add(min);
            params.add(max);
        } else if (min != null) {
            sb.append(col).append(" >= ?");
            params.add(min);
        } else {
            sb.append(col).append(" <= ?");
            params.add(max);
        }
        return sb.toString();
    }

    private String translateNullCheck(TreeBean node, Function<String, String> fieldResolver, String sqlSuffix) {
        String col = requireField(node, fieldResolver);
        return col + " " + sqlSuffix;
    }

    /**
     * 解析叶子条件的字段名：若提供 {@code fieldResolver}，经回调解析为 SQL 表达式（跳过白名单，因聚合表达式含括号）；
     * 否则按既有 {@code validateIdentifier} 白名单校验裸列名。
     */
    private String requireField(TreeBean node, Function<String, String> fieldResolver) {
        Object nameObj = node.getAttr(FilterBeanConstants.FILTER_ATTR_NAME);
        if (nameObj == null || nameObj.toString().isEmpty()) {
            throw new NopException(ERR_FILTER_MISSING_FIELD).param("op", String.valueOf(node.getTagName()));
        }
        String name = nameObj.toString();
        if (fieldResolver != null) {
            String resolved = fieldResolver.apply(name);
            if (resolved == null || resolved.isEmpty()) {
                // fieldResolver 命中失败（未选定 measure/dimension name）→ 显式失败（不静默跳过、不伪造）
                throw new NopException(ERR_FILTER_FIELD_RESOLVER_MISS)
                        .param("op", String.valueOf(node.getTagName()))
                        .param("name", name);
            }
            return resolved;
        }
        validateIdentifier(name);
        return name;
    }

    private static boolean hasAttr(TreeBean node, String attrName) {
        return node.getAttrs() != null && node.getAttrs().containsKey(attrName);
    }

    private static String sqlOpOf(String filterOp) {
        switch (filterOp) {
            case FilterBeanConstants.FILTER_OP_EQ:
                return "=";
            case FilterBeanConstants.FILTER_OP_NE:
                return "<>";
            case FilterBeanConstants.FILTER_OP_GT:
                return ">";
            case FilterBeanConstants.FILTER_OP_GE:
                return ">=";
            case FilterBeanConstants.FILTER_OP_LT:
                return "<";
            case FilterBeanConstants.FILTER_OP_LE:
                return "<=";
            case FilterBeanConstants.FILTER_OP_LIKE:
                return "LIKE";
            default:
                throw new NopException(ERR_FILTER_UNSUPPORTED_OP).param("op", filterOp);
        }
    }

    public static void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new NopException(ERR_FILTER_INVALID_IDENTIFIER)
                    .param("identifier", String.valueOf(identifier));
        }
    }}
