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
import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlTumbleTableSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17: TUMBLE(t, INTERVAL) 流时间切片伪表函数 parse 矩阵（D4 裁定的语法面落地）。
 * 仅断言 parse 成败与 AST 字段（表名/时间列/interval/别名）；
 * 不得使用 toSQL() 往返做断言——AstToEqlGenerator/AstToSqlGenerator 不认识
 * SqlTumbleTableSource，round-trip 不对称在案（WI17 Baseline），SQL 通道补齐归 WI20。
 */
public class TestEqlTumbleGrammarParse extends BaseTestCase {

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    SqlTumbleTableSource parseTumble(String sql) {
        SqlProgram program = parse(sql);
        assertNotNull(program, "parse must succeed: " + sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        assertTrue(select.getFrom().getTableSources().get(0) instanceof SqlTumbleTableSource,
                "first table source must be the TUMBLE pseudo-table function");
        return (SqlTumbleTableSource) select.getFrom().getTableSources().get(0);
    }

    // ---- success: parse 矩阵 ----

    @Test
    public void testTumbleBasic() {
        assertNotNull(parse("select a from TUMBLE(orders.ts, INTERVAL 5 SECOND)"));
    }

    @Test
    public void testTumbleLowerCase() {
        // 关键字大小写不敏感（Alphabet.g4 fragment 均为双大小写）
        assertNotNull(parse("select a from tumble(orders.ts, interval 5 second)"));
    }

    @Test
    public void testTumbleWithAlias() {
        SqlTumbleTableSource src = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL 5 SECOND) AS o");
        assertEquals("o", src.getAlias().getAlias());
    }

    @Test
    public void testTumbleAllFixedUnits() {
        for (String unit : new String[]{"SECOND", "MINUTE", "HOUR", "DAY", "WEEK"}) {
            assertNotNull(parse("select a from TUMBLE(orders.ts, INTERVAL 5 " + unit + ")"),
                    "unit " + unit + " must parse");
        }
    }

    @Test
    public void testTumbleInsideStreamQuery() {
        // 真实流查询形态：TUMBLE 表源 + WHERE + GROUP BY（WI17 编译器的纳入面查询）
        assertNotNull(parse("select item, sum(amount) from TUMBLE(orders.ts, INTERVAL 5 SECOND)"
                + " where amount > 0 group by item"));
    }

    @Test
    public void testTumbleAsJoinLeft() {
        // sqlTableSource 的 join 分支递归引用左源——TUMBLE 可作为 join 左侧
        assertNotNull(parse("select a from TUMBLE(o.ts, INTERVAL 5 SECOND)"
                + " inner join u on o.id = u.id"));
    }

    // ---- AST 字段断言 ----

    @Test
    public void testTumbleAstFields() {
        SqlTumbleTableSource src = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL 5 SECOND)");
        assertEquals("orders", src.getTableName());
        assertEquals("ts", src.getTimeColumn());
        SqlIntervalExpr interval = src.getInterval();
        assertNotNull(interval);
        assertTrue(interval.getExpr() instanceof SqlNumberLiteral,
                "interval value must be a number literal");
        assertEquals("5", ((SqlNumberLiteral) interval.getExpr()).getValue());
        assertEquals(io.nop.orm.eql.enums.SqlIntervalUnit.SECOND, interval.getIntervalUnit());
    }

    @Test
    public void testTumbleDurationExtraction() {
        assertEquals(5000L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 5 SECOND)").getInterval()));
        assertEquals(60000L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 1 MINUTE)").getInterval()));
        assertEquals(3600000L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 1 HOUR)").getInterval()));
        assertEquals(86400000L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 1 DAY)").getInterval()));
        assertEquals(604800000L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 1 WEEK)").getInterval()));
        // MICROSECOND 仅在整毫秒时接受
        assertEquals(5L, EqlParseHelper.intervalDurationMillis(
                parseTumble("select a from TUMBLE(orders.ts, INTERVAL 5000 MICROSECOND)").getInterval()));
    }

    // ---- INTERVAL→duration 提取 fail-fast（非正时长 / 非字面量 / 日历单位） ----

    @Test
    public void testNonPositiveIntervalFailsFast() {
        SqlTumbleTableSource zero = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL 0 SECOND)");
        NopException e0 = assertThrows(NopException.class,
                () -> EqlParseHelper.intervalDurationMillis(zero.getInterval()));
        assertEquals("nop.err.eql.invalid-interval-value", e0.getErrorCode().toString());

        // -1 经一元减号解析为 SqlUnaryExpr——非正整数字面量，parse 通过、提取 fail-fast
        SqlTumbleTableSource neg = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL -1 SECOND)");
        NopException e1 = assertThrows(NopException.class,
                () -> EqlParseHelper.intervalDurationMillis(neg.getInterval()));
        assertEquals("nop.err.eql.invalid-interval-value", e1.getErrorCode().toString());
    }

    @Test
    public void testNonLiteralIntervalFailsFast() {
        // 字符串字面量 '5' 非数字字面量
        SqlTumbleTableSource str = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL '5' SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(str.getInterval()))
                        .getErrorCode().toString());

        // 列引用非字面量（parse 合法——windowing 时长必须是编译期常量）
        SqlTumbleTableSource col = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL n SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(col.getInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testCalendarUnitIntervalFailsFast() {
        // MONTH/QUARTER/YEAR 无固定毫秒时长——流窗口时长不允许日历依赖
        for (String unit : new String[]{"MONTH", "QUARTER", "YEAR"}) {
            SqlTumbleTableSource src = parseTumble(
                    "select a from TUMBLE(orders.ts, INTERVAL 1 " + unit + ")");
            assertEquals("nop.err.eql.invalid-interval-value",
                    assertThrows(NopException.class,
                            () -> EqlParseHelper.intervalDurationMillis(src.getInterval()))
                            .getErrorCode().toString(),
                    "unit " + unit + " must fail fast");
        }
    }

    @Test
    public void testSubMillisecondMicrosecondFailsFast() {
        SqlTumbleTableSource src = parseTumble(
                "select a from TUMBLE(orders.ts, INTERVAL 999 MICROSECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(src.getInterval()))
                        .getErrorCode().toString());
    }

    // ---- 语法错误 ----

    @Test
    public void testTumbleSyntaxErrorFailsParse() {
        assertThrows(NopException.class, () -> parse("select a from TUMBLE(orders)"));
        assertThrows(NopException.class, () -> parse("select a from TUMBLE(orders.ts)"));
        assertThrows(NopException.class,
                () -> parse("select a from TUMBLE(orders.ts, INTERVAL 5)"));
    }

    // ---- 标识符兼容回归：TUMBLE 登记为 unreservedWord_ ----

    @Test
    public void testTumbleRemainsUsableAsIdentifier() {
        // 列名 / 表名可以用 tumble（WI1 对 RANGE/WINDOW 等七关键字的同款兼容义务）
        assertNotNull(parse("select tumble from t"));
        assertNotNull(parse("select a from tumble"));
        assertNotNull(parse("select a from t where tumble > 1"));
    }
}
