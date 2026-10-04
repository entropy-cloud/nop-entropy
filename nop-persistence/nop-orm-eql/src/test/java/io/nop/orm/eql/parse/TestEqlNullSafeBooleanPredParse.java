/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.enums.SqlOperator;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 2（plan 27；roadmap EC ③）：{@code IS [NOT] TRUE/FALSE/UNKNOWN} 文法激活与
 * 构建期真值表归一化（裁定
 * {@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md}
 * §一.3/§二.2/§五）。
 *
 * <p>规范 AST 形态（钉死展开式，标准 SQL 严格三值语义）：
 * {@code a IS TRUE ≡ a IS NOT NULL AND a = TRUE}；{@code a IS NOT TRUE ≡ NOT(...)}；
 * {@code a IS UNKNOWN ≡ a IS NULL}（SqlIsNullExpr 直用 not prop，生成体即正确零手写覆盖）。</p>
 *
 * <p>修复前红：六形态全部解析失败（`_tmp/wi8/red-probe.log`，offendingToken=true/false/unknown）。
 * ⚠️ 生成器对 TRUE/FALSE 四备选的**缺省体是透明委托**（返回操作数节点 = 静默丢谓词）——
 * 手写 visit 是语义唯一载体；NULL 行语义（{@code a IS NOT TRUE} 在 NULL 时 TRUE）不得降级为
 * 二元比较（roadmap 警告），负向对照见 {@code testExpansionIsNotBinaryComparisonDegradation}。</p>
 */
