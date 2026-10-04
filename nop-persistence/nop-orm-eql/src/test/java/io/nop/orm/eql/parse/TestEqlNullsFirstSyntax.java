/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ②）：{@code ORDER BY … NULLS FIRST/LAST} 文法激活——解析赋值
 * {@code SqlOrderByItem.nullsFirst}（Boolean 三态）+ 回显守护。
 *
 * <p>缺陷（修复前红）：{@code sqlOrderByItem} 只有 {@code expr=sqlExpr asc=(ASC|DESC)?}，
 * 两形态均解析失败。修复后门/消费已先于文法落地（WI7 Phase 2 的三分门），文法激活当天即有门。
 * 行为面（方言矩阵/省略/报错）见 {@code TestEqlNullsFirstSyntaxCompile}；程序化 AST 行为
 * 矩阵（Phase 2 无文法时的载体）见 {@code TestEqlNullsFirstDialectGate}。
 */
public class TestEqlNullsFirstSyntax {

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    static SqlOrderByItem firstOrderItem(String sql) {
        return parseSelect(sql).getOrderBy().getItems().get(0);
    }

    // ---- 修复前红组：两方向可解析且赋值 nullsFirst ----

    @Test
    public void testDescNullsLastParsesAndAssigns() {
        SqlOrderByItem item = firstOrderItem("select a.id from t a order by a.id desc nulls last");
        assertFalse(item.getAsc(), "desc 方向");
        assertEquals(Boolean.FALSE, item.getNullsFirst(), "nulls last ⇒ nullsFirst=false");
    }

    @Test
    public void testNullsFirstParsesAndAssigns() {
        SqlOrderByItem item = firstOrderItem("select a.id from t a order by a.id nulls first");
        assertTrue(item.getAsc(), "缺省 asc 方向");
        assertEquals(Boolean.TRUE, item.getNullsFirst(), "nulls first ⇒ nullsFirst=true");
    }

    @Test
    public void testAscNullsLastParsesAndAssigns() {
        SqlOrderByItem item = firstOrderItem("select a.id from t a order by a.id asc nulls last");
        assertTrue(item.getAsc());
        assertEquals(Boolean.FALSE, item.getNullsFirst());
    }

    @Test
    public void testNullsClauseIsOptionalPerItem() {
        SqlQuerySelect select = parseSelect("select a.id from t a order by a.id desc, b nulls first");
        SqlOrderByItem first = select.getOrderBy().getItems().get(0);
        assertNull(first.getNullsFirst(), "首项无 NULLS ⇒ nullsFirst 保持 null（三态）");
        SqlOrderByItem second = select.getOrderBy().getItems().get(1);
        assertEquals(Boolean.TRUE, second.getNullsFirst(), "次项 nulls first ⇒ true");
    }

    // ---- 回显守护（继承打印路径首次被文法激活） ----

    @Test
    public void testNullsFirstEchoed() {
        String out = echo("select a.id from t a order by a.id desc nulls first");
        assertTrue(out.contains("nulls first"), out);
        assertTrue(out.contains("desc"), out);
    }

    @Test
    public void testNullsLastEchoed() {
        String out = echo("select a.id from t a order by a.id nulls last");
        assertTrue(out.contains("nulls last"), out);
    }

    @Test
    public void testNoNullsEchoUnchanged() {
        String out = echo("select a.id from t a order by a.id desc");
        assertFalse(out.contains("nulls"), out);
        assertTrue(out.contains("order by"), out);
    }

    // ---- 标识符位置守护：unreservedWord_ 收编 NULLS 后 nulls 仍可作标识符 ----

    @Test
    public void testNullsAsIdentifierStillParses() {
        SqlQuerySelect select = parseSelect("select a.nulls from t a");
        assertNotNull(select.getProjections(), "nulls 作列名仍可解析");
        String out = echo("select a.nulls from t a");
        assertTrue(out.contains("nulls"), out);
    }

    @Test
    public void testNullsAsAliasStillParses() {
        // 别名位置（sqlAlias_ → sqlIdentifier_ → unreservedWord_）在 NULLS 收编后仍可解析
        String out = echo("select a.id from t nulls");
        assertTrue(out.contains("nulls"), out);
    }
}
