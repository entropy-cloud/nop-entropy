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
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.ReadWriteRoutingDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmErrors;
import io.nop.orm.OrmMigrationWriteGate;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import io.nop.orm.enc.EncAssistedHelper;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static io.nop.orm.OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * roadmap WI11 收口：分片 × 读写分离 × 可检索加密三能力叠加的组合 E2E。
 *
 * <p>组合装配（测试模型 {@code test.entity.TestTripleComboEntity}：useShard 动态实体，
 * examName 列同时声明 enc + ext:enc-assisted/ext:enc-like 辅助列）：分片策略按
 * {@code query-space-pattern: ds_{shard}} 路由到 querySpace {@code ds_1}；该 querySpace
 * 注册 {@link ReadWriteRoutingDataSource}（写=测试主库，读=独立从库计数器）。断言链：</p>
 * <ol>
 * <li>写路径（gate open）：save 落主库 {@code _N} 分片物理表，enc 主列为 {@code @enc:} 密文，
 *     assisted/like 辅助列自动派生；写不触碰从库</li>
 * <li>读路径（无事务）：带分片键的 EQL 等值/LIKE 查询——分片裁剪到 shard_1、只读意图路由到从库、
 *     谓词改写到辅助列、命中行实体加载解密回归明文</li>
 * <li>失败面（WI9 写停止关）：gate=closed 时 DML 显式 fail-fast（三能力交互相同的提交入口），
 *     写不入任何库、读路径不受影响，重开恢复</li>
 * </ol>
 *
 * <p>从库同构建表后手工灌入主库行（模拟复制延迟收敛后的从库副本），使「命中」与「计数」同时可观察。
 * shard_0 仅作分片裁剪对照行，其 querySpace 直连主库（不注册路由数据源，不在本测试观察面内）。</p>
 */
public class TestTripleComboShardReadwriteEnc extends AbstractOrmTestCase {

    static final String ENTITY = "test.entity.TestTripleComboEntity";
    static final String COMBO_MODEL = "/nop/test/orm/triple-combo.orm.xml";

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

    IShardSelector selector;
    CountingDataSource replicaDs;
    IJdbcTemplate replicaJdbc;

    /**
     * 组合实体模型经独立 orm 文件显式装载（不含共享模型的其它实体）：useShard 实体只进入本测试的
     * 会话工厂，不进入缺省合并模型，避免打破既有分片测试的 ShardBindingValidator 绑定断言面
     */
    @Override
    protected io.nop.orm.factory.IOrmModelProvider ormModelProvider() {
        OrmModel model = new OrmModelLoader().loadOrmModel(
                List.of(VirtualFileSystem.instance().getResource(COMBO_MODEL)));
        return new StaticOrmModelProvider(model);
    }

    @Override
    protected void prepareData() {
        // 独立模型不含共享模型的 SimsCollege/SimsClass，本测试自建数据
    }

    @Override
    protected void prepareDataInTenant(String tenantId) {
        // 同上：独立模型下无共享实体可灌
    }

    /** 显式注入已加载模型（形态参照 nop-dbtool-core TestShardAutoUpgrade 的 StaticModelProvider） */
    static class StaticOrmModelProvider implements io.nop.orm.factory.IOrmModelProvider {
        final OrmModel model;
        private volatile ILoadedOrmModel loaded;

        StaticOrmModelProvider(OrmModel model) {
            this.model = model;
        }

        @Override
        public ILoadedOrmModel getOrmModel(IPersistEnv env) {
            if (loaded == null)
                loaded = new LoadedOrmModel(env, model);
            return loaded;
        }

        @Override
        public void clearCache() {
            loaded = null;
        }

        @Override
        public void clearCacheForTenant(String tenantId) {
            loaded = null;
        }

        @Override
        public void close() {
        }
    }

