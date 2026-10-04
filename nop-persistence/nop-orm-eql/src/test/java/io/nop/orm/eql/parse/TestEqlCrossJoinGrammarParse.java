/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.enums.SqlJoinType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 3 / V2：{@code CROSS JOIN} 不得被静默误解析为表别名。
 *
 * <p>缺陷机制：{@code CROSS} 登记在 {@code unreservedWord_}（允许 {@code cross} 作标识符），
 * {@code sqlSingleTableSource} 的 alias 可选。对 {@code from a cross join b}，
 * 「alias=cross + 裸 JOIN」与「CROSS JOIN」两条路径都能完整解析，ANTLR 的 ALL(*)
 * 预测取最小备选号，于是先吃 alias，得到
 * {@code SqlJoinTableSource(JOIN, Single(a, alias=cross), b)}。
 *
 * <p>危害：{@code a} 的别名被改成 {@code cross}，源查询里对 {@code a} 的任何引用
 * （如 {@code where a.id = b.id}）都会报 {@code unknown-column-name}。
 * 修复前基线：{@code select 1 from a cross join b} → {@code select 1 from a as cross join b}。
 *
 * <p>修复见 {@code SqlJoinTableSource#normalize()} 与 {@code SqlJoinType.CROSS_JOIN}。
 */
public class TestEqlCrossJoinGrammarParse {

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        assertNotNull(program, "parse must succeed: " + sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    static SqlTableSource firstSource(String sql) {
        return parseSelect(sql).getFrom().getTableSources().get(0);
    }

    static String aliasOf(SqlTableSource ts) {
        if (!(ts instanceof SqlSingleTableSource))
            return null;
        SqlAlias alias = ((SqlSingleTableSource) ts).getAlias();
        return alias == null ? null : alias.getAlias();
    }

    // ---- 核心：cross join 必须成为 CROSS_JOIN，别名不得被污染 ----

    @Test
    public void testCrossJoinProducesCrossJoinType() {
        SqlTableSource src = firstSource("select 1 from a cross join b");
        assertTrue(src instanceof SqlJoinTableSource, "应解析为 join 表源");
        assertEquals(SqlJoinType.CROSS_JOIN, ((SqlJoinTableSource) src).getJoinType());
    }

    @Test
    public void testCrossJoinLeavesNoAliasOnLeft() {
        SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a cross join b");
        assertNull(aliasOf(join.getLeft()), "cross 不得残留为左表别名");
        assertNull(aliasOf(join.getRight()), "cross 不得残留为右表别名");
    }

    @Test
    public void testCrossJoinIsCaseInsensitive() {
        for (String kw : new String[]{"cross join", "CROSS JOIN", "Cross Join", "cRoSs JoIn"}) {
            SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a " + kw + " b");
            assertEquals(SqlJoinType.CROSS_JOIN, join.getJoinType(), kw + " 必须识别为 CROSS JOIN");
            assertNull(aliasOf(join.getLeft()), kw + " 不得残留别名");
        }
    }

    @Test
    public void testCrossJoinPrintedForm() {
        SqlProgram program = new EqlASTParser().parseFromText(null, "select 1 from a cross join b");
        assertEquals("select 1 from a cross join b", program.toSqlString().replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testCrossJoinAfterComma() {
        SqlQuerySelect select = parseSelect("select 1 from a, b cross join c");
        assertEquals(2, select.getFrom().getTableSources().size());
        SqlJoinTableSource join = (SqlJoinTableSource) select.getFrom().getTableSources().get(1);
        assertEquals(SqlJoinType.CROSS_JOIN, join.getJoinType());
        assertNull(aliasOf(join.getLeft()));
    }

    @Test
    public void testChainedCrossJoinLeavesNoPhantomAlias() {
        // 链式写法：第二个 cross 曾被吃成 b 的别名，输出过 `a cross join b as cross join c`
        SqlJoinTableSource outer = (SqlJoinTableSource) firstSource("select 1 from a cross join b cross join c");
        SqlJoinTableSource inner = (SqlJoinTableSource) outer.getLeft();
        assertEquals(SqlJoinType.CROSS_JOIN, inner.getJoinType());
        assertNull(aliasOf(inner.getLeft()), "内层左表不得有别名");
        assertNull(aliasOf(inner.getRight()), "内层右表不得残留 as cross");
    }

    @Test
    public void testCrossJoinFollowedByInnerJoinKeepsOnCondition() {
        SqlJoinTableSource outer = (SqlJoinTableSource) firstSource("select 1 from a cross join b join c on a.id = c.id");
        assertEquals(SqlJoinType.CROSS_JOIN, ((SqlJoinTableSource) outer.getLeft()).getJoinType());
        assertNotNull(outer.getCondition(), "带 on 的普通 join 条件必须保留");
    }

    // ---- 防误伤：cross 仍可作标识符 ----

    @Test
    public void testCrossStillUsableAsColumnName() {
        SqlProgram program = new EqlASTParser().parseFromText(null, "select cross from t");
        assertEquals("select cross from t", program.toSqlString().replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testCrossStillUsableAsTableName() {
        assertNotNull(firstSource("select 1 from cross"));
    }

    @Test
    public void testCrossStillUsableAsTableAlias() {
        // 无 join 时 cross 必须仍然被当作别名——这是「cross 登记为非保留字」的既定设计
        SqlTableSource src = firstSource("select 1 from a cross");
        assertTrue(src instanceof SqlSingleTableSource);
        assertEquals("cross", aliasOf(src));
    }

    @Test
    public void testCrossJoinWithOnIsNotTreatedAsCrossKeyword() {
        // `cross join b on cond`：cross 是别名、join 是带 on 的普通 join —— 判据要求不被改写
        SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a cross join b on a.id = b.id");
        assertEquals(SqlJoinType.JOIN, join.getJoinType());
        assertEquals("cross", aliasOf(join.getLeft()), "带 on 时 cross 应保留为别名");
        assertNotNull(join.getCondition());
    }

    @Test
    public void testPlainInnerJoinUnaffected() {
        SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a join b");
        assertEquals(SqlJoinType.JOIN, join.getJoinType());
        assertNull(aliasOf(join.getLeft()));
    }

    @Test
    public void testExplicitInnerJoinUnaffected() {
        SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a inner join b on a.id = b.id");
        assertEquals(SqlJoinType.JOIN, join.getJoinType());
        assertNotNull(join.getCondition());
    }

    @Test
    public void testLeftJoinUnaffected() {
        SqlJoinTableSource join = (SqlJoinTableSource) firstSource("select 1 from a left join b on a.id = b.id");
        assertEquals(SqlJoinType.LEFT_JOIN, join.getJoinType());
        assertNull(aliasOf(join.getLeft()));
    }

    @Test
    public void testAsPrefixedAliasStillWorks() {
        // AS 显式别名路径不受 normalize 影响
        SqlTableSource src = firstSource("select 1 from a as foo");
        assertEquals("foo", aliasOf(src));
        assertFalse(src instanceof SqlJoinTableSource);
    }
}