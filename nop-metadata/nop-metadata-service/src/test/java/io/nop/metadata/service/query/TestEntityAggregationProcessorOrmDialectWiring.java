/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.IDialect;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan 2309 Phase 3 接线验证：证明 {@code EntityAggregationProcessor} 的 ORM（via-EQL）路径
 * <b>确实解析出真实方言并传给 {@code buildOrderByClause}</b>，而不是仍然传 null。
 *
 * <p><b>为什么必须单独写这个测试</b>：本次修复的实质是把两处 `null` 字面量换成
 * {@code resolveDialectName(ctx, table)} 的结果。若只测 {@code buildOrderByClause}
 * 对显式方言串的行为，把 {@code resolveDialectName} 调用改回 {@code null}
 * <b>所有测试依然全绿</b>——已实测确认（变异后 33 个用例无一转红）。
 * 这正是「组件级验证通过 ≠ 系统级可用」的典型形态，故此处从 ORM 入口驱动、
 * 捕获最终交给 {@code IOrmTemplate.executeQuery} 的 SQL 来断言。
 *
 * <p>断言的判别信号：H2 解析成功时产出原生 {@code NULLS FIRST}；若方言解析失败（null），
 * {@code buildOrderByClause} 会走「无法判定方言」分支抛
 * {@code ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED}。
 */
public class TestEntityAggregationProcessorOrmDialectWiring {


    /** 捕获 via-EQL 路径最终交给 ORM 的 SQL 文本 */
    private String capturedSql(String dialectName, String querySpace) {
        return capturedSql(dialectName, querySpace, true);
    }

    private String capturedSql(String dialectName, String querySpace, boolean desc) {
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("E1");
        table.setTableName("T_AGG");
        table.setQuerySpace(querySpace);

        IDialect dialect = stubDialect(dialectName);

        IOrmTemplate orm = mock(IOrmTemplate.class);
        when(orm.getDialectForQuerySpace(any())).thenReturn(dialect);
        ArgumentCaptor<SQL> sqlCaptor = ArgumentCaptor.forClass(SQL.class);
        when(orm.executeQuery(sqlCaptor.capture(), isNull(), any())).thenReturn(Collections.emptyList());

        LAST_ORM.set(orm);
        MetaQueryContext ctx = mock(MetaQueryContext.class);
        when(ctx.orm()).thenReturn(orm);
        when(ctx.filterTranslator()).thenReturn(mock(FilterToSqlTranslator.class));

        OrderFieldBean order = desc ? OrderFieldBean.desc("total") : OrderFieldBean.asc("total");
        order.setNullsFirst(true);
        List<OrderFieldBean> orderBy = new ArrayList<>(List.of(order));

        Map<String, String> nameToExpr = new HashMap<>();
        nameToExpr.put("total", "SUM(AMOUNT)");

        new EntityAggregationProcessor().executeEntityAggregationViaEql(
                table, "T_AGG",
                List.of(new AggregationContext.MeasureSpec("total", "SUM(AMOUNT)")),
                Collections.emptyList(),
                null, null, orderBy, null, null, nameToExpr,
                List.of("total"), Collections.emptyList(), Collections.emptyMap(), ctx);

        return sqlCaptor.getValue().getText();
    }

    /**
     * 轻量方言 mock。ORM 路径实际只读 {@code getName()}——{@code resolveDialectName} 返回
     * {@code dialect.getName()}，{@code buildOrderByClause} 收的是字符串。
     * {@code DialectImpl.getName()} 由 resourcePath 推导、不可设定，故直接 mock 接口。
     * <p>注：<b>不</b> stub {@code isSupportNullsFirst()}——该位在本路径上从未被读取，
     * stub 它只会让测试看起来比实际覆盖更宽（closure audit MAJOR-5）。
     */
    private static IDialect stubDialect(String name) {
        IDialect dialect = mock(IDialect.class);
        when(dialect.getName()).thenReturn(name);
        return dialect;
    }

    @Test
    public void testViaEqlResolvesNativeDialectAndEmitsNullsClause() {
        String sql = capturedSql("h2", "qs-wiring");
        assertTrue(sql.contains("NULLS FIRST"),
                "ORM 路径应解析出 h2 并原生输出 NULLS FIRST（若仍传 null 会 fail-fast）。实际 SQL: " + sql);
    }

    @Test
    public void testViaEqlResolvesNullsLastToo() {
        String sql = capturedSql("postgresql", "qs-postgres");
        assertTrue(sql.contains("NULLS FIRST"), "postgresql 同样原生支持。实际 SQL: " + sql);
    }

    /**
     * 经 {@code x:extends} 继承为 true、但自身不声明该位的派生方言。
     * closure audit BLOCKER-1 正是因为缺少这类用例，白名单才漏判。
     */
    @Test
    public void testViaEqlResolvesInheritingDialects() {
        for (String dialect : new String[]{"h2gis", "postgis", "oracle-reverse"}) {
            String sql = capturedSql(dialect, "qs-" + dialect);
            assertTrue(sql.contains("NULLS FIRST"), dialect + " 应原生支持。实际 SQL: " + sql);
        }
    }

    /**
     * 非原生方言：mysql 解析出来后应命中 MySQL 分支——与 MySQL 缺省一致的组合被省略子句，
     * 不一致的组合 fail-fast。两者都<b>不产出</b>非法 SQL，这正是修复的目的。
     */
    @Test
    public void testViaEqlResolvesMySqlAndOmitsDefaultMatchingClause() {
        // ASC + NULLS FIRST 与 MySQL 缺省一致 -> 省略子句，产出可执行 SQL。
        // 若方言解析失败（传 null），本例会因「无法判定方言」而抛 —— 故此用例可判别变异。
        String sql = capturedSql("mysql", "qs-mysql", false);
        assertTrue(!sql.contains("NULLS FIRST"),
                "MySQL 不支持原生 NULLS 子句，且该组合与其缺省一致，应省略子句。实际 SQL: " + sql);
    }

    @Test
    public void testViaEqlMySqlInexpressibleCombinationFailsFast() {
        // MySQL 无法表达的组合（DESC + NULLS FIRST）必须响亮失败，不得产出非法 SQL
        io.nop.api.core.exceptions.NopException err =
                org.junit.jupiter.api.Assertions.assertThrows(
                        io.nop.api.core.exceptions.NopException.class,
                        () -> capturedSql("mysql", "qs-mysql", true));
        assertEquals("nop.err.metadata.aggr-order-by-nulls-unsupported", err.getErrorCode());
    }

    /**
     * querySpace 为空时回落默认空间。<b>断言传给方言解析器的实参</b>——
     * 否则本用例只能证明「非 null」，而原实现用的是 {@code any()} 匹配任何值。
     */
    @Test
    public void testViaEqlFallsBackToDefaultQuerySpace() {
        String sql = capturedSql("h2", null);
        org.mockito.Mockito.verify(lastOrm())
                .getDialectForQuerySpace(DaoConstants.DEFAULT_QUERY_SPACE);
        assertTrue(sql.contains("NULLS FIRST"),
                "querySpace 为空应回落 " + DaoConstants.DEFAULT_QUERY_SPACE + "。实际 SQL: " + sql);
    }

    /** 记录最近一次构造的 orm mock，供 verify 使用 */
    private static final ThreadLocal<IOrmTemplate> LAST_ORM = new ThreadLocal<>();

    private static IOrmTemplate lastOrm() {
        return LAST_ORM.get();
    }
}