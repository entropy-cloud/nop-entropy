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
import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_FEW_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 Phase 2：{@code weekofyear(d)} → ISO-8601 周号（1-53）的方言 lowering 端到端
 * （parse→transform→generate，Minimum Rules #23）。
 *
 * <p>裁定（WI6 Phase 0 裁定文档 §3）：mysql = 原生 WEEKOFYEAR（=WEEK(d,3) ISO）；h2 =
 * ISO_WEEK 模板（H2 无 WEEKOFYEAR 函数 [实测]，WEEK(d) locale 依赖弃用）；postgresql
 * （duckdb/postgis 继承）= extract(week from ...)；oracle = to_number(to_char(d,'IW'))；
 * mssql = DATEPART(iso_week,d)。dm/tdengine/db2/es 不注册 = 编译期响亮。
 *
 * <p>不等价登记（约束 4）：非 ISO 周各库差异（mysql WEEK() 默认 mode 受 default_week_format
 * 变量影响、mssql DATEPART(week) 受 SET DATEFIRST 影响）——本归一化只落 ISO 周。
 */
public class TestEqlWeekOfYearDialect {
    static final String EQL_WEEK = "select weekofyear(o.createTime) as w from AppUser o";

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
     * h2：ISO_WEEK 模板（H2 无 WEEKOFYEAR [实测]，ISO_WEEK 官方钉死 ISO 定义）。
     */
    @Test
    public void testH2IsoWeekTemplate() {
        String sql = compileToSql(EQL_WEEK, dialectOf("h2"));
        assertTrue(sql.contains("iso_week(o.create_time)"), () -> "h2 weekofyear → ISO_WEEK(d), got: " + sql);
    }

    /**
     * postgresql：extract(week from ...) 模板（duckdb/postgis 继承同形态）。
     */
    @Test
    public void testPostgresqlExtractWeekTemplate() {
        String sql = compileToSql(EQL_WEEK, dialectOf("postgresql"));
        assertTrue(sql.contains("extract(weekfromo.create_time)"), () -> "pg weekofyear → extract(week), got: " + sql);
        String duckdb = compileToSql(EQL_WEEK, dialectOf("duckdb"));
        assertTrue(duckdb.contains("extract(weekfromo.create_time)"), () -> "duckdb inherits pg template, got: " + duckdb);
    }

    /**
     * oracle：to_number(to_char(d,'IW'))（'IW' ISO 8601 周号，territory 无关）。
     */
    @Test
    public void testOracleIwTemplate() {
        String sql = compileToSql(EQL_WEEK, dialectOf("oracle"));
        assertTrue(sql.contains("to_number(to_char(o.create_time,'iw'))"), () -> "oracle weekofyear, got: " + sql);
    }

    /**
     * mssql：DATEPART(iso_week,d)（iso_week 不属 SET DATEFIRST 影响面）。[文档] 上限。
     */
    @Test
    public void testMssqlIsoWeekTemplate() {
        String sql = compileToSql(EQL_WEEK, dialectOf("mssql"));
        assertTrue(sql.contains("datepart(iso_week,o.create_time)"), () -> "mssql weekofyear, got: " + sql);
    }

    /**
     * mysql：原生 WEEKOFYEAR（mariadb/mysql5.7 继承）。
     */
    @Test
    public void testMysqlNativeWeekOfYear() {
        String sql = compileToSql(EQL_WEEK, dialectOf("mysql"));
        assertTrue(sql.contains("weekofyear(o.create_time)"), () -> "mysql weekofyear, got: " + sql);
    }

    /**
     * h2 真库执行跨年边界（ISO 周语义）：2025-12-29 与 2026-01-01 同属 ISO 2026-W01，
     * 2026-02-10 属 W07——跨年边界是 ISO 周的核心判别场景。
     */
    @Test
    public void testH2RealDbIsoWeekBoundaries() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_weekofyear;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64), CREATE_TIME timestamp)");
                st.execute("insert into APP_USER values (1,'a',TIMESTAMP '2025-12-29 10:00:00')");
                st.execute("insert into APP_USER values (2,'b',TIMESTAMP '2026-01-01 10:00:00')");
                st.execute("insert into APP_USER values (3,'c',TIMESTAMP '2026-02-10 10:00:00')");
            }
            ICompiledSql compiled = new EqlCompiler().compile("test", EQL_WEEK, contextOf(dialectOf("h2")));
            List<Integer> weeks = new ArrayList<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(compiled.getSql().getText())) {
                while (rs.next()) {
                    weeks.add(rs.getInt(1));
                }
            }
            assertEquals(java.util.Arrays.asList(1, 1, 7), weeks,
                    "ISO week: 2025-12-29/2026-01-01 both W01, 2026-02-10 W07");
        }
    }

    /**
     * 负向：未注册方言（dm）编译期 ERR_EQL_UNKNOWN_FUNCTION（funcName=weekofyear）。
     */
    @Test
    public void testUnregisteredDialectThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_WEEK, contextOf(dialectOf("dm"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("weekofyear", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 元数负向：1 参函数的 0/2 参调用响亮报元数错误码。
     */
    @Test
    public void testArityGuard() {
        NopException e0 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select weekofyear() from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_FEW_ARGS.getErrorCode(), e0.getErrorCode());
        assertEquals("weekofyear", e0.getParam(ARG_FUNC_NAME));

        NopException e2 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select weekofyear(o.createTime, o.name) from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e2.getErrorCode());
    }
}
