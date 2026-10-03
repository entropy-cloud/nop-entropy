/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.nop.dao.DaoErrors.ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9a：CAST 类型别名编译/生成层测试（`TEXT→CLOB`/`NUMBER→DECIMAL`/`BOOL→BOOLEAN`）。
 *
 * <p>载体与期望文本按裁定文档
 * `ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md` §六
 * 载体表执行——期望文本按 `dialect.stdToNativeSqlType` 实际链路推导
 * （std 键 → 该方言条目列表中首个非 deprecated 且 allowPrecision 匹配项），不按直觉：
 * h2 的 std DECIMAL 唯一映射条目是 native `NUMERIC` ⇒ `cast(x as number)` 在 h2 渲染
 * {@code NUMERIC}；oracle（唯一条目 native `NUMBER`）才渲染 {@code NUMBER}。
 *
 * <p>两类用例（roadmap 约束 4）：一致性（同义拼写同方言输出一致：text≡clob、
 * number≡decimal、bool≡boolean）+ 不等价（跨方言支持面/渲染差异：h2 CLOB vs
 * postgresql TEXT、h2 NUMERIC vs oracle NUMBER、mssql 无 CLOB 映射响亮报错）。
 *
 * <p>UUID 的方言正/负向用例见 {@code TestEqlCastUuidDialectCompile}。
 */
public class TestEqlCastTypeAliasCompile {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setStdDataType(StdDataType.STRING);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace("spaceA");
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static TestCompileContext contextOf(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppDept", entity("AppDept", "APP_DEPT",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, contextOf(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect;
        boolean enableFilter;

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return false;
        }

        @Override
        public boolean isEnableFilter() {
            return enableFilter;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return effectiveDialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            OrmEntityModel m = entities.get(entityName);
            if (m == null)
                return null;
            return new EntityTableMeta(m, null, getDialectForQuerySpace(m.getQuerySpace()));
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    /**
     * 手工最小 dialect：无任何 sqlDataType 映射（WI2 TestEqlCastBooleanCompile 先例）。
     * 不得用 es（伪方言红线）/db2（无 sqlDataTypes 段且无一手文档，裁定文档 §2.1 不入载体）。
     */
    static IDialect minimalDialect() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
    }

    // ---- TEXT→CLOB ----

    @Test
    public void testH2TextCastGeneratesClob() {
        String sql = sqlOf(dialectOf("h2"), "select cast(o.id as text) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as CLOB)"), sql);
    }

    @Test
    public void testH2TextAndClobConsistency() {
        // 约束 4 一致性：同义拼写同方言输出一致
        assertEquals(sqlOf(dialectOf("h2"), "select cast(o.id as clob) from AppDept o"),
                sqlOf(dialectOf("h2"), "select cast(o.id as text) from AppDept o"));
    }

    @Test
    public void testPostgresqlTextRendersNativeText() {
        // 约束 4 不等价：pg 的 std CLOB 首选条目是 native TEXT（渲染与 h2 的 CLOB 不同）。
        // pg 方言对小写标识符（o.sid/app_dept）与别名 as 风格与 h2 不同 ⇒ 键部分小写比对
        String sql = sqlOf(dialectOf("postgresql"), "select cast(o.id as text) from AppDept o");
        assertTrue(sql.toLowerCase().contains("cast(o.sid as text)"), sql);
    }

    @Test
    public void testMssqlTextFailsLoudlyNoClobMapping() {
        // mssql 真方言无 CLOB 映射：解析过、生成期响亮报错（不静默降级/改写）
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("mssql"), "select cast(o.id as text) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    // ---- NUMBER→DECIMAL（2026-10-04 G-3 增补裁决目标） ----

    @Test
    public void testH2NumberCastRendersNumericNotDecimal() {
        // 链路推导期望文本：h2 std DECIMAL 唯一映射条目是 native NUMERIC ⇒ 渲染 NUMERIC 而非 DECIMAL。
        // 带 precision/scale 形态（bare decimal 形态因 SqlTypeExpr.getScale() 缺省 0 会渲染
        // NUMERIC(-1,0)——既有渲染行为，number 与 decimal 逐字一致，见下方一致性用例，不在本用例断言）
        String sql = sqlOf(dialectOf("h2"), "select cast(o.id as number(10,2)) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as NUMERIC(10,2))"), sql);
    }

    @Test
    public void testH2NumberAndDecimalConsistency() {
        assertEquals(sqlOf(dialectOf("h2"), "select cast(o.id as decimal) from AppDept o"),
                sqlOf(dialectOf("h2"), "select cast(o.id as number) from AppDept o"));
    }

    @Test
    public void testOracleNumberRendersNativeNumber() {
        // 约束 4 不等价：oracle 的 std DECIMAL 首选条目是 native NUMBER
        // （oracle 标识符小写渲染 ⇒ 键部分小写比对）
        String sql = sqlOf(dialectOf("oracle"), "select cast(o.id as number(10,2)) from AppDept o");
        assertTrue(sql.toLowerCase().contains("cast(o.sid as number(10,2))"), sql);
    }

    @Test
    public void testH2BareNumberAndBareDecimalRenderIdentically() {
        // bare 形态（无 precision/scale）同样逐字一致——既有 scale=0 渲染行为对两者同型
        assertEquals(sqlOf(dialectOf("h2"), "select cast(o.id as decimal) from AppDept o"),
                sqlOf(dialectOf("h2"), "select cast(o.id as number) from AppDept o"));
    }

    // ---- BOOL→BOOLEAN ----

    @Test
    public void testH2BoolCastGeneratesBoolean() {
        String sql = sqlOf(dialectOf("h2"), "select cast(o.id as bool) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as BOOLEAN)"), sql);
    }

    @Test
    public void testH2BoolAndBooleanConsistency() {
        assertEquals(sqlOf(dialectOf("h2"), "select cast(o.id as boolean) from AppDept o"),
                sqlOf(dialectOf("h2"), "select cast(o.id as bool) from AppDept o"));
    }

    // ---- 无映射方言响亮报错（手工最小方言，WI2 先例） ----

    @Test
    public void testMinimalDialectNumberAndBoolFailLoudlyAtGeneration() {
        assertThrows(NopException.class,
                () -> sqlOf(minimalDialect(), "select cast(o.id as number) from AppDept o"));
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(minimalDialect(), "select cast(o.id as bool) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMinimalDialectStillCastsMappedTypes() {
        // 对照组：同一最小方言对 VARCHAR 仍可用（生成器内建分支），证明失败点确为映射缺失
        String sql = sqlOf(minimalDialect(), "select cast(o.id as varchar) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as VARCHAR)"), sql);
    }
}
