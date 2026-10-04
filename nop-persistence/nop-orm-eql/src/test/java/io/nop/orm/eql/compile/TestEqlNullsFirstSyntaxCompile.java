/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ②/④/⑤）：{@code ORDER BY … NULLS FIRST/LAST} 文本输入的**端到端**
 * 行为面——EQL 文本 → parse 赋值 {@code nullsFirst}（Phase 3 文法激活）→ transform →
 * {@code AstToSqlGenerator.visitSqlOrderByItem} 三分门（Phase 2 落地）→ 方言 SQL 或响亮报错
 * （接线验证 Minimum Rules #23：mysql 冲突报错一条 + postgresql 打印一条即本文件）。
 *
 * <p>裁定 = {@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md}
 * §三/§六。EC ⑤ 满足形态（登记项）：DuckDB 与 PG 的 {@code defaultNullsFirst} 均 false 且
 * 缺省不补子句 ⇒ 缺省文本差异编译期用例不可能存在；以**显式子句 parity 用例**
 * （{@code desc nulls last} 双库都输出 {@code nulls last}——本文件
 * {@code testDuckdbPostgresqlDescNullsLastParity}）+ owner doc 差异表行（已落
 * 「defaultNullsFirst 与 supportNullsFirst」小节）两件满足。
 */
public class TestEqlNullsFirstSyntaxCompile {

    static final String EQL_ASC = "select o.name from AppDept o order by o.id";
    static final String EQL_DESC = "select o.name from AppDept o order by o.id desc";

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

    // ---- 分支 1：supportNullsFirst=true 方言文本输入打印子句 ----

    @Test
    public void testPostgresqlPrintsNullsClauseFromText() {
        String sql = sqlOf(dialectOf("postgresql"), "select o.name from AppDept o order by o.id asc nulls first");
        assertTrue(sql.contains("nulls first"), sql);
    }

    @Test
    public void testOraclePrintsNullsClauseFromText() {
        // oracle 缺省 ASC nulls last（defaultNullsFirst=false）：显式 nulls first 与缺省不一致，
        // 但 support=true ⇒ 打印（分支 1 请求即打印，无需查缺省序）——EC ④ oracle 侧
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o order by o.id asc nulls first");
        assertTrue(sql.contains("nulls first"), sql);
    }

    @Test
    public void testDuckdbPostgresqlDescNullsLastParity() {
        // EC ⑤ parity 用例：desc nulls last 在 duckdb 与 pg 都输出 nulls last 子句
        // （两库均可经显式子句表达同一语义；缺省时两库行为不同——DuckDB keeps NULLS LAST
        // even for DESC vs PG places NULLs first on DESC——owner doc 差异表行已登记）
        String duckdb = sqlOf(dialectOf("duckdb"), "select o.name from AppDept o order by o.id desc nulls last");
        String pg = sqlOf(dialectOf("postgresql"), "select o.name from AppDept o order by o.id desc nulls last");
        assertTrue(duckdb.contains("nulls last"), duckdb);
        assertTrue(pg.contains("nulls last"), pg);
        // 而**缺省** desc 输出两库都不含 nulls 子句（缺省文本差异用例不可能——登记项的实证半边）
        assertFalse(sqlOf(dialectOf("duckdb"), EQL_DESC).contains("nulls"));
        assertFalse(sqlOf(dialectOf("postgresql"), EQL_DESC).contains("nulls"));
    }

    @Test
    public void testH2PrintsNullsClauseFromText() {
        String sql = sqlOf(dialectOf("h2"), "select o.name from AppDept o order by o.id desc nulls last");
        assertTrue(sql.contains("nulls last"), sql);
    }

    // ---- 分支 2：mysql（defaultNullsFirst=true + 一手文档）三态（EC ④ mysql 侧） ----

    @Test
    public void testMysqlAscNullsFirstOmittedAsConsistent() {
        String baseline = sqlOf(dialectOf("mysql"), EQL_ASC);
        String withClause = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id asc nulls first");
        assertFalse(withClause.contains("nulls"), withClause);
        assertEquals(baseline, withClause, "asc nulls first == mysql 缺省序 ⇒ 省略子句、输出逐字节一致");
    }

    @Test
    public void testMysqlDescNullsLastOmittedAsConsistent() {
        // DESC 镜像（一手文档：MySQL DESC 时 NULL 排后）经文法激活后验证
        String baseline = sqlOf(dialectOf("mysql"), EQL_DESC);
        String withClause = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id desc nulls last");
        assertFalse(withClause.contains("nulls"), withClause);
        assertEquals(baseline, withClause, "desc nulls last == mysql DESC 缺省序 ⇒ 省略子句");
    }

    @Test
    public void testMysqlAscNullsLastConflictFailsLoudly() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id asc nulls last"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("nullsFirst(last)", e.getParam("feature"));
    }

    // ---- 分支 2 集合外：dm/tdengine 文本显式请求一律报错（红线，端到端） ----

    @Test
    public void testDmAndTdengineExplicitRequestFailsLoudlyFromText() {
        for (String name : new String[]{"dm", "tdengine"}) {
            NopException e = assertThrows(NopException.class,
                    () -> sqlOf(dialectOf(name), "select o.name from AppDept o order by o.id asc nulls first"),
                    name + " explicit nulls first must throw");
            assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(), name);
            assertEquals("nullsFirst(first)", e.getParam("feature"), name);
        }
    }

    // ---- BC：无 NULLS 文本的既有 order by 输出逐字不变 ----

    @Test
    public void testPlainOrderByOutputUnchanged() {
        for (String name : new String[]{"mysql", "oracle", "h2", "postgresql"}) {
            String asc = sqlOf(dialectOf(name), EQL_ASC);
            assertFalse(asc.contains("nulls"), name + ": " + asc);
            String desc = sqlOf(dialectOf(name), EQL_DESC);
            assertFalse(desc.contains("nulls"), name + ": " + desc);
            assertTrue(asc.contains("asc"), name + ": " + asc);
            assertTrue(desc.contains("desc"), name + ": " + desc);
        }
    }
}
