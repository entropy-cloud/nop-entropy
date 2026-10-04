/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.enums.SqlOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 1（plan 27；roadmap EC ①②③的解析面）：{@code IS [NOT] DISTINCT FROM}
 * 文法激活与构建期归一化（裁定
 * {@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md} §一.2/§九）。
 *
 * <p>规范 AST 形态（钉死）：{@code a IS NOT DISTINCT FROM b} ⇒
 * {@code SqlBinaryExpr(operator=SAFE_EQ)}（左右操作数保序）；{@code a IS DISTINCT FROM b} ⇒
 * {@code SqlNotExpr(SqlBinaryExpr(SAFE_EQ))}。修复前红：六种 NULL 谓词形态今天全部解析失败
 * （响亮 {@code nop.err.antlr.not-end-properly} 类），无静默错误路径。</p>
 *
 * <p>方言输出文本（h2/mysql/负向）见 {@code TestEqlNullSafeDistinctDialect}；
 * {@code IS [NOT] TRUE/FALSE/UNKNOWN} 解析见 Phase 2 的 {@code TestEqlNullSafeBooleanPredParse}。</p>
 */
public class TestEqlNullSafeDistinctParse {

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ").trim();
    }

    // ---- 修复前红组：两方向可解析且规范 AST 形态正确 ----

    @Test
    public void testIsNotDistinctFromParsesToSafeEqBinary() {
        SqlQuerySelect select = parseSelect(
                "select a.id from T a, T b where a.id is not distinct from b.id");
        SqlBinaryExpr expr = assertInstanceOf(SqlBinaryExpr.class, select.getWhere().getExpr());
        assertEquals(SqlOperator.SAFE_EQ, expr.getOperator(), "IS NOT DISTINCT FROM ⇒ SAFE_EQ");
        assertNotNull(expr.getLeft());
        assertNotNull(expr.getRight());
        assertEquals("a.id", expr.getLeft().toSqlString().replaceAll("\\s+", " ").trim());
        assertEquals("b.id", expr.getRight().toSqlString().replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testIsDistinctFromParsesToNotWrappedSafeEq() {
        SqlQuerySelect select = parseSelect(
                "select a.id from T a, T b where a.id is distinct from b.id");
        SqlNotExpr notExpr = assertInstanceOf(SqlNotExpr.class, select.getWhere().getExpr());
        SqlBinaryExpr inner = assertInstanceOf(SqlBinaryExpr.class, notExpr.getExpr());
        assertEquals(SqlOperator.SAFE_EQ, inner.getOperator(), "IS DISTINCT FROM ⇒ not(SAFE_EQ)");
    }

    @Test
    public void testKeywordCaseInsensitive() {
        SqlQuerySelect select = parseSelect(
                "select a.id from T a, T b where a.id IS NOT DISTINCT FROM b.id");
        SqlBinaryExpr expr = assertInstanceOf(SqlBinaryExpr.class, select.getWhere().getExpr());
        assertEquals(SqlOperator.SAFE_EQ, expr.getOperator());
    }

    @Test
    public void testNestedOperandsKeepOrder() {
        // 左右操作数可为表达式，保序（transformer 参数保持性论证的前提）。
        // ⚠️ CONCAT_('||') token 存在但无 parser 规则消费——|| 不是 EQL 现有语法，用函数表达式。
        SqlQuerySelect select = parseSelect(
                "select a.id from T a, T b where upper(a.name) is not distinct from b.name");
        SqlBinaryExpr expr = assertInstanceOf(SqlBinaryExpr.class, select.getWhere().getExpr());
        assertEquals(SqlOperator.SAFE_EQ, expr.getOperator());
        String leftEcho = expr.getLeft().toSqlString().replaceAll("\\s+", " ").trim();
        String rightEcho = expr.getRight().toSqlString().replaceAll("\\s+", " ").trim();
        assertTrue(leftEcho.contains("upper"), "左操作数=函数表达式: " + leftEcho);
        assertTrue(leftEcho.contains("a.name"), leftEcho);
        assertTrue(rightEcho.contains("b.name"), "右操作数保序: " + rightEcho);
    }

    // ---- 优先级与组合 ----

    @Test
    public void testSafeEqBindsTighterThanOr() {
        SqlQuerySelect select = parseSelect(
                "select a.id from T a, T b where a.id is not distinct from b.id or a.id = b.id");
        // or 的左支 = SAFE_EQ 谓词，右支 = EQ
        io.nop.orm.eql.ast.SqlOrExpr or = assertInstanceOf(io.nop.orm.eql.ast.SqlOrExpr.class,
                select.getWhere().getExpr());
        SqlBinaryExpr left = assertInstanceOf(SqlBinaryExpr.class, or.getLeft());
        assertEquals(SqlOperator.SAFE_EQ, left.getOperator());
        SqlBinaryExpr right = assertInstanceOf(SqlBinaryExpr.class, or.getRight());
        assertEquals(SqlOperator.EQ, right.getOperator());
    }

    // ---- 回显守护（三态断言的「规范回显」面） ----

    @Test
    public void testEchoCanonicalForm() {
        assertEquals("select a.id from T as a, T as b where a.id <=> b.id",
                echo("select a.id from T a, T b where a.id is not distinct from b.id"));
        assertEquals("select a.id from T as a, T as b where not ( a.id <=> b.id )",
                echo("select a.id from T a, T b where a.id is distinct from b.id"));
    }

    // ---- 既有 IS NULL 行为逐字不变（守护，plan Non-Goal） ----

    @Test
    public void testExistingIsNullBehaviorUnchanged() {
        SqlQuerySelect pos = parseSelect("select a.id from T a where a.id is null");
        assertInstanceOf(io.nop.orm.eql.ast.SqlIsNullExpr.class, pos.getWhere().getExpr());
        SqlQuerySelect neg = parseSelect("select a.id from T a where a.id is not null");
        io.nop.orm.eql.ast.SqlIsNullExpr isNull = assertInstanceOf(io.nop.orm.eql.ast.SqlIsNullExpr.class,
                neg.getWhere().getExpr());
        assertEquals(true, isNull.getNot());
        assertEquals("select a.id from T as a where a.id is not null",
                echo("select a.id from T a where a.id is not null"));
    }
}
