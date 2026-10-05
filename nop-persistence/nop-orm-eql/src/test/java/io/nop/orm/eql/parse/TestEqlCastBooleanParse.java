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
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlTypeExpr;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI2 BUG-8：`cast(x as boolean)` 解析失败。
 *
 * <p>缺陷（修复前）：`dataTypeName_` 不含 {@code BOOLEAN}，且 BOOLEAN 是独立关键字
 * token（不在 {@code unreservedWord_}，{@code sqlIdentifier_} 备选救不了它）⇒
 * `cast(x as boolean)` / `cast(x as BOOLEAN)` 均解析失败。语义链已就绪：
 * `SqlTypeExpr_name`（toUpperCase + `StdSqlType.fromStdName` 校验）放行 BOOLEAN、
 * `StdSqlType.BOOLEAN` 已存在、生成侧经 `dialect.stdToNativeSqlType` 产方言映射。
 *
 * <p>修复后：文法 `dataTypeName_` 增加 {@code BOOLEAN} 备选，全链打通。编译/生成
 * 层验证见 {@code TestEqlCastBooleanCompile}（h2 正向文本钉死 + 无映射方言响亮报错）。
 */
public class TestEqlCastBooleanParse extends BaseTestCase {

    static SqlTypeExpr parseCastType(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        io.nop.orm.eql.ast.SqlExprProjection proj = (io.nop.orm.eql.ast.SqlExprProjection) select.getProjections()
                .get(0);
        SqlCastExpr cast = (SqlCastExpr) proj.getExpr();
        return cast.getDataType();
    }

    @Test
    public void testLowercaseBooleanParses() {
        // 修复前红：parse-fail（BOOLEAN 是独立关键字 token，不在 dataTypeName_ 备选）
        SqlTypeExpr type = parseCastType("select cast(a.id as boolean) from t a");
        assertEquals("BOOLEAN", type.getName());
    }

    @Test
    public void testUppercaseBooleanParses() {
        SqlTypeExpr type = parseCastType("select cast(a.id as BOOLEAN) from t a");
        assertEquals("BOOLEAN", type.getName());
    }

    @Test
    public void testBooleanEchoNormalizedToUpper() {
        // echo 走 visitSqlTypeExpr：打印归一化后的大写名
        SqlProgram program = new EqlASTParser().parseFromText(null, "select cast(a.id as boolean) from t a");
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertTrue(echo.contains("cast(a.id as BOOLEAN)"), echo);
    }

    @Test
    public void testIllegalTypeStillRejectedLoudly() {
        // 既有拒绝面回归：非法类型名经 sqlIdentifier_ 备选进入 SqlTypeExpr_name 后
        // 仍响亮报 ERR_EQL_INVALID_SQL_TYPE（BOOLEAN 进入 ARG_ALLOWED_NAMES 后不弱化拒绝面）
        try {
            parseCastType("select cast(a.id as nosuchtype) from t a");
            fail("nosuchtype must be rejected");
        } catch (NopException e) {
            assertEquals(ERR_EQL_INVALID_SQL_TYPE.getErrorCode(), e.getErrorCode());
        }
    }
}
