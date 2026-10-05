/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.datasource.ReadWriteHint;
import io.nop.dao.jdbc.datasource.ReadWriteRoutingDataSource;
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
 * plan 2319 Phase 4 双H2集成：primary建表插数、replica独立空库（克隆表结构），
 * 经事务模板执行的可观察路由判定：
 * 无事务只读查询→从库（空结果）、事务内查询→主库（1行）、runWithPrimary→主库、写→主库。
 * 连接归还经checkNoActive（写库）与replica池MXBean直接断言。
 */
public class TestReadWriteSplittingIntegration extends JdbcTestCase {
    private HikariDataSource replicaDs;
    private ReadWriteRoutingDataSource routing;

    @Override
    protected DataSource createDataSource() {
        HikariDataSource primary = newHikari();
        replicaDs = newHikari();
        createEmptyMyEntityTable(replicaDs);

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
        ds.setMaximumPoolSize(maxPoolSize);
        return ds;
    }

    /**
     * replica为独立空库：仅建与init.sql同构的my_entity空表，不插任何数据
     */
    private void createEmptyMyEntityTable(HikariDataSource ds) {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("create table my_entity(id INTEGER, a INTEGER, b INTEGER, c VARCHAR(100), d date)");
        } catch (Exception e) {
            throw new IllegalStateException("create replica table failed", e);
        }
    }

    @Override
    public void tearDown() {
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
        super.tearDown();
    }

    private SQL readOnlySelect() {
        return SQL.begin().sql("select id, c from my_entity").readOnly(true).end();
    }

    @Test
    public void testNoTxnReadOnlyQueryHitsReplica() {
        // primary有1行（init.sql），replica为空表：无事务只读查询命中从库→空结果
        List<Map<String, Object>> rows = jdbc().findAll(readOnlySelect());
        assertEquals(0, rows.size(), "read-only query without transaction must hit the empty replica");

        // 对照：无只读标志（语义未知fail-safe）→主库→1行
        List<Map<String, Object>> primaryRows = jdbc().findAll(new SQL("select id, c from my_entity"));
        assertEquals(1, primaryRows.size());
    }

    @Test
    public void testQueryInOpenTransactionHitsPrimary() {
        List<?> rows = jdbc().txn().runInTransaction(null, TransactionPropagation.REQUIRED,
                txn -> jdbc().findAll(readOnlySelect()));
        assertEquals(1, rows.size(), "query inside an open transaction must hit primary (1 row)");
    }

    @Test
    public void testRunWithPrimaryForcesPrimary() {
        int count = ReadWriteHint.runWithPrimary(() -> jdbc().findAll(readOnlySelect()).size());
        assertEquals(1, count, "explicit forcePrimary must read from primary even for read-only sql");
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testWriteGoesToPrimaryAndReplicaUnaffected() {
        jdbc().executeUpdate(new SQL("insert into my_entity(id, a, b, c, d) values(2, 3, 4, 'second', current_date)"));

        // 写后主库2行
        assertEquals(2, jdbc().findAll(new SQL("select id from my_entity")).size());
        // 从库不受影响仍为空（无隐式write-then-read粘滞）
        assertEquals(0, jdbc().findAll(readOnlySelect()).size());
    }

    @Test
    public void testConnectionsReturnedToPools() {
        jdbc().findAll(readOnlySelect());
        jdbc().findAll(new SQL("select id from my_entity"));
        jdbc().executeUpdate(new SQL("update my_entity set a=1 where id=1"));

        // 写库（经路由DS unwrap）与从库池均无活跃连接残留
        checkNoActive();
        assertTrue(replicaDs.getHikariPoolMXBean().getActiveConnections() == 0,
                "replica pool must have no active connections after queries");
    }
}
