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
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_INTERVAL_UNIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-5：`INTERVAL` 字面量的 PG 字符串形态（`interval '1 day'`）构建期归一化。
 *
 * <p>缺陷（修复前）：语法侧只有 `INTERVAL expr unit` 一种形态，`interval '1 day'`
 * 根本不解析（antlr parse error）；且既有形态不经任何方言模板直接输出（单位规范化为
 * token 文本），PostgreSQL 上非法。
 *
 * <p>修复后（裁定 = WI1 裁决文档 §3/plan Phase 4）：文法新增 `INTERVAL STRING_` 备选
 * 分支；手写 `EqlASTBuildVisitor` 在 interval 构建处把字符串字面量解析为 value+unit
 * 装入**既有** `SqlIntervalExpr`（不新增 AST 节点、不改 `EqlAST.xjava`）。归一化层
 * 拒绝多字段/非法单位，复用 `ERR_EQL_INVALID_INTERVAL_UNIT`；文法层失败（如
 * `'1-2' year to month`）走 antlr 既有错误码。逐方言模板 `sqlIntervalLiteral` 归
 * WI7b（唯一 owner）。
 */
public class TestEqlIntervalLiteralParse extends BaseTestCase {

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

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    @Test
    public void testPgStringFormNormalizedIntoExistingAst() {
        // 修复前红：interval '1 day' 解析失败（antlr parse error）
        SqlProgram program = parse("select o.name from AppUser o where o.createTime > interval '1 day'");
        SqlIntervalExpr expr = findInterval(program);
        assertNotNull(expr, "interval expr must be built");
        assertInstanceOf(SqlNumberLiteral.class, expr.getExpr());
        assertEquals("1", ((SqlNumberLiteral) expr.getExpr()).getValue());
        assertEquals(SqlIntervalUnit.DAY, expr.getIntervalUnit());
        // 归一化后复用既有打印路径（生成器零改动）
        assertTrue(program.toSqlString().replaceAll("\\s+", " ").contains("interval 1 DAY"),
                program.toSqlString());
    }

    @Test
    public void testUnitCaseInsensitive() {
        SqlProgram program = parse("select o.name from AppUser o where o.createTime > interval '3 Hours'");
        SqlIntervalExpr expr = findInterval(program);
        assertNotNull(expr);
        assertEquals("3", ((SqlNumberLiteral) expr.getExpr()).getValue());
        assertEquals(SqlIntervalUnit.HOUR, expr.getIntervalUnit());
    }

    @Test
    public void testMultiFieldValueRejectedInNormalizationLayer() {
        // 归一化层检查同码：SQL 标准多字段值（'1-2'）不支持 → ERR_EQL_INVALID_INTERVAL_UNIT
        NopException e = assertThrows(NopException.class,
                () -> parse("select o.name from AppUser o where o.createTime > interval '1-2'"));
        assertEquals(ERR_EQL_INVALID_INTERVAL_UNIT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnknownUnitRejectedInNormalizationLayer() {
        NopException e = assertThrows(NopException.class,
                () -> parse("select o.name from AppUser o where o.createTime > interval '1 fortnight'"));
        assertEquals(ERR_EQL_INVALID_INTERVAL_UNIT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testGrammarLevelFailureKeepsAntlrErrorCode() {
        // '1-2' year to month：qualifier 无文法支持 → 文法层失败走 antlr 错误码，
        // 不与归一化层的 ERR_EQL_INVALID_INTERVAL_UNIT 混同
        NopException e = assertThrows(NopException.class,
                () -> parse("select o.name from AppUser o where o.createTime > interval '1-2' year to month"));
        assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                "grammar-level failure must keep the antlr error code, got: " + e.getErrorCode());
    }

    @Test
    public void testLegacyFormUnchanged() {
        // 既有形态不破坏：interval 1 day 仍解析、AST 与回显不变（WI0 基线同形）
        SqlProgram program = parse("select o.name from AppUser o where o.createTime > interval 1 day");
        SqlIntervalExpr expr = findInterval(program);
        assertNotNull(expr);
        assertInstanceOf(SqlNumberLiteral.class, expr.getExpr());
        assertEquals("1", ((SqlNumberLiteral) expr.getExpr()).getValue());
        assertEquals(SqlIntervalUnit.DAY, expr.getIntervalUnit());
        assertTrue(program.toSqlString().replaceAll("\\s+", " ").contains("interval 1 DAY"),
                program.toSqlString());
    }
}
