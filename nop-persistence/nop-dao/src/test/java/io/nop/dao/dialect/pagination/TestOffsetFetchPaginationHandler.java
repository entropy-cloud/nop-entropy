/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.dao.dialect.pagination;

import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖 wi4#4（plan 2306 项 17）：SQL 标准要求 FETCH FIRST 必须有 OFFSET 子句前置。
 * buildPageExpr 在 offset 为空、limit 非空时必须补 OFFSET 0 ROWS。
 */
public class TestOffsetFetchPaginationHandler {

    static ISqlExpr constExpr(String text) {
        return sb -> sb.sql(text);
    }

    private static String render(SqlExprList exprs) {
        return exprs.getSqlString();
    }

    @Test
    public void testLimitOnlyGetsOffsetZeroPrefix() {
        SqlExprList exprs = OffsetFetchPaginationHandler.INSTANCE
                .buildPageExpr(constExpr("?"), null, constExpr("select * from t"));
        String sql = render(exprs);
        assertTrue(sql.contains("OFFSET 0 ROWS"),
                "limit-only paging must emit OFFSET 0 ROWS before FETCH FIRST, got: " + sql);
        assertTrue(sql.contains("FETCH") && sql.contains("FIRST"), sql);
    }

    @Test
    public void testOffsetAndLimitUseNextForm() {
        SqlExprList exprs = OffsetFetchPaginationHandler.INSTANCE
                .buildPageExpr(constExpr("?"), constExpr("?"), constExpr("select * from t"));
        String sql = render(exprs);
        assertTrue(sql.contains("OFFSET"), sql);
        assertTrue(sql.contains("FETCH") && sql.contains("NEXT"), sql);
    }

    @Test
    public void testNoLimitNoOffsetKeepsSqlOnly() {
        SqlExprList exprs = OffsetFetchPaginationHandler.INSTANCE
                .buildPageExpr(null, null, constExpr("select * from t"));
        assertEquals("select * from t", render(exprs).trim());
    }
}
