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
 * WI2 BUG-6：`supportFullJoin` 编译期能力门（`EqlTransformVisitor.checkJoinTypeFeature`，
 * 挂 `visitTableSource` 的 SqlJoinTableSource 分支、先门后遍历）。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi2-class-b-adjudications.md` §三）：
 * 复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + 新常量 `FEATURE_SUPPORT_FULL_JOIN`；
 * 不支持时响亮报错，绝不静默改写或丢弃 join 类型（"只打印不设门 = 净回归"）。
 * mysql/mariadb XML → accessor 链由 nop-dao 的 `TestDialectFullJoinFlag` 验证；
 * 本类用两值方言模型证明 flag 值真实改变编译行为（接线验证）。
 */
public class TestEqlFullJoinDialectGate {
    static final String EQL = "select d.id from AppDept d full outer join AppRole r on d.id = r.id";

    static IDialect unsupportedDialect;
    static IDialect supportedDialect;

    @BeforeAll
    public static void init() {
        unsupportedDialect = buildDialect(false);
        supportedDialect = buildDialect(true);
    }

    static IDialect buildDialect(boolean supportFullJoin) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        DialectFeatures features = new DialectFeatures();
        features.setSupportFullJoin(supportFullJoin);
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

    @Test
    public void testFullOuterJoinThrowsAtCompileTimeOnUnsupportedDialect() {
        // 负向用例：supportFullJoin=false 时编译期报错，断言错误码与 feature param
        // （dialect param 的值 = DialectImpl.getName()，经方言 XML resourcePath 推导，
        // 真方言载体由 nop-dao TestDialectFullJoinFlag 验证）
        NopException e = assertThrows(NopException.class, () -> sqlOf(unsupportedDialect, EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportFullJoin", e.getParam("feature"));
    }

    @Test
    public void testFullJoinWithoutOuterAlsoGated() {
        // `full join`（无 OUTER）与 full outer join 同属 FULL_JOIN 枚举，同受门控
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(unsupportedDialect, "select d.id from AppDept d full join AppRole r on d.id = r.id"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportFullJoin", e.getParam("feature"));
    }

    @Test
    public void testNestedFullJoinAlsoGated() {
        // 嵌套 join（左递归：((d join r) full join d2)）——内层非 full join 过门不改行为，
        // 外层 full join 经 visitJoinLeft 递归回 visitTableSource 后同样过门报错
        String nested = "select d.id from AppDept d join AppRole r on d.id = r.userId "
                + "full outer join AppDept d2 on r.userId = d2.id";
        NopException e = assertThrows(NopException.class, () -> sqlOf(unsupportedDialect, nested));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportFullJoin", e.getParam("feature"));
    }

    @Test
    public void testSupportedDialectOutputsFullJoinText() {
        // 正向用例：supportFullJoin=true 时正常输出 full join 文本（既有 getJoinType().getText() 路径）
        String sql = sqlOf(supportedDialect, EQL);
        assertTrue(sql.contains("full join"), sql);
        assertTrue(sql.contains("APP_DEPT"), sql);
        assertTrue(sql.contains("APP_ROLE"), sql);
    }

    @Test
    public void testFlagValueReallyChangesCompileBehavior() {
        // 接线验证（Minimum Rules #23）：同一查询在 supportFullJoin 两个取值下行为不同
        String sql = sqlOf(supportedDialect, EQL);
        assertTrue(sql.contains("full join"), sql);

        NopException e = assertThrows(NopException.class, () -> sqlOf(unsupportedDialect, EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
    }
}
