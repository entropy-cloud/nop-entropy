/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.OrmErrors;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.sql.GenSqlHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2309 Phase 4：bean 通道空序门控的<b>常驻守护</b>。
 *
 * <p>存在理由：门控失效时<b>不会报错</b>——它只会让 SQL 在 MySQL 等库上报语法错误，
 * 而那要等到真实部署才暴露。把方言取值改错、或把门控条件短路掉，都属于「静默降级」，
 * 故须由本测试钉住，使漂移在 CI 就暴露。
 *
 * <p>已做的变异验证（证明本测试非空断言）：
 * <ul>
 *   <li>把 {@code GenSqlHelper.appendNullsOrder} 的门控短路为恒真 → Phase 2 的 MySQL E2E 8/9 转红</li>
 *   <li>把 {@code resolveDialectName} 改回 {@code null} → Phase 3 的 ORM 接线测试 4/5 转红</li>
 * </ul>
 * 本类补的是第三条：<b>方言取值</b>本身被改错时能否被发现。
 */
public class TestBeanNullsOrderGate {

    static IDialect dialect(boolean supportNullsFirst) {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        features.setSupportNullsFirst(supportNullsFirst);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        SqlNativeFunctionModel fn = new SqlNativeFunctionModel();
        fn.setName("current_timestamp");
        fn.setHasParenthesis(false);
        fn.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(fn));
        return new DialectImpl(model);
    }

    static List<OrderFieldBean> order(boolean desc, Boolean nullsFirst) {
        OrderFieldBean f = desc ? OrderFieldBean.desc("SCORE") : OrderFieldBean.asc("SCORE");
        f.setNullsFirst(nullsFirst);
        List<OrderFieldBean> list = new ArrayList<>();
        list.add(f);
        return list;
    }

    static String build(IDialect dialect, boolean desc, Boolean nullsFirst) {
        SQL.SqlBuilder sb = SQL.begin();
        DaoQueryHelper.appendOrderBy(sb, dialect, "o", order(desc, nullsFirst));
        return sb.getText().replaceAll("\\s+", " ").trim();
    }

    // ---- 原生方言：直出 ----

    @Test
    public void testNativeDialectEmitsNullsClause() {
        assertTrue(build(dialect(true), false, true).contains("nulls first"));
        assertTrue(build(dialect(true), true, false).contains("nulls last"));
    }

    // ---- 非原生方言：改写，且绝不出现原生子句 ----

    @Test
    public void testNonNativeDialectRewritesAndNeverEmitsNativeClause() {
        for (boolean desc : new boolean[]{false, true}) {
            for (boolean nf : new boolean[]{true, false}) {
                String sql = build(dialect(false), desc, nf);
                assertFalse(sql.contains("nulls first") || sql.contains("nulls last"),
                        "非原生方言不得输出原生 NULLS 子句（该语法在 MySQL 上是语法错误）: " + sql);
                assertTrue(sql.contains("case when"), "应改写为 CASE 排序键: " + sql);
            }
        }
    }

    /** 改写必须出现两次列引用：CASE 键一次、原排序列一次 */
    @Test
    public void testRewriteDuplicatesColumnReference() {
        String sql = build(dialect(false), false, true);
        assertEquals(2, count(sql, "o.SCORE"), "改写须把排序列写两遍: " + sql);
    }

    static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    /** 未请求空序时产物逐字不变（回归锚点） */
    @Test
    public void testNoNullsRequestProducesNoCaseRewrite() {
        for (boolean nativeSupport : new boolean[]{true, false}) {
            String sql = build(dialect(nativeSupport), false, null);
            assertFalse(sql.contains("case when"), sql);
            assertFalse(sql.contains("nulls"), sql);
        }
    }

    // ---- 不可复制表达式：响亮失败 ----

    @Test
    public void testBindParameterFailsFastWithReason() {
        IDialect d = dialect(false);
        NopException e = assertThrows(NopException.class, () ->
                GenSqlHelper.appendNullsOrder(SQL.begin(), d, "case when X = ? then 0 else 1 end", false, true));
        assertEquals(OrmErrors.ERR_ORM_NULLS_ORDER_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        assertEquals("bind-parameter-not-duplicable", e.getParam("reason"));
    }

    @Test
    public void testEmptyColumnFailsFastWithReason() {
        IDialect d = dialect(false);
        NopException e = assertThrows(NopException.class, () ->
                GenSqlHelper.appendNullsOrder(SQL.begin(), d, "", false, true));
        assertEquals("empty-expression", e.getParam("reason"));
    }

    /** 原生方言下不可复制的表达式**不需要**复制，故直接放行 */
    @Test
    public void testNativeDialectDoesNotRequireDuplication() {
        SQL.SqlBuilder sb = SQL.begin();
        GenSqlHelper.appendNullsOrder(sb, dialect(true), "case when X = ? then 0 else 1 end", false, true);
        assertTrue(sb.getText().contains("nulls first"), sb.getText());
    }

    /** 门控不可被静默跳过：失败必须带错误码与 reason */
    @Test
    public void testFailureCarriesErrorCodeAndReason() {
        try {
            GenSqlHelper.appendNullsOrder(SQL.begin(), dialect(false), "?", false, true);
            org.junit.jupiter.api.Assertions.fail("应当抛异常而非静默产出");
        } catch (NopException e) {
            assertEquals(OrmErrors.ERR_ORM_NULLS_ORDER_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
            assertTrue(e.getParam("reason") != null, "reason 必须非空");
        }
    }

    /**
     * 能力位语义回归：{@code null}（未声明）必须等价 false。
     * 若某天 DialectImpl 把未声明当 true，未实测方言会集体产出非法 SQL。
     */
    @Test
    public void testUndeclaredCapabilityBehavesAsFalse() {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures()); // 不设置 supportNullsFirst
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        SqlNativeFunctionModel fn = new SqlNativeFunctionModel();
        fn.setName("current_timestamp");
        fn.setHasParenthesis(false);
        fn.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(fn));
        IDialect undeclared = new DialectImpl(model);
        assertFalse(undeclared.isSupportNullsFirst(), "未声明必须回落 false");
        assertTrue(build(undeclared, false, true).contains("case when"),
                "未声明方言须走改写路径");
    }
}