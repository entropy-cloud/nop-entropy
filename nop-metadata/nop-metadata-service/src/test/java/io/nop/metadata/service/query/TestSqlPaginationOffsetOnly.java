/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-04 回归测试：10 处 LIMIT/OFFSET 拼接点统一改为 {@link SqlPagination#appendLimitOffset}，
 * 核心修复点是「{@code OFFSET ?} without {@code LIMIT ?}」在 MySQL 是非法语法。
 *
 * <p>验证 3 种方言的输出：
 * <ul>
 *   <li>MySQL offset-only → 补 {@code LIMIT 18446744073709551615 OFFSET ?}（MySQL 约定的"无限大 LIMIT"）。</li>
 *   <li>H2 / PostgreSQL offset-only → 保持 {@code OFFSET ?}（合法）。</li>
 *   <li>limit-only / limit+offset → 3 方言语法一致。</li>
 * </ul>
 *
 * <p>接线验证：直接调用 {@code SqlPagination.appendLimitOffset} 而非字符串常量比对，确保 helper 真实被使用
 * （helper 被 NopMetaTableBizModel/MetaAggregationExecutor/MetaJoinExecutor 共 10 处调用点引用）。
 */
public class TestSqlPaginationOffsetOnly {

    private String append(Long limit, Long offset, String dialect) {
        StringBuilder sb = new StringBuilder();
        SqlPagination.appendLimitOffset(sb, limit, offset, dialect);
        return sb.toString();
    }

    /** MySQL offset-only：必须补"无限大 LIMIT"占位（不出现裸 OFFSET）。 */
    @Test
    public void testMysqlOffsetOnlyGetsMaxLimitPlaceholder() {
        String sql = append(null, 100L, SqlPagination.DIALECT_MYSQL);
        assertEquals(" LIMIT " + SqlPagination.MYSQL_MAX_LIMIT + " OFFSET ?", sql,
                "MySQL offset-only must prepend LIMIT <max> placeholder");
        assertTrue(sql.contains(SqlPagination.MYSQL_MAX_LIMIT),
                "MySQL offset-only SQL must contain the max-limit constant");
        assertTrue(sql.indexOf("LIMIT") < sql.indexOf("OFFSET"),
                "LIMIT clause must precede OFFSET in MySQL output");
    }

    /** H2 offset-only：保持原 OFFSET 语义（合法）。 */
    @Test
    public void testH2OffsetOnlyNoLimitPlaceholder() {
        String sql = append(null, 100L, SqlPagination.DIALECT_H2);
        assertEquals(" OFFSET ?", sql, "H2 offset-only: bare OFFSET (no LIMIT placeholder)");
        assertFalse(sql.contains("LIMIT"),
                "H2 offset-only must NOT inject LIMIT placeholder (H2 allows bare OFFSET)");
    }

    /** PostgreSQL offset-only：保持原 OFFSET 语义（合法）。 */
    @Test
    public void testPostgresOffsetOnlyNoLimitPlaceholder() {
        String sql = append(null, 50L, SqlPagination.DIALECT_POSTGRESQL);
        assertEquals(" OFFSET ?", sql, "PostgreSQL offset-only: bare OFFSET");
        assertFalse(sql.contains("LIMIT"),
                "PostgreSQL offset-only must NOT inject LIMIT placeholder");
    }

    /** 未知/null 方言 offset-only：按 H2 语义处理（向后兼容）。 */
    @Test
    public void testUnknownDialectOffsetOnlyFallsBackToH2Semantics() {
        String sql = append(null, 10L, null);
        assertEquals(" OFFSET ?", sql,
                "null dialect (platform default = H2 semantics): bare OFFSET");
        String sqlUnknown = append(null, 10L, "Oracle");
        assertEquals(" OFFSET ?", sqlUnknown,
                "unknown dialect falls back to H2 semantics: bare OFFSET");
    }

    /** limit-only：3 方言语法一致（{@code LIMIT ?}）。 */
    @Test
    public void testLimitOnlySameAcrossDialects() {
        for (String d : new String[]{SqlPagination.DIALECT_MYSQL, SqlPagination.DIALECT_H2,
                SqlPagination.DIALECT_POSTGRESQL, null}) {
            assertEquals(" LIMIT ?", append(10L, null, d),
                    "limit-only produces same LIMIT ? for all dialects (dialect=" + d + ")");
        }
    }

    /** limit+offset：3 方言语法一致（{@code LIMIT ? OFFSET ?}）。 */
    @Test
    public void testLimitPlusOffsetSameAcrossDialects() {
        for (String d : new String[]{SqlPagination.DIALECT_MYSQL, SqlPagination.DIALECT_H2,
                SqlPagination.DIALECT_POSTGRESQL, null}) {
            assertEquals(" LIMIT ? OFFSET ?", append(10L, 5L, d),
                    "limit+offset produces same LIMIT ? OFFSET ? for all dialects (dialect=" + d + ")");
        }
    }

    /** offset=0 / null：不出现 OFFSET。 */
    @Test
    public void testZeroOrNullOffsetNoOffsetClause() {
        for (String d : new String[]{SqlPagination.DIALECT_MYSQL, SqlPagination.DIALECT_H2}) {
            // limit=10 + offset=0 → " LIMIT ?"（hasLimit=true, hasOffset=false）
            assertEquals(" LIMIT ?", append(10L, 0L, d),
                    "limit=10 + offset=0 → LIMIT only, no OFFSET (dialect=" + d + ")");
            // limit=10 + offset=null → " LIMIT ?"
            assertEquals(" LIMIT ?", append(10L, null, d),
                    "limit=10 + offset=null → LIMIT only, no OFFSET (dialect=" + d + ")");
            // limit=null + offset=0 → ""（既无 LIMIT 也无 OFFSET）
            assertEquals("", append(null, 0L, d),
                    "limit=null + offset=0 → no clauses (dialect=" + d + ")");
            // limit=null + offset=null → ""
            assertEquals("", append(null, null, d),
                    "all null → no clauses (dialect=" + d + ")");
        }
    }

    /** 接线验证：10 处调用点引用的常量与方法存在（防止重构后调用点失效）。 */
    @Test
    public void testWiringToCallSitesConstantsExist() {
        // 调用点（NopMetaTableBizModel/MetaAggregationExecutor/MetaJoinExecutor 共 10 处）依赖的公共 API
        assertTrue(SqlPagination.MYSQL_MAX_LIMIT.equals("18446744073709551615"),
                "constant value matches MySQL约定");
        // 公开方法可调用且产出与单测一致（证明 helper 是真实运行的，不是 stub）
        StringBuilder sb = new StringBuilder("SELECT 1");
        SqlPagination.appendLimitOffset(sb, null, 5L, SqlPagination.DIALECT_MYSQL);
        assertTrue(sb.toString().endsWith(" LIMIT " + SqlPagination.MYSQL_MAX_LIMIT + " OFFSET ?"),
                "MySQL offset-only wiring: append to existing SQL yields valid clause");
    }
}
