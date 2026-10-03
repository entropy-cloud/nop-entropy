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
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
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
 * WI10b（Phase 0 §2.5 终裁）：`supportRightJoin` 编译期能力门。
 *
 * <p>门形态：`EqlTransformVisitor#checkJoinTypeFeature` 扩展 RIGHT_JOIN 分支（与 WI2 的
 * FULL_JOIN 同门同层），`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + 新常量
 * `FEATURE_SUPPORT_RIGHT_JOIN`。负向载体 = 合成方言 false（全部既有方言 XML 继承
 * default 的 true——RIGHT JOIN 为全支持标准能力，无任何真实方言应配 false 的一手证据，
 * 裁定规则点名取合成方言，`TestEqlTranslationSemantics#buildDialect` 先例）。
 * XML → 模型 → accessor 连通由 nop-dao `TestDialectDeadFlagDisposition` 验证。
 */
public class TestEqlRightJoinDialectGate {
    static final String EQL = "select d.id from AppDept d right outer join AppRole r on d.id = r.id";

    static IDialect unsupportedDialect;
    static IDialect supportedDialect;

    @BeforeAll
    public static void init() {
        unsupportedDialect = buildDialect(false);
        supportedDialect = buildDialect(true);
    }

    static IDialect buildDialect(boolean supportRightJoin) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        DialectFeatures features = new DialectFeatures();
        features.setSupportRightJoin(supportRightJoin);
        model.setFeatures(features);
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

    static TestCompileContext baseContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppDept", entity("AppDept", "APP_DEPT",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        ctx.entities.put("AppRole", entity("AppRole", "APP_ROLE",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, baseContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    @Test
    public void testRightJoinThrowsAtCompileTimeOnUnsupportedDialect() {
        // 负向用例：supportRightJoin=false 时编译期报错，断言错误码与 feature param
        NopException e = assertThrows(NopException.class, () -> sqlOf(unsupportedDialect, EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportRightJoin", e.getParam("feature"));
    }

    @Test
    public void testRightJoinWithoutOuterAlsoGated() {
        // `right join`（无 OUTER）与 right outer join 同属 RIGHT_JOIN 枚举，同受门控
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(unsupportedDialect, "select d.id from AppDept d right join AppRole r on d.id = r.id"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportRightJoin", e.getParam("feature"));
    }

    @Test
    public void testRightJoinCompilesOnSupportingDialect() {
        // 正向用例：true 方言编译通过且 join 类型保真输出（不静默改写）
        String sql = sqlOf(supportedDialect, EQL);
        assertTrue(sql.contains("right join"), sql);
    }

    @Test
    public void testLeftJoinUnaffectedByRightJoinGate() {
        // 守护：left join 不受 right join 门影响（只门 RIGHT_JOIN 枚举值）
        String sql = sqlOf(unsupportedDialect, "select d.id from AppDept d left join AppRole r on d.id = r.id");
        assertTrue(sql.contains("left join"), sql);
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
}
