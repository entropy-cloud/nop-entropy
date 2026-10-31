/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.db.migration.AbstractMigrationTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红⑧（C1-003/C10-021，裁定⑧）：迁移历史表跨进程互斥 + upsert 保留。
 * 缺陷态红判据：并发 ensureHistoryTableExists 的 check-then-act 竞态（DDL 重复执行）+
 * 并发 recordMigration 双 INSERT 主键炸裂；修复后：守卫互斥串行化 + 条件 INSERT 捕获冲突
 * 回落 UPDATE（upsert 保留——RP-1 钉保持绿）。
 *
 * <p>方言 seam（m3）：mysql/pg 咨询锁离线以 SQL 文本级断言；真库行为用例 docker-gated
 * （勘误 9 三联口径）。H2 守卫表实现为缺省离线测试方言。</p>
 */
public class TestMigrationHistoryGuard extends AbstractMigrationTestCase {

    // ------------------------------------------------------------------
    // 红⑧：并发守卫
    // ------------------------------------------------------------------

    @Test
    public void testConcurrentEnsureHistoryTable_DdlIdempotentUnderGuard() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<Future<Void>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<Void>) () -> {
                go.await();
                try {
                    // 每线程独立 manager 实例（多节点语义），独立守卫实例
                    MigrationHistoryManager manager = new MigrationHistoryManager(jdbcTemplate, "default");
                    manager.ensureHistoryTableExists(dialect);
                } catch (Throwable e) {
                    errors.add(e);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<Void> future : futures)
            future.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(List.of(), errors, "concurrent DDL guard must not fail");
        // 历史表存在且守卫表无残留锁行（全部 finally 释放）
        MigrationHistoryManager manager = new MigrationHistoryManager(jdbcTemplate, "default");
        manager.recordMigration(newRecord("1.0.0", true));
        assertTrue(manager.getExecutedVersions().contains("1.0.0"),
                "history table must be usable after concurrent guard");
    }

    @Test
    public void testConcurrentRecordMigration_NoDirtyRowOverwrite() throws Exception {
        MigrationHistoryManager bootstrap = new MigrationHistoryManager(jdbcTemplate, "default");
        bootstrap.ensureHistoryTableExists(dialect);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<Future<Void>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String desc = "writer-" + i;
            futures.add(pool.submit((Callable<Void>) () -> {
                go.await();
                try {
                    MigrationHistoryManager manager = new MigrationHistoryManager(jdbcTemplate, "default");
                    manager.recordMigration(newRecord("2.0.0", true, desc));
                } catch (Throwable e) {
                    errors.add(e);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<Void> future : futures)
            future.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(List.of(), errors,
                "concurrent recordMigration must not explode on primary key conflict");
        // 终态单行：多节点并发写收敛为一次覆盖写（upsert 保留），无双 INSERT 炸裂
        assertEquals(1, countHistoryRows("2.0.0"), "exactly one history row per version");
        MigrationRecord record = bootstrap.getMigrationByVersion("2.0.0");
        assertEquals(true, record.isSuccess());
    }

    @Test
    public void testGuardTableLock_ExclusiveAndReleasable() {
        GuardTableLockGuard first = new GuardTableLockGuard(jdbcTemplate, "default", 200, 20);
        GuardTableLockGuard second = new GuardTableLockGuard(jdbcTemplate, "default", 200, 20);

        first.acquire("test-lock");
        // 唯一冲突 = 持锁方在：类型化失败（非静默）
        NopException e = assertThrows(NopException.class, () -> second.acquire("test-lock"));
        assertEquals(ERR_DB_MIGRATION_GUARD_LOCK_TIMEOUT.getErrorCode(), e.getErrorCode());

        first.release("test-lock");
        // owner 比对释放后可重获
        second.acquire("test-lock");
        second.release("test-lock");
    }

    // ------------------------------------------------------------------
    // 方言 seam（m3）：SQL 文本级离线断言（真库行为 docker-gated）
    // ------------------------------------------------------------------

    @Test
    public void testDialectSeam_SqlTextAndSelection() {
        assertEquals("SELECT GET_LOCK(?, ?)", MySqlAdvisoryLockGuard.ACQUIRE_SQL);
        assertEquals("SELECT RELEASE_LOCK(?)", MySqlAdvisoryLockGuard.RELEASE_SQL);
        assertEquals("SELECT pg_advisory_lock(?)", PostgresAdvisoryLockGuard.ACQUIRE_SQL);
        assertEquals("SELECT pg_advisory_unlock(?)", PostgresAdvisoryLockGuard.RELEASE_SQL);

        // 未列方言（h2）回落守卫表实现；方言选择由 dialect name 驱动
        IMigrationLockGuard guard = AbstractAdvisoryLockGuard.forDialect(
                dialect, jdbcTemplate, "default", 1000);
        assertTrue(guard instanceof GuardTableLockGuard,
                "h2 must fall back to the guard-table implementation, got: " + guard.getClass().getName());
    }

    // ------------------------------------------------------------------

    private MigrationRecord newRecord(String version, boolean success) {
        return newRecord(version, success, "desc");
    }

    private MigrationRecord newRecord(String version, boolean success, String description) {
        MigrationRecord record = new MigrationRecord();
        record.setVersion(version);
        record.setDescription(description);
        record.setSuccess(success);
        record.setInstalledBy("test");
        return record;
    }

    private int countHistoryRows(String version) {
        Long count = jdbcTemplate.findLong(
                io.nop.core.lang.sql.SQL.begin()
                        .sql("SELECT COUNT(*) FROM " + MigrationHistoryManager.TABLE_NAME
                                + " WHERE version = ?", version)
                        .end(), 0L);
        return count == null ? 0 : count.intValue();
    }
}
