package io.nop.core.lang.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 1：SQL只读意图标志的传播完整性。
 * 分页handler等以SQL.begin(sql)/SqlBuilder.copy()重建SQL的路径必须保留标志，
 * 否则findPage会静默回落主库（第1轮对抗审查Blocker）。
 * enableFilter漏拷是在案同类先例，copy()修复一并钉住。
 */
public class TestSqlReadOnlyFlag {
    @Test
    public void testDefaultNotReadOnly() {
        SQL sql = SQL.begin().sql("select 1").end();
        assertFalse(sql.isReadOnly());
    }

    @Test
    public void testBuilderReadOnlyFlag() {
        SQL sql = SQL.begin().sql("select 1").readOnly().end();
        assertTrue(sql.isReadOnly());

        SQL sql2 = SQL.begin().sql("select 1").readOnly(true).end();
        assertTrue(sql2.isReadOnly());

        SQL sql3 = SQL.begin().sql("update t set a=1").readOnly(false).end();
        assertFalse(sql3.isReadOnly());
    }

    @Test
    public void testBeginFromSqlKeepsReadOnly() {
        SQL sql = SQL.begin().name("q").querySpace("qs").sql("select 1").readOnly(true).end();
        SQL copy = SQL.begin(sql).sql(" LIMIT ?", 5).end();
        assertTrue(copy.isReadOnly(), "SQL.begin(sql) rebuild must preserve readOnly");
        assertEquals("q", copy.getName());
        assertEquals("qs", copy.getQuerySpace());
        assertEquals(sql.getText() + " LIMIT ?", copy.getText());
    }

    @Test
    public void testBuilderCopyKeepsReadOnlyAndEnableFilter() {
        SQL.SqlBuilder sb = SQL.begin().name("q").querySpace("qs").readOnly(true).enableFilter(true);
        SQL.SqlBuilder copy = sb.copy();
        assertTrue(copy.isReadOnly(), "copy() must preserve readOnly");
        assertTrue(copy.isEnableFilter(), "copy() must preserve enableFilter (in-case precedent)");
        assertEquals("q", copy.end().getName());
    }

    @Test
    public void testCopyDefaultsUnset() {
        SQL.SqlBuilder copy = SQL.begin().copy();
        assertFalse(copy.isReadOnly());
        assertFalse(copy.isEnableFilter());
    }
}
