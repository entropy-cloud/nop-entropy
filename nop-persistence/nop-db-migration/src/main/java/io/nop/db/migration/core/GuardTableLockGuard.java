/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

import static io.nop.db.migration.DbMigrationErrors.ARG_LOCK_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_GUARD_LOCK_FAILED;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT;

/**
 * 守卫表互斥实现（plan 2518 裁定⑧方言矩阵的 H2 及未列方言缺省形态，离线可跑）：
 * 单行主键 + owner 标识 + timestamp 的条件 INSERT 单行守卫表——条件 INSERT 成功 = 持锁；
 * 唯一冲突 = 持锁方在（重试至超时后类型化失败）；释放 = finally 显式 DELETE 本 owner 守卫行
 * （owner 比对，只删自己插入的行）。守卫表创建自身以 {@code CREATE TABLE IF NOT EXISTS} 幂等
 * 承载，无二层蛋鸡问题。
 *
 * <p>崩溃残留显式声明：进程崩溃未走 finally 时守卫行残留 → 人工恢复（DELETE 残留行）——
 * H2 为测试方言，可接受；生产方言（mysql/pg）走连接级咨询锁，连接断开自动释放无此残留面。</p>
 */
public class GuardTableLockGuard implements IMigrationLockGuard {
    private static final Logger LOG = LoggerFactory.getLogger(GuardTableLockGuard.class);

    public static final String GUARD_TABLE_NAME = "nop_db_migration_lock_guard";

    /** 守卫表 DDL（幂等）。 */
    public static final String GUARD_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS " + GUARD_TABLE_NAME + " ("
                    + "lock_name VARCHAR(200) PRIMARY KEY, "
                    + "owner VARCHAR(100) NOT NULL, "
                    + "lock_time TIMESTAMP)";

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private final long lockTimeoutMillis;
    private final long retryIntervalMillis;
    private final String owner = "owner-" + CoreMetrics.currentTimeMillis() + "-"
            + java.util.UUID.randomUUID();
    private volatile boolean guardTableReady;

    public GuardTableLockGuard(IJdbcTemplate jdbcTemplate, String querySpace,
                               long lockTimeoutMillis, long retryIntervalMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace;
        this.lockTimeoutMillis = lockTimeoutMillis;
        this.retryIntervalMillis = retryIntervalMillis;
    }

    private void ensureGuardTable() {
        if (guardTableReady)
            return;
        jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                .name("create-migration-guard-lock-table")
                .append(GUARD_TABLE_DDL)
                .end());
        guardTableReady = true;
    }

    @Override
    public void acquire(String lockName) {
        ensureGuardTable();
        long deadline = CoreMetrics.currentTimeMillis() + lockTimeoutMillis;
        while (true) {
            try {
                long updated = jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                        .name("acquire-migration-guard-lock")
                        .sql("INSERT INTO " + GUARD_TABLE_NAME + " (lock_name, owner, lock_time) VALUES (")
                        .param(lockName).sql(",").param(owner)
                        .sql(",").param(new Timestamp(CoreMetrics.currentTimeMillis()))
                        .sql(")").end());
                if (updated == 1)
                    return; // 条件 INSERT 成功 = 持锁
            } catch (Exception conflict) {
                // 唯一冲突 = 持锁方在：重试至超时（失败语义见下，非静默继续）
                LOG.trace("nop.db-migration.guard-lock-conflict:lockName={}", lockName, conflict);
            }
            if (CoreMetrics.currentTimeMillis() >= deadline)
                throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT)
                        .param(ARG_LOCK_NAME, lockName);
            try {
                Thread.sleep(retryIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopException(ERR_DB_MIGRATION_GUARD_LOCK_FAILED, e)
                        .param(ARG_LOCK_NAME, lockName);
            }
        }
    }

    @Override
    public void release(String lockName) {
        try {
            // owner 比对：只删自己插入的守卫行
            jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                    .name("release-migration-guard-lock")
                    .sql("DELETE FROM " + GUARD_TABLE_NAME + " WHERE lock_name = ")
                    .param(lockName)
                    .sql(" AND owner = ").param(owner)
                    .end());
        } catch (Exception e) {
            // 释放失败留痕：守卫行残留需人工恢复（崩溃残留语义，成文登记）
            LOG.warn("nop.err.db-migration.guard-lock-release-failed:lockName={}", lockName, e);
        }
    }
}
