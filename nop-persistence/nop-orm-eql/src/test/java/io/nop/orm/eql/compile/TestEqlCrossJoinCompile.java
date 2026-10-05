/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CROSS JOIN} 的编译通道行为（add/add 合并：ours WI1 BUG-10 与 master Plan 2307
 * Phase 3/V2 对同一缺陷的双侧测试并集，harness 取 master 侧增强版并补 ours 实体集）。
 *
 * <p>缺陷背景（两分支各自独立认定）：<code>cross</code> 被吃成左表别名后，源查询里对原
 * 别名的引用会报 <code>unknown-column-name</code>；且 EqlTransformVisitor.markMainSource
 * 仅对 LEFT/RIGHT/FULL 分派，CROSS_JOIN 需走 no-op 分支不崩溃。本类断言
 * 「cross join 之后仍可用原别名引用左表」——这正是修复前会失败的点（危害高于语法拒绝）。
 *
 * <p>【merge note】ours 原用例 {@code testEntityJoinWithoutConditionStillRejected} 断言
 * 实体间 {@code cross join} 无 ON 时报 ERR_EQL_JOIN_NO_CONDITION——那是修复前的既有行为；
 * master Plan 2307 V2b 已将该检查对 CROSS_JOIN 显式豁免（"实体 join 漏写 on"拦截对
 * 按定义无连接条件的 CROSS JOIN 是误报，EqlTransformVisitor 内注释为准），本合并按
 * 新契约改写为 {@code testEntityCrossJoinWithoutConditionAllowed}。
 *
 * <p>自带最小 harness 而非继承 {@link TestEqlCompileSql}：继承具体测试类会让父类全部
 * {@code @Test} 在子类名下重跑，测试数虚增、失败定位变模糊。
 */
public class TestEqlCrossJoinCompile {

    static IDialect dialect;

    @BeforeAll
    public static void init() {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        features.setSupportReturningForUpdate(true);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造时即调用 buildFunctionSql("current_timestamp")，
        // 故 current_timestamp 必须登记，否则 dialect 构造本身就 NPE
        model.setFunctions(Arrays.asList(
                nativeFunc("current_timestamp", false, StdSqlType.TIMESTAMP),
                nativeFunc("upper", true, StdSqlType.VARCHAR)));
        dialect = new DialectImpl(model);
    }

