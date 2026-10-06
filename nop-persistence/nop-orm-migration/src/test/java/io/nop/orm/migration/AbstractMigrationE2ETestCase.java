/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.DaoConfigs;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.OrmMigrationWriteGate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * H2→H2 端到端基座：单 H2 实例承载源表 + 目标分片表 + 编排状态表 + epoch ledger；
 * 实体模型面以最小 fake（{@link FakeEntityModelFacade}）供给 {@link TargetShardMapping}，
 * ORM session 层的行为由 nop-orm 模块自身测试与写门测试覆盖。
 */
public abstract class AbstractMigrationE2ETestCase {

    protected HikariDataSource dataSource;
    protected IJdbcTemplate jdbcTemplate;
    protected IOrmSessionFactory entityFacade;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUpBase() {
        dataSource = new HikariDataSource();
        dataSource.setMetricRegistry(GlobalMeterRegistry.instance());
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txnTemplate = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txnTemplate);
        entityFacade = FakeEntityModelFacade.facade("mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");

        createTables();
    }

    @AfterEach
    void tearDownBase() {
        OrmMigrationWriteGate.setClosed(false);
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        if (dataSource != null)
            dataSource.close();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            // 源表（旧单表形态；shard_key 为业务分片键列）
            st.execute("CREATE TABLE MIG_ORDER (SID VARCHAR(64) PRIMARY KEY, "
                    + "AMOUNT NUMERIC(18,2), SHARD_KEY VARCHAR(32))");
            // 目标分片表（auto-upgrade 先行建好的新 M 物理表，模式同构）
            st.execute("CREATE TABLE MIG_ORDER_0 (SID VARCHAR(64) PRIMARY KEY, "
                    + "AMOUNT NUMERIC(18,2), SHARD_KEY VARCHAR(32))");
            st.execute("CREATE TABLE MIG_ORDER_1 (SID VARCHAR(64) PRIMARY KEY, "
                    + "AMOUNT NUMERIC(18,2), SHARD_KEY VARCHAR(32))");
            // epoch ledger（lag=0 观测面；结构与 jdbc-2pc sink 一致）
            st.execute("CREATE TABLE IF NOT EXISTS stream_epoch_ledger_v2 ("
                    + "sink_namespace VARCHAR(200) NOT NULL, epoch_id BIGINT NOT NULL, "
                    + "subtask_id INT NOT NULL, committed_at TIMESTAMP, "
                    + "PRIMARY KEY (sink_namespace, epoch_id, subtask_id))");
        } catch (Exception e) {
            throw new IllegalStateException("create migration e2e tables failed", e);
        }
    }

    protected void insertSourceRow(String sid, String amount, String shardKey) {
        String sql = "INSERT INTO MIG_ORDER (SID, AMOUNT, SHARD_KEY) VALUES (?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            ps.setString(2, amount);
            ps.setString(3, shardKey);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected int countRows(String table) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
             var rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected String amountOf(String table, String sid) {
        String sql = "SELECT AMOUNT FROM " + table + " WHERE SID = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected long ledgerRowCount() {
        return jdbcTemplate.findLong(io.nop.core.lang.sql.SQL.begin()
                .sql("SELECT COUNT(*) FROM stream_epoch_ledger_v2").end(), 0L);
    }

    protected void insertLedgerRow(String namespace, long epoch, int subtask) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("INSERT INTO stream_epoch_ledger_v2 (sink_namespace, epoch_id, subtask_id,"
                        + " committed_at) VALUES (").param(namespace)
                .sql("," + epoch + "," + subtask + ", CURRENT_TIMESTAMP)").end());
    }

    /**
     * rescale 1→2 作业配置（无 query-space-pattern：querySpace 为 null → 全部落缺省 querySpace；
     * count + 全量逐行 checksum 三级校验开满）。
     */
    protected MigrationJobConfig rescaleConfig() {
        java.util.Map<String, Object> targetStrategy = Json.map(
                "strategies", Json.map("order-mod",
                        Json.map("type", "mod", "shard-count", 2,
                                "entities", List.of("mig.entity.MigOrder"))));
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(targetStrategy)
                .verify(true, 1.0, false)
                .quiesceSeconds(1)
                .build();
    }

    /** tiny JSON map builder（避免测试里手拼字符串）。 */
    static final class Json {
        static java.util.Map<String, Object> map(Object... kv) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2)
                map.put((String) kv[i], kv[i + 1]);
            return map;
        }
    }
}
