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
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 5：遮蔽放开的 transform 层响亮行为锁定与已注册方言 lowering。
 *
 * <p>本 Phase 是文法放开：五词可解析后——(a) 已注册字段函数的方言（EXTRACT 字段注册
 * 覆盖的 15 方言，含 h2）正常 lowering（如 {@code day(o.name)} → {@code day(o.name)}）；
 * (b) 未注册方言编译期 {@code ERR_EQL_UNKNOWN_FUNCTION} 响亮报错（无静默跳过）。
 * 归一化日期函数族（dayofweek/dateAdd 等）的注册 owner 是 WI6。
 */
public class TestEqlKeywordShadowDialect {

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
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        return ctx;
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " must be loadable");
        return dialect;
    }

    /**
     * 已注册方言（h2，经 Phase 3 EXTRACT 字段注册）：day(x) 正常 lowering。
     */
    @Test
    public void testDayLowersOnRegisteredDialect() {
        ICompiledSql compiled = new EqlCompiler().compile("test",
                "select o.id from AppUser o where day(o.name) = 1", contextOf(dialectOf("h2")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("day(o.name)"), () -> "h2 must lower day(o.name), got: " + sql);
    }

    /**
     * 未注册方言：编译期 ERR_EQL_UNKNOWN_FUNCTION（funcName=day）——响亮行为锁定。
     */
    @Test
    public void testDayFailsLoudOnUnregisteredDialect() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test",
                        "select o.id from AppUser o where day(o.name) = 1", contextOf(noDateFuncDialect())));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("day", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * month()/year() 行为不变守护：month 在 h2 亦经 Phase 3 注册（与放开前"month 0 注册"
     * 的时代相比行为变化登记于裁定 §2——本用例锁定的是当前口径下 month 与 day 同待遇，
     * 且 year 在 h2 行为不变）。
     */
    @Test
    public void testMonthYearCompileBehavior() {
        ICompiledSql compiledYear = new EqlCompiler().compile("test",
                "select o.id from AppUser o where year(o.name) = 2026", contextOf(dialectOf("h2")));
        assertTrue(compiledYear.getSql().getText().replaceAll("\\s+", "").toLowerCase().contains("year(o.name)"),
                "year must lower unchanged");
        // month 与 day/month/... 同为 Phase 3 注册的字段函数，h2 上 lowering
        ICompiledSql compiledMonth = new EqlCompiler().compile("test",
                "select o.id from AppUser o where month(o.name) = 1", contextOf(dialectOf("h2")));
        assertTrue(compiledMonth.getSql().getText().replaceAll("\\s+", "").toLowerCase().contains("month(o.name)"),
                "month must lower same as the other EXTRACT field functions");
    }

    /**
     * 手构方言：只注册 upper/current_timestamp，无 day/month/year 等字段函数。
     */
    static IDialect noDateFuncDialect() {
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
