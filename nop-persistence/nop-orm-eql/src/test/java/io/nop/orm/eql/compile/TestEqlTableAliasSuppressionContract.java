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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10b（Phase 0 §3.1 终裁：改档=档 2 行为契约登记）：`supportUpdateTableAlias`/
 * `supportDeleteTableAlias` 的 EQL 实际行为是**"接受并归一化"**——文法与 transform 均消费
 * 别名（`o.col` 可解析），打印期统一抑制别名输出（单表 UPDATE/DELETE 语义无损）。
 *
 * <p>编译期门已被裁定否决：全部方言 false ⇒ 门=拒绝今天语义无损可解析的写法，纯 BC
 * 破坏零收益（非静默错误——输出 SQL 语义正确）。打印器按 flag 分支输出的形态亦被否决
 * （审查 Major-4：静默抑制违反无静默跳过）。本测试锁定该契约：**flag 翻转不改变输出**
 * （false 与 true 方言产出逐字相同的 SQL——证明无按 flag 的打印分支，也无按 flag 的
 * 编译期拒绝），别名写法在 false 方言上照常编译。
 */
public class TestEqlTableAliasSuppressionContract {
    static IDialect falseDialect;
    static IDialect trueDialect;

    @BeforeAll
    public static void init() {
        falseDialect = buildDialect(false);
        trueDialect = buildDialect(true);
    }

    static IDialect buildDialect(boolean supportAlias) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        DialectFeatures features = new DialectFeatures();
        features.setSupportUpdateTableAlias(supportAlias);
        features.setSupportDeleteTableAlias(supportAlias);
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
     * UPDATE 别名在 false 方言（全部既有方言的现值）上照常编译：别名被接受、
     * o.col 正常解析、输出统一抑制别名（语义无损归一化）。
     */
    @Test
    public void testUpdateAliasAcceptedAndSuppressedOnFalseDialect() {
        String sql = sqlOf(falseDialect, "update AppDept o set o.name = 'x' where o.id = 1");
        assertTrue(sql.contains("update APP_DEPT"), sql);
        assertTrue(sql.contains("set NAME= 'x'"), "alias-qualified column resolved: " + sql);
        assertTrue(sql.contains("where SID = 1"), sql);
        assertFalse(sql.toUpperCase().contains(" APP_DEPT O") || sql.contains("as o"),
                "alias suppressed in output: " + sql);
    }

    /**
     * 契约锁定：flag=true 方言产出与 false 方言**逐字相同**的 SQL——
     * 证明既无按 flag 的打印分支（无静默抑制路径），也无按 flag 的编译期拒绝。
     */
    @Test
    public void testOutputIdenticalRegardlessOfFlag() {
        String fromFalse = sqlOf(falseDialect, "update AppDept o set o.name = 'x' where o.id = 1");
        String fromTrue = sqlOf(trueDialect, "update AppDept o set o.name = 'x' where o.id = 1");
        assertEquals(fromFalse, fromTrue, "printer must not branch on the flag");

        String delFalse = sqlOf(falseDialect, "delete from AppDept o where o.id = 1");
        String delTrue = sqlOf(trueDialect, "delete from AppDept o where o.id = 1");
        assertEquals(delFalse, delTrue, "printer must not branch on the flag");
    }

    /**
     * DELETE 别名同样"接受并归一化"。
     */
    @Test
    public void testDeleteAliasAcceptedAndSuppressedOnFalseDialect() {
        String sql = sqlOf(falseDialect, "delete from AppDept o where o.id = 1");
        assertTrue(sql.contains("delete"), sql);
        assertTrue(sql.contains("APP_DEPT"), sql);
        assertTrue(sql.contains("where SID = 1"), sql);
        assertFalse(sql.contains("as o") || sql.contains(" O where"), "alias suppressed: " + sql);
    }

    /**
     * 别名作用域真实可用：where 中经别名引用列正常解析（非 only 抑制——transform 期
     * `makeTableAlias` 注册进 table scope 的既有消费被保留）。
     */
    @Test
    public void testAliasScopedColumnResolutionWorks() {
        String sql = sqlOf(falseDialect, "update AppDept o set o.name = o.name where o.id = 1");
        assertTrue(sql.contains("set NAME=NAME"), sql);
    }

    /**
     * 无别名写法不受任何影响（回归守护）。
     */
    @Test
    public void testNoAliasFormUnchanged() {
        String sql = sqlOf(falseDialect, "update AppDept set name = 'x' where id = 1");
        assertTrue(sql.contains("update APP_DEPT set NAME= 'x' where SID = 1"), sql);
    }

    /**
     * 既有 returning 门不因本处置变化（回归守护——别名处置零行为变更的直接证据）。
     */
    @Test
    public void testReturningGateStillEnforced() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(falseDialect, "update AppDept o set o.name = 'x' where o.id = 1 returning o.name"));
        assertEquals("nop.err.eql.dialect-not-support-feature", e.getErrorCode());
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
