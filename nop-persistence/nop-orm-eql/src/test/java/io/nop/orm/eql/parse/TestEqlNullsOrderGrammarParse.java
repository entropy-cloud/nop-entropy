/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2308 Phase 3 / D8：EQL {@code ORDER BY ... NULLS FIRST/LAST} 的语法入口。
 *
 * <p>Phase 3 只负责"能解析进 AST + EQL 通道 round-trip"，方言相关输出在 Phase 4。
 *
 * <p>覆盖：
 * <ul>
 *   <li>4 个方向组合的大小写变体都能解析，且 {@code nullsFirst} 三态正确（null/TRUE/FALSE）；</li>
 *   <li>{@code nulls} 登记为非保留字后可作标识符；</li>
 *   <li>关键的歧义回归——{@code nulls} 作 ORDER BY 列名（{@code order by nulls asc}）
 *       在新增 {@code NULLS FIRST} 之后仍必须按列名解析，不能被当成空序修饰；</li>
 *   <li>{@code FIRST}/{@code LAST} 早已是 token（{@code SQL92Keyword.g4}）但无规则引用，
 *       单独出现不应被误当作空序修饰。</li>
 * </ul>
 */
public class TestEqlNullsOrderGrammarParse {

    static SqlProgram parse(String eql) {
        return new EqlASTParser().parseFromText(null, eql);
    }

    static SqlQuerySelect parseSelect(String eql) {
        return (SqlQuerySelect) parse(eql).getStatements().get(0);
    }

    /** 取查询级 ORDER BY 的第 index 项（不是窗口内的） */
    static SqlOrderByItem orderItem(String eql, int index) {
        SqlOrderBy orderBy = parseSelect(eql).getOrderBy();
        assertNotNull(orderBy, "ORDER BY 必须存在: " + eql);
        return orderBy.getItems().get(index);
    }

    // ---- 4 个方向组合 × 三态 ----

    @Test
    public void testNullsFirstAsc() {
        SqlOrderByItem item = orderItem("select a from t order by a asc nulls first", 0);
        assertEquals(Boolean.TRUE, item.getNullsFirst());
        assertTrue(item.getAsc());
    }

    @Test
    public void testNullsLastAsc() {
        SqlOrderByItem item = orderItem("select a from t order by a asc nulls last", 0);
        assertEquals(Boolean.FALSE, item.getNullsFirst());
        assertTrue(item.getAsc());
    }

    @Test
    public void testNullsFirstDesc() {
        SqlOrderByItem item = orderItem("select a from t order by a desc nulls first", 0);
        assertEquals(Boolean.TRUE, item.getNullsFirst());
        assertFalse(item.getAsc());
    }

    @Test
    public void testNullsLastDesc() {
        SqlOrderByItem item = orderItem("select a from t order by a desc nulls last", 0);
        assertEquals(Boolean.FALSE, item.getNullsFirst());
        assertFalse(item.getAsc());
    }

    @Test
    public void testNullsOrderIsCaseInsensitive() {
        for (String kw : new String[]{"nulls first", "NULLS FIRST", "Nulls First", "nUlLs FiRsT"}) {
            assertEquals(Boolean.TRUE, orderItem("select a from t order by a asc " + kw, 0).getNullsFirst(),
                    kw + " 必须被识别");
        }
        for (String kw : new String[]{"nulls last", "NULLS LAST", "Nulls Last"}) {
            assertEquals(Boolean.FALSE, orderItem("select a from t order by a asc " + kw, 0).getNullsFirst(),
                    kw + " 必须被识别");
        }
    }

    @Test
    public void testNullsWithoutDirectionDefaultsToAsc() {
        // 只写 nulls last、不写 asc/desc：方向取缺省 asc，nullsFirst=false
        SqlOrderByItem item = orderItem("select a from t order by a nulls last", 0);
        assertEquals(Boolean.FALSE, item.getNullsFirst());
        assertTrue(item.getAsc(), "未写方向时应为缺省 asc");
    }

    @Test
    public void testNoNullsClauseLeavesFieldNull() {
        // 关键三态：未写 NULLS 时必须为 null（三态中的"未指定"），
        // 以便 Phase 4 区分"未请求空序"与"请求了 FIRST/LAST"
        SqlOrderByItem item = orderItem("select a from t order by a asc", 0);
        assertNull(item.getNullsFirst(), "未写 NULLS 时 nullsFirst 必须为 null");
    }

    @Test
    public void testMultipleOrderByItemsEachCarryOwnNulls() {
        SqlOrderBy orderBy = parseSelect(
                "select a from t order by a asc nulls first, b desc nulls last, c").getOrderBy();
        assertEquals(3, orderBy.getItems().size());
        assertEquals(Boolean.TRUE, orderBy.getItems().get(0).getNullsFirst());
        assertEquals(Boolean.FALSE, orderBy.getItems().get(1).getNullsFirst());
        assertNull(orderBy.getItems().get(2).getNullsFirst(), "第三项未写 NULLS");
    }

    // ---- round-trip（EQL 通道，Phase 4 之前基类已能输出） ----

