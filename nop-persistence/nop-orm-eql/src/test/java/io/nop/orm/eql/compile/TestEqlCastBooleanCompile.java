/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
 * WI2 BUG-8：`cast(x as boolean)` 全链验证（文法放开后的编译/生成层）。
 *
 * <p>正向载体钉死在 h2（已配映射：`h2.dialect.xml` 的
 * {@code <sqlDataType name="BOOLEAN" stdSqlType="BOOLEAN"/>}）：
 * `cast(o.id as boolean)` → 生成 {@code cast(o.SID as BOOLEAN)}。
 *
 * <p>方言边界声明：无 BOOLEAN 映射的方言（手构最小 dialect，空 dataTypes 映射——
 * {@code TestEqlTranslationSemantics.buildDialect()} 先例；禁用 es 伪方言红线；db2 自
 * WI3 补 mandatory 子节点后可加载但无 sqlDataTypes 段且无一手文档，按 WI9a 裁定
 * §2.1 不入载体）在 {@code stdToNativeSqlType} 处响亮报
 * {@code ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED}——此类方言状态是"解析已过、
 * 生成期显式报错"，不属"可生成"缺陷回归，不做 lowering（G-1 已采纳 Oracle 23ai
 * 为承诺基线，无版本门代码）。
 */
public class TestEqlCastBooleanCompile {

    @BeforeAll
    public static void init() {
        // DialectManager 加载 h2.dialect.xml 需要 dialect 模型类型注册（TestH2WindowFrameCompileEndToEnd 先例）
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

    static IDialect h2Dialect() {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect, "h2 dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    /**
     * 手工最小 dialect：无任何 sqlDataType 映射（TestEqlTranslationSemantics.buildDialect()
     * 先例、空 dataTypes 映射）。不得用 es（伪方言红线）；db2 可加载但无
     * sqlDataTypes 段且无一手文档，不入载体（WI9a 裁定 §2.1，勘正原"不可实例化"表述）。
     */
    static IDialect minimalDialectWithoutBooleanMapping() {
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

    @Test
    public void testH2BooleanCastGeneratesNativeText() {
        // 正向文本钉死：h2 已配 BOOLEAN 映射，cast → cast(o.SID as BOOLEAN)
        String sql = sqlOf(h2Dialect(), "select cast(o.id as boolean) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as BOOLEAN)"), sql);
    }

    @Test
    public void testH2UppercaseBooleanSameResult() {
        String sql = sqlOf(h2Dialect(), "select cast(o.id as BOOLEAN) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as BOOLEAN)"), sql);
    }

    @Test
    public void testNoBooleanMappingDialectFailsLoudlyAtGeneration() {
        // 无 BOOLEAN 映射的方言：解析已过、生成期显式报错（不静默、不做 lowering）
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(minimalDialectWithoutBooleanMapping(), "select cast(o.id as boolean) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMinimalDialectStillCastsMappedTypes() {
        // 对照组：同一最小方言对有内建支持的 cast 形态（VARCHAR 无 precision 直印）仍可用，
        // 证明负向用例的失败点确为 BOOLEAN 映射缺失而非方言构造错误
        String sql = sqlOf(minimalDialectWithoutBooleanMapping(), "select cast(o.id as varchar) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as VARCHAR)"), sql);
    }
}
