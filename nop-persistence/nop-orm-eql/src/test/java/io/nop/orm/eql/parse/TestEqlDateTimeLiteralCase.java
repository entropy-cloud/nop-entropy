/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.enums.SqlDateTimeType;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import io.nop.orm.eql.parse.antlr.EqlLexer;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_DATETIME_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI2 BUG-7：小写 `date '…'` 抛 ClassCastException 掩盖真实错误码。
 *
 * <p>缺陷（修复前）：词法层关键字大小写不敏感（Alphabet.g4 fragment [Aa]），小写
 * `date` 正常词法化为 DATE token；但 `SqlDateTimeLiteral_type` 里
 * `SqlDateTimeType.fromText` 是大小写敏感精确匹配，小写返回 null 进错误分支后
 * `loc((ParserRuleContext) node)` 对 Token 参数强转 ⇒ 先抛 CCE，掩盖
 * `ERR_EQL_INVALID_DATETIME_TYPE`。
 *
 * <p>修复后：token 文本先 {@code toUpperCase(Locale.ROOT)} 再 fromText；错误分支用
 * {@code ParseTreeHelper.loc(Token)} 重载（类型正确），非法类型名响亮报
 * `ERR_EQL_INVALID_DATETIME_TYPE`。
 */
public class TestEqlDateTimeLiteralCase extends BaseTestCase {

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    static Token lexerToken(String text) {
        // loc(Token) 读取 token.getInputStream()，须用真实 lexer 产物（合成 token 无流）
        EqlLexer lexer = new EqlLexer(CharStreams.fromString(text));
        return lexer.nextToken();
    }

    static SqlDateTimeLiteral parseWhereLiteral(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        return (SqlDateTimeLiteral) ((SqlBinaryExpr) ((SqlWhere) select.getWhere()).getExpr()).getRight();
    }

    @Test
    public void testLowercaseDateParsesWithEnumAndEcho() {
        // 修复前红：ClassCastException。修复后双断言钉死：枚举值 + 回显文本
        SqlDateTimeLiteral lit = parseWhereLiteral("select a.id from t a where a.dt = date '2020-01-01'");
        assertEquals(SqlDateTimeType.DATE, lit.getType());
        assertEquals("2020-01-01", lit.getValue());
        String out = echo("select a.id from t a where a.dt = date '2020-01-01'");
        assertTrue(out.contains("DATE '2020-01-01'"), out);
    }

    @Test
    public void testLowercaseTimeParses() {
        SqlDateTimeLiteral lit = parseWhereLiteral("select a.id from t a where a.tm = time '10:20:30'");
        assertEquals(SqlDateTimeType.TIME, lit.getType());
        String out = echo("select a.id from t a where a.tm = time '10:20:30'");
        assertTrue(out.contains("TIME '10:20:30'"), out);
    }

    @Test
    public void testLowercaseTimestampParses() {
        SqlDateTimeLiteral lit = parseWhereLiteral(
                "select a.id from t a where a.ts = timestamp '2020-01-01 10:20:30'");
        assertEquals(SqlDateTimeType.TIMESTAMP, lit.getType());
        String out = echo("select a.id from t a where a.ts = timestamp '2020-01-01 10:20:30'");
        assertTrue(out.contains("TIMESTAMP '2020-01-01 10:20:30'"), out);
    }

    @Test
    public void testMixedCaseDateParses() {
        // 词法层本就大小写不敏感（Alphabet.g4 fragment [Aa]）：混合大小写与枚举同值
        SqlDateTimeLiteral lit = parseWhereLiteral("select a.id from t a where a.dt = Date '2020-01-01'");
        assertEquals(SqlDateTimeType.DATE, lit.getType());
    }

    @Test
    public void testUppercaseUnchanged() {
        // 大写形态回归不变
        SqlDateTimeLiteral lit = parseWhereLiteral("select a.id from t a where a.dt = DATE '2020-01-01'");
        assertEquals(SqlDateTimeType.DATE, lit.getType());
        String out = echo("select a.id from t a where a.dt = DATE '2020-01-01'");
        assertTrue(out.contains("DATE '2020-01-01'"), out);
    }

    @Test
    public void testIllegalTypeNameThrowsSpecificErrorCode() {
        // 修复前红：CCE 掩盖 ERR_EQL_INVALID_DATETIME_TYPE。直接以非法类型名 token 调
        // 访问器（文法层 type=(DATE|TIME|TIMESTAMP) 限定下无解析期输入可到达该分支），
        // 断言响亮抛具体错误码 + ARG_VALUE param，不再被 CCE 掩盖。
        EqlASTBuildVisitor visitor = new EqlASTBuildVisitor();
        Token badToken = lexerToken("dat");
        NopEvalException e = assertThrows(NopEvalException.class,
                () -> visitor.SqlDateTimeLiteral_type(badToken));
        assertEquals(ERR_EQL_INVALID_DATETIME_TYPE.getErrorCode(), e.getErrorCode());
        assertEquals("dat", e.getParam("value"));
    }

    @Test
    public void testLowercaseIllegalTypeAlsoSpecificError() {
        // 小写非法类型名同样响亮（修复前 CCE）
        EqlASTBuildVisitor visitor = new EqlASTBuildVisitor();
        Token badToken = lexerToken("dat");
        try {
            visitor.SqlDateTimeLiteral_type(badToken);
            fail("must throw");
        } catch (NopException e) {
            assertEquals(ERR_EQL_INVALID_DATETIME_TYPE.getErrorCode(), e.getErrorCode());
        }
    }
}
