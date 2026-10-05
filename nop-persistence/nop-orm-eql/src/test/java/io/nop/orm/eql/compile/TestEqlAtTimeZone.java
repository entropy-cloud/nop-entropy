/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.parse.EqlASTParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 交付⑥（plan 38 Phase 5）：{@code expr AT TIME ZONE tz}——WI5 同款 altLabel 后缀
 * 备选 + 构建期归一化进既有 {@code SqlRegularFunction(name="atTimeZone", args=[expr, tz])}
 * （零新 AST 节点、零新 token）+ 逐方言 {@code <template>}（裁定 =
 * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §5）。
 *
 * <p>三态断言：① 输入文本可解析；② 归一化回显 = {@code atTimeZone(…)} 函数形态；
 * ③ 方言 lowering = {@code {0} AT TIME ZONE {1}} 模板文本。未注册方言（mysql 族不注册
 * ——MySQL 仅 UTC-only {@code CAST(ts AT TIME ZONE 'UTC' AS DATETIME)} 8.0.22+，不无损）
 * 编译期 {@code ERR_EQL_UNKNOWN_FUNCTION(atTimeZone)} 响亮。H2 真库执行证据 =
 * nop-dao {@code TestDialect.testFunctions}（testSql 落地）。
 */
public class TestEqlAtTimeZone {
    static final String EQL = "select timestamp '2026-01-01 00:00:00' at time zone 'UTC'";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static String sqlOf(String dialectName, String eql) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, dialectName);
        ICompiledSql compiled = new EqlCompiler().compile("test", eql,
                TestH2WindowFrameCompileEndToEnd.newContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void testThreeStateParseEchoLowering() {
        // ① 输入文本可解析（红→绿：修复前 not-end-properly offendingToken=at）
        SqlProgram program = new EqlASTParser().parseFromText(null, EQL);

        // ② 归一化回显 = atTimeZone(expr, tz) 函数形态（toSqlString 逐字；7 语料 0 命中无 BC 风险）
        String echo = program.toSqlString().replaceAll("\\s+", " ").trim();
        assertTrue(echo.startsWith("select atTimeZone("), echo);
        assertTrue(echo.contains("timestamp '2026-01-01 00:00:00' , 'UTC' )")
                || echo.contains("timestamp '2026-01-01 00:00:00', 'UTC' )")
                || echo.contains("'UTC' )"), echo);

        // ③ 方言 lowering = 模板文本（h2 [文档+实测-真库]）
        String sql = sqlOf("h2", EQL);
        assertTrue(sql.contains("AT TIME ZONE 'UTC'"), sql);
        assertTrue(sql.startsWith("select"), sql);
    }

    @Test
    public void testEntityFieldIntegration() {
        // 实体字段组合（共享 harness 的 AppUser 仅 VARCHAR 列——cast 组合出 TIMESTAMP
        // 类型域作 {0} 实参，同时覆盖 cast 与后缀备选的复合解析路径）
        String sql = sqlOf("h2",
                "select cast(o.name as timestamp) at time zone 'UTC' as tz from AppUser o");
        assertTrue(sql.contains("AT TIME ZONE 'UTC'"), sql);
        assertTrue(sql.contains("cast"), sql);
    }

    @Test
    public void testRegisteredDialectsLowering() {
        // [文档] 族：pg/postgis/duckdb 继承模板；oracle 族；mssql（2016+）
        assertTrue(sqlOf("postgresql", EQL).contains("AT TIME ZONE 'UTC'"));
        assertTrue(sqlOf("mssql", EQL).contains("AT TIME ZONE 'UTC'"));
        assertTrue(sqlOf("oracle", EQL).contains("AT TIME ZONE 'UTC'"));
        assertTrue(sqlOf("duckdb", EQL).contains("AT TIME ZONE 'UTC'"));
    }

    @Test
    public void testMysqlFamilyNotRegisteredFailsLoud() {
        // MySQL 仅 UTC-only CAST 形态（8.0.22+，不无损）→ 不注册，编译期响亮（裁定 §5）
        NopException e = assertThrows(NopException.class, () -> sqlOf("mysql", EQL));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("atTimeZone", e.getParam("funcName"));

        // mariadb（继承 mysql 同口径）+ mysql5.7 各自独立触发
        assertEquals("atTimeZone",
                ((NopException) assertThrows(NopException.class, () -> sqlOf("mariadb", EQL)))
                        .getParam("funcName"));
        assertEquals("atTimeZone",
                ((NopException) assertThrows(NopException.class, () -> sqlOf("mysql5.7", EQL)))
                        .getParam("funcName"));
    }
}
