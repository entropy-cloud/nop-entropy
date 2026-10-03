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
 * WI5 Phase 2：{@code SUBSTRING(x FROM a [FOR b])} 的编译层方言 lowering
 * （parse→transform→generate 三层连通，Minimum Rules #23）。
 *
 * <p>正向：h2 的 2 参与 3 参两种输出（substr 多态）；mssql 的 substr realName=substring
 * 换名输出。
 *
 * <p>负向（EC ② 双条）：未注册 substr 的方言报 {@code ERR_EQL_UNKNOWN_FUNCTION}；
 * 把 maxArgCount 压到 2 的方言上 3 参形态报 {@code ERR_EQL_FUNC_TOO_MANY_ARGS}。
 * 既有逗号形态 {@code substring(x,a,b)} 行为不变：解析通过（name=substring）、未注册
 * 仍然响亮报错（守护用例）。
 */
public class TestEqlSubstringFunctionDialect {
    static final String EQL2 = "select o.id from AppUser o where substring(o.name from 2) = 'b'";
    static final String EQL3 = "select o.id from AppUser o where substring(o.name from 2 for 3) = 'b'";

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
     * h2 的 2 参形态：substr(name,2)（minArgCount=2 多态下限）。
     */
    @Test
    public void testH2TwoArgSubstr() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL2, contextOf(dialectOf("h2")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("substr(o.name,2)"), () -> "h2 two-arg substr, got: " + sql);
    }

    /**
     * h2 的 3 参形态：substr(name,2,3)（多态上限）。
     */
    @Test
    public void testH2ThreeArgSubstr() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL3, contextOf(dialectOf("h2")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("substr(o.name,2,3)"), () -> "h2 three-arg substr, got: " + sql);
    }

    /**
     * realName 方言：mssql substr→substring（SUBSTRING(expr,start,len) 语义同序）。
     */
    @Test
    public void testMssqlRealNameSubstring() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL3, contextOf(dialectOf("mssql")));
        String sql = compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("substring(o.name,2,3)"), () -> "mssql must emit realName substring, got: " + sql);
    }

    /**
     * 负向一：无 substr 的方言编译期 ERR_EQL_UNKNOWN_FUNCTION（funcName=substr）。
     */
    @Test
    public void testUnknownDialectThrowsLoudUnknownFunction() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL3, contextOf(noSubstrDialect())));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("substr", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 负向二（元数边界）：方言把 substr 的 maxArgCount 压到 2 时，3 参形态报
     * ERR_EQL_FUNC_TOO_MANY_ARGS——归一化后的 3 参走既有元数校验通路。
     */
    @Test
    public void testArgCountUpperBoundStillEnforced() {
        IDialect dialect = twoArgOnlySubstrDialect();
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", EQL3, contextOf(dialect)));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e.getErrorCode());
        assertEquals("substr", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 既有逗号形态守护：substring(x,a,b) 解析为 name=substring 的普通函数调用，未注册
     * 方言仍响亮报错（行为与放开前一致，不允许静默改道 substr）。
     */
    @Test
    public void testLegacyCommaFormStillLoud() {
        String eql = "select o.id from AppUser o where substring(o.name, 2, 3) = 'b'";
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", eql, contextOf(noSubstrDialect())));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("substring", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 手构方言：只注册 upper/current_timestamp，无 substr（DialectImpl 构造器需要
     * current_timestamp，先例 TestEqlCompileSql.init）。
     */
    static IDialect noSubstrDialect() {
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

    /**
     * 手构方言：substr 压到 maxArgCount=2（2/3 元多态的收口边界）。
     */
    static IDialect twoArgOnlySubstrDialect() {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel substr = new SqlNativeFunctionModel();
        substr.setName("substr");
        substr.setArgTypes(Arrays.asList(StdSqlType.VARCHAR, StdSqlType.INTEGER));
        substr.setReturnType(StdSqlType.VARCHAR);
        substr.setMinArgCount(2);
        substr.setMaxArgCount(2);

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);

        model.setFunctions(Arrays.asList(substr, currentTimestamp));
        return new DialectImpl(model);
    }
}
