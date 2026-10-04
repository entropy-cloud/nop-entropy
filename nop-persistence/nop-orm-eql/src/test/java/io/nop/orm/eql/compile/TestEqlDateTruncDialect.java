package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_FEW_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 Phase 4：{@code dateTrunc(unit, d)}（unit = 字符串字面量，如 {@code 'month'}）的方言
 * lowering 端到端（parse→transform→generate，Minimum Rules #23）。
 *
 * <p>裁定（WI6 Phase 0 裁定文档 §7）：字符串单位约定下可落 cell = h2/h2gis + postgresql/
 * postgis/duckdb 三方**共享同一模板形态** {@code date_trunc({0},{1})}（h2 [实测]：DATE_TRUNC
 * 接受带引号字符串单位且参数序 (unit, value) 与 pg 一致；DuckDB scratch 同通过）。
 * mysql（无原生，构造式非无损）/oracle（TRUNC fmt 词表无法经 fixed template 翻译）/
 * mssql（datepart 明文拒绝字符串）**不落 = 编译期响亮**。规范单位词表 = 三方交集：
 * year/quarter/month/week/day/hour/minute/second。
 */
public class TestEqlDateTruncDialect {
    static final String EQL_TRUNC_MONTH = "select dateTrunc('month', o.createTime) as tm from AppUser o";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestEqlCompileSql.TestCompileContext contextOf(IDialect dialect) {
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false),
                TestEqlCompileSql.col("createTime", "CREATE_TIME", 3, false)));
        return ctx;
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " must be loadable");
        return dialect;
    }

    static String compileToSql(String eql, IDialect dialect) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, contextOf(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * h2：date_trunc({0},{1}) 模板——字符串单位原样渲染为带引号字面量。
     */
    @Test
    public void testH2DateTruncTemplate() {
        String sql = compileToSql(EQL_TRUNC_MONTH, dialectOf("h2"));
        assertTrue(sql.contains("date_trunc('month',o.create_time)"), () -> "h2 dateTrunc, got: " + sql);
    }

    /**
     * postgresql：同模板形态（duckdb/postgis 继承——pg/duckdb/h2 三方共享同一 lowering 形态）。
     */
    @Test
    public void testPostgresqlAndDuckdbSameTemplateForm() {
        String pg = compileToSql(EQL_TRUNC_MONTH, dialectOf("postgresql"));
        assertTrue(pg.contains("date_trunc('month',o.create_time)"), () -> "pg dateTrunc, got: " + pg);
        String duckdb = compileToSql(EQL_TRUNC_MONTH, dialectOf("duckdb"));
        assertTrue(duckdb.contains("date_trunc('month',o.create_time)"), () -> "duckdb inherits, got: " + duckdb);
    }

    /**
     * h2 真库执行截断语义：2026-02-10 12:34:56 → month 截断 = 2026-02-01 00:00:00；
     * week 截断 = 2026-02-09（周一）。
     */
    @Test
    public void testH2RealDbTruncationSemantics() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_datetrunc;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64), CREATE_TIME timestamp)");
                st.execute("insert into APP_USER values (1,'a',TIMESTAMP '2026-02-10 12:34:56')");
            }
            ICompiledSql compiled = new EqlCompiler().compile("test", EQL_TRUNC_MONTH, contextOf(dialectOf("h2")));
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(compiled.getSql().getText())) {
                assertTrue(rs.next());
                assertEquals(Timestamp.valueOf("2026-02-01 00:00:00"), rs.getTimestamp(1),
                        "month truncation must zero the day/time parts");
            }
            ICompiledSql week = new EqlCompiler().compile("test",
                    "select dateTrunc('week', o.createTime) as tw from AppUser o", contextOf(dialectOf("h2")));
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(week.getSql().getText())) {
                assertTrue(rs.next());
                assertEquals(Timestamp.valueOf("2026-02-09 00:00:00"), rs.getTimestamp(1),
                        "week truncation must snap to Monday");
            }
        }
    }

    /**
     * 负向（不落 cell）：mysql（无原生/构造式非无损）编译期 ERR_EQL_UNKNOWN_FUNCTION——
     * 禁止"部分单位正确的模板"（静默错误 SQL > 响亮报错）。
     */
    @Test
    public void testMysqlNotLandedThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_TRUNC_MONTH, contextOf(dialectOf("mysql"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("datetrunc", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 元数负向：2 参函数的 1/3 参调用响亮报元数错误码。
     */
    @Test
    public void testArityGuard() {
        NopException e1 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select dateTrunc('month') from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_FEW_ARGS.getErrorCode(), e1.getErrorCode());
        assertEquals("datetrunc", e1.getParam(ARG_FUNC_NAME));

        NopException e3 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select dateTrunc('month', o.createTime, o.name) from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e3.getErrorCode());
    }
}
