/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 3 Proof：验证 {@link MetaTableQueryExecutor} happy path + error path。
 *
 * <p>覆盖 SQL 构建 + 结果包装助手（无需真实 DB 连接）。
 */
public class TestMetaTableQueryExecutor {

    @Test
    public void testBuildExternalSelectSqlAllColumns() {
        String sql = MetaTableQueryExecutor.buildExternalSelectSql("my_table",
                Collections.emptyList(), null, null, null, "H2");
        assertEquals("SELECT * FROM my_table", sql);
    }

    @Test
    public void testBuildExternalSelectSqlWithColumnsAndFilter() {
        String sql = MetaTableQueryExecutor.buildExternalSelectSql("my_table",
                Arrays.asList("id", "name"), "id > ?", 10L, 5L, "PostgreSQL");
        assertEquals("SELECT id,name FROM my_table WHERE id > ? LIMIT ? OFFSET ?", sql);
    }

    @Test
    public void testBuildExternalSelectSqlRejectsInjection() {
        // 列名含注入字符 → 显式失败（不静默跳过）
        assertThrows(Exception.class,
                () -> MetaTableQueryExecutor.buildExternalSelectSql("my_table",
                        Arrays.asList("id; DROP TABLE users"), null, null, null, "H2"));
    }

    @Test
    public void testBuildSqlSelectSqlWrapsSubquery() {
        String sql = MetaTableQueryExecutor.buildSqlSelectSql(
                "SELECT id FROM src WHERE x=1", "id > ?", 100L, null, "MySQL");
        assertEquals("SELECT * FROM (SELECT id FROM src WHERE x=1) _t WHERE id > ? LIMIT ?", sql);
    }

    @Test
    public void testBuildQueryResultStructure() {
        Object result = MetaTableQueryExecutor.buildQueryResult("external", null);
        assertNotNull(result);
        assertTrue(result instanceof java.util.Map, "result must be a Map");
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
        assertEquals("external", map.get("tableType"));
        assertNotNull(map.get("items"));
        assertTrue(((java.util.List<?>) map.get("items")).isEmpty());
    }

    @Test
    public void testMessageOfFallback() {
        // null message → 类名
        Throwable t = new Throwable();
        String msg = MetaTableQueryExecutor.messageOf(t);
        assertEquals(t.getClass().getName(), msg);

        // non-null message → 原样返回
        Throwable t2 = new Throwable("some message");
        assertEquals("some message", MetaTableQueryExecutor.messageOf(t2));
    }
}
