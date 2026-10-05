/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ①）：分页两语法形态的**端到端** lowering——EQL 文本 → parse 归一化
 * （{@code TestEqlPaginationFetchParse}）→ transform 遍历 →
 * {@code AstToSqlGenerator.visitSqlQuerySelect} → {@code dialect.getPaginationHandler()
 * .buildPageExpr(...)} → 方言 SQL 文本（接线验证 Minimum Rules #23：消费链真实连通，
 * 覆盖 LimitOffset 与 OffsetFetch 双族）。
 *
 * <p>裁定 = `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md`
 * §一（候选 A）：输入形态归一化进既有 {@code SqlLimit}{@code {limit, offset}}，输出形态由
 * 方言 {@code paginationHandler} 配置决定——h2 走 default 继承的
 * {@code LimitOffsetPaginationHandler}（其自身 OffsetFetch 配置处于注释态
 * {@code h2.dialect.xml:20}，Non-Blocking Follow-up 登记），oracle 走
 * {@code OffsetFetchPaginationHandler}（{@code oracle.dialect.xml:12}）。
 *
 * <p>两类用例（roadmap 约束 4）：一致性（同 LIMIT 语义两形态同方言输出一致）+
 * 不等价（同一输入跨 handler 族输出形态不同——h2 {@code LIMIT 5} vs oracle
 * {@code OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY}，AST 无形态字段、差异全部落在 lowering）。
 * 既有 {@code LIMIT/OFFSET} 形态逐字不变（{@code TestEqlSelectAllLimitCompile} 守护）。
 */
public class TestEqlPaginationFetchCompile {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    static String sqlOf(IDialect dialect, String eql) {
        return TestEqlCastTypeAliasCompile.sqlOf(dialect, eql);
    }

    // ---- LimitOffset 族（h2，default 继承） ----

    @Test
    public void testH2FetchFirstLowersToLimit() {
        String sql = sqlOf(dialectOf("h2"), "select o.name from AppDept o fetch first 5 rows only");
        assertTrue(sql.contains("LIMIT 5"), sql);
        assertFalse(sql.contains("FETCH"), sql);
    }

    @Test
    public void testH2OffsetFetchNextLowersToLimitOffset() {
        String sql = sqlOf(dialectOf("h2"), "select o.name from AppDept o offset 3 rows fetch next 10 rows only");
        assertTrue(sql.contains("LIMIT 10"), sql);
        assertTrue(sql.contains("OFFSET 3"), sql);
        assertFalse(sql.contains("FETCH"), sql);
    }

    @Test
    public void testH2FetchFirstMarkerLowersToLimitMarker() {
        String sql = sqlOf(dialectOf("h2"), "select o.name from AppDept o fetch first ? rows only");
        assertTrue(sql.contains("LIMIT ?"), sql);
    }

    // ---- OffsetFetch 族（oracle，OffsetFetchPaginationHandler） ----

    @Test
    public void testOracleFetchFirstLowersToOffsetFetchFirst() {
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o fetch first 5 rows only");
        // offset 缺省 ⇒ handler 自动补 OFFSET 0 ROWS 前置桥接（SQL 标准：FETCH 须有 OFFSET 前置）
        assertTrue(sql.toUpperCase().contains("OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY"), sql);
    }

    @Test
    public void testOracleOffsetFetchNextLowersToOffsetFetchNext() {
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o offset 3 rows fetch next 10 rows only");
        assertTrue(sql.toUpperCase().contains("OFFSET 3 ROWS FETCH NEXT 10 ROWS ONLY"), sql);
    }

    @Test
    public void testOracleFetchFirstMarkerLowersToOffsetFetchMarker() {
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o fetch first ? rows only");
        assertTrue(sql.toUpperCase().contains("OFFSET 0 ROWS FETCH FIRST ? ROWS ONLY"), sql);
    }

    // ---- 一致性（同语义两形态，同方言输出一致） ----

    @Test
    public void testH2FetchFirstConsistentWithLimitForm() {
        String viaFetch = sqlOf(dialectOf("h2"), "select o.name from AppDept o fetch first 5 rows only");
        String viaLimit = sqlOf(dialectOf("h2"), "select o.name from AppDept o limit 5");
        assertEquals(viaLimit, viaFetch);
    }

    @Test
    public void testH2OffsetFetchConsistentWithLimitOffsetForm() {
        String viaFetch = sqlOf(dialectOf("h2"), "select o.name from AppDept o offset 3 rows fetch next 10 rows only");
        String viaLimit = sqlOf(dialectOf("h2"), "select o.name from AppDept o limit 10 offset 3");
        assertEquals(viaLimit, viaFetch);
    }

    @Test
    public void testOracleFetchFirstConsistentWithLimitForm() {
        String viaFetch = sqlOf(dialectOf("oracle"), "select o.name from AppDept o fetch first 5 rows only");
        String viaLimit = sqlOf(dialectOf("oracle"), "select o.name from AppDept o limit 5");
        assertEquals(viaLimit, viaFetch);
    }

    // ---- 不等价登记（同输入跨 handler 族输出形态不同，约束 4 第二类） ----

    @Test
    public void testSameInputDifferentShapesAcrossHandlerFamilies() {
        String h2 = sqlOf(dialectOf("h2"), "select o.name from AppDept o fetch first 5 rows only");
        String oracle = sqlOf(dialectOf("oracle"), "select o.name from AppDept o fetch first 5 rows only");
        assertTrue(h2.contains("LIMIT 5"), h2);
        assertFalse(oracle.contains("LIMIT"), oracle);
        assertTrue(oracle.toUpperCase().contains("FETCH FIRST 5 ROWS ONLY"), oracle);
    }

    // ---- 既有 LIMIT 形态逐字不变守护（双族） ----

    @Test
    public void testLimitFormUnchangedOnH2() {
        String sql = sqlOf(dialectOf("h2"), "select o.name from AppDept o limit 10 offset 5");
        assertTrue(sql.contains("LIMIT 10"), sql);
        assertTrue(sql.contains("OFFSET 5"), sql);
        assertEquals(1, TestEqlSelectAllLimitCompile.countOccurrences(sql, "LIMIT"), sql);
        assertEquals(1, TestEqlSelectAllLimitCompile.countOccurrences(sql, "OFFSET"), sql);
    }

    @Test
    public void testLimitFormUnchangedOnOracle() {
        // 修复前即有行为：limit 形态在 OffsetFetch 族输出 OFFSET..FETCH NEXT（handler 决定）
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o limit 10 offset 5");
        assertTrue(sql.toUpperCase().contains("OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY"), sql);
        assertFalse(sql.toUpperCase().contains("LIMIT"), sql);
    }
}
