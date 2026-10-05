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
import io.nop.dao.dialect.model.SqlDataTypeModel;
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
 * WI9b（plan 32 Phase 2）：CAST LOCAL 时区拼写的编译/生成层端到端测试（Anti-Hollow 主证据：
 * EQL 文本 → 解析 → 闸门 A → 生成器 → `stdToNativeSqlType` → 方言 native 文本 / 响亮报错）。
 *
 * <p>载体与期望文本按裁定文档
 * `ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md` §六载体表：
 * "支持"载体 = oracle（其真实方言 XML 条目按 Phase 3 逐方言独立提交落地，本文件先以
 * **裁定同构的测试方言**承载端到端正向断言——native 名 `TIMESTAMP WITH LOCAL TIME ZONE`
 * 与裁定条目逐字同形，期望渲染文本相同）；"不支持"载体 = h2（真方言，§2.1 实测 H2 2.4.240
 * 无 LOCAL 宿主）+ 手工最小方言（WI2 先例）。`TIME_WITH_LOCAL_TIME_ZONE` 按裁定逐成员
 * 全负（全方言无原生宿主），仅有负向响亮报错用例。
 *
 * <p>反向映射（native→std 反查）断言随 Phase 3 的 oracle XML 提交（`getNativeType` 需真实
 * 方言模型）。
 */
public class TestEqlCastTimezoneTypeCompile {

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

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    /**
     * 与 Phase 0 裁定 oracle 条目逐字同形的测试方言：`<sqlDataType name="TIMESTAMP WITH
     * LOCAL TIME ZONE" stdSqlType="TIMESTAMP_WITH_LOCAL_TIME_ZONE"/>`（零撞名键，
     * 裁定文档 §2.4/§六）。期望渲染文本与 Phase 3 的真实 oracle 载体一致。
     */
    static IDialect tzMappedDialect() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        SqlDataTypeModel tz = new SqlDataTypeModel();
        tz.setName("TIMESTAMP WITH LOCAL TIME ZONE");
        tz.setStdSqlType(StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE);
        model.setSqlDataTypes(Collections.singletonList(tz));
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
    }

    /**
     * 手工最小方言：无任何 sqlDataType 映射（WI2 TestEqlCastBooleanCompile 先例；
     * 不用 es 伪方言/db2）。
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

    // ---- 正向端到端：多词拼写 → 闸门 A → 生成器 → 裁定 native 文本 ----

    @Test
    public void testSpelledTimestampWithLocalTimeZoneRendersAdjudicatedNativeName() {
        String sql = sqlOf(tzMappedDialect(), "select cast(o.id as timestamp with local time zone) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as TIMESTAMP WITH LOCAL TIME ZONE)"), sql);
    }

    @Test
    public void testUnderscoreSpellingRendersIdentically() {
        // 约束 4 一致性：下划线拼写（sqlIdentifier_ 兜底）与多词拼写同方言输出逐字一致
        assertEquals(
                sqlOf(tzMappedDialect(), "select cast(o.id as timestamp_with_local_time_zone) from AppDept o"),
                sqlOf(tzMappedDialect(), "select cast(o.id as timestamp with local time zone) from AppDept o"));
    }

    @Test
    public void testPrecisionAfterSuffixRendersSameAsBare() {
        // precision 后置形态可解析（parse 层实测）但按成员 (false,false) 不渲染 precision——
        // 与 bare 形态输出逐字一致
        assertEquals(
                sqlOf(tzMappedDialect(), "select cast(o.id as timestamp with local time zone) from AppDept o"),
                sqlOf(tzMappedDialect(), "select cast(o.id as timestamp with local time zone(6)) from AppDept o"));
    }

    @Test
    public void testLowercaseEchoNormalizedToEnumName() {
        // 回显路径打印 AST 归一化枚举名（AstToEqlGenerator 打印 name 字段）
        String sql = sqlOf(tzMappedDialect(), "select cast(o.id as timestamp with local time zone) from AppDept o");
        assertTrue(sql.contains("TIMESTAMP WITH LOCAL TIME ZONE"), sql);
    }

    // ---- 负向响亮报错：无映射方言解析过、生成期响亮报错（错误码字面值断言） ----

    @Test
    public void testH2TimestampMemberFailsLoudly() {
        // h2 真方言：§2.1 实测 2.4.240 无 LOCAL 宿主 ⇒ 无映射，不静默降级
        NopException e = assertThrows(NopException.class, () -> sqlOf(dialectOf("h2"),
                "select cast(o.id as timestamp with local time zone) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testH2TimeMemberFailsLoudly() {
        // TIME 成员逐成员全负（裁定 §六）：含 h2 在内的全部方言均无宿主
        NopException e = assertThrows(NopException.class, () -> sqlOf(dialectOf("h2"),
                "select cast(o.id as time with local time zone) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testOracleTimeMemberFailsLoudly() {
        // oracle：TIMESTAMP 变体有宿主（Phase 3 条目），TIME 变体无宿主（Oracle 无任何
        // TIME-only 类型，§2.2 一手文档）⇒ 约束 4 不等价类载体
        NopException e = assertThrows(NopException.class, () -> sqlOf(dialectOf("oracle"),
                "select cast(o.id as time with local time zone) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMinimalDialectFailsLoudlyForBothMembers() {
        for (String spelling : Arrays.asList("timestamp with local time zone", "time with local time zone")) {
            NopException e = assertThrows(NopException.class,
                    () -> sqlOf(minimalDialect(), "select cast(o.id as " + spelling + ") from AppDept o"),
                    spelling);
            assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testTzMappedDialectTimeMemberStillFailsLoudly() {
        // 映射方言只映射 TIMESTAMP 成员（裁定条目），TIME 成员仍响亮报错——证明"支持面
        // 逐成员精确"，无静默家族级降级
        NopException e = assertThrows(NopException.class, () -> sqlOf(tzMappedDialect(),
                "select cast(o.id as time with local time zone) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }
}
