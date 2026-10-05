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
 * WI10b（Phase 0 §2.6 终裁）：`supportRowValueConstructor` 编译期能力门。
 *
 * <p>可达性实测（裁定文档）：`sqlExpr_primary` 含 `sqlMultiValueExpr`
 * （BaseRule.g4:196），SqlMultiValueExpr 三形态全部可达且无条件打印——
 * ① `(a,b)` 独立值表达式；② `(a,b) = (1,'x')` 真行值构造器比较；
 * ③ `(a,b) IN ((1,'x'))`（IN 列表元素本身是行组）。门 =
 * `EqlTransformVisitor#visitSqlMultiValueExpr` 覆写（transform 遍历点统一拦截三形态），
 * `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `FEATURE_SUPPORT_ROW_VALUE_CONSTRUCTOR`。
 * 与 IN 列表长度门正交（不同节点、不同职责）。全部既有方言 XML 继承 default 的 true
 * ⇒ 负向载体 = 合成方言 false（无任何真实方言应配 false 的一手证据）。
 */
public class TestEqlRowValueConstructorGate {
    static IDialect supportedDialect;
    static IDialect unsupportedDialect;

    @BeforeAll
    public static void init() {
        supportedDialect = buildDialect(true);
        unsupportedDialect = buildDialect(false);
    }

    static IDialect buildDialect(boolean supportRowValueConstructor) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        DialectFeatures features = new DialectFeatures();
        features.setSupportRowValueConstructor(supportRowValueConstructor);
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
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, baseContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    /**
     * 形态②（真行值构造器比较）：true 方言编译通过，行组形态保真输出。
     */
    @Test
    public void testRowValueComparisonCompilesOnSupportingDialect() {
        String sql = sqlOf(supportedDialect, "select o.id from AppDept o where (o.id, o.name) = (1, 'a')");
        assertTrue(flat(sql).contains("(o.SID,o.NAME)=(1,'a')"), sql);
    }

    /**
     * 形态② 负向：false 方言编译期报错（feature=supportRowValueConstructor）。
     */
    @Test
    public void testRowValueComparisonThrowsOnUnsupportedDialect() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(unsupportedDialect, "select o.id from AppDept o where (o.id, o.name) = (1, 'a')"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportRowValueConstructor", e.getParam("feature"));
    }

    /**
     * 形态③（行组作 IN 列表元素）：同一门拦截——IN 列表长度门只数外层元素个数，
     * 行组语义由本门负责（裁定 §2.6 的正交性证明）。
     */
    @Test
    public void testRowGroupAsInListElementThrowsOnUnsupportedDialect() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(unsupportedDialect, "select o.id from AppDept o where o.id in ((1, 'a'))"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("supportRowValueConstructor", e.getParam("feature"));
    }

    /**
     * 形态③ 正向：true 方言下 IN 列表中的行组照常编译（且不触发 IN 长度门——单元素）。
     */
    @Test
    public void testRowGroupAsInListElementCompilesOnSupportingDialect() {
        String sql = sqlOf(supportedDialect, "select o.id from AppDept o where o.id in ((1, 'a'))");
        assertTrue(flat(sql).contains("o.SIDin((1,'a'))"), sql);
    }

    static String flat(String sql) {
        return sql.replaceAll("\\s+", "");
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
