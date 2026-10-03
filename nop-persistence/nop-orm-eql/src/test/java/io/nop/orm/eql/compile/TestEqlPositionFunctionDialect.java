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
 * WI5 Phase 1：{@code POSITION(x IN y)} 的编译层方言 lowering（parse→transform→generate
 * 三层连通，Minimum Rules #23 接线验证）。
 *
 * <p>正向：h2 输出 {@code instr(o.name,'b')}（native 换序）；duckdb 输出 {@code strpos(...)}
 * （realName 换名）；mssql 输出 {@code charindex('b',o.name)}（模板内换序）。
 *
 * <p>负向（EC ②）：未注册 {@code instr} 的方言编译期响亮 {@code ERR_EQL_UNKNOWN_FUNCTION}
 * ——错误 funcName 是归一化名 {@code instr}（非用户原文 position）；不允许静默按原文输出
 * {@code position(...)}。既有逗号形态 {@code position(x,y)} 行为不变：解析通过、未注册
 * 仍然响亮报错（守护用例）。
 */
public class TestEqlPositionFunctionDialect {
    static final String EQL = "select o.id from AppUser o where position('b' in o.name) > 0";

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
     * WI0 快照既有事实的守护：instr 在 h2 已注册（15/15 之一），本 Phase 零新增注册。
     */
    @Test
    public void testH2LowersToInstrWithSwappedArgs() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, contextOf(dialectOf("h2")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("instr(o.name,'b')"), () -> "h2 must lower to instr with swapped args, got: " + sql);
    }

    /**
     * realName 方言：duckdb instr→strpos（换名输出）。
     */
    @Test
    public void testDuckDbLowersToStrposRealName() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, contextOf(dialectOf("duckdb")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        // duckdb 标识符带引号（o."name"）；instr→strpos 换名 + instr(str, substr) 同序
        assertTrue(sql.contains("strpos(o.\"name\",'b')"), () -> "duckdb must lower to strpos, got: " + sql);
    }

    /**
     * 模板方言：mssql instr 模板 charindex({1},{0}) 自带换序。
     */
    @Test
    public void testMssqlLowersToCharindexTemplate() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, contextOf(dialectOf("mssql")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("charindex('b',o.name)"), () -> "mssql must lower to charindex template, got: " + sql);
    }

    /**
     * 负向：无 instr 的方言编译期 ERR_EQL_UNKNOWN_FUNCTION，且 funcName 参数为归一化名 instr。
     */
    @Test
    public void testUnknownDialectThrowsLoudUnknownFunction() {
        IDialect dialect = noInstrDialect();
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL, contextOf(dialect)));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("instr", e.getParam(ARG_FUNC_NAME),
                "error funcName must be the normalized lowering target name, not the source syntax name");
    }

    /**
     * 既有逗号形态守护：position(x,y) 解析通过，但在未注册方言仍响亮报错（行为不变）。
     */
    @Test
    public void testLegacyCommaFormStillLoudOnUnregisteredDialect() {
        String eql = "select o.id from AppUser o where position(o.name, 'b') > 0";
        IDialect dialect = noInstrDialect();
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", eql, contextOf(dialect)));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("position", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 手构最小方言：只注册 upper/current_timestamp，无 instr（先例 TestEqlCompileSql.init；
     * current_timestamp 为 DialectImpl 构造器 buildFunctionSql("current_timestamp") 所必需）。
     */
    static IDialect noInstrDialect() {
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