    @Test
    public void testRoundTripKeepsNullsFirst() {
        assertEquals("select a from t order by a asc nulls first",
                parse("select a from t order by a asc nulls first").toSqlString()
                        .replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testRoundTripKeepsNullsLast() {
        assertEquals("select a from t order by a desc nulls last",
                parse("select a from t order by a desc nulls last").toSqlString()
                        .replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testRoundTripMultiItem() {
        assertEquals("select a from t order by a asc nulls first , b desc nulls last",
                parse("select a from t order by a asc nulls first, b desc nulls last").toSqlString()
                        .replaceAll("\\s+", " ").trim());
    }

    // ---- 歧义回归：nulls 作标识符 ----

    @Test
    public void testNullsUsableAsColumnName() {
        SqlProgram program = parse("select nulls from t");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        SqlExprProjection proj = (SqlExprProjection) select.getProjections().get(0);
        assertEquals("nulls", ((SqlColumnName) proj.getExpr()).getName());
    }

    @Test
    public void testNullsUsableAsAlias() {
        SqlQuerySelect select = parseSelect("select a as nulls from t");
        SqlExprProjection proj = (SqlExprProjection) select.getProjections().get(0);
        assertNotNull(proj.getAlias());
        assertEquals("nulls", proj.getAlias().getAlias());
    }

    @Test
    public void testNullsUsableAsOrderByColumnName() {
        // 最关键的歧义位置：order by nulls asc 中，nulls 是列名而非空序修饰
        SqlOrderByItem item = orderItem("select a from t order by nulls asc", 0);
        assertTrue(item.getExpr() instanceof SqlColumnName, "nulls 在此应是列名");
        assertEquals("nulls", ((SqlColumnName) item.getExpr()).getName());
        assertNull(item.getNullsFirst(), "此处不应被解析成空序修饰");
    }

    @Test
    public void testNullsColumnWithNullsClauseOnAnotherItem() {
        // 列名 nulls 与空序修饰同时出现，必须各自归位
        SqlOrderBy orderBy = parseSelect(
                "select a from t order by nulls asc, b desc nulls last").getOrderBy();
        assertEquals("nulls", ((SqlColumnName) orderBy.getItems().get(0).getExpr()).getName());
        assertNull(orderBy.getItems().get(0).getNullsFirst());
        assertEquals("b", ((SqlColumnName) orderBy.getItems().get(1).getExpr()).getName());
        assertEquals(Boolean.FALSE, orderBy.getItems().get(1).getNullsFirst());
    }

    @Test
    public void testNullsUsableAsTableAlias() {
        assertNotNull(parseSelect("select 1 from t nulls").getFrom());
    }

    // ---- FIRST / LAST 是既有 token，但不是空序修饰 ----

    /**
     * {@code first} 作为标识符：<b>既有</b>限制，非本计划引入。
     *
     * <p>{@code FIRST}/{@code LAST} 在本计划之前就已是 {@code SQL92Keyword.g4} 的 token，
     * 但从未登记进 {@code unreservedWord_}，因此<b>本来就</b>不能作裸标识符。
     * 本计划只新增 {@code NULLS} 一个 token 并把它登记为非保留字（D8），
     * 未改变 {@code first}/{@code last} 的可用性。
     */
    @Test
    public void testFirstIsStillNotBareIdentifier_PreExisting() {
        // 记录既有事实：first 是 token、不是非保留字，故 select first from t 解析失败。
        // 断言"仍失败"是为了防止本计划误把它悄悄放开（或误判为回归）。
        org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> parse("select first from t"),
                "first 既来就不是非保留字；本计划不应改变这一既有限制");
    }

    @Test
    public void testQuotedFirstStillUsable() {
        // 需要 first/last 作列名时可用引号（STRING_ 分支），这是既有逃生口
        assertNotNull(parseSelect("select \"first\" from t"));
    }

    // ---- 窗口内也能解析（Phase 4 负责 fail-fast，这里只验语法可达） ----

    @Test
    public void testNullsOrderInsideOverIsParseable() {
        // Phase 4 会在此位置 fail-fast；但语法层必须先能解析，错误码由 Phase 4 断言
        assertNotNull(parse("select row_number() over (order by a nulls last) from t"));
    }

    @Test
    public void testNullsOrderInsideNamedWindowIsParseable() {
        assertNotNull(parse("select row_number() over w from t window w as (order by a nulls last)"));
    }

    @Test
    public void testUnaffectedGrammarStillParses() {
        // 防误伤：分组、having、limit、窗口 frame 等既有形态不受影响
        assertNotNull(parse("select a from t order by a asc"));
        assertNotNull(parse("select a from t group by a order by a desc nulls first"));
        assertNotNull(parse("select a from t group by a having count(*) > 0 order by a nulls last limit 10"));
        // EQL 子句顺序固定为 ... order by -> limit -> offset（sqlQueryRule 定义），
        // 故 limit/offset 在 ORDER BY **之后**，不是之前
        assertNotNull(parse("select a from t order by a nulls last limit 5 offset 2"));
        assertFalse(parse("select a from t order by a nulls first").toSqlString().isEmpty());
    }
}