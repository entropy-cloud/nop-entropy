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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ②/④/⑤）：{@code ORDER BY … NULLS FIRST/LAST} 文本输入的**端到端**
 * 行为面——EQL 文本 → parse 赋值 {@code nullsFirst}（Phase 3 文法激活）→ transform →
 * {@code AstToSqlGenerator} 的方言化发射 → 方言 SQL 或结构不可改写处响亮报错。
 *
 * <p>【merge note / Plan 2308 supersede】本类原基于 ours WI7 三分门（"缺省一致省略 /
 * 冲突或集合外报错"，裁定 wi7-phase0-adjudications §三）编写；merge 裁决采纳 master
 * Plan 2308 emission（support 位 gate + NullsOrderRewriter CASE 改写 + D2/D3/D4
 * fail-fast，翻案依据见 AstToSqlGenerator merge 裁决记录），分支 2 用例按下表改写：
 * <ul>
 * <li>mysql「缺省一致 ⇒ 省略」→ 改为断言 CASE 改写成功且方向正确（不再做省略优化）；</li>
 * <li>mysql/dm/tdengine「冲突/集合外 ⇒ ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE」→ 改为
 * 断言 CASE 改写成功（纯列引用可无损模拟）；</li>
 * <li>db2 由 [未取证] 报错口径改为原生直出（0ea57a097e supportNullsFirst=true 一手修正）；</li>
 * <li>fail-fast（非列引用/DISTINCT）改断言 nop.err.eql.nulls-order-not-supported + reason。</li>
 * </ul>
 * EC ⑤ 满足形态（登记项）：DuckDB 与 PG 的缺省文本差异编译期用例不可能存在；以
 * **显式子句 parity 用例**（{@code desc nulls last} 双库都输出 {@code nulls last}——
 * {@code testDuckdbPostgresqlDescNullsLastParity}）+ owner doc 差异表行两件满足。
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

    // ---- supportNullsFirst=true 方言文本输入打印子句（含 db2 一手修正后入组） ----

    @Test
    public void testPostgresqlPrintsNullsClauseFromText() {
        String sql = sqlOf(dialectOf("postgresql"), "select o.name from AppDept o order by o.id asc nulls first");
        assertTrue(sql.contains("nulls first"), sql);
    }

    @Test
    public void testOraclePrintsNullsClauseFromText() {
        // oracle 缺省 ASC nulls last（defaultNullsFirst=false）：显式 nulls first 与缺省不一致，
        // 但 support=true ⇒ 打印（请求即语义，无需查缺省序）——EC ④ oracle 侧
        String sql = sqlOf(dialectOf("oracle"), "select o.name from AppDept o order by o.id asc nulls first");
        assertTrue(sql.contains("nulls first"), sql);
    }

    @Test
    public void testDb2PrintsNullsClauseFromText() {
        // 0ea57a097e（master 一手修正）：DB2 LUW 原生支持 NULLS FIRST/LAST，显式请求原生直出
        //（merge 前 ours 口径为 [未取证] 报错，已按任务书联动指令调整）
        String asc = sqlOf(dialectOf("db2"), "select o.name from AppDept o order by o.id asc nulls first");
        assertTrue(asc.contains("nulls first"), asc);
        String desc = sqlOf(dialectOf("db2"), "select o.name from AppDept o order by o.id desc nulls last");
        assertTrue(desc.contains("nulls last"), desc);
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

    // ---- support=false 方言：CASE 改写（NullsOrderRewriter，方向正确性四组合） ----

    @Test
    public void testMysqlCaseRewriteAscNullsFirst() {
        String sql = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id asc nulls first");
        // null 在前：nullness 键（null→1）DESC 在前，原列保持 asc 在后；不再出现原生子句
        assertTrue(sql.matches("(?s).*SID is null then 1 else 0 end desc.*"), sql);
        assertTrue(sql.contains("SID asc"), sql);
        assertFalse(sql.contains("nulls"), sql);
    }

    @Test
    public void testMysqlCaseRewriteAscNullsLast() {
        // 原「冲突报错」用例改写：asc nulls last 经 CASE 无损模拟（null→0 组后置）
        String sql = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id asc nulls last");
        assertTrue(sql.matches("(?s).*SID is null then 0 else 1 end desc.*"), sql);
        assertTrue(sql.contains("SID asc"), sql);
        assertFalse(sql.contains("nulls"), sql);
    }

    @Test
    public void testMysqlCaseRewriteDescCombinations() {
        String first = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id desc nulls first");
        assertTrue(first.matches("(?s).*SID is null then 0 else 1 end asc.*"), first);
        assertTrue(first.contains("SID desc"), first);
        String last = sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by o.id desc nulls last");
        assertTrue(last.matches("(?s).*SID is null then 1 else 0 end asc.*"), last);
        assertTrue(last.contains("SID desc"), last);
        assertFalse(last.contains("nulls"), last);
    }

    @Test
    public void testUndocumentedDialectsCaseRewriteFromText() {
        // 原「集合外一律报错」用例改写：dm/tdengine 纯列引用显式请求走 CASE 改写
        for (String name : new String[]{"dm", "tdengine"}) {
            String sql = sqlOf(dialectOf(name), "select o.name from AppDept o order by o.id asc nulls first");
            assertTrue(sql.matches("(?s).*SID is null then 1 else 0 end desc.*"),
                    name + " must case-rewrite: " + sql);
            assertFalse(sql.contains("nulls"), name + ": " + sql);
        }
    }

    // ---- 结构不可改写处 fail-fast（D2/D3，真实方言端到端） ----

    @Test
    public void testNonColumnExpressionFailsFastFromText() {
        // 表达式不可安全复制 ⇒ 响亮拒绝（不再按缺省序推导放行/报 nullsFirst feature）
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("mysql"), "select o.name from AppDept o order by upper(o.name) nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("expression-not-duplicable", e.getParam("reason"));
    }

    @Test
    public void testDistinctSelectFailsFastFromText() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("mysql"),
                        "select distinct o.name from AppDept o order by o.id nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("distinct-not-supported", e.getParam("reason"));
    }

    // ---- BC：无 NULLS 文本的既有 order by 输出逐字不变 ----

    @Test
    public void testPlainOrderByOutputUnchanged() {
        for (String name : new String[]{"mysql", "oracle", "h2", "postgresql", "db2"}) {
            String asc = sqlOf(dialectOf(name), EQL_ASC);
            assertFalse(asc.contains("nulls"), name + ": " + asc);
            String desc = sqlOf(dialectOf(name), EQL_DESC);
            assertFalse(desc.contains("nulls"), name + ": " + desc);
            assertTrue(asc.contains("asc"), name + ": " + asc);
            assertTrue(desc.contains("desc"), name + ": " + desc);
        }
    }
}
