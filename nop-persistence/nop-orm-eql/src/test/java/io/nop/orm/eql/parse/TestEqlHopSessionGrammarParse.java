/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast.SqlHopTableSource;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlSessionTableSource;
import io.nop.orm.eql.ast.SqlTableSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI26: HOP/SESSION 伪表函数 grammar 解析矩阵——镜像 TestEqlTumbleGrammarParse 先例
 * （成功矩阵 + fail-fast 矩阵；不得用 toSQL 往返断言——W2/T1 stream-only）。
 */
public class TestEqlHopSessionGrammarParse {

    io.nop.orm.eql.ast.SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    private SqlTableSource parseFirstTableSource(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        io.nop.orm.eql.ast.SqlQuerySelect select =
                (io.nop.orm.eql.ast.SqlQuerySelect) program.getStatements().get(0);
        return select.getFrom().getTableSources().get(0);
    }

    private SqlHopTableSource parseHop(String sql) {
        return assertInstanceOf(SqlHopTableSource.class, parseFirstTableSource(sql));
    }

    private SqlSessionTableSource parseSession(String sql) {
        return assertInstanceOf(SqlSessionTableSource.class, parseFirstTableSource(sql));
    }

    // ---------------- success matrix ----------------

    @Test
    public void testHopBasic() {
        SqlHopTableSource hop = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND)");
        assertEquals("orders", hop.getTableName());
        assertEquals("ts", hop.getTimeColumn());
        assertEquals("2", hop.getSlideInterval().getExpr().toSqlString().trim());
        assertEquals(SqlIntervalUnit.SECOND, hop.getSlideInterval().getIntervalUnit());
        assertEquals("4", hop.getSizeInterval().getExpr().toSqlString().trim());
    }

    @Test
    public void testHopLowerCase() {
        parseHop("SELECT item FROM hop(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND)");
    }

    @Test
    public void testHopWithAlias() {
        SqlHopTableSource hop = parseHop(
                "SELECT w.item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND) AS w");
        assertEquals("w", hop.getAlias().getAlias());
    }

    @Test
    public void testHopMixedUnits() {
        SqlHopTableSource hop = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 30 MINUTE, INTERVAL 1 HOUR)");
        assertEquals(SqlIntervalUnit.MINUTE, hop.getSlideInterval().getIntervalUnit());
        assertEquals(SqlIntervalUnit.HOUR, hop.getSizeInterval().getIntervalUnit());
    }

    @Test
    public void testSessionBasic() {
        SqlSessionTableSource session = parseSession(
                "SELECT item FROM SESSION(orders.ts, INTERVAL 5 SECOND)");
        assertEquals("orders", session.getTableName());
        assertEquals("ts", session.getTimeColumn());
        assertEquals("5", session.getGapInterval().getExpr().toSqlString().trim());
        assertEquals(SqlIntervalUnit.SECOND, session.getGapInterval().getIntervalUnit());
    }

    @Test
    public void testSessionLowerCase() {
        parseSession("SELECT item FROM session(orders.ts, INTERVAL 5 SECOND)");
    }

    @Test
    public void testHopRemainsUsableAsIdentifier() {
        // HOP/SESSION 登记为 unreservedWord_——列/表名兼容（TUMBLE 先例：assertNotNull）
        assertNotNull(parse("select hop from t"));
        assertNotNull(parse("select a from hop"));
        assertNotNull(parse("select a from t where hop > 1"));
        assertNotNull(parse("select session from t"));
        assertNotNull(parse("select a from session"));
        // 列名 hop/session 出现在伪表函数的标识符位置同样合法
        parseSession("SELECT item FROM SESSION(events.session, INTERVAL 5 SECOND)");
        parseHop("SELECT hop FROM HOP(events.hop, INTERVAL 2 SECOND, INTERVAL 4 SECOND)");
    }

    // ---------------- 提取层 fail-fast 矩阵（镜像 TUMBLE 先例——parse 合法、提取抛） ----------------

    @Test
    public void testHopDurationExtraction() {
        SqlHopTableSource hop = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND)");
        assertEquals(2000L, EqlParseHelper.intervalDurationMillis(hop.getSlideInterval()));
        assertEquals(4000L, EqlParseHelper.intervalDurationMillis(hop.getSizeInterval()));
        // MICROSECOND 仅整毫秒
        SqlHopTableSource micro = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2500000 MICROSECOND, INTERVAL 4 SECOND)");
        assertEquals(2500L, EqlParseHelper.intervalDurationMillis(micro.getSlideInterval()));
    }

    @Test
    public void testSessionDurationExtraction() {
        SqlSessionTableSource session = parseSession(
                "SELECT item FROM SESSION(orders.ts, INTERVAL 5 SECOND)");
        assertEquals(5000L, EqlParseHelper.intervalDurationMillis(session.getGapInterval()));
    }

    @Test
    public void testHopNonLiteralIntervalFailsFast() {
        // 字符串字面量/列引用 parse 合法、提取 fail-fast（windowing 时长必须编译期常量）
        SqlHopTableSource str = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL '2' SECOND, INTERVAL 4 SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(str.getSlideInterval()))
                        .getErrorCode().toString());
        SqlHopTableSource col = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL n SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(col.getSizeInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testSessionNonLiteralIntervalFailsFast() {
        SqlSessionTableSource str = parseSession(
                "SELECT item FROM SESSION(orders.ts, INTERVAL '5' SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(str.getGapInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testHopCalendarUnitFailsFast() {
        SqlHopTableSource month = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 1 MONTH, INTERVAL 4 SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(month.getSlideInterval()))
                        .getErrorCode().toString());
        SqlHopTableSource year = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 1 YEAR)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(year.getSizeInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testSessionCalendarUnitFailsFast() {
        SqlSessionTableSource quarter = parseSession(
                "SELECT item FROM SESSION(orders.ts, INTERVAL 1 QUARTER)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(quarter.getGapInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testHopNonPositiveIntervalFailsFast() {
        // 0 经提取层正值校验拒绝（-1 经一元减号 parse 合法、提取拒绝）
        SqlHopTableSource zero = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 0 SECOND, INTERVAL 4 SECOND)");
        assertEquals("nop.err.eql.invalid-interval-value",
                assertThrows(NopException.class,
                        () -> EqlParseHelper.intervalDurationMillis(zero.getSlideInterval()))
                        .getErrorCode().toString());
    }

    @Test
    public void testHopSyntaxErrorFailsParse() {
        // 单 interval 的 HOP 是语法错误（slide 与 size 都必填）
        assertThrows(Exception.class, () -> parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND)"));
    }

    @Test
    public void testOrmChannelHopResolveFailsFast() {
        // ORM 编译通道与 TUMBLE 同码 fail-fast——wrapper 的 resolve 访问器
        // （stream-only 契约，EqlTransformVisitor 分支在 ORM 编译路径生效）
        SqlHopTableSource hop = parseHop(
                "SELECT item FROM HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND)");
        assertThrows(NopException.class, hop::getSourceSelect);
        assertThrows(NopException.class, hop::getResolvedTableMeta);
        SqlSessionTableSource session = parseSession(
                "SELECT item FROM SESSION(orders.ts, INTERVAL 5 SECOND)");
        assertThrows(NopException.class, session::getSourceSelect);
        assertThrows(NopException.class, session::getResolvedTableMeta);
    }
}