public class TestEqlNullSafeBooleanPredParse {

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ").trim();
    }

    /** 展开 ≠ 复用 DISTINCT FROM 模板：规范形态无 SAFE_EQ（负向守护，EC ③）。 */
    static SqlAndExpr assertTruthExpansionShape(SqlQuerySelect select, boolean literalValue) {
        SqlAndExpr and = assertInstanceOf(SqlAndExpr.class, select.getWhere().getExpr());
        SqlIsNullExpr notNull = assertInstanceOf(SqlIsNullExpr.class, and.getLeft());
        assertTrue(notNull.getNot(), "展开左支 = a IS NOT NULL");
        SqlBinaryExpr eq = assertInstanceOf(SqlBinaryExpr.class, and.getRight());
        assertEquals(SqlOperator.EQ, eq.getOperator(), "展开右支 = a = <literal>（EQ，非 SAFE_EQ——不得复用 DISTINCT FROM 模板）");
        SqlBooleanLiteral literal = assertInstanceOf(SqlBooleanLiteral.class, eq.getRight());
        assertEquals(literalValue, literal.getValue());
        return and;
    }

    // ---- 修复前红组：六形态可解析且展开形态正确 ----

    @Test
    public void testIsTrueParsesToSafeExpansion() {
        assertTruthExpansionShape(parseSelect("select a.id from T a where a.id is true"), true);
    }

    @Test
    public void testIsNotTrueWrapsExpansionInNot() {
        SqlNotExpr not = assertInstanceOf(SqlNotExpr.class,
                parseSelect("select a.id from T a where a.id is not true").getWhere().getExpr());
        assertInstanceOf(SqlAndExpr.class, not.getExpr());
    }

    @Test
    public void testIsFalseParsesToSafeExpansion() {
        assertTruthExpansionShape(parseSelect("select a.id from T a where a.id is false"), false);
    }

    @Test
    public void testIsNotFalseWrapsExpansionInNot() {
        SqlNotExpr not = assertInstanceOf(SqlNotExpr.class,
                parseSelect("select a.id from T a where a.id is not false").getWhere().getExpr());
        assertInstanceOf(SqlAndExpr.class, not.getExpr());
    }

    @Test
    public void testIsUnknownNormalizesToIsNullExpr() {
        // UNKNOWN ≡ NULL 三值精确等价：直用 SqlIsNullExpr（not=false），零展开、全方言复用 IS NULL 渲染
        SqlIsNullExpr expr = assertInstanceOf(SqlIsNullExpr.class,
                parseSelect("select a.id from T a where a.id is unknown").getWhere().getExpr());
        assertFalse(expr.getNot());
    }

    @Test
    public void testIsNotUnknownNormalizesToIsNotNullExpr() {
        SqlIsNullExpr expr = assertInstanceOf(SqlIsNullExpr.class,
                parseSelect("select a.id from T a where a.id is not unknown").getWhere().getExpr());
        assertTrue(expr.getNot());
    }

    @Test
    public void testKeywordCaseInsensitive() {
        SqlAndExpr and = assertInstanceOf(SqlAndExpr.class,
                parseSelect("select a.id from T a where a.id IS TRUE").getWhere().getExpr());
        assertNotNullShape(and);
    }

    private void assertNotNullShape(SqlAndExpr and) {
        assertTrue(and.getLeft() instanceof SqlIsNullExpr && and.getRight() instanceof SqlBinaryExpr);
    }

    // ---- 回显守护（三态断言的「规范回显」面） ----

    @Test
    public void testEchoCanonicalForms() {
        assertEquals("select a.id from T as a where a.id is not null and (a.id = true )",
                echo("select a.id from T a where a.id is true"));
        assertEquals("select a.id from T as a where not ( a.id is not null and (a.id = true ) )",
                echo("select a.id from T a where a.id is not true"));
        assertEquals("select a.id from T as a where a.id is not null and (a.id = false )",
                echo("select a.id from T a where a.id is false"));
        assertEquals("select a.id from T as a where not ( a.id is not null and (a.id = false ) )",
                echo("select a.id from T a where a.id is not false"));
        assertEquals("select a.id from T as a where a.id is null",
                echo("select a.id from T a where a.id is unknown"));
        assertEquals("select a.id from T as a where a.id is not null",
                echo("select a.id from T a where a.id is not unknown"));
    }

    /**
     * 展开式回显与手写等价式回显**逐字节一致**（约束 4「一致性」的构建期形态面）：
     * {@code a is true} 归一化产物 ≡ 手写 {@code a is not null and a = true} 的解析产物。
     */
    @Test
    public void testExpansionEchoEqualsHandWrittenEquivalent() {
        assertEquals(
                echo("select a.id from T a where a.id is not null and a.id = true"),
                echo("select a.id from T a where a.id is true"));
        assertEquals(
                echo("select a.id from T a where not (a.id is not null and a.id = true)"),
                echo("select a.id from T a where a.id is not true"));
        assertEquals(
                echo("select a.id from T a where a.id is not null and a.id = false"),
                echo("select a.id from T a where a.id is false"));
        assertEquals(
                echo("select a.id from T a where a.id is not null"),
                echo("select a.id from T a where a.id is not unknown"));
        assertEquals(
                echo("select a.id from T a where a.id is null"),
                echo("select a.id from T a where a.id is unknown"));
    }

    // ---- 错误降级负向对照（roadmap EC ③ / 约束 4 ③项：NULL 行语义守护） ----

    @Test
    public void testExpansionIsNotBinaryComparisonDegradation() {
        // a IS NOT TRUE 不得降级为 a<>TRUE / a<>0 / NOT a / truthy 裸条件：
        // 规范展开文本必含 "is not null"（guard 支），且不出现 <> 比较符
        String expanded = echo("select a.id from T a where a.id is not true");
        assertTrue(expanded.contains("is not null"), expanded);
        assertFalse(expanded.contains("<>"), "禁止二元比较降级: " + expanded);
        // 布尔谓词不得复用 DISTINCT FROM 展开模板（<=>）
        assertFalse(expanded.contains("<=>"), expanded);
    }

    // ---- 参数标记操作数响亮拒绝（裁定 §四.3：展开复制操作数 ⇒ 复制参数 = 绑定错位） ----

    @Test
    public void testParamMarkerOperandRejectedLoud() {
        NopEvalException ex = assertThrows(NopEvalException.class,
                () -> parseSelect("select a.id from T a where ? is true"));
        assertEquals(ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER.getErrorCode(), ex.getErrorCode());

        NopEvalException ex2 = assertThrows(NopEvalException.class,
                () -> parseSelect("select a.id from T a where (a.id + ?) is not false"));
        assertEquals(ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER.getErrorCode(), ex2.getErrorCode());
    }

    @Test
    public void testUnknownAllowsParamMarkerOperand() {
        // IS [NOT] UNKNOWN 归一化不复制操作数（单引用）——参数标记合法
        SqlIsNullExpr expr = assertInstanceOf(SqlIsNullExpr.class,
                parseSelect("select a.id from T a where ? is unknown").getWhere().getExpr());
        assertFalse(expr.getNot());
    }
}
