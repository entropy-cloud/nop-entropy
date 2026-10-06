/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 编排作业状态行的 JDBC 持久化（自治表 {@code nop_orm_migration_job}）。
 *
 * <p>建表语义沿用 {@code JdbcTwoPhaseCommitSink.initializeLedgerTable} 的自治先例：
 * {@code CREATE TABLE IF NOT EXISTS} + 方言转译；类型面只用 VARCHAR/TIMESTAMP 兼容三方
 * （mysql/postgresql/H2）。状态行是编排器的唯一恢复面：重启后按行从最后完成阶段续跑，
 * 绝不隐式回退阶段（设计 §4.1）。</p>
 */
public class MigrationJobStateStore {
    public static final String TABLE_NAME = "nop_orm_migration_job";

    private static final String DDL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + "job_id VARCHAR(64) PRIMARY KEY, "
            + "phase VARCHAR(32) NOT NULL, "
            + "scenario VARCHAR(16), "
            + "entity_name VARCHAR(200), "
            + "position_p0 VARCHAR(2000), "
            + "full_task_name VARCHAR(200), "
            + "verify_result VARCHAR(16), "
            + "route_rebuild VARCHAR(32), "
            + "abort_reason VARCHAR(2000), "
            + "create_time TIMESTAMP, "
            + "update_time TIMESTAMP)";

    private static final String COLS = "job_id,phase,scenario,entity_name,position_p0,full_task_name,"
            + "verify_result,route_rebuild,abort_reason,create_time,update_time";

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private volatile boolean tableReady;

    public MigrationJobStateStore(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace == null ? "" : querySpace;
    }

    /**
     * 自治建表（幂等）。{@code CREATE TABLE IF NOT EXISTS} 各方言通用，无需改写。
     */
    public void initializeTable() {
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);
        try (Connection conn = jdbcTemplate.openConnection(querySpace)) {
            try (PreparedStatement ps = conn.prepareStatement(DDL)) {
                ps.execute();
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("orm-migration-state-init", e);
        }
        tableReady = true;
    }

    private void ensureTable() {
        if (!tableReady) {
            synchronized (this) {
                if (!tableReady)
                    initializeTable();
            }
        }
    }

    public void insert(MigrationJobState state) {
        ensureTable();
        // SqlBuilder.param(value) 会在 SQL 文本上追加 '?' 占位并绑定值（EncAssistedBackfiller 先例）——
        // 占位符必须经 param 链生成，不能先拼完整 SQL 再补 param
        long updated = jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                .name("orm-migration-state-insert")
                .sql("INSERT INTO " + TABLE_NAME + " (" + COLS + ") VALUES (")
                .param(state.getJobId()).sql(",").param(state.getPhase().name())
                .sql(",").param(state.getScenario()).sql(",").param(state.getEntityName())
                .sql(",").param(state.getPositionP0()).sql(",").param(state.getFullTaskName())
                .sql(",").param(state.getVerifyResult()).sql(",").param(state.getRouteRebuild())
                .sql(",").param(state.getAbortReason())
                .sql(",").param(new Timestamp(state.getCreateTimeMillis()))
                .sql(",").param(new Timestamp(state.getUpdateTimeMillis()))
                .sql(")").end());
        if (updated != 1)
            throw new IllegalStateException("insert migration job state failed: " + state.getJobId());
    }

    public void update(MigrationJobState state) {
        ensureTable();
        state.setUpdateTimeMillis(CoreMetrics.currentTimeMillis());
        long updated = jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                .name("orm-migration-state-update")
                .sql("UPDATE " + TABLE_NAME + " SET phase=").param(state.getPhase().name())
                .sql(",scenario=").param(state.getScenario())
                .sql(",entity_name=").param(state.getEntityName())
                .sql(",position_p0=").param(state.getPositionP0())
                .sql(",full_task_name=").param(state.getFullTaskName())
                .sql(",verify_result=").param(state.getVerifyResult())
                .sql(",route_rebuild=").param(state.getRouteRebuild())
                .sql(",abort_reason=").param(state.getAbortReason())
                .sql(",update_time=").param(new Timestamp(state.getUpdateTimeMillis()))
                .sql(" WHERE job_id=").param(state.getJobId())
                .end());
        if (updated != 1)
            throw new IllegalStateException("update migration job state failed (row missing): " + state.getJobId());
    }

    public MigrationJobState load(String jobId) {
        ensureTable();
        String sql = "SELECT " + COLS + " FROM " + TABLE_NAME + " WHERE job_id=";
        List<MigrationJobState> rows = jdbcTemplate.findAll(SQL.begin().querySpace(querySpace)
                        .name("orm-migration-state-load").sql(sql).param(jobId).end(),
                (row, rowNum, colMapper) -> mapRow(row));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<MigrationJobState> loadAll() {
        ensureTable();
        String sql = "SELECT " + COLS + " FROM " + TABLE_NAME + " ORDER BY create_time";
        return jdbcTemplate.findAll(SQL.begin().querySpace(querySpace)
                        .name("orm-migration-state-load-all").sql(sql).end(),
                (row, rowNum, colMapper) -> mapRow(row));
    }

    private MigrationJobState mapRow(io.nop.dataset.IDataRow row) {
        // nop dataset 列序为 0 基（JdbcDataSet: rs.getXxx(index + 1)）
        MigrationJobState state = new MigrationJobState();
        state.setJobId(row.getString(0));
        state.setPhase(MigrationPhase.valueOf(row.getString(1)));
        state.setScenario(row.getString(2));
        state.setEntityName(row.getString(3));
        state.setPositionP0(row.getString(4));
        state.setFullTaskName(row.getString(5));
        state.setVerifyResult(row.getString(6));
        state.setRouteRebuild(row.getString(7));
        state.setAbortReason(row.getString(8));
        Timestamp created = row.getTimestamp(9);
        state.setCreateTimeMillis(created == null ? 0L : created.getTime());
        Timestamp updated = row.getTimestamp(10);
        state.setUpdateTimeMillis(updated == null ? 0L : updated.getTime());
        return state;
    }
}
