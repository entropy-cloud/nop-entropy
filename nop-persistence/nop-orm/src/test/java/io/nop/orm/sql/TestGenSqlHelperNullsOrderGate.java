/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2309（closure audit MAJOR-3）：{@code GenSqlHelper} 两个 ORDER BY 产出点的空序门控守护。
 *
 * <p><b>为什么单独写这个类</b>：closure audit 发现 {@code GenSqlHelper.genOrderBy} 与
 * {@code GenSqlHelper.appendOrderBy} 这两个被修复的生产点<b>没有任何测试覆盖其 ORDER BY 产物</b>——
 * 把它们改回裸 {@code sb.nullsFirst(...)} 不会让任何测试转红。已有的
 * {@code TestBeanNullsOrderGate} 驱动的是 {@code DaoQueryHelper} 与
 * {@code GenSqlHelper.appendNullsOrder}（单元），并非这两个调用点；
 * {@code TestEqlNullsOrder*} 走的是 {@code AstToSqlGenerator}。三者互不覆盖。
 *
 * <p>{@code appendOrderBy} 还是 {@code TdSqlHelper:256} 的实际调用点，而
 * {@code tdengine.dialect.xml} 未声明 {@code supportNullsFirst}（回落 false），
 * 即该方言在本计划中从「原生直出」变成「CASE 改写」——必须有测试钉住。
 *
 * <p>断言取<b>方向与取值</b>（能区分方向写反），而非仅「含 case when」。
 */
public class TestGenSqlHelperNullsOrderGate {

    static IDialect nativeDialect;
    static IDialect caseDialect;
    static OrmEntityModel entity;

    @BeforeAll
    public static void init() {
        nativeDialect = dialect(true);
        caseDialect = dialect(false);
        entity = new OrmEntityModel();
        entity.setName("GateEntity");
        entity.setTableName("T_GATE");
        entity.setQuerySpace("spaceA");
        List<OrmColumnModel> cols = new ArrayList<>();
        cols.add(col("id", "ID", 1, true));
        cols.add(col("score", "SCORE", 2, false));
        entity.setColumns(cols);
        entity.init();
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(io.nop.commons.type.StdSqlType.INTEGER);
        c.setStdDataType(io.nop.commons.type.StdDataType.INT);
        c.setPrimary(primary);
        return c;
    }

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
        fn.setReturnType(io.nop.commons.type.StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(fn));
        return new DialectImpl(model);
    }

    static List<OrderFieldBean> order(boolean desc, Boolean nullsFirst) {
        OrderFieldBean f = desc ? OrderFieldBean.desc("score") : OrderFieldBean.asc("score");
        f.setNullsFirst(nullsFirst);
        return Collections.singletonList(f);
    }

    static String norm(SQL.SqlBuilder sb) {
        return sb.getText().replaceAll("\\s+", " ").trim();
    }

    // ---------- appendOrderBy（TdSqlHelper 的实际调用点） ----------

    static String appendOrderBy(IDialect d, boolean desc, Boolean nullsFirst) {
        SQL.SqlBuilder sb = SQL.begin();
        GenSqlHelper.appendOrderBy(sb, d, "o", entity, order(desc, nullsFirst));
        return norm(sb);
    }

    static String genOrderBy(IDialect d, boolean desc, Boolean nullsFirst) {
        SQL.SqlBuilder sb = SQL.begin();
        GenSqlHelper.genOrderBy(sb, d, entity, "o", order(desc, nullsFirst));
        return norm(sb);
    }

    @Test
    public void testAppendOrderByNativeEmitsNullsClause() {
        assertTrue(appendOrderBy(nativeDialect, false, true).contains("nulls first"));
        assertTrue(appendOrderBy(nativeDialect, true, false).contains("nulls last"));
    }

    @Test
    public void testAppendOrderByNonNativeRewritesFourCombinations() {
        // 键方向 = asc 的反方向；then 值 = (nullsFirst == asc) ? 1 : 0
        assertTrue(appendOrderBy(caseDialect, false, true)
                .contains("case when o.SCORE is null then 1 else 0 end desc, o.SCORE asc"),
                appendOrderBy(caseDialect, false, true));
        assertTrue(appendOrderBy(caseDialect, false, false)
                .contains("then 0 else 1 end desc"));
        assertTrue(appendOrderBy(caseDialect, true, true)
                .contains("then 0 else 1 end asc"));
        assertTrue(appendOrderBy(caseDialect, true, false)
                .contains("then 1 else 0 end asc"));
    }

    @Test
    public void testAppendOrderByNonNativeNeverEmitsNativeClause() {
        for (boolean desc : new boolean[]{false, true}) {
            for (boolean nf : new boolean[]{true, false}) {
                String sql = appendOrderBy(caseDialect, desc, nf);
                assertFalse(sql.contains("nulls first") || sql.contains("nulls last"),
                        "非原生方言不得输出原生 NULLS 子句: " + sql);
                assertEquals(2, count(sql, "o.SCORE"), "改写须把排序列写两遍: " + sql);
            }
        }
    }

    // ---------- genOrderBy ----------

    @Test
    public void testGenOrderByNativeEmitsNullsClause() {
        assertTrue(genOrderBy(nativeDialect, false, true).contains("nulls first"));
        assertTrue(genOrderBy(nativeDialect, true, false).contains("nulls last"));
    }

    @Test
    public void testGenOrderByNonNativeRewritesFourCombinations() {
        assertTrue(genOrderBy(caseDialect, false, true)
                .contains("case when o.SCORE is null then 1 else 0 end desc, o.SCORE asc"),
                genOrderBy(caseDialect, false, true));
        assertTrue(genOrderBy(caseDialect, false, false).contains("then 0 else 1 end desc"));
        assertTrue(genOrderBy(caseDialect, true, true).contains("then 0 else 1 end asc"));
        assertTrue(genOrderBy(caseDialect, true, false).contains("then 1 else 0 end asc"));
    }

    @Test
    public void testGenOrderByNonNativeNeverEmitsNativeClause() {
        for (boolean desc : new boolean[]{false, true}) {
            for (boolean nf : new boolean[]{true, false}) {
                String sql = genOrderBy(caseDialect, desc, nf);
                assertFalse(sql.contains("nulls first") || sql.contains("nulls last"), sql);
                assertEquals(2, count(sql, "o.SCORE"), sql);
            }
        }
    }

    // ---------- 未请求空序：产物逐字不变 ----------

    @Test
    public void testNoNullsRequestUnchangedForBothSites() {
        for (IDialect d : new IDialect[]{nativeDialect, caseDialect}) {
            for (String sql : new String[]{appendOrderBy(d, false, null), genOrderBy(d, false, null)}) {
                assertFalse(sql.contains("nulls"), sql);
                assertFalse(sql.contains("case when"), sql);
            }
        }
    }

    static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}