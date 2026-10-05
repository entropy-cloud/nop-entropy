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
 * WI5 Phase 4：TRIM 三段式的编译层方言 lowering（parse→transform→generate 三层连通）。
 *
 * <p>正向：无 chars 三方向在 h2 输出 {@code trim/ltrim/rtrim(...)}；带 chars 在 h2 输出
 * 模板渲染文本 {@code trim(both 'x' from ...)}；duckdb 输出函数形态 {@code trim(o.name,'x')}。
 *
 * <p>负向：**带 chars 形态在无模板方言（dm——未注册 trimChars 族）上编译期显式报错**
 * （不允许静默丢 chars、不允许降级单参 trim）；无 chars 形态目标函数缺失方言响亮报错。
 * 单参 {@code trim(x)} 行为逐字不变（守护）。
 */
public class TestEqlTrimFunctionDialect {

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

    static String compileToSql(String eql, IDialect dialect) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, contextOf(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * h2 无 chars 三方向：既有单参目标函数（15/15 注册，零新增）。
     */
    @Test
    public void testH2NoCharsDirections() {
        assertEquals("trim(o.name)",
                compileToSql("select o.id from AppUser o where trim(both from o.name) = 'a'", dialectOf("h2"))
                        .replaceAll(".*?(trim\\(o\\.name\\)).*", "$1"));
        assertTrue(compileToSql("select o.id from AppUser o where trim(leading from o.name) = 'a'", dialectOf("h2"))
                .contains("ltrim(o.name)"));
        assertTrue(compileToSql("select o.id from AppUser o where trim(trailing from o.name) = 'a'", dialectOf("h2"))
                .contains("rtrim(o.name)"));
    }

    /**
     * h2 带 chars：模板渲染文本（h2 原生支持标准 TRIM(BOTH x FROM y) 语法）。
     */
    @Test
    public void testH2CharsTemplate() {
        String sql = compileToSql("select o.id from AppUser o where trim(both 'x' from o.name) = 'a'",
                dialectOf("h2"));
        assertTrue(sql.contains("trim(both'x'fromo.name)"), () -> "h2 chars template, got: " + sql);
        String leading = compileToSql("select o.id from AppUser o where trim(leading ' ' from o.name) = 'a'",
                dialectOf("h2"));
        // whitespace-strip 断言口径下字面量 ' ' 内的空格也被剥掉（trim(leading''from...)）——
        // 模板渲染正确性以 trim(leading 前缀 + fromo.name 尾段锁定
        assertTrue(leading.contains("trim(leading") && leading.contains("fromo.name)"),
                () -> "h2 leading template, got: " + leading);
    }

    /**
     * duckdb 无关键字形态：函数形态模板 trim(str, chars)（必须显式覆盖，否则继承
     * postgresql 的关键字模板产出非法 DuckDB SQL）。
     */
    @Test
    public void testDuckDbFunctionFormTemplate() {
        String sql = compileToSql("select o.id from AppUser o where trim(both 'x' from o.name) = 'a'",
                dialectOf("duckdb"));
        // duckdb 标识符带引号（o."name"）；函数形态 trim(str, chars)
        assertTrue(sql.contains("trim(o.\"name\",'x')"), () -> "duckdb function-form template, got: " + sql);
    }

    /**
     * 负向（硬性禁令）：带 chars 形态在未注册模板的方言（dm）上编译期显式报错
     * ERR_EQL_UNKNOWN_FUNCTION(trimChars)——不静默丢 chars、不静默降级单参 trim。
     */
    @Test
    public void testCharsFormFailsLoudOnUnregisteredDialect() {
        String eql = "select o.id from AppUser o where trim(both 'x' from o.name) = 'a'";
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", eql, contextOf(dialectOf("dm"))));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("trimChars", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 负向：无 chars 形态目标函数缺失的方言响亮报错（手构无 trim/ltrim/rtrim 方言）。
     */
    @Test
    public void testNoCharsFormFailsLoudWithoutTargetFunction() {
        String eql = "select o.id from AppUser o where trim(leading from o.name) = 'a'";
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", eql, contextOf(noTrimDialect())));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("ltrim", e.getParam(ARG_FUNC_NAME));
    }

    /**
     * 单参 trim(x) 行为逐字不变（既有 native trim 单参注册）。
     */
    @Test
    public void testSingleArgTrimUnchanged() {
        String sql = compileToSql("select o.id from AppUser o where trim(o.name) = 'a'", dialectOf("h2"));
        assertTrue(sql.contains("trim(o.name)"), () -> "single-arg trim must stay untouched, got: " + sql);
    }

    /**
     * 手构方言：只注册 upper/current_timestamp，无 trim 族。
     */
    static IDialect noTrimDialect() {
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
