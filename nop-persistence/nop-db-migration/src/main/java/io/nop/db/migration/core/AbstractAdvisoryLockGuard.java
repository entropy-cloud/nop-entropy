/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.db.migration.DbMigrationErrors.ARG_LOCK_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_GUARD_LOCK_FAILED;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT;

/**
 * 连接级咨询锁守卫的公共形态（plan 2518 裁定⑧方言矩阵 mysql/pg 分支，SQL 文本级离线可测）：
 * 持锁连接在 acquire 时建立并由 guard 持有，release 在同一连接上解锁后关闭（连接级互斥的
 * 同连接承载前提）；连接断开自动释放，无守卫行崩溃残留面。真库行为用例 docker-gated
 * （环境在案时补跑留痕，勘误 9 三联口径）。
 */
abstract class AbstractAdvisoryLockGuard implements IMigrationLockGuard {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAdvisoryLockGuard.class);

    protected final IJdbcTemplate jdbcTemplate;
    protected final String querySpace;
    protected final long lockTimeoutMillis;
    /** 每锁名持有连接（模块内互斥面为迁移启动/版本写段，串行使用；同名重复 acquire 显式失败）。 */
    private final Map<String, Connection> held = new ConcurrentHashMap<>();

    protected AbstractAdvisoryLockGuard(IJdbcTemplate jdbcTemplate, String querySpace,
                                        long lockTimeoutMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace;
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    /** 方言锁获取 SQL（seam 单测断言到 SQL 文本级）。 */
    protected abstract String acquireSql();

    /** 方言锁释放 SQL。 */
    protected abstract String releaseSql();

    /** acquire 结果列值语义判定（mysql GET_LOCK 返回 1/0；pg_advisory_lock 返回 key 行）。 */
    protected boolean isAcquireSuccess(Object result) {
        return result != null;
    }

    /** 超时参数的 JDBC 参数序号（0 = 该方言无超时参数，如 pg_advisory_lock）。 */
    protected int acquireTimeoutParamIndex() {
        return 0;
    }

    @Override
    public void acquire(String lockName) {
        if (held.containsKey(lockName))
            throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_FAILED)
                    .param(ARG_LOCK_NAME, lockName);
        Connection conn;
        try {
            conn = jdbcTemplate.openConnection(querySpace);
        } catch (Exception e) {
            throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_FAILED, e)
                    .param(ARG_LOCK_NAME, lockName);
        }
        try {
            long timeoutSeconds = Math.max(1L, lockTimeoutMillis / 1000L);
            try (PreparedStatement ps = conn.prepareStatement(acquireSql())) {
                ps.setString(1, lockName);
                if (acquireTimeoutParamIndex() == 2)
                    ps.setInt(2, (int) timeoutSeconds);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean success = rs.next() && isAcquireSuccess(rs.getObject(1));
                    if (!success)
                        throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT)
                                .param(ARG_LOCK_NAME, lockName);
                }
            }
            held.put(lockName, conn);
        } catch (NopException e) {
            closeQuietly(conn);
            throw e;
        } catch (SQLException e) {
            closeQuietly(conn);
            throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_FAILED, e)
                    .param(ARG_LOCK_NAME, lockName);
        }
    }

    @Override
    public void release(String lockName) {
        Connection conn = held.remove(lockName);
        if (conn == null)
            return;
        try (PreparedStatement ps = conn.prepareStatement(releaseSql())) {
            ps.setString(1, lockName);
            ps.execute();
        } catch (SQLException e) {
            LOG.warn("nop.err.db-migration.guard-lock-release-failed:lockName={}", lockName, e);
        } finally {
            closeQuietly(conn);
        }
    }

    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException e) {
            LOG.trace("guard lock connection close failed", e);
        }
    }

    /** 方言选型（dialect name 驱动；未列方言回落守卫表实现——裁定⑧方言矩阵）。 */
    static IMigrationLockGuard forDialect(IDialect dialect, IJdbcTemplate jdbcTemplate,
                                          String querySpace, long lockTimeoutMillis) {
        String name = dialect == null || dialect.getName() == null
                ? "" : dialect.getName().toLowerCase();
        if (name.contains("mysql") || name.contains("mariadb"))
            return new MySqlAdvisoryLockGuard(jdbcTemplate, querySpace, lockTimeoutMillis);
        if (name.contains("postgres"))
            return new PostgresAdvisoryLockGuard(jdbcTemplate, querySpace, lockTimeoutMillis);
        return new GuardTableLockGuard(jdbcTemplate, querySpace, lockTimeoutMillis, 100L);
    }
}
