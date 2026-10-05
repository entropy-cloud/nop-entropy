/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-2：`LATERAL` 子查询表源 + `supportLateral` 能力门。
 *
 * <p>缺陷（修复前）：{@code AstToEqlGenerator.visitSqlSubqueryTableSource} 从不读
 * {@code getLateral()}，{@code AstToSqlGenerator} 也无 override —— `lateral (sub)` 与
 * `(sub)` 输出字节相同，静默退化为不相关子查询，零信号。
 *
 * <p>修复后（裁定 = WI1 裁决文档 §4）：回显路径无损打印 `lateral`（基类，不受方言开关
 * 影响）；执行路径按方言门控 —— `supportLateral=false`（含未配置的保守缺省）时编译期
 * 报错，复用 {@code ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE}；`true` 时输出 ` lateral `。
 */
public class TestEqlLateralSubqueryTableSource extends BaseTestCase {
    static final String EQL = "select x.id from (select o.id from AppUser o) x, "
            + "lateral (select r.id from AppRole r where r.userId = x.id) y";

    static IDialect lateralDialect;
    static IDialect noLateralDialect;

    @BeforeAll
    public static void init() {
        lateralDialect = buildDialect(true);
        noLateralDialect = buildDialect(false);
    }

    static DialectModel newDialectModel(boolean supportLateral) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        io.nop.dao.dialect.model.DialectFeatures features = new io.nop.dao.dialect.model.DialectFeatures();
        features.setSupportLateral(supportLateral);
        model.setFeatures(features);
        return model;
    }

    static IDialect buildDialect(boolean supportLateral) {
        DialectModel model = newDialectModel(supportLateral);
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
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

    static TestCompileContext baseContext() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        ctx.entities.put("AppRole", entity("AppRole", "APP_ROLE",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        TestCompileContext ctx = baseContext();
        ctx.effectiveDialect = dialect;
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, ctx);
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect;
        boolean enableFilter;

        TestCompileContext() {
            this.effectiveDialect = lateralDialect;
        }

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

    @Test
    public void testLateralDroppedInExecutionPath() {
        // 修复前红：lateral 被静默丢弃，输出与去掉 lateral 字节相同
        String sql = sqlOf(lateralDialect, EQL);
        assertTrue(sql.contains(" lateral ("), sql);
    }

    @Test
    public void testEchoPathPrintsLateralLosslessly() {
        // 回显路径（基类）无损打印 lateral，不受方言开关影响
        SqlProgram program = new EqlASTParser().parseFromText(null, EQL);
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertTrue(echo.contains(" lateral ("), echo);
    }

    @Test
    public void testUnsupportedDialectThrowsAtCompileTime() {
        // 负向用例：supportLateral=false 时编译期报错，不允许静默丢掉 lateral 关键字
        NopException e = assertThrows(NopException.class, () -> sqlOf(noLateralDialect, EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportLateral", e.getParam("feature"));
    }

    @Test
    public void testFlagValueReallyChangesExecutionPathOutput() {
        // 接线验证（Minimum Rules #23）：同一查询在开关两个值下行为不同 ——
        // true 输出 lateral 文本，false 编译期报错，证明 XML 配置值 → retention →
        // accessor → 生成器行为的运行时链路连通
        assertTrue(sqlOf(lateralDialect, EQL).contains(" lateral ("));
        assertThrows(NopException.class, () -> sqlOf(noLateralDialect, EQL));
    }
}
