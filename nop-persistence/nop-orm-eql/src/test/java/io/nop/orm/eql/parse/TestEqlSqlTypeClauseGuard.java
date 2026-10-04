/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.parse.antlr.EqlLexer;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI2 BUG-15：`SqlTypeExpr.characterSet`/`collate` 的潜在静默丢弃防护。
 *
 * <p>事实（裁定文档 §六，候选 A=解析期拒绝）：生成物写入点 `_EqlASTBuildVisitor` 调手写
 * `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate` 写字段，但两个生成器都零读取——
 * `AstToEqlGenerator.visitSqlTypeExpr` 只打印 name/precision/scale，
 * `AstToSqlGenerator.visitSqlCastExpr` 绕过 SqlTypeExpr 渲染。字段一旦有值即被静默丢弃。
 *
 * <p>不可达性（今天的现状固化）：`characterSet_` 期望 `STRING` token——parser token 表
 * 有 `STRING=359` 而 lexer token 表止于 `BIT_NUM_=357`（token 分叉，机器可证；WI7 引入 NULLS=353 使 parser 侧隐式 token 顺延 +1），任何输入
 * 都词法不出 STRING ⇒ 分支永久不可匹配；`collateClause_` 期望 `STRING_`（带引号字面量），
 * unquoted 形态（`utf8`/`xx`）词法为 IDENTIFIER_ ⇒ 不可达；**quoted 形态
 * `collate 'xx'`（STRING_ 可词法化）今天可达**——修复前字段被写入后被静默丢弃（活的最小
 * 静默丢弃通路），守卫把它即刻转为响亮拒绝。
 *
 * <p>守卫本体：一旦分支可达（文法被第三方修复 / quoted 形态），手写访问器无条件抛
 * `ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT`——"未来文法修好后不得静默退化成 BUG-1 同型
 * 静默丢弃"。
 */
public class TestEqlSqlTypeClauseGuard extends BaseTestCase {

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    static Token lexerToken(String text) {
        EqlLexer lexer = new EqlLexer(CharStreams.fromString(text));
        return lexer.nextToken();
    }

    @Test
    public void testCharacterSetClauseUnreachable() {
        // 今天的现状固化：STRING token 无 lexer 规则 ⇒ 解析失败（antlr 错误码）
        try {
            echo("select cast(a.id as varchar(10) character set utf8) from t a");
            fail("character set clause must keep failing at parse time");
        } catch (NopException e) {
            assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                    "expected antlr parse error, got: " + e.getErrorCode());
        }
    }

    @Test
    public void testCharacterSetQuotedAlsoUnreachable() {
        // 加引号同样失败：'utf8' 词法为 STRING_ ≠ STRING（token 分叉证据的行为面锁定）
        try {
            echo("select cast(a.id as varchar(10) character set 'utf8') from t a");
            fail("quoted character set clause must keep failing at parse time");
        } catch (NopException e) {
            assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                    "expected antlr parse error, got: " + e.getErrorCode());
        }
    }

    @Test
    public void testCollateUnquotedUnreachable() {
        // unquoted collate：utf8/xx 词法为 IDENTIFIER_ ≠ STRING_ ⇒ 解析失败
        try {
            echo("select cast(a.id as varchar(10) collate xx) from t a");
            fail("unquoted collate clause must keep failing at parse time");
        } catch (NopException e) {
            assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                    "expected antlr parse error, got: " + e.getErrorCode());
        }
    }

    @Test
    public void testQuotedCollateExplicitlyRejectedNotSilentlyDropped() {
        // 修复前红（活的最小静默丢弃通路）：`collate 'xx'` 可解析（STRING_ 可词法化），
        // collate 字段被写入后被两个生成器静默丢弃——echo 不含任何 collate 痕迹。
        // 修复后：解析期显式拒绝 ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT。
        NopException e = assertThrows(NopException.class,
                () -> echo("select cast(a.id as varchar(10) collate 'xx') from t a"));
        assertEquals(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCharacterSetAccessorRejectsWhenReachable() {
        // 守卫本体（候选 A）：手构 CharacterSet_Context（public Token 字段可注入）直调
        // 访问器——一旦文法修复使分支可达，访问器无条件显式拒绝
        EqlASTBuildVisitor visitor = new EqlASTBuildVisitor();
        EqlParser.CharacterSet_Context ctx = new EqlParser.CharacterSet_Context(null, 0);
        ctx.start = lexerToken("utf8");
        ctx.characterSet = lexerToken("'utf8'");
        NopException e = assertThrows(NopException.class, () -> visitor.SqlTypeExpr_characterSet(ctx));
        assertEquals(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCollateAccessorRejectsWhenReachable() {
        EqlASTBuildVisitor visitor = new EqlASTBuildVisitor();
        EqlParser.CollateClause_Context ctx = new EqlParser.CollateClause_Context(null, 0);
        ctx.start = lexerToken("collate");
        ctx.collate = lexerToken("'xx'");
        NopException e = assertThrows(NopException.class, () -> visitor.SqlTypeExpr_collate(ctx));
        assertEquals(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testGuardIsUnconditionalNotValueDependent() {
        // 无静默跳过：拒绝不得依赖字段值（任意非空文本都拒绝）——空串文本同样拒绝
        EqlASTBuildVisitor visitor = new EqlASTBuildVisitor();
        EqlParser.CollateClause_Context ctx = new EqlParser.CollateClause_Context(null, 0);
        ctx.start = lexerToken("collate");
        ctx.collate = lexerToken("''");
        assertThrows(NopException.class, () -> visitor.SqlTypeExpr_collate(ctx));
    }
}
