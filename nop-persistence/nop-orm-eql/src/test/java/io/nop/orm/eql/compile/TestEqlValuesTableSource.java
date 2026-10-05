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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_VALUES_ROW_ARITY_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 交付⑤（plan 38 Phase 4）：{@code FROM (VALUES …)} 表源端到端——parse → transform
 * （select * 展开 / 列解析 / querySpace 实体无关回落）→ 生成（方言能力门）→ H2 真库执行。
 * 证据分级：h2 [文档+实测-真库]、postgresql/mssql [文档]、mysql 族不注册（MySQL 行形态是
 * {@code VALUES ROW(…)} 硬分叉，裁定 §4 矩阵）、oracle/db2/dm/tdengine [未取证] 不配置。
 */
public class TestEqlValuesTableSource {
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
    public void testParseValuesTableSource() {
        // 文法备选可解析（alias 必填 = EQL 相对 H2 收紧，裁定 §4）
        assertTrue(sqlOf("h2", "select * from (values (1),(2)) t").endsWith("(values ( 1 ), ( 2 )) as t"), sqlOf("h2", "select * from (values (1),(2)) t"));
        assertTrue(sqlOf("h2", "select * from (values (1),(2)) as t").endsWith("(values ( 1 ), ( 2 )) as t"));
    }

    @Test
    public void testExplicitColumnAliases() {
        // 显式别名列清单：限定名投影列解析 + 生成文本带列清单。
        // 非限定名对非实体表源 = 既有响亮语义（resolveDefaultTableSource 仅放行
        // SqlSingleTableSource，子查询表源同Throws——VALUES 源与子查询同形，不放宽）。
        String sql = sqlOf("h2", "select t.c1 from (values (1),(2)) t(c1)");
        assertTrue(sql.contains("from (values ( 1 ), ( 2 )) as t(c1)"), sql);
        assertTrue(sql.startsWith("select t.c1 as"), sql);
    }

    @Test
    public void testUnqualifiedColumnLikeSubquerySourceFailsLoud() {
        // 与子查询表源同形：非实体源上的非限定列 = 既有 unknown-column 响亮（不放宽既有守卫）
        assertThrows(NopException.class,
                () -> sqlOf("h2", "select c1 from (values (1),(2)) t(c1)"));
        assertThrows(NopException.class,
                () -> sqlOf("h2", "select x from (select 1 as x) t"));
    }

    @Test
    public void testTransformSelectStarExpansion() {
        // select * 展开：meta 列名缺省 C1..Cn（H2 惯例）——transform 端到端
        // （Anti-Hollow：VALUES 源不被 ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE 等既有约束挡下）
        String sql = sqlOf("h2", "select * from (values (1,'a'),(2,'b')) t(x, y)");
        assertTrue(sql.startsWith("select t.x as c1"), sql);
        assertTrue(sql.contains("t.y as c2"), sql);
    }

    @Test
    public void testValuesPlusEntityMixedSource() {
        // 混合源：实体 querySpace 胜出（addResolvedEntity 无条件覆盖）+ VALUES 源并存
        String sql = sqlOf("h2",
                "select o.name, v.c1 from AppUser o, (values (1),(2)) v(c1) where o.id = v.c1");
        assertTrue(sql.contains("(values ( 1 ), ( 2 )) as v(c1)"), sql);
    }

    @Test
    public void testEchoPathRoundTrip() {
        // 回显路径逐字打印（无方言门——方言门在子类 AstToSqlGenerator）
        io.nop.orm.eql.ast.SqlProgram program = new io.nop.orm.eql.parse.EqlASTParser()
                .parseFromText(null, "select c1 from (values (1),(2)) t(c1)");
        String echo = program.toSqlString().replaceAll("\\s+", " ").trim();
        assertTrue(echo.contains("(values ( 1 ), ( 2 )) as t(c1)"), echo);
    }

    @Test
    public void testRaggedRowsFailLoudAtParse() {
        // ragged rows 解析期响亮（新错误码，不允许静默）
        NopException e = assertThrows(NopException.class,
                () -> new io.nop.orm.eql.parse.EqlASTParser()
                        .parseFromText(null, "select * from (values (1),(2,3)) t"));
        assertEquals(ERR_EQL_VALUES_ROW_ARITY_MISMATCH.getErrorCode(), e.getErrorCode());
        assertEquals(2, ((Number) e.getParam("rowIndex")).intValue());
    }

    @Test
    public void testAliasMandatoryAtParse() {
        // alias 必填（EQL 相对 H2 的子集化收紧，裁定 §4）——缺 alias 解析响亮失败
        assertThrows(NopException.class,
                () -> new io.nop.orm.eql.parse.EqlASTParser()
                        .parseFromText(null, "select * from (values (1),(2))"));
    }

    @Test
    public void testMysqlNotRegisteredFailsLoud() {
        // MySQL 行形态是 VALUES ROW(…) 硬分叉（裁定 §4 矩阵）→ 不注册，编译期响亮
        NopException e = assertThrows(NopException.class,
                () -> sqlOf("mysql", "select * from (values (1),(2)) t"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("valuesTableSource", e.getParam("feature"));
    }

    @Test
    public void testPostgresqlAndMssqlRegistered() {
        // [文档] PG Table Expression / MSSQL FROM (VALUES …) AS t(c1,c2)
        assertTrue(sqlOf("postgresql", "select * from (values (1),(2)) t").endsWith("(values ( 1 ), ( 2 )) as t"));
        assertTrue(sqlOf("mssql", "select * from (values (1),(2)) as t(x)").endsWith("(values ( 1 ), ( 2 )) as t(x)"));
    }

    @Test
    public void testH2TrueDbExecution() throws Exception {
        // 真库执行证据（Primary）：官方文档语法 FROM (VALUES …) alias(col) 在 H2 上真执行
        String sql = sqlOf("h2", "select * from (values (1),(2)) t(x)");
        try (java.sql.Connection con = java.sql.DriverManager
                .getConnection("jdbc:h2:mem:e2e_values;DB_CLOSE_DELAY=-1")) {
            List<Integer> xs = new ArrayList<>();
            try (java.sql.Statement st = con.createStatement();
                 java.sql.ResultSet rs = st.executeQuery(sql)) {
                while (rs.next())
                    xs.add(rs.getInt(1));
            }
            assertEquals(2, xs.size());
            assertEquals(1, xs.get(0).intValue());
            assertEquals(2, xs.get(1).intValue());
        }
    }
}
