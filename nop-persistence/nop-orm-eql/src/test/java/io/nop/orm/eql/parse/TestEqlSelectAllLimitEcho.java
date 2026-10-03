/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlWhere;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 BUG-4：回显丢 `selectAll` 与 `LIMIT`。
 *
 * <p>缺陷（修复前）：`AstToEqlGenerator.printSelect` 手写遍历从不 visit {@code limit}，
 * 且 projections 为空（{@code select *}）时什么都不打印 ⇒ `select * from t` 回显为
 * `select from t`、`limit 10 offset 5` 整体丢失；回显文本再喂回编译器会静默变义。
 *
 * <p>修复后（裁定 = `ai-dev/design/eql-cross-db-compat/2026-10-03-wi2-class-b-adjudications.md`
 * §二，候选 A）：`selectAll && projections.isEmpty()` 时回显 {@code *}；limit 经新钩子
 * `printLimit` 回显（复用 {@code visitSqlLimit}，不删除——{@code _SqlQuerySelect} 遍历链
 * 仍依赖它）。字面量一致化：字符串维持 EQL 规范形（escapeSql 双写 round-trip 无损）；
 * 布尔回显由 {@code 1/0} 改为 {@code true/false}（1/0 重解析为 NUMBER，类型语义分歧）。
 */
public class TestEqlSelectAllLimitEcho {

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    @Test
    public void testSelectAllEchoesStar() {
        // 修复前红：projections 为空时什么都不打印，回显 `select from t`
        String out = echo("select * from t");
        assertTrue(out.contains("select * from t"), out);
    }

    @Test
    public void testSelectDistinctStarEchoes() {
        // 修复前红：回显 `select distinct from t`
        String out = echo("select distinct * from t");
        assertTrue(out.contains("select distinct * from t"), out);
    }

    @Test
    public void testLimitOffsetEchoed() {
        // 修复前红：limit/offset 整体丢失
        String out = echo("select a.id from t a order by a.id limit 10 offset 5");
        assertTrue(out.contains("limit 10"), out);
        assertTrue(out.contains("offset 5"), out);
    }

    @Test
    public void testLimitOnlyEchoed() {
        String out = echo("select a.id from t a limit 3");
        assertTrue(out.contains("limit 3"), out);
        assertFalse(out.contains("offset"), out);
    }

    @Test
    public void testEchoRoundTripsSelectAllAndLimit() {
        // 本缺陷的复刻形态：回显文本再喂回解析器，selectAll/limit/offset 语义不丢
        String eql = "select * from t limit 10 offset 5";
        String echoed = echo(eql);

        SqlQuerySelect roundTripped = parseSelect(echoed);
        assertTrue(roundTripped.getSelectAll(), "selectAll must survive echo round-trip: " + echoed);
        assertNotNull(roundTripped.getLimit(), "limit must survive echo round-trip: " + echoed);
        SqlLimit limit = roundTripped.getLimit();
        assertInstanceOf(SqlNumberLiteral.class, limit.getLimit());
        assertEquals("10", ((SqlNumberLiteral) limit.getLimit()).getValue());
        assertInstanceOf(SqlNumberLiteral.class, limit.getOffset());
        assertEquals("5", ((SqlNumberLiteral) limit.getOffset()).getValue());
    }

    @Test
    public void testBooleanLiteralEchoIsEqlKeywordForm() {
        // 修复前红：回显 ` 1 ` / ` 0 `——重解析为 NUMBER 字面量，boolean→integer 类型分歧
        String out = echo("select a.id from t a where a.flag = true");
        assertTrue(out.contains("true"), out);
        assertFalse(out.matches(".*\\b1\\b.*"), out);

        SqlQuerySelect select = parseSelect("select a.id from t a where a.flag = true");
        SqlBooleanLiteral lit = (SqlBooleanLiteral) ((SqlBinaryExpr) ((SqlWhere) select.getWhere()).getExpr())
                .getRight();
        assertTrue(lit.getValue());

        String falseEcho = echo("select a.id from t a where a.flag = false");
        assertTrue(falseEcho.contains("false"), falseEcho);
    }

    @Test
    public void testStringLiteralQuoteEscapeMatchesExecutionForm() {
        // 字面量一致化（字符串侧，裁定=候选 A 维持 escapeSql(value,false) 不变）：
        // 值 a'b（源码 'a\'b'，EQL 唯一可产生该值的输入形态）的回显为 '' 双写形——
        // 与执行路径 escapeSlash=false 方言的 dialect.getStringLiteral 渲染逐字一致
        // （'a''b'，标准 SQL 形）。
        // 已知既有缺口（不在 BUG-4 scope，登记 Non-Blocking Follow-up）：EQL parse 层
        // stringLiteralValue 只 unescape 反斜杠形态、不 un-double ''（lexer Literals.g4
        // 的 STRING_ 接受 '' 但值层不还原），故回显文本重解析后的值含字面 ''——
        // 这是 parse 语义缺口而非回显缺陷，本测试锁定回显文本形态不变。
        String out = echo("select a.id from t a where a.name = 'a\\'b'");
        assertTrue(out.contains("'a''b'"), out);

        // 简单字符串（无内嵌引号，WI0 语料唯一存在的形态）round-trip 值还原
        SqlQuerySelect select = parseSelect("select a.id from t a where a.name = 'Biology'");
        assertEquals("Biology", ((SqlStringLiteral) ((SqlBinaryExpr) ((SqlWhere) select.getWhere()).getExpr())
                .getRight()).getValue());
    }
}
