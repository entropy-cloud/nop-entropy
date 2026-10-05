/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.compile.EqlCompiler;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 2308 Phase 4：EQL {@code ORDER BY ... NULLS FIRST/LAST} 的**三库真实执行**矩阵（docker opt-in）。
 *
 * <p>开启方式：{@code -Dnop.test.docker.enabled=true}
 *
 * <p>与 {@code ai-dev/tools/probe-nulls-order-matrix.sh} 的分工：那个脚本用手写 SQL 常量探测
 * <b>数据库本身</b>的空序语义（Phase 0 用）；本类走 <b>EQL 编译器</b>产出 SQL 再执行，
 * 验证<b>本特性生成的 SQL</b>在各库上可执行且行序正确。二者缺一不可：
 * 手写 SQL 可执行 ≠ 生成的 SQL 可执行。
 *
 * <p>覆盖：
 * <ul>
 *   <li>postgresql:16-alpine —— 原生支持 {@code supportNullsFirst=true}</li>
 *   <li>mysql:8.0 —— 不支持（{@code supportNullsFirst=false}），走 CASE WHEN 改写</li>
 * </ul>
 * 两个方言各跑 4 个方向组合 + 改写/原生等价性 + 多键键序。
 *
 * <p>镜像刻意钉小版本：{@code TestPostgreDialect} 曾钉 9.6.12 已下架导致拉取超时。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestEqlNullsOrderMatrix {

    static final String PG_URL = "jdbc:tc:postgresql:16-alpine:///test?TC_DAEMON=true";
    static final String MYSQL_URL = "jdbc:tc:mysql:8.0:///test?TC_DAEMON=true";

    // ---------------- 方言与实体元数据（内存构造，免代码生成） ----------------

    static IDialect buildDialect(boolean supportNullsFirst) {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.getFeatures().setSupportNullsFirst(supportNullsFirst);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType("grp".equals(name) ? StdSqlType.VARCHAR : StdSqlType.INTEGER);
        c.setStdDataType("grp".equals(name) ? StdDataType.STRING : StdDataType.INT);
        c.setPrimary(primary);
        return c;
    }

    static class Ctx implements ISqlCompileContext {
        final IDialect dialect;
        final OrmEntityModel model;

        Ctx(IDialect dialect, OrmEntityModel model) {
            this.dialect = dialect;
            this.model = model;
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
            return model.getName().equals(entityName) ? new EntityTableMeta(model, null, dialect) : null;
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    static OrmEntityModel entityModel() {
        OrmEntityModel m = new OrmEntityModel();
        m.setName("NullsOrderEntity");
        m.setTableName("t_nulls_order");
        m.setQuerySpace("spaceA");
        m.setColumns(new ArrayList<>(List.of(
                col("sid", "SID", 1, true), col("score", "SCORE", 2, false),
                col("grp", "GRP", 3, false))));
        m.init();
        return m;
    }

    // ---------------- 数据与执行 ----------------

    static void setupTable(Connection con) throws Exception {
        try (Statement st = con.createStatement()) {
            try {
                st.execute("drop table t_nulls_order");
            } catch (Exception ignore) {
                // 表不存在
            }
            st.execute("create table t_nulls_order (sid int primary key, score int, grp varchar(8))");
            st.execute("insert into t_nulls_order(sid,score,grp) values (1,10,'A'),(2,null,'A'),(3,20,'B')");
        }
    }

    List<Integer> querySids(Connection con, String sql) throws Exception {
        List<Integer> out = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                out.add(rs.getInt(1));
        }
        return out;
    }

    /** EQL 文本 -> EqlCompiler -> SQL -> 真实执行 -> sid 行序 */
    List<Integer> sidsByEql(Connection con, IDialect dialect, OrmEntityModel model, String orderBy)
            throws Exception {
        String eql = "select o.sid from NullsOrderEntity o " + orderBy;
        String sql = new EqlCompiler().compile("t", eql, new Ctx(dialect, model)).getSql().getText();
        return querySids(con, sql);
    }

    // ---------------- 共享断言 ----------------

    void assertFourCombinations(Connection con, IDialect dialect, OrmEntityModel model, String label)
            throws Exception {
        assertEquals(List.of(2, 1, 3), sidsByEql(con, dialect, model, "order by o.score asc nulls first"),
                label + " ASC + NULLS FIRST");
        assertEquals(List.of(1, 3, 2), sidsByEql(con, dialect, model, "order by o.score asc nulls last"),
                label + " ASC + NULLS LAST");
        assertEquals(List.of(2, 3, 1), sidsByEql(con, dialect, model, "order by o.score desc nulls first"),
                label + " DESC + NULLS FIRST");
        assertEquals(List.of(3, 1, 2), sidsByEql(con, dialect, model, "order by o.score desc nulls last"),
                label + " DESC + NULLS LAST");
    }

    // ---------------- PostgreSQL：原生分支 ----------------

    @Test
    public void testPostgresNativeNullsOrder() throws Exception {
        try (HikariDataSource ds = dataSource(PG_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(true);
            assertFourCombinations(con, dialect, model, "postgresql(native)");
        }
    }

    // ---------------- MySQL：CASE 改写分支 ----------------

    @Test
    public void testMySqlCaseRewriteNullsOrder() throws Exception {
        try (HikariDataSource ds = dataSource(MYSQL_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(false);
            assertFourCombinations(con, dialect, model, "mysql(case-rewrite)");
        }
    }

    /**
     * 等价性：MySQL 上 CASE 改写分支必须与 PostgreSQL 上原生分支给出**同一行序**。
     * 这是跨方言语义一致性的最强断言——两个独立容器、两种互斥的生成策略。
     */
    @Test
    public void testCaseRewriteEquivalentToNativeAcrossDialects() throws Exception {
        List<List<Integer>> nativeOrder = new ArrayList<>();
        try (HikariDataSource ds = dataSource(PG_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(true);
            for (String clause : new String[]{
                    "order by o.score asc nulls first", "order by o.score asc nulls last",
                    "order by o.score desc nulls first", "order by o.score desc nulls last"}) {
                nativeOrder.add(sidsByEql(con, dialect, model, clause));
            }
        }

        try (HikariDataSource ds = dataSource(MYSQL_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(false);
            String[] clauses = {
                    "order by o.score asc nulls first", "order by o.score asc nulls last",
                    "order by o.score desc nulls first", "order by o.score desc nulls last"};
            for (int i = 0; i < clauses.length; i++) {
                assertEquals(nativeOrder.get(i), sidsByEql(con, dialect, model, clauses[i]),
                        "CASE 改写必须与原生语义一致: " + clauses[i]);
            }
        }
    }

    /** 多键键序（D5 最简形态）：首键唯一，CASE 键若被提升到列表头则行序被打乱 */
    @Test
    public void testMultiKeyPrecedenceOnMySql() throws Exception {
        try (HikariDataSource ds = dataSource(MYSQL_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(false);
            assertEquals(List.of(1, 2, 3),
                    sidsByEql(con, dialect, model, "order by o.sid asc, o.score asc nulls last"),
                    "首键唯一时空序键不得改变整体行序");
        }
    }

    /**
     * 多键键序（D5 计划 Exit Criteria 要求的形态）：**第一键有重复值** + 第二键含 NULL。
     *
     * <p>数据设计经过一轮返工——第一版数据在「紧邻」与「prepend 到列表头」两种实现下
     * 结果恰好相同，测不出差别。判别条件是：<b>两键的空序分组必须在跨组处发生冲突</b>，
     * 即存在「首键较小但次键为空」与「首键较大但次键非空」的两行。
     * 数据：grp='A'(10) / 'B'(null) / 'A'(20) / 'B'(30)
     * <ul>
     *   <li>紧邻（正确）：先按 grp 分组 -> [1,3] 组 'A'，组内 score nulls first -> 1(10),3(20)；
     *       [2,4] 组 'B'，组内 -> 2(null),4(30)。结果 [1,3,2,4]</li>
     *   <li>prepend（反模式）：score 的 nullness 被提升到最前 -> 2(null) 先 -> [2,1,3,4]</li>
     * </ul>
     */
    @Test
    public void testMultiKeyWithDuplicateFirstKeyOnMySql() throws Exception {
        try (HikariDataSource ds = dataSource(MYSQL_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            try (Statement st = con.createStatement()) {
                st.execute("delete from t_nulls_order");
                st.execute("insert into t_nulls_order(sid,grp,score) values"
                        + " (1,'A',10),(2,'B',null),(3,'A',20),(4,'B',30)");
            }
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(false);
            // 首键 grp 有重复值（两个 'A'、两个 'B'）但无 NULL，故其空序修饰为空操作，
            // 排序完全由 grp 值决定；次键 score 含 NULL，空序修饰在此生效。
            String eql = "select o.sid from NullsOrderEntity o"
                    + " order by o.grp asc nulls last, o.score asc nulls first";
            String sql = new EqlCompiler().compile("t", eql, new Ctx(dialect, model)).getSql().getText();
            List<Integer> got = querySids(con, sql);
            assertEquals(List.of(1, 3, 2, 4), got,
                    "空序键只能在自己那一组内生效；若 CASE 键被 prepend 到列表头，结果会是 [2,1,3,4]，实际 " + got);
        }
    }

    HikariDataSource dataSource(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        ds.setJdbcUrl(url);
        ds.setUsername("test");
        ds.setPassword("test");
        ds.setMaximumPoolSize(4);
        return ds;
    }
}