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
import io.nop.orm.eql.compile.TestEqlCompileSql.TestCompileContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static io.nop.dao.DaoErrors.ERR_DIALECT_INVALID_INTERVAL_LITERAL_TEMPLATE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7b Phase 1（roadmap EC ③⑦）：`sqlIntervalLiteral` 模板消费端到端。
 *
 * <p>修复前红（BUG-5 的另一半）：`AstToSqlGenerator` 无 `visitSqlIntervalExpr` override，
 * 执行 SQL 对所有方言都输出裸 ` interval 1 DAY`——探针已证该形态在 H2 与 PG 上非法
 * （`ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`）。
 *
 * <p>修复后（裁定 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md`
 * §1/§2）：已配置模板的方言输出 `INTERVAL '{value}' {unit}`（h2/pg/oracle，value 带引号）或
 * `INTERVAL {value} {unit}`（mysql/duckdb，value 裸值）；未配置方言维持既有裸输出（BC）；
 * 已配置方言上非字面量 value 编译期显式报错（裁定处置②）；坏 marker 方言加载期抛错。
 */
public class TestEqlSqlIntervalTemplate {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestCompileContext newContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    static String compile(String dialectName, String eql) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, "dialect not loaded: " + dialectName);
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, newContext(dialect));
        return compiled.getSql().getText();
    }

    /**
     * EC ③ 端到端（接线验证，guide 规则 #23）：EQL 文本 → parse 归一化 → transform →
     * `visitSqlIntervalExpr` 模板渲染 → H2 真库执行返回。修复前此查询生成的裸
     * `interval 1 DAY` 在 H2 上语法错（探针 90022 族）。
     */
    @Test
    public void testH2QuotedTemplateCompileAndExecute() throws Exception {
        String sql = compile("h2", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(sql.contains("INTERVAL '1' DAY"), sql);

        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_itv;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64))");
                st.execute("insert into APP_USER values (1,'g'),(2,'g'),(3,'g')");
            }
            Timestamp ts = null;
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                assertTrue(rs.next());
                ts = rs.getTimestamp(1);
            }
            assertEquals(Timestamp.valueOf(LocalDateTime.of(2020, 1, 2, 0, 0, 0)), ts);
        }
    }

    /**
     * EC ③ 三形态（+duckdb 显式覆盖格）：同一 EQL 输入在不同方言输出形态不同
     * （约束 4 不等价类素材——h2/pg/oracle value 带引号，mysql/duckdb 裸值）。
     */
    @Test
    public void testPostgresqlQuotedValueTemplate() {
        String sql = compile("postgresql", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(sql.contains("INTERVAL '1' DAY"), sql);
    }

    @Test
    public void testOracleQuotedValueTemplate() {
        String sql = compile("oracle", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(sql.contains("INTERVAL '1' DAY"), sql);
    }

    @Test
    public void testMysqlBareValueTemplate() {
        String sql = compile("mysql", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(sql.contains("INTERVAL 1 DAY"), sql);
    }

    @Test
    public void testDuckdbBareValueTemplate() {
        String sql = compile("duckdb", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(sql.contains("INTERVAL 1 DAY"), sql);
    }

    /** 两种既有语法形态归一化后模板渲染输出一致（约束 4 一致性类素材）。 */
    @Test
    public void testBothSyntaxFormsRenderSameTemplate() {
        String viaUnit = compile("h2", "select timestamp '2020-01-01' + interval 1 day from AppUser o");
        String viaString = compile("h2", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        assertTrue(viaUnit.contains("INTERVAL '1' DAY"), viaUnit);
        assertEquals(viaUnit.replaceAll("\\s+", " "), viaString.replaceAll("\\s+", " "));
    }

    /** 未配置模板的方言（mssql，T-SQL 无 INTERVAL 字面量故不配置）维持既有裸输出——BC 守护。 */
    @Test
    public void testUnconfiguredDialectKeepsBareOutput() {
        String sql = compile("mssql", "select timestamp '2020-01-01' + interval '1 day' from AppUser o");
        // pretty 打印会在 token 间加空格，故归一化空白后比对（裸形态文本本体未变）
        assertTrue(sql.replaceAll("\\s+", " ").contains(" interval 1 DAY"), sql);
    }

    /**
     * 裁定处置②：已配置模板的方言上非字面量 value（列引用）编译期显式报错——
     * 回落裸输出会继续产出探针已证非法的 `interval <col> DAY` 文本（静默错误 SQL）。
     */
    @Test
    public void testNonLiteralValueCompileErrorOnConfiguredDialect() {
        NopException e = assertThrows(NopException.class,
                () -> compile("h2", "select o.id + interval o.id day from AppUser o"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("intervalNonLiteralValue", e.getParam("feature"));
    }

    /** 坏 marker 模板在方言加载期（DialectImpl 构造期）抛错——WI0 marker 校验同纪律。 */
    @Test
    public void testBadMarkerTemplateRejectedAtLoad() {
        io.nop.dao.dialect.model.DialectModel model = new io.nop.dao.dialect.model.DialectModel();
        model.setReservedKeywords(java.util.Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        sqls.setSqlIntervalLiteral("INTERVAL {voodoo} {unit}");
        model.setSqls(sqls);
        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(java.util.Collections.singletonList(currentTimestamp));

        NopException e = assertThrows(NopException.class, () -> new io.nop.dao.dialect.impl.DialectImpl(model));
        assertEquals(ERR_DIALECT_INVALID_INTERVAL_LITERAL_TEMPLATE.getErrorCode(), e.getErrorCode());
    }
}
