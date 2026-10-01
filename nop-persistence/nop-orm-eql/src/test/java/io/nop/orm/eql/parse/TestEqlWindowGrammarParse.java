/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlWindowClause;
import io.nop.orm.eql.ast.SqlWindowDecl;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlWindowExpr;
import io.nop.orm.eql.ast.SqlWindowFrame;
import io.nop.orm.eql.ast.SqlWindowFrameBound;
import io.nop.orm.eql.enums.SqlWindowFrameBoundType;
import io.nop.orm.eql.enums.SqlWindowFrameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI1: 分析窗口语法 parse 矩阵——OVER 单子句 / 空参 / frame 三单位 / 命名窗口。
 * 仅断言 parse 成败（AST 字段断言见同目录字段断言用例）；
 * 不得使用 toSQL() 往返做断言（AstToEqlGenerator 打印新语法归 WI2）。
 */
public class TestEqlWindowGrammarParse extends BaseTestCase {

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    // ---- success: OVER 子句可选形态 ----

    @Test
    public void testOverPartitionByOnly() {
        assertNotNull(parse("select sum(a) over (partition by b) from t"));
    }

    @Test
    public void testOverOrderByOnly() {
        assertNotNull(parse("select sum(a) over (order by b) from t"));
    }

    @Test
    public void testOverEmpty() {
        assertNotNull(parse("select sum(a) over () from t"));
    }

    @Test
    public void testOverLegacyBothClauses() {
        // 既有形态回归：PARTITION BY + ORDER BY 同时出现
        assertNotNull(parse("select sum(a) over (partition by b order by c) from t"));
    }

    @Test
    public void testOverNamedWindowRef() {
        assertNotNull(parse("select sum(a) over w from t window w as (partition by b)"));
    }

    // ---- success: WINDOW 命名窗口子句 ----

    @Test
    public void testWindowClause() {
        assertNotNull(parse("select a from t window w as (partition by b), w2 as (order by c)"));
    }

    @Test
    public void testWindowAfterHavingBeforeOrderBy() {
        // 钉子句顺序：HAVING 之后、ORDER BY 之前
        SqlProgram program = parse("select a, sum(b) from t where a > 1 group by a "
                + "having count(*) > 1 window w as (partition by a) order by a limit 10");
        assertNotNull(program);
    }

    // ---- success: frame 三单位 ----

    @Test
    public void testRowsFrameBetween() {
        assertNotNull(parse("select sum(a) over (partition by b order by c "
                + "rows between unbounded preceding and current row) from t"));
    }

    @Test
    public void testRangeFrameExprBounds() {
        assertNotNull(parse("select sum(a) over (order by c "
                + "range between 5 preceding and 5 following) from t"));
    }

    @Test
    public void testGroupsFrameSingleBound() {
        assertNotNull(parse("select sum(a) over (order by c groups 1 preceding) from t"));
    }

    @Test
    public void testRowsUnboundedFollowing() {
        assertNotNull(parse("select sum(a) over (order by c "
                + "rows between current row and unbounded following) from t"));
    }

    // ---- fail: 语法残缺必须 fail-fast ----

    @Test
    public void testFrameMissingAnd() {
        Executable e = () -> parse("select sum(a) over (order by c rows between unbounded preceding) from t");
        assertThrows(NopException.class, e);
    }

    @Test
    public void testOverMissingParensAndNotName() {
        // OVER 后既非括号也非标识符（聚合函数直接跟 OVER 是残缺形态）
        Executable e = () -> parse("select sum(a) over from t");
        assertThrows(NopException.class, e);
    }

    @Test
    public void testWindowDeclMissingSpec() {
        Executable e = () -> parse("select a from t window w");
        assertThrows(NopException.class, e);
    }

    // ---- AST 字段断言（每能力至少一条，经 parseFromText 全链路；不使用 toSQL 往返） ----

    SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = parse(sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    SqlWindowExpr firstWindowExpr(SqlQuerySelect select) {
        return (SqlWindowExpr) ((SqlExprProjection) select.getProjections().get(0)).getExpr();
    }

    @Test
    public void testFieldAssertionsOverClauses() {
        SqlWindowExpr expr = firstWindowExpr(parseSelect(
                "select sum(a) over (partition by b order by c) from t"));
        assertEquals("sum", ((SqlAggregateFunction) expr.getFunction()).getName());
        assertEquals(1, expr.getPartitionBy().getItems().size());
        assertEquals(1, expr.getOrderBy().getItems().size());
        assertNull(expr.getFrame());
        assertNull(expr.getWindowName());
    }

    @Test
    public void testFieldAssertionsOverEmpty() {
        SqlWindowExpr expr = firstWindowExpr(parseSelect("select sum(a) over () from t"));
        assertNull(expr.getPartitionBy());
        assertNull(expr.getOrderBy());
        assertNull(expr.getFrame());
    }

    @Test
    public void testFieldAssertionsFrame() {
        SqlWindowExpr expr = firstWindowExpr(parseSelect("select sum(a) over (order by c "
                + "rows between unbounded preceding and current row) from t"));
        SqlWindowFrame frame = expr.getFrame();
        assertNotNull(frame);
        assertEquals(SqlWindowFrameType.ROWS, frame.getUnit());
        assertEquals(SqlWindowFrameBoundType.UNBOUNDED_PRECEDING, frame.getStart().getBoundType());
        assertNull(frame.getStart().getOffset());
        assertEquals(SqlWindowFrameBoundType.CURRENT_ROW, frame.getEnd().getBoundType());
    }

    @Test
    public void testFieldAssertionsFrameExprOffset() {
        SqlWindowExpr expr = firstWindowExpr(parseSelect("select sum(a) over (order by c "
                + "range between 5 preceding and 5 following) from t"));
        SqlWindowFrame frame = expr.getFrame();
        assertEquals(SqlWindowFrameType.RANGE, frame.getUnit());
        assertEquals(SqlWindowFrameBoundType.PRECEDING, frame.getStart().getBoundType());
        assertEquals(5, Integer.parseInt(((io.nop.orm.eql.ast.SqlNumberLiteral) frame.getStart().getOffset()).getValue()));
        assertEquals(SqlWindowFrameBoundType.FOLLOWING, frame.getEnd().getBoundType());
    }

    @Test
    public void testFieldAssertionsUnboundedFollowingShortForm() {
        // 单 bound 简写 + unbounded following 归一化
        SqlWindowExpr expr = firstWindowExpr(parseSelect(
                "select sum(a) over (order by c rows unbounded following) from t"));
        SqlWindowFrame frame = expr.getFrame();
        assertEquals(SqlWindowFrameType.ROWS, frame.getUnit());
        assertEquals(SqlWindowFrameBoundType.UNBOUNDED_FOLLOWING, frame.getStart().getBoundType());
        assertNull(frame.getStart().getOffset());
        assertNull(frame.getEnd());
    }

    @Test
    public void testFieldAssertionsNamedWindow() {
        SqlQuerySelect select = parseSelect(
                "select sum(a) over w from t window w as (partition by b order by c rows 1 preceding)");
        SqlWindowExpr expr = firstWindowExpr(select);
        assertEquals("w", expr.getWindowName());
        assertNull(expr.getPartitionBy());

        SqlWindowClause clause = select.getWindowClause();
        assertNotNull(clause);
        assertEquals(1, clause.getItems().size());
        SqlWindowDecl decl = clause.getItems().get(0);
        assertEquals("w", decl.getName());
        assertEquals(1, decl.getPartitionBy().getItems().size());
        assertEquals(1, decl.getOrderBy().getItems().size());
        assertNotNull(decl.getFrame());
        assertEquals(SqlWindowFrameType.ROWS, decl.getFrame().getUnit());
        assertEquals(SqlWindowFrameBoundType.PRECEDING, decl.getFrame().getStart().getBoundType());
        assertEquals("1", ((io.nop.orm.eql.ast.SqlNumberLiteral) decl.getFrame().getStart().getOffset()).getValue());
    }

    @Test
    public void testFieldAssertionsWindowClausePosition() {
        // WINDOW 子句位于 HAVING 之后 ORDER BY 之前：windowClause 与 orderBy 同现且互不干扰
        SqlQuerySelect select = parseSelect("select a, sum(b) from t group by a "
                + "having count(*) > 1 window w as (partition by a) order by a");
        assertNotNull(select.getWindowClause());
        assertNotNull(select.getOrderBy());
        assertNull(select.getWhere());
    }
}
