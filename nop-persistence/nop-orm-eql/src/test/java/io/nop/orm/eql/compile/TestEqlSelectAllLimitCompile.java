/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 BUG-4 执行路径回归：BUG-4 仅是回显缺陷，不波及执行。
 *
 * <p>`select *` 在执行路径经 `EqlTransformVisitor` 展开为实体列投影；limit 经
 * `AstToSqlGenerator.visitSqlQuerySelect` → `dialect.getPaginationHandler().buildPageExpr(...)`
 * 消费（在外层追加 LIMIT/OFFSET 文本）。修复引入的 `printLimit` 回显钩子在
 * `AstToSqlGenerator` override 置空——本测试钉死分页内嵌渲染路径（`printWrappedNode`
 * → `printSelect`）不会二次打印 LIMIT（双 LIMIT 回归锚）。
 */
public class TestEqlSelectAllLimitCompile {
    static IDialect dialect;

    @BeforeAll
    public static void init() {
        dialect = buildDialect();
    }

    static IDialect buildDialect() {
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

    static String sqlOf(String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, baseContext());
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect = dialect;
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

    static TestCompileContext baseContext() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppDept", entity("AppDept", "APP_DEPT",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    @Test
    public void testSelectAllCompilesToExpandedColumnsWithSingleLimit() {
        ICompiledSql compiled = new EqlCompiler().compile("test", "select * from AppDept d limit 10 offset 5",
                baseContext());
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");

        // selectAll 展开为实体列（不产出裸 *）
        assertFalse(sql.contains("*"), sql);
        assertTrue(sql.contains("SID"), sql);
        assertTrue(sql.contains("NAME"), sql);
        assertTrue(sql.contains("APP_DEPT"), sql);
        // 分页文本只出现一份（printLimit 置空 override 的双 LIMIT 回归锚）
        assertEquals(1, countOccurrences(sql, "LIMIT"), sql);
        assertEquals(1, countOccurrences(sql, "OFFSET"), sql);
        assertTrue(sql.contains("LIMIT 10"), sql);
        assertTrue(sql.contains("OFFSET 5"), sql);
    }

    @Test
    public void testLimitTranslationBehaviorUnchanged() {
        // 与既有 TestEqlTranslationSemantics.testLimitOffsetTranslation 同口径：行为不变
        String sql = sqlOf("select o.name from AppDept o order by o.id limit 10 offset 5");
        assertTrue(sql.contains("LIMIT"), sql);
        assertTrue(sql.contains("OFFSET"), sql);
    }

    static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
