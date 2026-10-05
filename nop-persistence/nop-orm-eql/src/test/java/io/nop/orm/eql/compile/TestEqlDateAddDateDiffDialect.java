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
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 Phase 5：{@code dateAdd(unit,n,d)} / {@code dateDiff(unit,d1,d2)} 的无损 cell 落地
 * 与守护（parse→transform→generate，Minimum Rules #23）。
 *
 * <p>裁定（WI6 Phase 0 裁定文档 §4）：字符串单位约定下唯一新增可落 cell = **duckdb
 * `datediff`**（native realName=date_diff；`date_diff(part, starttimestamp, endtimestamp)`
 * = 边界跨越语义与 h2 锚点一致、unit 在前同序，`datediff` 为官方别名）。**h2/h2gis 既有
 * 注册行为不变**（守护用例锁定逐字行为——h2 dateadd/datediff 字符串单位仓内既有 [实测]）。
 * mysql/mariadb/mysql5.7/postgresql/postgis/oracle/oracle-reverse/mssql **不落 = 编译期
 * 响亮**（裸关键字单位/语义分叉/DS-YM 双函数分叉——ledger 处置，roadmap ledger 行随本
 * Phase 登记）。
 *
 * <p>dateDiff 语义锚点 = 边界跨越（crossed unit boundaries，h2/duckdb 共有语义）；
 * 不等价登记：mysql TIMESTAMPDIFF=完整单位数、mysql DATEDIFF/pg date-date=仅天。
 */
public class TestEqlDateAddDateDiffDialect {
    static final String EQL_ADD = "select dateAdd('MONTH', -1, o.createTime) as d from AppUser o";
    static final String EQL_DIFF = "select dateDiff('MONTH', o.createTime, o.updateTime) as n from AppUser o";

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
                TestEqlCompileSql.col("createTime", "CREATE_TIME", 3, false),
                TestEqlCompileSql.col("updateTime", "UPDATE_TIME", 4, false)));
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

    // ===== h2/h2gis 既有注册守护（行为不变） =====

    /**
     * 守护：h2 dateAdd 既有 native（argTypes=ANY,INTEGER,TIMESTAMP，字符串单位）lowering
     * 逐字不变。
     */
    @Test
    public void testH2DateAddGuard() {
        String sql = compileToSql(EQL_ADD, dialectOf("h2"));
        assertEquals("selectdateadd('month',-1,o.create_time)asdfromapp_useraso", sql,
                "h2 dateAdd lowering must be byte-identical to baseline (guard)");
    }

    /**
     * 守护：h2 dateDiff 既有 native（边界跨越语义）lowering 逐字不变。
     */
    @Test
    public void testH2DateDiffGuard() {
        String sql = compileToSql(EQL_DIFF, dialectOf("h2"));
        assertEquals("selectdatediff('month',o.create_time,o.update_time)asnfromapp_useraso", sql,
                "h2 dateDiff lowering must be byte-identical to baseline (guard)");
    }

    /**
     * 守护：h2 真库执行 dateAdd('MONTH',-1,d)（2026-03-10 → 2026-02-10）与
     * dateDiff('MONTH', 2026-01-31, 2026-02-02)（边界跨越 = 1，与 scratch 实测一致）。
     */
    @Test
    public void testH2RealDbAddDiffSemantics() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_datediff;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64), "
                        + "CREATE_TIME timestamp, UPDATE_TIME timestamp)");
                st.execute("insert into APP_USER values (1,'a',TIMESTAMP '2026-03-10 08:00:00',TIMESTAMP '2026-02-02 08:00:00')");
                st.execute("insert into APP_USER values (2,'b',TIMESTAMP '2026-01-31 08:00:00',TIMESTAMP '2026-03-10 08:00:00')");
            }
            ICompiledSql add = new EqlCompiler().compile("test", EQL_ADD, contextOf(dialectOf("h2")));
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(add.getSql().getText())) {
                assertTrue(rs.next());
                assertEquals(java.sql.Timestamp.valueOf("2026-02-10 08:00:00"), rs.getTimestamp(1),
                        "dateAdd('MONTH',-1, 2026-03-10) = 2026-02-10");
            }
            ICompiledSql diff = new EqlCompiler().compile("test",
                    "select dateDiff('MONTH', o.createTime, o.updateTime) as n from AppUser o "
                            + "where o.id = 2", contextOf(dialectOf("h2")));
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(diff.getSql().getText())) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1),
                        "dateDiff('MONTH', 2026-01-31, 2026-03-10) crossed boundaries = 2 (complete units would be 1)");
            }
        }
    }

    // ===== duckdb datediff（本 Phase 唯一新增注册 cell） =====

    /**
     * duckdb：native realName=date_diff（边界跨越语义与 h2 锚点一致）。
     */
    @Test
    public void testDuckdbDateDiffNative() {
        String sql = compileToSql(EQL_DIFF, dialectOf("duckdb"));
        assertTrue(sql.contains("date_diff('month',o.create_time,o.update_time)"),
                () -> "duckdb dateDiff → date_diff(part,...), got: " + sql);
    }

    // ===== 不落 cell 负向（响亮 + ledger） =====

    /**
     * 负向：mysql dateAdd 不落（DATE_ADD unit 明文裸关键字）——编译期响亮。
     */
    @Test
    public void testMysqlDateAddNotLandedThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_ADD, contextOf(dialectOf("mysql"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("dateadd", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 负向：postgresql dateDiff 不落（date-date 减法语义无单位参数通路）——编译期响亮。
     */
    @Test
    public void testPostgresqlDateDiffNotLandedThrowsLoud() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_DIFF, contextOf(dialectOf("postgresql"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("datediff", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 元数负向：dateAdd 3 参形态在 h2 的 2 参调用响亮报元数错误码。
     */
    @Test
    public void testArityGuard() {
        NopException e2 = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "select dateAdd('MONTH', o.createTime) from AppUser o", contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_FEW_ARGS.getErrorCode(), e2.getErrorCode());
        assertEquals("dateadd", e2.getParam(ARG_FUNC_NAME));
    }
}
