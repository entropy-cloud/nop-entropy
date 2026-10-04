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
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
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
 * Plan 2308 Phase 4：ORDER BY 空序的方言化输出（编译通道）。
 *
 * <p>覆盖 D1 算法两个分支（原生直出 / CASE 改写）、CASE 四组合的**方向正确性**、
 * D5 的前置同级键位置，以及 D2/D3/D4 三条 fail-fast 路径的错误码。
 *
 * <p>不继承 {@link TestEqlCompileSql}：继承具体测试类会让父类全部 {@code @Test}
 * 在子类名下重跑，测试数虚增、失败定位变模糊。
 */
public class TestEqlNullsOrderCompile {

    static IDialect nativeDialect;
    static IDialect caseDialect;

    @BeforeAll
    public static void init() {
        nativeDialect = build(Boolean.TRUE);
        caseDialect = build(null); // 未声明 -> false -> CASE 改写路径
    }

    static IDialect build(Boolean supportNullsFirst) {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        if (supportNullsFirst != null)
            features.setSupportNullsFirst(supportNullsFirst);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造即调用 buildFunctionSql("current_timestamp")；其余为本类用例所需。
        // 未登记的函数名会在函数解析阶段先报 unknown-function，掩盖真正要测的逻辑，故必须齐备。
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);

        SqlNativeFunctionModel upper = new SqlNativeFunctionModel();
        upper.setName("upper");
        upper.setHasParenthesis(true);
        upper.setArgTypes(Collections.singletonList(StdSqlType.VARCHAR));
        upper.setReturnType(StdSqlType.VARCHAR);

        SqlNativeFunctionModel rowNumber = new SqlNativeFunctionModel();
        rowNumber.setName("row_number");
        rowNumber.setHasParenthesis(true);
        rowNumber.setReturnType(StdSqlType.BIGINT);
        rowNumber.setOnlyForWindowExpr(true);

        model.setFunctions(Arrays.asList(currentTimestamp, upper, rowNumber));
        return new DialectImpl(model);
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
        final IDialect dialect;

