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
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 Phase 3：{@code dayofweek(d)} → **ISO-8601 计法（1=Monday..7=Sunday）** 的方言
 * lowering 端到端（parse→transform→generate，Minimum Rules #23）。
 *
 * <p>裁定（WI6 Phase 0 裁定文档 §2）：ODBC 计法被拒（h2 DAY_OF_WEEK locale 依赖、
 * oracle TO_CHAR 'D' NLS territory 依赖、mssql DATEPART(weekday) SET DATEFIRST 依赖——
 * 三个会话漂移点全部一手文档明文）。ISO 计法各方言确定性 lowering：h2 = ISO_DAY_OF_WEEK；
 * postgresql（duckdb/postgis 继承）= extract(isodow from ...)；mysql = (1 + WEEKDAY(d))；
 * oracle = TRUNC(d) - TRUNC(d,'IW') + 1；mssql = DATEDIFF(DAY,0,d) 负模修正式（DAY 不属
 * DATEFIRST 影响面）。dm/tdengine/db2/es 不注册 = 编译期响亮。
 *
 * <p>约束 4"一致性"：同一日期在 h2 真库的规范值 = 各方言文档值域（ISO 1-7）——
 * 周一=1/周二=2/周日=7 三点锁定。不等价登记：各库原生同名词值域分叉（owner doc 对照行）。
 */
public class TestEqlDayOfWeekDialect {
    static final String EQL_DOW = "select dayofweek(o.createTime) as dw from AppUser o";

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
     * h2：ISO_DAY_OF_WEEK 模板（官方 "ISO day of the week (1 means Monday)"）。
     */
    @Test
    public void testH2IsoDayOfWeekTemplate() {
        String sql = compileToSql(EQL_DOW, dialectOf("h2"));
        assertTrue(sql.contains("iso_day_of_week(o.create_time)"), () -> "h2 dayofweek, got: " + sql);
    }

    /**
     * postgresql：extract(isodow from ...)（duckdb/postgis 继承同形态）。
     */
    @Test
    public void testPostgresqlIsodowTemplate() {
        String sql = compileToSql(EQL_DOW, dialectOf("postgresql"));
        assertTrue(sql.contains("extract(isodowfromo.create_time)"), () -> "pg dayofweek, got: " + sql);
        String duckdb = compileToSql(EQL_DOW, dialectOf("duckdb"));
        assertTrue(duckdb.contains("extract(isodowfromo.create_time)"), () -> "duckdb inherits, got: " + duckdb);
    }

    /**
     * mysql：(1 + WEEKDAY(d))（WEEKDAY 0=Monday..6=Sunday ⇒ +1 = ISO 1..7）。
     */
    @Test
    public void testMysqlWeekdayPlusOne() {
        String sql = compileToSql(EQL_DOW, dialectOf("mysql"));
        assertTrue(sql.contains("(1+weekday(o.create_time))"), () -> "mysql dayofweek, got: " + sql);
    }

    /**
     * oracle：TRUNC(d) - TRUNC(d,'IW') + 1（'IW' = ISO 周一起点，territory 无关；
     * DATE-DATE 减法 = 天数）。弃用 TO_CHAR 'D'（NLS territory 依赖）。
     */
    @Test
    public void testOracleTruncIwArithmetic() {
        String sql = compileToSql(EQL_DOW, dialectOf("oracle"));
        assertTrue(sql.contains("trunc(o.create_time)-trunc(o.create_time,'iw')+1"),
                () -> "oracle dayofweek, got: " + sql);
    }

    /**
     * mssql：DATEDIFF(DAY, 0, d) 负模修正式（0 = 1900-01-01 星期一；DAY datepart 不属
     * SET DATEFIRST 影响面）。弃用 DATEPART(weekday)（DATEFIRST 依赖）。[文档] 上限。
     */
    @Test
    public void testMssqlDatediffArithmetic() {
        String sql = compileToSql(EQL_DOW, dialectOf("mssql"));
        assertTrue(sql.contains("((datediff(day,0,o.create_time)%7)+7)%7+1"),
                () -> "mssql dayofweek, got: " + sql);
    }

    /**
     * 约束 4"一致性"：h2 真库三点值锁定——2026-02-16(周一)=1、2026-02-10(周二)=2、
     * 2026-02-15(周日)=7。同输入同规范值是本函数核心验收。
     */
    @Test
    public void testH2RealDbIsoValuePoints() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_dayofweek;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64), CREATE_TIME timestamp)");
                st.execute("insert into APP_USER values (1,'a',TIMESTAMP '2026-02-16 10:00:00')");
                st.execute("insert into APP_USER values (2,'b',TIMESTAMP '2026-02-10 10:00:00')");
                st.execute("insert into APP_USER values (3,'c',TIMESTAMP '2026-02-15 10:00:00')");
            }
            ICompiledSql compiled = new EqlCompiler().compile("test", EQL_DOW, contextOf(dialectOf("h2")));
            List<Integer> dows = new ArrayList<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(compiled.getSql().getText())) {
                while (rs.next()) {
                    dows.add(rs.getInt(1));
                }
            }
            assertEquals(java.util.Arrays.asList(1, 2, 7), dows,
                    "ISO day-of-week: Monday=1, Tuesday=2, Sunday=7");
        }
    }

    /**
     * 负向：未注册方言（dm）编译期 ERR_EQL_UNKNOWN_FUNCTION（funcName=dayofweek）。
     */
    @Test
    public void testUnregisteredDialectThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_DOW, contextOf(dialectOf("dm"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("dayofweek", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 元数负向：2 参调用响亮报元数错误码。
     */
    @Test
    public void testArityGuard() {
        NopException e2 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select dayofweek(o.createTime, o.name) from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e2.getErrorCode());
    }
}
