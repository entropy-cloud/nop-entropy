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
import java.time.LocalDate;

import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 Phase 1：{@code lastDay(d)} 归一化（返回 d 所在月份最后一天的日期值）的方言 lowering
 * 端到端（parse→transform→generate，Minimum Rules #23）。
 *
 * <p>裁定（{@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi6-phase0-adjudications.md} §6）：
 * h2/mysql/oracle = 原生 LAST_DAY native（h2 带 testSql 真库执行）；postgresql（duckdb/postgis 继承）=
 * {@code date_trunc} 精确算术模板；mssql = EOMONTH 模板；dm/tdengine/db2/es 不注册 = 编译期
 * {@code ERR_EQL_UNKNOWN_FUNCTION} 响亮（注册面纪律：不进 default）。
 *
 * <p>不等价登记（约束 4）：pg 模板对 timestamptz 输入按会话 TimeZone 截断（时变），
 * 原生 LAST_DAY 族无此时变——输入类型域钉死 DATE/TIMESTAMP（owner doc 差异行）。
 */
public class TestEqlLastDayDialect {
    static final String EQL_LAST_DAY = "select lastDay(o.createTime) as d from AppUser o";

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
     * h2：原生 LAST_DAY（realName 渲染）。
     */
    @Test
    public void testH2NativeLastDay() {
        String sql = compileToSql(EQL_LAST_DAY, dialectOf("h2"));
        assertTrue(sql.contains("last_day(o.create_time)"), () -> "h2 lastDay → LAST_DAY(d), got: " + sql);
    }

    /**
     * postgresql：date_trunc 精确算术模板（duckdb/postgis 继承同形态）。
     */
    @Test
    public void testPostgresqlTemplate() {
        String sql = compileToSql(EQL_LAST_DAY, dialectOf("postgresql"));
        assertTrue(sql.contains("cast(date_trunc('month',o.create_time)+interval'1month'-interval'1day'asdate)"),
                () -> "pg lastDay → date_trunc arithmetic, got: " + sql);
    }

    /**
     * duckdb 继承 pg 模板（duckdb extends postgresql）。
     */
    @Test
    public void testDuckdbInheritsPgTemplate() {
        String sql = compileToSql(EQL_LAST_DAY, dialectOf("duckdb"));
        assertTrue(sql.contains("date_trunc('month',o.create_time)"),
                () -> "duckdb lastDay inherits pg template, got: " + sql);
    }

    /**
     * mssql：EOMONTH 模板（[文档]，CI 无 MSSQL 真库——渲染文本即替代证据）。
     */
    @Test
    public void testMssqlEomonthTemplate() {
        String sql = compileToSql(EQL_LAST_DAY, dialectOf("mssql"));
        assertTrue(sql.contains("eomonth(o.create_time)"), () -> "mssql lastDay → EOMONTH(d), got: " + sql);
    }

    /**
     * mysql：原生 LAST_DAY（mariadb/mysql5.7 继承）。
     */
    @Test
    public void testMysqlNativeLastDay() {
        String sql = compileToSql(EQL_LAST_DAY, dialectOf("mysql"));
        assertTrue(sql.contains("last_day(o.create_time)"), () -> "mysql lastDay → LAST_DAY(d), got: " + sql);
    }

    /**
     * h2 真库执行语义：lastDay('2026-02-10') → 2026-02-28（含非闰年 2 月边界）。
     */
    @Test
    public void testH2RealDbExecution() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_lastday;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64), CREATE_TIME timestamp)");
                st.execute("insert into APP_USER values (1,'a',TIMESTAMP '2026-02-10 12:34:56')");
            }
            ICompiledSql compiled = new EqlCompiler().compile("test", EQL_LAST_DAY, contextOf(dialectOf("h2")));
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(compiled.getSql().getText())) {
                assertTrue(rs.next());
                Object value = rs.getObject(1);
                LocalDate d = value instanceof java.sql.Timestamp
                        ? ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate()
                        : (value instanceof java.sql.Date ? ((java.sql.Date) value).toLocalDate()
                        : LocalDate.parse(String.valueOf(value).substring(0, 10)));
                assertEquals(LocalDate.of(2026, 2, 28), d, "lastDay(2026-02-10) must be 2026-02-28");
            }
        }
    }

    /**
     * 负向：未注册方言（dm——default 无 lastday，dm 自身无注册）编译期 ERR_EQL_UNKNOWN_FUNCTION。
     * 无静态默认返回/回退原文输出（无静默跳过）。
     */
    @Test
    public void testUnregisteredDialectThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_LAST_DAY, contextOf(dialectOf("dm"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("lastday", e.getParam(ARG_FUNC_NAME));
    }
}
