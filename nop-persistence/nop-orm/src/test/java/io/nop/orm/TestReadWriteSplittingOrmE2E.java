/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlHelper;
import io.nop.dao.jdbc.datasource.ReadWriteHint;
import io.nop.dao.jdbc.datasource.ReadWriteRoutingDataSource;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.UNSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 4 ORM级端到端：AbstractJdbcTestCase覆写createDataSource()返回读写分离路由DS，
 * 从配置装配语义到主从分流的完整路径双方向验证：
 * <ul>
 * <li>ORM findPage（QueryBean→EQL分页路径）无事务→从库（SimsClass空表0行）</li>
 * <li>事务内ORM读与ORM写→主库（观察到主库数据）</li>
 * <li>runWithPrimary强制主库</li>
 * <li>sql-lib原生项readOnly="true"→从库、未标注→主库（Phase 3遗留断言）</li>
 * </ul>
 * 判别方式：replica为同构空库（ORM模型DDL克隆+空my_entity），主库有数据——按行数区分命中库。
 */
public class TestReadWriteSplittingOrmE2E extends AbstractOrmTestCase {
    private HikariDataSource replicaDs;
    private ReadWriteRoutingDataSource routing;

    private static final String EQL_ALL = "select o from SimsClass o";

    @Override
    protected DataSource createDataSource() {
        HikariDataSource primary = newHikari();
        replicaDs = newHikari();

        routing = new ReadWriteRoutingDataSource();
        routing.setQuerySpace("default");
        routing.setWriteDataSource(primary);
        routing.addReplica("replica", replicaDs);
        return routing;
    }

    private HikariDataSource newHikari() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID());
        return ds;
    }

    /**
     * 在replica上克隆ORM模型全部表结构（空表）+ my_entity空表，
     * 使从库可执行同构查询但观察不到主库数据
     */
    @BeforeEach
    public void setUpReplicaSchema() {
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(sessionFactory.getOrmModel().getEntityModelsInTopoOrder(), false);
        executeOnReplica(createSql);
        executeOnReplica(
                "create table my_entity(id INTEGER, a INTEGER, b INTEGER, c VARCHAR(100), d date)");
    }

    private void executeOnReplica(String multiSql) {
        try (Connection conn = replicaDs.getConnection()) {
            for (SQL sql : SqlHelper.splitSqlText(multiSql)) {
                try (Statement st = conn.createStatement()) {
                    st.execute(sql.getText());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("execute ddl on replica failed", e);
        }
    }

    @AfterEach
    @Override
    public void tearDown() {
        // 先完成基类销毁（sessionFactory.destroy可能经连接池flush，
        // 且必须先注销全局orm-interceptor-cache再关物理池，否则残留缓存影响同JVM后续测试。
        // 注意：JUnit5下覆写的tearDown必须自带@AfterEach，否则遮蔽基类生命周期方法）
        super.tearDown();
        // 基类tearDown对非AutoCloseable的routing为no-op，物理池在此显式关闭
        if (routing != null && routing.getWriteDataSource() instanceof AutoCloseable) {
            try {
                ((AutoCloseable) routing.getWriteDataSource()).close();
            } catch (Exception e) {
                throw new IllegalStateException("close primary pool failed", e);
            }
        }
        if (replicaDs != null) {
            replicaDs.close();
        }
    }

    private SQL readOnlyEql() {
        return SQL.begin().name("rwE2E").sql(EQL_ALL).readOnly(true).end();
    }

    @Test
    public void testOrmFindPageNoTxnHitsReplica() {
        // 无事务ORM EQL查询：JdbcQueryExecutor对statementKind==SELECT自动标记只读→从库→空表0行
        assertEquals(0, orm().findAll(readOnlyEql()).size());
        // 显式readOnly标志与ORM自动标记路由结果一致（均从库）
        assertEquals(0, orm().findAll(new SQL(EQL_ALL)).size());
    }

    @Test
    public void testOrmFindPagePaginationPathHitsReplica() {
        // findPage分页路径（带range，SQL重建）仍保留ORM自动标记的只读标志（Phase 1 Blocker回归面：
        // 若分页重建丢失标志，此查询会静默回落主库并观察到1行）
        List<?> page = orm().findPage(new SQL(EQL_ALL), 0, 10);
        assertEquals(0, page.size(), "paged ORM query without transaction must hit the empty replica");
        List<?> explicitPage = orm().findPage(readOnlyEql(), 0, 10);
        assertEquals(0, explicitPage.size());
    }

    @Test
    public void testOrmQueryInOpenTransactionHitsPrimary() {
        List<?> rows = jdbc().txn().runInTransaction(null, TransactionPropagation.REQUIRED,
                txn -> orm().findAll(readOnlyEql()));
        assertEquals(1, rows.size(), "ORM query inside an open transaction must hit primary");
    }

    @Test
    public void testRunWithPrimaryForcesPrimary() {
        int count = ReadWriteHint.runWithPrimary(() -> orm().findAll(readOnlyEql()).size());
        assertEquals(1, count, "runWithPrimary must override read-only routing");
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testOrmWriteGoesToPrimary() {
        io.nop.app.SimsClass entity = daoProvider().daoFor(io.nop.app.SimsClass.class).newEntity();
        entity.setClassId("22");
        entity.setCollegeId("1");
        entity.setClassName("classB");
        entity.setMajorId("a");
        orm().save(entity);

        // 写入落主库：runWithPrimary读到2行（prepareData的1行+新写1行）
        assertEquals(2, ReadWriteHint.runWithPrimary(() -> orm().findAll(readOnlyEql()).size()));
        // 从库仍为空（无隐式写后读粘滞）
        assertEquals(0, orm().findAll(readOnlyEql()).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSqlLibReadOnlyAnnotationRouting() {
        // sql-lib原生项readOnly="true"→从库（my_entity空表0行）；未标注→主库（1行）
        IEvalScope scope = XLang.newEvalScope();
        List<Object> replicaRows = (List<Object>) sqlLibManager.invoke("readonly.findAllReadOnly", null, scope);
        assertEquals(0, replicaRows.size(), "sql-lib item with readOnly=true must hit the empty replica");

        List<Object> primaryRows = (List<Object>) sqlLibManager.invoke("readonly.findAllDefault", null,
                XLang.newEvalScope());
        assertEquals(1, primaryRows.size(), "sql-lib item without readOnly must hit primary (fail-safe)");
    }

    @Test
    public void testConnectionsReturnedToPools() {
        orm().findAll(readOnlyEql());
        orm().findAll(new SQL(EQL_ALL));

        // 写库与从库池均无活跃连接残留（nop-orm侧无JdbcTestCase基类，直接断言池MXBean）
        HikariDataSource primary = (HikariDataSource) routing.getWriteDataSource();
        assertEquals(0, primary.getHikariPoolMXBean().getActiveConnections(), "primary pool must have no active connections");
        assertEquals(0, replicaDs.getHikariPoolMXBean().getActiveConnections(), "replica pool must have no active connections");
    }
}