    @Override
    protected IShardSelector shardSelector() {
        if (selector == null) {
            ConfigShardSelector selector = new ConfigShardSelector();
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD,
                    "{\"strategies\":{\"triple-combo\":{\"type\":\"mod\",\"shard-count\":2,"
                            + "\"query-space-pattern\":\"ds_{shard}\","
                            + "\"entities\":[\"" + ENTITY + "\"]}}}");
            try {
                selector.init();
            } finally {
                AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
            }
            this.selector = selector;
        }
        return selector;
    }

    @AfterEach
    public void tearDownTripleCombo() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        OrmMigrationWriteGate.setClosed(false);
        selector = null;
        if (replicaDs != null && replicaDs.delegate instanceof AutoCloseable) {
            try {
                ((AutoCloseable) replicaDs.delegate).close();
            } catch (Exception e) {
                throw new IllegalStateException("close replica pool failed", e);
            }
        }
        replicaDs = null;
        replicaJdbc = null;
    }

    @Override
    protected void createTables() {
        super.createTables();

        // 组合装配：ds_1 querySpace 指向读写分离路由数据源（写=测试主库，读=独立从库计数器）
        HikariDataSource replica = new HikariDataSource();
        replica.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        replica.setDriverClassName("org.h2.Driver");
        replica.setUsername("sa");
        replica.setPassword("");
        replica.setMaximumPoolSize(2);
        replica.setMetricRegistry(GlobalMeterRegistry.instance());
        replicaDs = new CountingDataSource(replica);
        replicaJdbc = new JdbcFactory().newJdbcTemplate(
                new JdbcFactory().newTransactionTemplate(replica, null));

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setQuerySpace("ds_1");
        routing.setWriteDataSource(dataSource);
        routing.addReplica("nopReadWriteDs_ds_1_read", replicaDs);

        TransactionTemplateImpl txnTemplate = (TransactionTemplateImpl) transactionTemplate;
        DefaultTransactionManager txnManager = (DefaultTransactionManager) txnTemplate.getTransactionManager();
        txnManager.addTransactionFactory("ds_1", new JdbcTransactionFactory(routing));
        // shard_0 对照行的 querySpace：直连主库（不注册路由数据源，不在观察面内）
        txnManager.addTransactionFactory("ds_0", new JdbcTransactionFactory(dataSource));

        // 分片物理表在主库（写侧）与从库（读侧）都建出：从库可执行同构查询，
        // 数据经 replicateRowToReplica 模拟主从复制后可被读路径命中
        IEntityModel entityModel = sessionFactory.getOrmModel().requireEntityModel(ENTITY);
        DdlSqlCreator creator = DdlSqlCreator.forDialect(jdbcTemplate.getDialectForQuerySpace(null));
        for (String physicalTable : ShardDdlHelper.shardTableNames(selector, entityModel)) {
            if (!jdbcTemplate.existsTable(null, physicalTable)) {
                jdbcTemplate.executeUpdate(new SQL(creator.createTable(entityModel, false, physicalTable)));
            }
            if (!replicaJdbc.existsTable(null, physicalTable)) {
                replicaJdbc.executeUpdate(new SQL(creator.createTable(entityModel, false, physicalTable)));
            }
        }
    }

    private IOrmEntity newComboEntity(String sid, String examName, String shardValue) {
        IOrmEntity entity = orm().newEntity(ENTITY);
        entity.orm_propValueByName("sid", sid);
        entity.orm_propValueByName("examName", examName);
        entity.orm_propValueByName("nopShard", shardValue);
        return entity;
    }

    private void saveCombo(String sid, String examName, String shardValue) {
        orm().runInSession(() -> orm().save(newComboEntity(sid, examName, shardValue)));
    }

    private String shardTable(int shard) {
        return ShardDdlHelper.shardTableNames(selector,
                sessionFactory.getOrmModel().requireEntityModel(ENTITY)).get(shard);
    }

    /**
     * 模拟主从复制：把主库分片表的指定行原样灌入从库同构表（密文与辅助列摘要均按物理值复制）
     */
    private void replicateRowToReplica(String physicalTable, String sid) {
        Map<String, Object> row = jdbcTemplate.findFirst(
                new SQL("select * from " + physicalTable + " where SID = '" + sid + "'"));
        assertNotNull(row, "primary row must exist: " + physicalTable + "#" + sid);

        IEntityModel entityModel = sessionFactory.getOrmModel().requireEntityModel(ENTITY);
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner marks = new StringJoiner(", ");
        List<Object> vals = new ArrayList<>();
        for (IColumnModel col : entityModel.getColumns()) {
            cols.add(col.getCode());
            marks.add("?");
            vals.add(row.get(col.getCode()));
        }
        replicaJdbc.executeUpdate(SQL.begin()
                .sql("insert into " + physicalTable + " (" + cols + ") values (" + marks + ")", vals.toArray())
                .end());
    }

    private long countRows(String physicalTable) {
        return jdbcTemplate.findLong(new SQL("select count(1) from " + physicalTable), 0L);
    }

    private List<?> queryByShardAndName(String shardValue, String eqlNamePredicate, Object nameParam) {
        return orm().findAll(SQL.begin()
                .sql("select o from " + ENTITY + " o where o.nopShard = ? and o.examName " + eqlNamePredicate,
                        shardValue, nameParam)
                .end());
    }

    // ---------- 写路径：gate open 下 save → 主库 _N 分片表 + 密文 + 辅助列自动派生 ----------

    @Test
    public void testWritePathShardPrimaryCiphertextAndDerivedHelpers() {
        saveCombo("s1", "HelloWorld", "1");
        saveCombo("s0", "ControlRow", "0");

        // shard 裁剪：行各落 _1/_0 物理分片表
        assertEquals(1, countRows(shardTable(1)), "shard value 1 must land in physical shard table _1");
        assertEquals(1, countRows(shardTable(0)), "shard value 0 must land in physical shard table _0");

        // enc 主列密文 + assisted/like 辅助列自动派生（写路径三能力叠加的核心断言）
        Map<String, Object> row = jdbcTemplate.findFirst(
                new SQL("select * from " + shardTable(1) + " where SID = 's1'"));
        String masterValue = String.valueOf(row.get("EXAM_NAME"));
        assertTrue(masterValue.startsWith("@enc:"), "master column must store ciphertext: " + masterValue);
        assertFalse(masterValue.contains("HelloWorld"), "plaintext must not leak to storage");
        assertEquals(EncAssistedHelper.instance().assistedDigest("HelloWorld"), row.get("NAME_ASSISTED"),
                "assisted column must be derived on save");
        assertEquals(EncAssistedHelper.instance().likeDigest("HelloWorld"), row.get("NAME_LIKE"),
                "like column must be derived on save");

        // 写路径不触碰从库（路由数据源仅读侧可达）
        assertEquals(0, replicaDs.connections.get(), "write path must not touch the replica");
    }

    // ---------- 读路径：无事务 EQL → replica + 辅助列改写 + 命中解密 ----------

    @Test
    public void testReadPathReplicaRoutingRewriteAndDecrypt() {
        saveCombo("s1", "HelloWorld", "1");
        saveCombo("s0", "ControlRow", "0");
        replicateRowToReplica(shardTable(1), "s1");

        // 等值查询：分片裁剪（nopShard=1）× 只读路由（replica）× enc 改写（NAME_ASSISTED）三能力叠加于同一条 SQL
        int replicaConnsBefore = replicaDs.connections.get();
        List<?> rows = queryByShardAndName("1", "= ?", "HelloWorld");
        assertEquals(1, rows.size(), "eq via assisted column must hit the replicated shard row");
        IOrmEntity hit = (IOrmEntity) rows.get(0);
        assertEquals("s1", hit.orm_propValueByName("sid"));
        assertEquals("HelloWorld", hit.orm_propValueByName("examName"),
                "entity load must decrypt master column");
        assertEquals(replicaConnsBefore + 1, replicaDs.connections.get(),
                "no-txn sharded SELECT must route through read-write splitting to the replica");

        // LIKE 查询同理：改写到 NAME_LIKE、经从库命中并解密
        int beforeLike = replicaDs.connections.get();
        List<?> likeRows = queryByShardAndName("1", "like ?", "%World%");
        assertEquals(1, likeRows.size(), "like via like column must hit the replicated shard row");
        assertEquals("HelloWorld", ((IOrmEntity) likeRows.get(0)).orm_propValueByName("examName"),
                "like hit must decrypt master column on load");
        assertEquals(beforeLike + 1, replicaDs.connections.get(),
                "like query must also route to the replica");

        // 分片裁剪对照：同谓词换 shard 0 不命中（enc 改写不破坏分片路由，且不 fanout 到其它分片）
        int beforeControl = replicaDs.connections.get();
        assertTrue(queryByShardAndName("0", "= ?", "HelloWorld").isEmpty(),
                "eq on another shard must not hit");
        assertEquals(beforeControl, replicaDs.connections.get(),
                "shard 0 querySpace is wired to primary directly, must not touch the replica");
    }

    // ---------- 失败面：gate=closed 时 DML 显式失败，读路径不受影响 ----------

    @Test
    public void testGateClosedDmlFailsExplicitlyReadsUnaffected() {
        saveCombo("s1", "HelloWorld", "1");
        replicateRowToReplica(shardTable(1), "s1");
        long primaryRowsBefore = countRows(shardTable(1));
        int replicaConnsBefore = replicaDs.connections.get();

        try {
            OrmMigrationWriteGate.setClosed(true);

            // 三能力共用 ORM DML 提交入口：写停止关关闭时显式 fail-fast，不静默落任何库
            NopException e = assertThrows(NopException.class,
                    () -> saveCombo("s2", "BlockedRow", "1"));
            assertEquals(ERR_ORM_MIGRATION_WRITE_GATE_CLOSED.getErrorCode(), e.getErrorCode());
            assertEquals("flush", e.getParams().get(OrmErrors.ARG_OPERATION));
            assertEquals(primaryRowsBefore, countRows(shardTable(1)), "DML must not reach primary");
            assertEquals(replicaConnsBefore, replicaDs.connections.get(), "DML must not reach replica");

            // 读路径不受写停止关影响：等值查询仍路由从库、命中并解密
            List<?> rows = queryByShardAndName("1", "= ?", "HelloWorld");
            assertEquals(1, rows.size(), "read path must stay unaffected by the write gate");
            assertEquals("HelloWorld", ((IOrmEntity) rows.get(0)).orm_propValueByName("examName"));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }

        // 重开写停止关恢复写入
        saveCombo("s3", "ReopenedRow", "1");
        assertEquals(primaryRowsBefore + 1, countRows(shardTable(1)), "reopen gate must restore writes");
    }
}