    static io.nop.dao.dialect.model.SqlNativeFunctionModel nativeFunc(String name, boolean hasParenthesis,
                                                                      StdSqlType returnType) {
        io.nop.dao.dialect.model.SqlNativeFunctionModel fn =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        fn.setName(name);
        fn.setHasParenthesis(hasParenthesis);
        fn.setReturnType(returnType);
        return fn;
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

    static TestCompileContext ctx() {
        TestCompileContext c = new TestCompileContext();
        // ours 用例集需要 AppDept（子查询右源用例）；master 用例集需要 AppUser/AppRole
        c.entities.put("AppDept", entity("AppDept", "APP_DEPT",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        c.entities.put("AppUser", entity("AppUser", "APP_USER",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        c.entities.put("AppRole", entity("AppRole", "APP_ROLE",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false)));
        return c;
    }

    static String sql(String eql) {
        return new EqlCompiler().compile("test", eql, ctx()).getSql().getText()
                .replaceAll("\\s+", " ").trim();
    }

    static class TestCompileContext implements ISqlCompileContext {
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

    // ---- ours WI1 BUG-10：实体编译路径 + 子查询右源 ----

    @Test
    public void testCrossJoinEntityCompilePathProducesSql() {
        // EqlCompiler 全链路：markMainSource 对 CROSS_JOIN 走 no-op 不崩溃，
        // 产出 SQL 文本含 cross join（右侧为子查询表源）
        String sql = sql("select d.id from AppDept d cross join (select r.id from AppRole r) x");
        assertTrue(sql.contains("cross join"), sql);
        assertTrue(sql.contains("APP_DEPT"), sql);
    }

    /**
     * 【merge 改写】原 ours 用例断言本形态报 ERR_EQL_JOIN_NO_CONDITION（修复前行为）；
     * master Plan 2307 V2b 对 CROSS_JOIN 豁免该检查（按定义即笛卡尔积、无连接条件），
     * 改为断言编译成功且产物含 cross join。租户/逻辑删除过滤改落 WHERE
     * （AstToSqlGenerator.printJoin 的 CROSS_JOIN 分支），不再产 `cross join ... on ...`。
     */
    @Test
    public void testEntityCrossJoinWithoutConditionAllowed() {
        String s = sql("select d.name from AppDept d cross join AppRole r");
        assertTrue(s.contains("cross join"), s);
        assertFalse(s.contains("cross join APP_ROLE on"), "CROSS JOIN 不得带 ON: " + s);
    }

    // ---- master Plan 2307 Phase 3/V2：核心别名存活断言 ----

    @Test
    public void testCrossJoinKeepsLeftAliasUsableInWhere() {
        // 修复前：cross 被吃成 a 的别名，此处的 u 会报 unknown-column-name
        String s = sql("select u.name from AppUser u cross join AppRole r where u.id = r.userId");
        assertTrue(s.contains("cross join"), "应输出 CROSS JOIN: " + s);
        assertTrue(s.contains("SID"), s);
    }

    @Test
    public void testCrossJoinLeftAliasResolvesInProjection() {
        String s = sql("select u.name from AppUser u cross join AppRole r");
        assertTrue(s.contains("cross join"), s);
        assertTrue(s.contains("NAME"), "左表投影必须解析成功: " + s);
    }

    @Test
    public void testCrossJoinEmitsNoPhantomAlias() {
        // 修复前输出 `from APP_USER as cross` —— cross 不该成为别名
        String s = sql("select u.name from AppUser u cross join AppRole r");
        assertFalse(s.contains("as cross"), "不得输出幻觉别名 as cross: " + s);
    }

    @Test
    public void testCrossJoinIsCaseInsensitiveInSource() {
        for (String kw : new String[]{"cross join", "CROSS JOIN", "Cross Join"}) {
            String s = sql("select u.name from AppUser u " + kw + " AppRole r");
            assertTrue(s.contains("cross join"), kw + " -> " + s);
            assertFalse(s.contains("as cross"), kw + " -> " + s);
        }
    }

    @Test
    public void testCrossJoinInSqlOutput() {
        String s = sql("select u.name from AppUser u, AppRole r cross join AppRole r2");
        assertTrue(s.contains("cross join"), s);
    }

    // ---- 防误伤 ----

    @Test
    public void testPlainJoinOutputUnchanged() {
        // 普通 join 的产物形态不得被 CROSS_JOIN 修复波及
        assertEquals("select u.NAME as c1 from APP_USER u join APP_ROLE r on u.SID = r.USER_ID",
                sql("select u.name from AppUser u join AppRole r on u.id = r.userId"));
    }

    @Test
    public void testLeftJoinOutputUnchanged() {
        assertEquals("select u.NAME as c1 from APP_USER u left join APP_ROLE r on u.SID = r.USER_ID",
                sql("select u.name from AppUser u left join AppRole r on u.id = r.userId"));
    }

    @Test
    public void testCrossAsColumnNameStillCompilable() {
        // cross 登记为非保留字，故名为 cross 的列应可被引用（此处断言不因词法而失败）
        TestCompileContext c = new TestCompileContext();
        OrmEntityModel e = entity("CrossEntity", "CROSS_ENTITY", col("cross", "CROSS_VAL", 1, true));
        c.entities.put("CrossEntity", e);
        String s = new EqlCompiler().compile("test", "select o.cross from CrossEntity o", c)
                .getSql().getText().replaceAll("\\s+", " ").trim();
        assertTrue(s.contains("CROSS_VAL"), s);
    }

    @Test
    public void testCrossAsTableAliasStillCompilable() {
        // 无 join 时 cross 仍可作表别名
        String s = sql("select cross.name from AppUser cross");
        assertTrue(s.contains("NAME"), s);
    }
}
