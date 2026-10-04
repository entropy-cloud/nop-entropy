/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
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
        c.setStdSqlType(StdSqlType.INTEGER);
        c.setStdDataType(StdDataType.INT);
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
                col("sid", "SID", 1, true), col("score", "SCORE", 2, false))));
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
            st.execute("create table t_nulls_order (sid int primary key, score int)");
            st.execute("insert into t_nulls_order values (1,10),(2,null),(3,20)");
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

    /** 多键键序：D5 的「CASE 键不得 prepend 到列表头」在真实库上的表现 */
    @Test
    public void testMultiKeyPrecedenceOnMySql() throws Exception {
        try (HikariDataSource ds = dataSource(MYSQL_URL); Connection con = ds.getConnection()) {
            setupTable(con);
            OrmEntityModel model = entityModel();
            IDialect dialect = buildDialect(false);
            // sid 唯一，故结果必为 1,2,3 —— 若 CASE 键被提升到首键之前，行序会被打乱
            assertEquals(List.of(1, 2, 3),
                    sidsByEql(con, dialect, model, "order by o.sid asc, o.score asc nulls last"),
                    "首键唯一时空序键不得改变整体行序");
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