        Ctx(IDialect dialect) {
            this.dialect = dialect;
            OrmEntityModel m = new OrmEntityModel();
            m.setName("AppUser");
            m.setTableName("APP_USER");
            m.setQuerySpace("spaceA");
            m.setColumns(new ArrayList<>(Arrays.asList(
                    col("id", "SID", 1, true), col("name", "NAME", 2, false),
                    col("age", "AGE", 3, false))));
            m.init();
            entities.put("AppUser", m);
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

    static String sql(IDialect dialect, String eql) {
        return new EqlCompiler().compile("test", eql, new Ctx(dialect)).getSql().getText()
                .replaceAll("\\s+", " ").trim();
    }

    // ================= D1 分支一：原生直出 =================

    @Test
    public void testNativeDialectEmitsNullsClause() {
        String s = sql(nativeDialect, "select o.name from AppUser o order by o.age nulls first");
        assertTrue(s.contains("nulls first"), s);
        assertFalse(s.contains("case when"), "原生支持时不得输出 CASE 改写: " + s);
    }

    @Test
    public void testNativeDialectEmitsNullsLast() {
        String s = sql(nativeDialect, "select o.name from AppUser o order by o.age desc nulls last");
        assertTrue(s.contains("nulls last"), s);
        assertFalse(s.contains("case when"), s);
    }

    // ================= D1 分支二：CASE 改写 =================

    @Test
    public void testNonNativeDialectUsesCaseRewrite() {
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age nulls first");
        assertTrue(s.contains("case when"), "不支持原生语法时应走 CASE 改写: " + s);
        assertFalse(s.contains("nulls first"), "改写后不应再出现原生 NULLS 子句: " + s);
    }

    /**
     * CASE 四组合的**方向正确性**——这四个断言直接对应 Phase 0 实测的期望行序。
     * CASE 键方向与原方向相反（先按 nullness 分组，再按原列排）。
     */
    @Test
    public void testCaseRewriteAscNullsFirst() {
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age asc nulls first");
        assertTrue(s.contains("case when"), s);
        // null 在前 -> then 1（CASE 键 DESC 把 null 排到前）
        assertTrue(s.matches("(?s).*is null then 1 else 0 end desc.*"), s);
    }

    @Test
    public void testCaseRewriteAscNullsLast() {
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age asc nulls last");
        assertTrue(s.matches("(?s).*is null then 0 else 1 end desc.*"), s);
    }

    @Test
    public void testCaseRewriteDescNullsFirst() {
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age desc nulls first");
        assertTrue(s.matches("(?s).*is null then 0 else 1 end asc.*"), s);
    }

    @Test
    public void testCaseRewriteDescNullsLast() {
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age desc nulls last");
        assertTrue(s.matches("(?s).*is null then 1 else 0 end asc.*"), s);
    }

    @Test
    public void testCaseRewriteKeepsOriginalDirectionOnSecondKey() {
        // 第二个键（原列）必须保持原方向
        assertTrue(sql(caseDialect, "select o.name from AppUser o order by o.age asc nulls first")
                .contains("AGE asc"));
        assertTrue(sql(caseDialect, "select o.name from AppUser o order by o.age desc nulls last")
                .contains("AGE desc"));
    }

    // ================= D5：前置同级键位置 =================

    @Test
    public void testCaseKeyIsPrecedingSiblingNotListHead() {
        // 两个键，第一个需要改写：CASE 键必须紧邻第一个排序项之前，
        // 而不是被 prepend 到整个 ORDER BY 列表头（那会颠倒优先级）
        String s = sql(caseDialect, "select o.name from AppUser o order by o.age nulls first, o.name");
        int casePos = s.indexOf("case when");
        int agePos = s.indexOf("AGE");
        int namePos = s.lastIndexOf("NAME");
        assertTrue(casePos >= 0 && agePos > casePos, "CASE 键应在 AGE 之前: " + s);
        assertTrue(namePos > agePos, "第二个排序项 NAME 应在 AGE 之后: " + s);
    }

    @Test
    public void testOnlySecondItemRewrittenKeepsFirstItemFirst() {
        // 第一个键不改写、第二个键改写：CASE 键必须出现在第二个键之前，不能跑到列表头。
        // 用「ORDER BY 段内首次出现 NAME」定位，而非全文 indexOf——投影里的 NAME 恒在
        // ORDER BY 之前，用全文匹配会让本断言恒真（空断言）。
        String s = sql(caseDialect, "select o.name from AppUser o order by o.name, o.age nulls last");
        int orderByPos = s.indexOf("order by");
        int namePos = s.indexOf("NAME", orderByPos);
        int casePos = s.indexOf("case when");
        assertTrue(namePos >= 0 && casePos > namePos,
                "CASE 键必须位于第一个排序项 NAME 之后（不得 prepend 到列表头）: " + s);
    }

    @Test
    public void testMixedNativeAndCaseItems() {
        // 混合：一个键原生、一个键改写，各自独立判定
        String s = sql(caseDialect, "select o.name from AppUser o order by o.name, o.age nulls last");
        assertFalse(s.contains("nulls last"), "改写项不应出现原生子句: " + s);
        assertTrue(s.contains("case when"), s);
    }

    // ================= 防误伤 =================

    @Test
    public void testNoNullsClauseOutputUnchanged() {
        // 未写 NULLS 时，产物不得出现 case / nulls
        for (IDialect d : new IDialect[]{nativeDialect, caseDialect}) {
            String s = sql(d, "select o.name from AppUser o order by o.age desc");
            assertFalse(s.contains("nulls"), "未请求空序时不得输出 nulls: " + s);
            assertFalse(s.contains("case when"), "未请求空序时不得输出 CASE: " + s);
        }
    }

    @Test
    public void testUnrelatedQueryUnaffected() {
        String s = sql(caseDialect, "select o.name from AppUser o where o.id = '1'");
        assertFalse(s.contains("nulls"), s);
        assertFalse(s.contains("case when"), s);
    }

    // ================= D2 fail-fast：非可复制表达式 =================

    @Test
    public void testNonColumnExpressionFailsFast() {
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select o.name from AppUser o order by upper(o.name) nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("expression-not-duplicable", e.getParam("reason"));
    }

    @Test
    public void testNumericLiteralSortFailsFast() {
        // order by 1 是序号定位，复制后排序失效 -> 必须失败而非产出错误 SQL
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select o.name from AppUser o order by 1 nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("expression-not-duplicable", e.getParam("reason"));
    }

    @Test
    public void testFunctionExpressionAllowedOnNativeDialect() {
        // 原生方言不需要复制表达式，故 upper(...) 合法
        String s = sql(nativeDialect, "select o.name from AppUser o order by upper(o.name) nulls last");
        assertTrue(s.contains("nulls last"), s);
    }

    // ================= D3 fail-fast：DISTINCT =================

    @Test
    public void testDistinctWithUnprojectedSortColumnFailsFast() {
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select distinct o.name from AppUser o order by o.age nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("distinct-not-supported", e.getParam("reason"));
    }

    @Test
    public void testDistinctWithProjectedSortColumnStillFailsFast() {
        // 实测 PostgreSQL 16：即便排序列已在投影列表中，注入的 CASE 仍是新的 ORDER BY
        // 表达式，PG 报 `for SELECT DISTINCT, ORDER BY expressions must appear in select list`。
        // 故「排序列已投影」只是必要条件，改写路径对 DISTINCT 一律 fail-fast。
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select distinct o.age from AppUser o order by o.age nulls last"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("distinct-not-supported", e.getParam("reason"));
    }

    @Test
    public void testDistinctOnNativeDialectUnaffected() {
        // 原生路径不受 DISTINCT 限制
        String s = sql(nativeDialect, "select distinct o.name from AppUser o order by o.age nulls last");
        assertTrue(s.contains("nulls last"), s);
    }

    // ================= D4 fail-fast：窗口上下文 =================

    @Test
    public void testNullsOrderInsideOverFailsFast() {
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select row_number() over (order by o.age nulls last) from AppUser o"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("window-not-supported", e.getParam("reason"));
    }

    @Test
    public void testNullsOrderInsideNamedWindowFailsFast() {
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select row_number() over w from AppUser o window w as (order by o.age nulls last)"));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("window-not-supported", e.getParam("reason"));
    }

    @Test
    public void testWindowWithoutNullsStillWorks() {
        // 未写 NULLS 的窗口查询不受影响（不得被新 fail-fast 误伤）
        String s = sql(caseDialect, "select row_number() over (order by o.age desc) from AppUser o");
        assertTrue(s.contains("over"), s);
        assertFalse(s.contains("case when"), s);
    }

    @Test
    public void testQueryLevelNullsOrderAllowedEvenWithWindow() {
        // 查询级 ORDER BY 带 NULLS、查询里另有窗口函数 -> 允许（窗口上下文判定只看 ORDER BY 项自身）
        String s = sql(caseDialect,
                "select row_number() over (order by o.id) from AppUser o order by o.age nulls last");
        assertTrue(s.contains("case when"), s);
    }

    // ================= 错误码自检 =================

    @Test
    public void testErrorCodeCarriesDialectParam() {
        NopException e = assertThrows(NopException.class, () ->
                sql(caseDialect, "select o.name from AppUser o order by upper(o.name) nulls last"));
        assertEquals("DialectImpl", e.getParam("dialect"));
    }
}