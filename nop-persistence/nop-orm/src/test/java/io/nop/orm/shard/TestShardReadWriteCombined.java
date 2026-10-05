/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.shard;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.datasource.ReadWriteRoutingDataSource;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2320 Phase 4组合验证：分库分表 × 读写分离叠加（真实组合E2E，plan 2319已落地）。
 *
 * <p>链路：分片策略按 {@code query-space-pattern: ds_{shard}} 将useShard实体的查询路由到
 * querySpace {@code ds_1}；该querySpace注册的是{@link ReadWriteRoutingDataSource}（写=本测试
 * 主库，读=独立从库，plan 2319组件）。断言：保存落主库分片表；无事务SELECT经
 * readOnly意图路由到从库（从库计数器+1）；default querySpace查询不经过路由数据源。</p>
 */
public class TestShardReadWriteCombined extends AbstractOrmTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";
    static final String MAIN_ENTITY = "test.entity.TestOrmTable";

    static class CountingDataSource implements DataSource {
        final DataSource delegate;
        final AtomicInteger connections = new AtomicInteger();

        CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connections.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            connections.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    CountingDataSource replicaDs;

    @Override
    protected IShardSelector shardSelector() {
        if (selector == null) {
            ConfigShardSelector selector = new ConfigShardSelector();
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD,
                    "{\"strategies\":{\"combined\":{\"type\":\"mod\",\"shard-count\":2,"
                            + "\"query-space-pattern\":\"ds_{shard}\","
                            + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + MAIN_ENTITY + "\"]}}}");
            try {
                selector.init();
            } finally {
                AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
            }
            this.selector = selector;
        }
        return selector;
    }

    IShardSelector selector;

    @AfterEach
    public void tearDownShard() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        selector = null;
    }

    @Override
    protected void createTables() {
        super.createTables();

        // 组合装配：ds_1 querySpace指向读写分离路由数据源（写=本测试主库，读=独立从库）
        HikariDataSource replica = new HikariDataSource();
        replica.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        replica.setDriverClassName("org.h2.Driver");
        replica.setUsername("sa");
        replica.setPassword("");
        replica.setMaximumPoolSize(2);
        replica.setMetricRegistry(GlobalMeterRegistry.instance());
        replicaDs = new CountingDataSource(replica);

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setQuerySpace("ds_1");
        routing.setWriteDataSource(dataSource);
        routing.addReplica("nopReadWriteDs_ds_1_read", replicaDs);

        TransactionTemplateImpl txnTemplate = (TransactionTemplateImpl) transactionTemplate;
        DefaultTransactionManager txnManager = (DefaultTransactionManager) txnTemplate.getTransactionManager();
        txnManager.addTransactionFactory("ds_1", new JdbcTransactionFactory(routing));

        // 分片物理表在主库（写侧）与从库（读侧，空表无数据）都建出：
        // 从库空表验证SELECT确实路由到从库（返回0行而非报表不存在）
        IShardSelector shardSelector = selector;
        io.nop.dao.jdbc.IJdbcTemplate replicaJdbc = new io.nop.dao.jdbc.impl.JdbcFactory()
                .newJdbcTemplate(new io.nop.dao.jdbc.impl.JdbcFactory().newTransactionTemplate(replica, null));
        DdlSqlCreator creator = DdlSqlCreator.forDialect(jdbcTemplate.getDialectForQuerySpace(null));
        for (String entityName : new String[]{SHARD_ENTITY, MAIN_ENTITY}) {
            var entityModel = sessionFactory.getOrmModel().requireEntityModel(entityName);
            for (String physicalTable : ShardDdlHelper.shardTableNames(shardSelector, entityModel)) {
                if (!jdbcTemplate.existsTable(null, physicalTable)) {
                    jdbcTemplate.executeUpdate(new SQL(creator.createTable(entityModel, false, physicalTable)));
                }
                if (!replicaJdbc.existsTable(null, physicalTable)) {
                    replicaJdbc.executeUpdate(new SQL(creator.createTable(entityModel, false, physicalTable)));
                }
            }
        }
    }

    private IOrmEntity newShardEntity(String sid, String strValue, String shardValue) {
        IOrmEntity entity = orm().newEntity(SHARD_ENTITY);
        entity.orm_propValueByName("sid", sid);
        entity.orm_propValueByName("strValue", strValue);
        entity.orm_propValueByName("nopShard", shardValue);
        return entity;
    }

    @Test
    public void testShardQuerySpaceRoutesThroughReadWriteSplitting() {
        // 写路径：save经shard选择(querySpace=ds_1) → 路由数据源写侧 → 主库分片表
        orm().runInSession(() -> orm().save(newShardEntity("s1", "v1", "1")));

        assertEquals(1, countRowsInPrimary("TEST_ORM_SHARD_TABLE_1"), "insert must land on primary shard table");

        // 读路径：无事务SELECT携带readOnly意图 → ds_1路由数据源 → 从库（从库无此表，返回空但计数+1）
        int replicaConnsBefore = replicaDs.connections.get();
        List<?> rows = orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", "1").end());
        assertEquals(0, rows.size(), "replica db is empty: select must have been routed to replica");
        assertEquals(replicaConnsBefore + 1, replicaDs.connections.get(),
                "sharded SELECT in ds_1 must route through read-write splitting to replica");

        // 对照：default querySpace查询不经过ds_1路由数据源
        int before = replicaDs.connections.get();
        orm().findAll(SQL.begin().sql("select o.collegeName from io.nop.app.SimsCollege o where o.collegeId = ?", "1").end());
        assertEquals(before, replicaDs.connections.get(),
                "default querySpace query must not touch ds_1 routing data source");
    }

    private long countRowsInPrimary(String tableName) {
        return jdbcTemplate.findLong(new SQL("select count(1) from " + tableName), 0L);
    }
}
