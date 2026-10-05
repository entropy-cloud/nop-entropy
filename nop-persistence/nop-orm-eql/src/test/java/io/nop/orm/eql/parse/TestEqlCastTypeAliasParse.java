/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlTypeExpr;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ALLOWED_NAMES;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI9a：CAST 类型别名解析层测试（`TEXT`/`NUMBER`/`BOOL` 别名 + `UUID` 枚举名）。
 *
 * <p>裁定依据：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md`
 * （NUMBER→DECIMAL 为 2026-10-04 G-3 增补裁决；UUID 为新枚举常量）。别名仅扩展
 * `StdSqlType.stdNameMap` 解析接受集，不修改任何既有映射条目。四个新拼写均无 lexer
 * 关键字规则，词法化为 `IDENTIFIER_` 经 `sqlIdentifier_` 兜底进入手写闸门
 * `SqlTypeExpr_name`（`StdSqlType.fromStdName` 判空拒绝）——别名入表后自动放行。
 *
 * <p>修复前红：以上拼写全部在闸门 A 报 {@code ERR_EQL_INVALID_SQL_TYPE}；
 * 修复后同输入解析通过。编译/生成层验证见 {@code TestEqlCastTypeAliasCompile} 与
 * {@code TestEqlCastUuidDialectCompile}。
 */
public class TestEqlCastTypeAliasParse extends BaseTestCase {

    static SqlTypeExpr parseCastType(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        io.nop.orm.eql.ast.SqlExprProjection proj = (io.nop.orm.eql.ast.SqlExprProjection) select.getProjections()
                .get(0);
        SqlCastExpr cast = (SqlCastExpr) proj.getExpr();
        return cast.getDataType();
    }

    @SuppressWarnings("unchecked")
    static Set<String> allowedNamesOf(NopException e) {
        return (Set<String>) e.getParam(ARG_ALLOWED_NAMES);
    }

    @Test
    public void testLowercaseAliasParses() {
        // SqlTypeExpr.name 存归一化后的大写输入（别名拼写本身）；别名→目标枚举的语义解析
        // （TEXT→CLOB 等）在 compile 层验证（TestEqlCastTypeAliasCompile）
        assertEquals("TEXT", parseCastType("select cast(a.id as text) from t a").getName());
        assertEquals("NUMBER", parseCastType("select cast(a.id as number) from t a").getName());
        assertEquals("BOOL", parseCastType("select cast(a.id as bool) from t a").getName());
        assertEquals("UUID", parseCastType("select cast(a.id as uuid) from t a").getName());
    }

    @Test
    public void testUppercaseAliasParses() {
        assertEquals("TEXT", parseCastType("select cast(a.id as TEXT) from t a").getName());
        assertEquals("NUMBER", parseCastType("select cast(a.id as NUMBER) from t a").getName());
        assertEquals("BOOL", parseCastType("select cast(a.id as BOOL) from t a").getName());
        assertEquals("UUID", parseCastType("select cast(a.id as UUID) from t a").getName());
    }

    @Test
    public void testAliasEchoNormalizedToUpper() {
        // echo 打印归一化后的大写别名拼写（SqlTypeExpr.name 存大写输入，不替换为目标枚举名）
        SqlProgram program = new EqlASTParser().parseFromText(null, "select cast(a.id as text) from t a");
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertTrue(echo.contains("cast(a.id as TEXT)"), echo);
    }

    @Test
    public void testIllegalTypeStillRejectedAndAllowedNamesContainNewKeys() {
        // WI2 底线回归：未知拼写仍响亮拒绝，且 ARG_ALLOWED_NAMES 键集随别名/UUID 变大（预期行为）
        try {
            parseCastType("select cast(a.id as nosuchtype) from t a");
            fail("nosuchtype must be rejected");
        } catch (NopException e) {
            assertEquals(ERR_EQL_INVALID_SQL_TYPE.getErrorCode(), e.getErrorCode());
            Set<String> allowed = allowedNamesOf(e);
            assertTrue(allowed.containsAll(Arrays.asList("TEXT", "NUMBER", "BOOL", "UUID")),
                    "allowed names must contain new alias/uuid keys: " + allowed);
        }
    }
}
