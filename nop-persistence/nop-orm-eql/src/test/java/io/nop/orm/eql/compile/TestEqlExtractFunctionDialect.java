package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 3：{@code EXTRACT(f FROM d)} 的编译层逐字段方言 lowering
 * （parse→transform→generate 三层连通，Minimum Rules #23）。
 *
 * <p>正向（逐字段形态）：h2 上 {@code extract(year from o.createTime)} → {@code year(...)}；
 * mssql → {@code datepart(year,...)}；oracle/postgresql → {@code extract(YEAR from ...)}；
 * 其余六字段在 h2 各一条（Phase 3 注册后）。
 *
 * <p>负向：无 year 的手构方言编译期 {@code ERR_EQL_UNKNOWN_FUNCTION(year)}。既有逗号形态
 * {@code extract(f,d)} 行为不变（h2 注册的 extract 单参，2 参响亮报元数错——既有现状守护）。
 */
public class TestEqlExtractFunctionDialect {
    static final String EQL_YEAR = "select o.id from AppUser o where extract(year from o.createTime) = 2026";

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
     * h2：year 字段（15/15 既有注册）。
     */
    @Test
    public void testH2YearField() {
        String sql = compileToSql(EQL_YEAR, dialectOf("h2"));
        assertTrue(sql.contains("year(o.create_time)"), () -> "h2 extract(year from d) → year(d), got: " + sql);
    }

    /**
     * mssql：datepart 模板（year 既有 + 其余字段 Phase 3 注册）。
     */
    @Test
    public void testMssqlDatepartTemplates() {
        String sql = compileToSql(EQL_YEAR, dialectOf("mssql"));
        assertTrue(sql.contains("datepart(year,o.create_time)"), () -> "mssql must emit datepart, got: " + sql);
        String quarter = compileToSql(
                "select o.id from AppUser o where extract(quarter from o.createTime) = 1", dialectOf("mssql"));
        assertTrue(quarter.contains("datepart(quarter,o.create_time)"),
                () -> "mssql quarter field, got: " + quarter);
    }

    /**
     * oracle：extract(FIELD from {0}) 模板。
     */
    @Test
    public void testOracleExtractTemplates() {
        String sql = compileToSql(EQL_YEAR, dialectOf("oracle"));
        assertTrue(sql.contains("extract(yearfromo.create_time)"), () -> "oracle must emit extract, got: " + sql);
    }

    /**
     * postgresql：extract(FIELD from {0}) 模板（PG 无 month() 函数形态）。
     */
    @Test
    public void testPostgresqlExtractTemplates() {
        String sql = compileToSql(EQL_YEAR, dialectOf("postgresql"));
        assertTrue(sql.contains("extract(yearfromo.create_time)"), () -> "pg must emit extract, got: " + sql);
    }

    /**
     * h2 其余六字段：native 名即真名（Phase 3 在 default 注册 month/day/hour/minute/
     * second/quarter 后 h2 经继承获得）。
     */
    @Test
    public void testH2AllSixNewFields() {
        for (String field : Arrays.asList("month", "day", "hour", "minute", "second", "quarter")) {
            String sql = compileToSql(
                    "select o.id from AppUser o where extract(" + field + " from o.createTime) = 1",
                    dialectOf("h2"));
            assertTrue(sql.contains(field + "(o.create_time)"),
                    () -> "h2 must lower extract(" + field + " from d) to " + field + "(d), got: " + sql);
        }
    }

    /**
     * 负向：无 year 的手构方言编译期 ERR_EQL_UNKNOWN_FUNCTION（funcName=year——归一化名）。
     */
    @Test
    public void testUnknownDialectThrowsLoudUnknownFunction() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL_YEAR, contextOf(noYearDialect())));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("year", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 既有逗号形态守护：h2 注册的 extract 为单参（argTypes=TIMESTAMP），逗号形态 2 参
     * 走既有元数校验响亮报错——本 Phase 不改其行为（差异登记第 7 条）。
     */
    @Test
    public void testLegacyCommaFormBehaviorUnchanged() {
        String eql = "select o.id from AppUser o where extract(o.createTime, 'year') = 1";
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", eql, contextOf(dialectOf("h2"))));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e.getErrorCode());
        assertEquals("extract", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 手构方言：只注册 upper/current_timestamp，无 year 等字段函数。
     */
    static IDialect noYearDialect() {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel upper = new SqlNativeFunctionModel();
        upper.setName("upper");
        upper.setArgTypes(Collections.singletonList(StdSqlType.VARCHAR));
        upper.setReturnType(StdSqlType.VARCHAR);

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);

        model.setFunctions(Arrays.asList(upper, currentTimestamp));
        return new DialectImpl(model);
    }
}
