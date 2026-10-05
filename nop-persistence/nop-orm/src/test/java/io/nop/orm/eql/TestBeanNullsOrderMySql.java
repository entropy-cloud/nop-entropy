/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.orm.sql.GenSqlHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2309 Phase 2：<b>bean 通道</b> ORDER BY 空序门控的端到端验证（真实 MySQL，docker opt-in）。
 *
 * <p>开启方式：{@code -Dnop.test.docker.enabled=true}。
 * <b>不显式传该参数则本类被跳过</b>——仓库无任何 pom 设置它，故 CI 绿灯不代表本测试已跑。
 *
 * <p><b>为什么必须在 MySQL 上验证</b>：MySQL 8.0 不接受 {@code NULLS FIRST/LAST} 子句。
 * 修复前 bean 通道无条件拼接该子句 ⇒ 在 MySQL 上产出语法错误 SQL；本类断言的是
 * <b>真实执行后的结果行序</b>，因此能同时证明「SQL 可执行」与「行序语义正确」。
 *
 * <p>与 {@code TestEqlNullsOrderMatrix} 的分工：那个走 EQL 通道，这个走 bean 通道
 * （{@code OrderFieldBean} → {@code DaoQueryHelper.appendOrderBy}）。两者共用
 * {@code NullsOrderRewriter} 的改写实现，故本类也间接守护 EQL 侧的算法。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestBeanNullsOrderMySql extends AbstractOrmTestCase {

    @Override
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        ds.setJdbcUrl("jdbc:tc:mysql:8.0:///test?TC_DAEMON=true");
        ds.setUsername("test");
        ds.setPassword("test");
        ds.setMaximumPoolSize(2);
        return ds;
    }

    /**
     * 基类的 {@code /init.sql} 是 H2 方言的建表脚本，在 MySQL 上会因语法差异失败
     * （如 tinyint/自增等）。本类只关心 ORDER BY 空序，不需要那批表，
     * 故覆盖为 no-op；测试用表 {@code TEST_NULLS_ORDER} 由
     * {@code AbstractOrmTestCase.createTables()} 从 ORM 模型建出。
     */
    @Override
    protected void executeSqlFile(String sqlFile) {
        // 刻意不执行 H2 专用 init.sql
    }

    /**
     * 基类按完整 ORM 模型建表，但测试模型里有 H2 方言的列定义，在 MySQL 上会失败。
     * 本类只需 {@code TEST_NULLS_ORDER}，故只建这一张。
     */
    @Override
    protected void createTables() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin().sql(
                "create table TEST_NULLS_ORDER (SID integer not null primary key,"
                        + " SCORE integer, SEQ_COL integer)").end());
    }

    /** 租户种子数据同样依赖 H2 表结构，本类不需要 */
    @Override
    protected void prepareDataInTenant(String tenantId) {
    }

    /**
     * 基类的种子数据依赖 H2 方言表结构，本类不需要；改为植入本用例自己的数据。
     * score 可空，构造确定的 NULL 分布。基类在 {@code createTables()} 之后调用本方法。
     */
    @Override
    protected void prepareData() {
        jdbcTemplate.executeUpdate(SQL.begin().sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 2)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (3, 20, 3)").end());
    }

    /** 经 bean 通道（OrderFieldBean）构造 SQL —— 这正是修复前会产出语法错误的那条路径 */
    private SQL beanSql(boolean desc, Boolean nullsFirst) {
        QueryBean query = new QueryBean();
        query.setSourceName("TEST_NULLS_ORDER");
        query.addField(QueryFieldBean.forField("sid"));
        OrderFieldBean order = OrderFieldBean.forField("score");
        order.setDesc(desc);
        order.setNullsFirst(nullsFirst);
        query.setOrderBy(Collections.singletonList(order));
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(null);
        // 用 queryToSelectFieldsSql：queryToSelectObjectSql 产出的是「select o from ...」
        // 的实体形态，作用于裸表名会得到非法 SQL
        return DaoQueryHelper.queryToSelectFieldsSql(dialect, query, null).end();
    }

    private List<Integer> sidsOf(SQL sql) {
        return sidsOfText(sql.getText());
    }

    private List<Integer> sidsOfText(String sqlText) {
        List<Integer> out = new ArrayList<>();
        jdbcTemplate.executeQuery(SQL.begin().sql(sqlText).end(), ds -> {
            for (io.nop.dataset.IDataRow row : ds.readAll())
                out.add(((Number) row.toMap().values().iterator().next()).intValue());
            return null;
        });
        return out;
    }

    // ---- 四种方向组合的真实行序（MySQL 走 CASE 改写分支） ----

    @Test
    public void testAscNullsFirst() {
        assertEquals(List.of(2, 1, 3), sidsOf(beanSql(false, true)), "ASC + NULLS FIRST");
    }

    @Test
    public void testAscNullsLast() {
        assertEquals(List.of(1, 3, 2), sidsOf(beanSql(false, false)), "ASC + NULLS LAST");
    }

    @Test
    public void testDescNullsFirst() {
        assertEquals(List.of(2, 3, 1), sidsOf(beanSql(true, true)), "DESC + NULLS FIRST");
    }

    @Test
    public void testDescNullsLast() {
        assertEquals(List.of(3, 1, 2), sidsOf(beanSql(true, false)), "DESC + NULLS LAST");
    }

    /** 修复前会在此抛 SQL 语法错误（MySQL 不接受 NULLS 子句）——现在必须可执行 */
    @Test
    public void testBeanPathSqlIsExecutableOnMySql() {
        String text = beanSql(false, true).getText().replaceAll("\\s+", " ");
        assertFalse(text.contains("nulls first"),
                "MySQL 不支持原生 NULLS 子句，产物不得包含它: " + text);
        assertTrue(text.contains("case when"), "应改写为 CASE 排序键: " + text);
    }

    /** 未请求空序时产物不得出现 CASE——防误伤 */
    @Test
    public void testNoNullsRequestProducesNoCaseRewrite() {
        String text = beanSql(false, null).getText().replaceAll("\\s+", " ");
        assertFalse(text.contains("case when"), text);
        assertFalse(text.contains("nulls"), text);
    }

    // ---- 反转排序（D6）：null 组相对位置必须保持 ----

    @Test
    public void testReverseOrderIsStrictReverseOfForwardOrder() {
        OrderFieldBean order = OrderFieldBean.forField("score");
        order.setDesc(false);
        order.setNullsFirst(true);   // 正序：NULL 在前
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(null);

        SQL.SqlBuilder sb = SQL.begin().append("select o.sid from TEST_NULLS_ORDER as o");
        DaoQueryHelper.appendReverseOrderBy(sb, dialect, "o", Collections.singletonList(order));

        // reverseOrderField 同时翻转 isDesc 与 nullsFirst，故
        //   正序 ASC + NULLS FIRST  = [2(null), 1(10), 3(20)]
        // 反转为 DESC + NULLS LAST，是该序列的严格逆序：
        //   [3(20), 1(10), 2(null)]
        // findNext/findPrev 依赖的正是「反转是全序的逆」这一性质，
        // 而非「NULL 组位置不变」——后者是错的预期。
        assertEquals(List.of(3, 1, 2), sidsOfText(sb.end().getText()),
                "反转排序须得到原序列的严格逆序");
    }

    // ---- 多键：空序键只能在自己那一组内生效 ----

    @Test
    public void testMultiKeyPrecedence() {
        jdbcTemplate.executeUpdate(SQL.begin().sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (3, 10, 2)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (4, null, 2)").end());

        QueryBean query = new QueryBean();
        query.setSourceName("TEST_NULLS_ORDER");
        query.addField(QueryFieldBean.forField("sid"));
        OrderFieldBean score = OrderFieldBean.forField("score");
        score.setNullsFirst(true);
        // 首键用 sid：DaoQueryHelper 走属性名直拼列名（不查 entityModel 的 prop->code 映射），
        // 而本实体的 seq 属性对应物理列 SEQ_COL，故此处的首键取 sid。
        query.setOrderBy(new ArrayList<>(Arrays.asList(OrderFieldBean.forField("sid"), score)));

        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(null);
        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(dialect, query, null).end();
        // 首键 sid 唯一 => 行序已由首键定死，次键的空序修饰不可能改变结果。
        // 若 CASE 键被 prepend 到列表头，空序会变成主序，结果将不再是 1,2,3,4。
        assertEquals(List.of(1, 2, 3, 4), sidsOf(sql),
                "首键 sid 唯一时，空序键不得改变整体行序（CASE 键须紧邻其排序项而非列表头）");
    }

    // ---- 不可复制表达式：响亮失败而非静默产出错误 SQL ----

    @Test
    public void testBindParameterInSortExpressionFailsFast() {
        // 直接对 Gate 层做断言：绑定参数标记不可复制
        assertEquals("bind-parameter-not-duplicable",
                io.nop.orm.eql.sql.NullsOrderRewriter.duplicationBlocker("case when X = ? then 1 else 0 end")
                        .orElse(null));
        // 且真正门控时抛带 reason 的错误码
        NopException e = assertThrows(NopException.class, () ->
                GenSqlHelper.appendNullsOrder(SQL.begin(), jdbcTemplate.getDialectForQuerySpace(null),
                        "case when ? = 1 then 0 else 1 end", false, true));
        assertEquals("nop.err.orm.nulls-order-not-supported", e.getErrorCode());
        assertEquals("bind-parameter-not-duplicable", e.getParam("reason"));
    }
}