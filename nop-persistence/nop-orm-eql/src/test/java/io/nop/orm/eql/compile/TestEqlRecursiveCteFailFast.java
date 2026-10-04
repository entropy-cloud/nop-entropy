/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 5 / V6：递归 CTE 与投影的未判空强转加固。
 *
 * <p>修复前的实测缺陷：{@code with recursive t as (select 1 union all select 2 from t) select 1 from t}
 * 抛<strong>裸 {@link NullPointerException}</strong>（{@code Cannot invoke
 * "ISqlExprMeta.getColumnNames()" because "exprMeta" is null}）——无错误码、无定位。
 * 即 EQL 的递归 CTE 当前整体不可用。
 *
 * <p>根因在 <code>AstToSqlGenerator#appendWithColumns</code>：<code>WITH RECURSIVE</code> 且
 * CTE 体是 <code>UNION</code> 时要为 H2 生成 columns 段，该方法对左支 projection 逐项取
 * {@code getResolvedExprMeta()} 后<strong>无判空</strong>即调 {@code getColumnNames()}。
 *
 * <p>本类断言：加固后同一输入得到<strong>带错误码</strong>的失败，而不是裸 NPE。
 *
 * <p>不继承 {@link TestEqlCompileSql}：继承具体测试类会让父类全部 {@code @Test} 在子类名下
 * 重跑，测试数虚增、失败定位变模糊。
 */
public class TestEqlRecursiveCteFailFast {

    static IDialect dialect;

    @BeforeAll
    public static void init() {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造即调用 buildFunctionSql("current_timestamp")，缺该函数会 NPE
        io.nop.dao.dialect.model.SqlNativeFunctionModel fn =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        fn.setName("current_timestamp");
        fn.setHasParenthesis(false);
        fn.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(fn));
        dialect = new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static class Ctx implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();

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
            return false;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return dialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            OrmEntityModel m = entities.get(entityName);
            return m == null ? null : new EntityTableMeta(m, null, dialect);
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    static Ctx ctx() {
        Ctx c = new Ctx();
        OrmEntityModel m = new OrmEntityModel();
        m.setName("AppUser");
        m.setTableName("APP_USER");
        m.setQuerySpace("spaceA");
        m.setColumns(new ArrayList<>(Arrays.asList(
                col("id", "SID", 1, true), col("name", "NAME", 2, false))));
        m.init();
        c.entities.put("AppUser", m);
        return c;
    }

    static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    static void assertFailsLoudly(String label, String eql) {
        Throwable t = catchThrowable(() -> new EqlCompiler().compile("test", eql, ctx()));
        assertNotNull(t, label + "：该查询必须失败");
        assertFalse(t instanceof NullPointerException,
                label + "：不得抛裸 NullPointerException（无错误码、无定位），实际: " + t);
        assertTrue(t instanceof NopException,
                label + "：必须是带错误码的 NopException，实际: " + t.getClass().getName());
    }

    // ---- 核心：不得抛裸 NPE ----

    @Test
    public void testRecursiveCteLiteralLeftBranchFailsLoudly() {
        assertFailsLoudly("字面量左支",
                "with recursive t as (select 1 union all select 2 from t) select 1 from t");
    }

    @Test
    public void testRecursiveCteSelectAllLeftBranchFailsLoudly() {
        assertFailsLoudly("select * 左支",
                "with recursive t as (select * from AppUser o union all select 1 from t) select 1 from t");
    }

    @Test
    public void testRecursiveCteOwnerAllLeftBranchFailsLoudly() {
        assertFailsLoudly("owner.* 左支",
                "with recursive t as (select o.* from AppUser o union all select 1 from t) select 1 from t");
    }

    @Test
    public void testRecursiveCteColumnLeftBranchFailsLoudly() {
        // 该形态止于 unknown-alias（递归 CTE 自引用别名未注册），本身已是带错误码的失败
        assertFailsLoudly("列引用左支",
                "with recursive t as (select o.name from AppUser o union all select o.name from t) select 1 from t");
    }

    // ---- 防误伤：普通查询与非递归 CTE 不受影响 ----

    @Test
    public void testPlainSelectStillCompiles() {
        ICompiledSql cs = new EqlCompiler().compile("test", "select o.name from AppUser o", ctx());
        assertTrue(cs.getSql().getText().contains("NAME"), cs.getSql().getText());
    }

    @Test
    public void testSelectAllStillCompiles() {
        // 顶层 select * 必须照常展开（V6 的强转加固不得放宽这条主路径）
        ICompiledSql cs = new EqlCompiler().compile("test", "select * from AppUser o", ctx());
        assertTrue(cs.getSql().getText().contains("SID"), cs.getSql().getText());
        assertTrue(cs.getSql().getText().contains("NAME"), cs.getSql().getText());
    }

    @Test
    public void testNonRecursiveCteStillCompiles() {
        ICompiledSql cs = new EqlCompiler().compile("test",
                "with t as (select o.name as n from AppUser o) select t.n from t", ctx());
        assertNotNull(cs.getSql());
    }
}