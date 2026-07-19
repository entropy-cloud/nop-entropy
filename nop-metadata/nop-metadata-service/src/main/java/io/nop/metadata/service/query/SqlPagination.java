/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

/**
 * LIMIT/OFFSET 拼接 helper（plan 2026-07-19-1250-1 Phase 3，AR-04 修复）。
 *
 * <p><b>问题</b>：原 10 处拼接点的语义为「{@code limit != null} 拼 LIMIT，{@code offset > 0} 拼 OFFSET」，
 * 但 {@code OFFSET ?} without {@code LIMIT ?} 在 MySQL 是非法语法（MySQL 要求 OFFSET 必须跟在 LIMIT 后）。
 * H2/PostgreSQL 允许 offset-only。
 *
 * <p><b>修复策略</b>：统一抽 helper，按方言分派：
 * <ul>
 *   <li>MySQL offset-only（{@code offset != null && offset > 0 && limit == null}）：
 *       在 OFFSET 之前补 {@code LIMIT 18446744073709551615}（MySQL 约定的"无限大 LIMIT"）。</li>
 *   <li>H2 / PostgreSQL offset-only：保持 {@code OFFSET ?}（合法）。</li>
 *   <li>未知/null 方言：按 H2 语义处理（保持向后兼容）。</li>
 *   <li>limit-only / limit+offset：3 方言语法一致，原样拼。</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   SqlPagination.appendLimitOffset(sb, limit, offset, "MySQL");
 *   // 占位 ? 仍由调用方按原有条件绑定：
 *   //   if (limit != null) params.add(limit);
 *   //   if (offset != null && offset > 0) params.add(offset);
 *   // MySQL offset-only 时 helper 已直接拼入常量 LIMIT 18446744073709551615，不占参数位。
 * </pre>
 *
 * <p><b>绑定顺序约定</b>：调用方应按 {@code filter 参数 → limit 参数 → offset 参数} 的顺序绑定，
 * 因为 helper 先拼 LIMIT（一个 ? 占位），后拼 OFFSET（一个 ? 占位）。
 */
public final class SqlPagination {

    /** MySQL 约定的"无限大 LIMIT"常量（{@code UNSIGNED BIGINT 最大值}），用于 offset-only 分页。 */
    public static final String MYSQL_MAX_LIMIT = "18446744073709551615";

    /** MySQL 数据库产品名（{@code DatabaseMetaData.getDatabaseProductName()}）。 */
    public static final String DIALECT_MYSQL = "MySQL";
    /** H2 数据库产品名。 */
    public static final String DIALECT_H2 = "H2";
    /** PostgreSQL 数据库产品名。 */
    public static final String DIALECT_POSTGRESQL = "PostgreSQL";

    private SqlPagination() {
    }

    /**
     * 按方言追加 LIMIT/OFFSET 子句到 {@code sb}。
     *
     * <p><b>不</b>在此方法绑定参数值。调用方需按原有条件绑定：
     * <pre>
     *   if (limit != null) params.add(limit);
     *   if (offset != null && offset > 0) params.add(offset);
     * </pre>
     *
     * @param sb             目标 StringBuilder
     * @param limit          LIMIT 值（null 表示不限制）
     * @param offset         OFFSET 值（null 或 ≤ 0 表示不偏移）
     * @param databaseProductName 数据库产品名（MySQL / H2 / PostgreSQL）；null 视为 H2 语义（向后兼容）
     */
    public static void appendLimitOffset(StringBuilder sb, Long limit, Long offset, String databaseProductName) {
        boolean hasOffset = offset != null && offset > 0;
        boolean hasLimit = limit != null;
        boolean isMysqlOffsetOnly = hasOffset && !hasLimit && DIALECT_MYSQL.equals(databaseProductName);

        if (hasLimit) {
            sb.append(" LIMIT ?");
        } else if (isMysqlOffsetOnly) {
            // AR-04: MySQL 不允许 OFFSET without LIMIT，用约定常量"无限大 LIMIT"占位
            sb.append(" LIMIT ").append(MYSQL_MAX_LIMIT);
        }
        if (hasOffset) {
            sb.append(" OFFSET ?");
        }
    }
}
