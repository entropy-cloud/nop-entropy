package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 5：放开 {@code day/hour/minute/second/quarter} 关键字遮蔽（五词加入
 * unreservedWord_，函数名位置与标识符位置可解析）。
 *
 * <p>修复前：五词是 Keyword.g4 保留 token 且不在 unreservedWord_/sqlIdentifier_func_，
 * {@code day(x)}/{@code o.day} 解析层即失败（遮蔽不一致：month()/year() 能解析而
 * day() 不能）。修复后可解析——函数位置产出 {@link SqlRegularFunction}（name 小写）；
 * 标识符位置（列名/别名）照常。**可用性判据到 parse/echo 层为止**；transform 层在
 * 未注册方言断言具体错误码（响亮行为锁定），归一化日期函数族的注册 owner 是 WI6
 * （EXTRACT 字段函数已随 Phase 3 注册的方言上则正常 lowering）。
 */
public class TestEqlKeywordShadowReleaseParse extends BaseTestCase {

    static SqlRegularFunction findRegularFunction(EqlASTNode node) {
        if (node instanceof SqlRegularFunction)
            return (SqlRegularFunction) node;
        SqlRegularFunction[] found = new SqlRegularFunction[1];
        node.forEachChild(child -> {
            if (found[0] == null)
                found[0] = findRegularFunction(child);
        });
        return found[0];
    }

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    /**
     * 修复前红：五个词在函数名位置可解析，产出 SqlRegularFunction 且 name 小写。
     */
    @Test
    public void testFiveWordsParseAsFunctionNames() {
        for (String word : new String[]{"day", "hour", "minute", "second", "quarter"}) {
            SqlProgram program = parse("select o.id from MyEntity o where " + word + "(o.name) = 1");
            SqlRegularFunction fn = findRegularFunction(program);
            assertNotNull(fn, word + "(x) must parse after shadow release");
            assertEquals(word, fn.getName(), "function name must be lowercased");
            assertEquals(1, fn.getArgs().size());
        }
    }

    /**
     * 修复前红：标识符位置（列名）可解析——select o.day from ... 的回显照常。
     */
    @Test
    public void testIdentifierPositionParseAndEcho() {
        SqlProgram program = parse("select o.day from MyEntity o");
        assertNotNull(program);
        String echo = program.toSqlString().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(echo.contains("o.day"), () -> "column named day must echo unchanged, got: " + echo);
    }

    /**
     * month()/year() 行为不变：解析与回显与放开前一致（守护）。
     */
    @Test
    public void testMonthYearBehaviorUnchanged() {
        for (String word : new String[]{"month", "year"}) {
            SqlProgram program = parse("select o.id from MyEntity o where " + word + "(o.name) = 1");
            SqlRegularFunction fn = findRegularFunction(program);
            assertNotNull(fn);
            assertEquals(word, fn.getName());
        }
    }

    /**
     * interval 共存回归：interval 1 day / interval '3 hours'（WI1 形态）解析与输出
     * 逐字不变——DAY/HOUR 在 intervalUnit_ 与 unreservedWord_ 双重身份下 ANTLR 预测
     * 无歧义（interval 上下文只有 intervalUnit_ 可达）。
     */
    @Test
    public void testIntervalFormsUnchanged() {
        SqlProgram program = parse("select o.name from AppUser o where o.createTime > interval 1 day");
        SqlIntervalExpr expr = findInterval(program);
        assertNotNull(expr);
        assertEquals(SqlIntervalUnit.DAY, expr.getIntervalUnit());
        assertTrue(program.toSqlString().replaceAll("\\s+", " ").contains("interval 1 DAY"),
                program.toSqlString());

        SqlProgram program2 = parse("select o.name from AppUser o where o.createTime > interval '3 hours'");
        SqlIntervalExpr expr2 = findInterval(program2);
        assertNotNull(expr2);
        assertEquals(SqlIntervalUnit.HOUR, expr2.getIntervalUnit());
    }

    /**
     * 遮蔽放开引入的响亮失败形态登记：无参 day() 照常解析（args 在 sqlRegularFunction
     * 为可选，与所有函数一致），响亮失败发生在 transform 层（元数校验/函数表解析）——
     * 由 compile 层 TestEqlKeywordShadowDialect 锁定，parse 层不断言其失败。
     */
    @Test
    public void testZeroArgDayParsesLikeAnyFunction() {
        SqlProgram program = parse("select o.id from MyEntity o where day() = 1");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("day", fn.getName());
        assertEquals(0, fn.getArgs().size());
    }

    static SqlIntervalExpr findInterval(EqlASTNode node) {
        if (node instanceof SqlIntervalExpr)
            return (SqlIntervalExpr) node;
        SqlIntervalExpr[] found = new SqlIntervalExpr[1];
        node.forEachChild(child -> {
            if (found[0] == null)
                found[0] = findInterval(child);
        });
        return found[0];
    }
}
