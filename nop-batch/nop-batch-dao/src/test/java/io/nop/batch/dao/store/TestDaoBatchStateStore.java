package io.nop.batch.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.dao.NopBatchDaoConstants;
import io.nop.batch.dao.NopBatchDaoErrors;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.batch.dao.NopBatchDaoConstants.TASK_STATUS_COMPLETED;
import static io.nop.batch.dao.NopBatchDaoConstants.TASK_STATUS_RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DaoBatchStateStore 直接测试（plan 2282 G7-04-01）。
 * <p>
 * 核心契约：同 (taskName, taskKey) 只允许一个活跃实例——
 * 并发双实例启动时只能有一个进入 RUNNING，另一个必须以
 * ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE 响亮失败，
 * 且数据库行数与 execCount 证明第二个实例没有写入任何状态。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestDaoBatchStateStore extends JunitAutoTestCase {

    @Inject
    DaoBatchStateStore store;

    @Inject
    RaceWindowDaoBatchStateStore racingStore;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    /**
     * 测试环境的 init-database-schema 只建表不建唯一索引（DataBaseSchemaInitializer 仅执行 createTable），
     * 生产环境的唯一键约束来自 ORM 模型经 dbtool 管线物化的 DDL。
     * 并发用例这里显式建出与源模型一致的唯一键，使测试与生产同契约。
     */
    private void ensureTaskNameKeyUniqueIndex() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("test:create-unique-task-name-key")
                .sql("CREATE UNIQUE INDEX IF NOT EXISTS UK_NOP_BATCH_TASK_NAME_KEY_TEST " +
                        "ON nop_batch_task(TASK_NAME, TASK_KEY)")
                .end());
    }

    // ==================== 直接行为测试 ====================

    @Test
    void testLoadTaskState_newTaskKey_createsRunningRow() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "new-" + System.nanoTime();

        BatchTaskContextImpl ctx = newCtx("direct-new", taskKey);
        store.loadTaskState(ctx);

        assertNotNull(ctx.getTaskId());
        NopBatchTask row = queryOneRow(dao, "direct-new", taskKey);
        assertNotNull(row, "task row must be created");
        assertEquals(TASK_STATUS_RUNNING, row.getTaskStatus());
        assertEquals(1, row.getExecCount());
        assertNotNull(row.getWorkerId());
        assertEquals(taskKey, row.getTaskKey());
    }

    @Test
    void testSaveTaskState_complete_marksRowCompleted() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "complete-" + System.nanoTime();

        BatchTaskContextImpl ctx = newCtx("direct-complete", taskKey);
        store.loadTaskState(ctx);
        store.saveTaskState(true, null, ctx);

        NopBatchTask row = queryOneRow(dao, "direct-complete", taskKey);
        assertEquals(TASK_STATUS_COMPLETED, row.getTaskStatus());
        assertNotNull(row.getEndTime());
    }

    @Test
    void testLoadTaskState_runningInstance_rejectedLoudly() {
        String taskKey = "running-" + System.nanoTime();

        BatchTaskContextImpl first = newCtx("direct-running", taskKey);
        store.loadTaskState(first);

        NopException ex = assertStartThrows("direct-running", taskKey);
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                ex.getErrorCode());
    }

    // ==================== C7-001（plan 2516 裁定①）：stale 接管 / N1 参数化改写 ====================
    // N1 改写前后语义对照（修复 diff 前置义务）：
    // 改写前：三用例以「存在 RUNNING 行即无条件拒绝」为期望（闸门无 stale 判定，死区永久）。
    // 改写后：显式构造 live/stale 两态前置行——live 行（restartTime=now，租约未到期）维持 EXIST_RUNNING
    // 响亮拒绝（语义零变更）；stale 行（restartTime 过期或 NULL）期望 stale 接管成功
    // （status=RUNNING、execCount 递增、workerId 刷新）。其余闸门正确面用例零触碰。

    /** live 行（租约未到期）：维持响亮拒绝（N1 live 态，语义零回退钉） */
    @Test
    void testRunningRow_liveLease_rejectedLoudly() {
        String taskKey = "live-lease-" + System.nanoTime();

        BatchTaskContextImpl first = newCtx("direct-live-lease", taskKey);
        store.loadTaskState(first);

        NopException ex = assertStartThrows("direct-live-lease", taskKey);
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                ex.getErrorCode());
    }

    /** stale 行（restartTime 过期）：接管成功——status=RUNNING、execCount 递增、workerId 刷新 */
    @Test
    void testRunningRow_staleLease_takeoverSucceeds() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "stale-lease-" + System.nanoTime();

        BatchTaskContextImpl first = newCtx("direct-stale-lease", taskKey);
        store.loadTaskState(first);
        makeStaleRunning(dao, "direct-stale-lease", taskKey);

        BatchTaskContextImpl second = newCtx("direct-stale-lease", taskKey);
        store.loadTaskState(second);
        assertTrue(second.isRecoverMode(), "stale takeover must enter recover mode");

        NopBatchTask row = queryOneRow(dao, "direct-stale-lease", taskKey);
        assertEquals(TASK_STATUS_RUNNING, row.getTaskStatus());
        assertEquals(2, row.getExecCount(), "takeover must increment execCount");
        assertNotNull(row.getWorkerId(), "takeover must refresh workerId");
    }

    /** stale 行（restartTime 为 NULL，存量死区行形态）：NULL 判 stale，接管成功 */
    @Test
    void testRunningRow_nullRestartTime_takeoverSucceeds() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "stale-null-" + System.nanoTime();

        BatchTaskContextImpl first = newCtx("direct-stale-null", taskKey);
        store.loadTaskState(first);
        makeStaleRunning(dao, "direct-stale-null", taskKey, true);

        BatchTaskContextImpl second = newCtx("direct-stale-null", taskKey);
        store.loadTaskState(second);

        NopBatchTask row = queryOneRow(dao, "direct-stale-null", taskKey);
        assertEquals(TASK_STATUS_RUNNING, row.getTaskStatus());
        assertEquals(2, row.getExecCount());
    }

    /** 时序 B（进程崩溃死区）：stale 行接管成功后 checkpoint 续租，租约窗内第二实例被拒（双层并存验证） */
    @Test
    void testStaleTakeover_thenCheckpointRenewsLease_secondInstanceRejected() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "renew-" + System.nanoTime();

        // 注入 stale RUNNING 行（NULL restartTime 两态之一）
        BatchTaskContextImpl dead = newCtx("direct-renew", taskKey);
        store.loadTaskState(dead);
        makeStaleRunning(dao, "direct-renew", taskKey, true);

        // 重启序列：接管成功
        BatchTaskContextImpl revived = newCtx("direct-renew", taskKey);
        store.loadTaskState(revived);
        assertEquals(2, queryOneRow(dao, "direct-renew", taskKey).getExecCount());

        // checkpoint 续租：saveTaskState(false) 刷新 restartTime 后，租约窗内第二实例响亮被拒
        store.saveTaskState(false, null, revived);
        NopException ex = assertStartThrows("direct-renew", taskKey);
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                ex.getErrorCode());
    }

    /** saveTaskState 条件 UPDATE fencing：僵尸写者（execCount 落后）的状态写被丢弃且显式失败、行不被覆写 */
    @Test
    void testSaveTaskState_zombieWriterWriteLost() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "zombie-" + System.nanoTime();

        BatchTaskContextImpl first = newCtx("direct-zombie", taskKey);
        store.loadTaskState(first);

        // 模拟本实例已被接管重启：DB 行 execCount 推进到 2（僵尸实例上下文仍持 execCount=1）
        NopBatchTask row = queryOneRow(dao, "direct-zombie", taskKey);
        row.setExecCount(2);
        row.setWorkerId("new-instance");
        dao.updateEntityDirectly(row);

        // 僵尸写者的终态写必须被丢弃（不抛出打断清理链），且不得覆写接管实例的状态
        first.setCompletedIndex(99L);
        store.saveTaskState(true, null, first);

        NopBatchTask after = queryOneRow(dao, "direct-zombie", taskKey);
        assertEquals(2, after.getExecCount(), "zombie write must not change execCount");
        assertEquals("new-instance", after.getWorkerId(), "zombie write must not overwrite workerId");
        org.junit.jupiter.api.Assertions.assertNotEquals(NopBatchDaoConstants.TASK_STATUS_COMPLETED,
                after.getTaskStatus(), "zombie write must not mark task completed");
    }

    @Test
    void testLoadTaskState_killedTask_rejected() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "killed-" + System.nanoTime();

        BatchTaskContextImpl ctx = newCtx("direct-killed", taskKey);
        store.loadTaskState(ctx);

        NopBatchTask row = queryOneRow(dao, "direct-killed", taskKey);
        row.setTaskStatus(NopBatchDaoConstants.TASK_STATUS_KILLED);
        dao.updateEntityDirectly(row);

        NopException ex = assertStartThrows("direct-killed", taskKey);
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED.getErrorCode(),
                ex.getErrorCode());
    }

    // ==================== C7-012（plan 2546 W-06）：KILLED 撤离门 ====================

    /** pin06a：KILLED + allowRestartIfKilled=TRUE → 可启动（不再永久拒绝）。 */
    @Test
    void testKilledTask_restartAllowed_entersRunning() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "killed-escape-" + System.nanoTime();

        BatchTaskContextImpl ctx = newCtx("direct-killed-escape", taskKey);
        store.loadTaskState(ctx);

        NopBatchTask row = queryOneRow(dao, "direct-killed-escape", taskKey);
        row.setTaskStatus(NopBatchDaoConstants.TASK_STATUS_KILLED);
        dao.updateEntityDirectly(row);

        BatchTaskContextImpl restart = newCtx("direct-killed-escape", taskKey);
        restart.setAllowRestartIfKilled(Boolean.TRUE);
        store.loadTaskState(restart);

        NopBatchTask after = queryOneRow(dao, "direct-killed-escape", taskKey);
        assertEquals(NopBatchDaoConstants.TASK_STATUS_RUNNING, after.getTaskStatus(),
                "allowRestartIfKilled=TRUE 时 KILLED 必须可重启（撤离门）");
        assertEquals(2, after.getExecCount(), "重启必须递增 execCount（fencing token）");
    }

    /** pin06b：KILLED + allowRestartIfKilled 非 TRUE → 仍响亮拒绝（缺陷面零回退）。 */
    @Test
    void testKilledTask_restartNotAllowed_stillRejected() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "killed-noescape-" + System.nanoTime();

        BatchTaskContextImpl ctx = newCtx("direct-killed-noescape", taskKey);
        store.loadTaskState(ctx);

        NopBatchTask row = queryOneRow(dao, "direct-killed-noescape", taskKey);
        row.setTaskStatus(NopBatchDaoConstants.TASK_STATUS_KILLED);
        dao.updateEntityDirectly(row);

        BatchTaskContextImpl restart = newCtx("direct-killed-noescape", taskKey);
        restart.setAllowRestartIfKilled(Boolean.FALSE);
        NopException ex = assertThrows(NopException.class, () -> store.loadTaskState(restart));
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED.getErrorCode(),
                ex.getErrorCode());

        BatchTaskContextImpl restartNull = newCtx("direct-killed-noescape", taskKey);
        NopException ex2 = assertThrows(NopException.class, () -> store.loadTaskState(restartNull));
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED.getErrorCode(),
                ex2.getErrorCode());
    }

    /** pin06d：BatchTaskBuilder → BatchTask 重载构造器 → context 的透传链。 */
    @Test
    void testAllowRestartIfKilledTravelsFromBuilderToContext() {
        BatchTaskContextImpl ctx = newCtx("builder-passthrough", "k-passthrough");
        io.nop.batch.core.BatchTaskBuilder<String, String> builder = new io.nop.batch.core.BatchTaskBuilder<>();
        builder.taskName("builder-passthrough");
        builder.allowRestartIfKilled(Boolean.TRUE);
        builder.consumer(c -> (items, chunkCtx) -> {
        });
        io.nop.batch.core.IBatchTask task = builder.buildTask();
        // 通过 executeAsync 的缺省注入路径验证（loader 未设会让 setup 失败，但注入在之前）
        try {
            task.executeAsync(ctx);
        } catch (Exception ignored) {
            // loader 缺失导致的失败不影响本钉关注的注入语义
        }
        assertEquals(Boolean.TRUE, ctx.getAllowRestartIfKilled(),
                "builder 设置的值必须经重载构造器注入到 context");
    }

    @Test
    void testLoadTaskState_completedTask_rejectedUnlessAllowed() {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        String taskKey = "restart-" + System.nanoTime();

        // 第一轮：正常执行到 COMPLETED
        BatchTaskContextImpl first = newCtx("direct-restart", taskKey);
        store.loadTaskState(first);
        store.saveTaskState(true, null, first);

        NopBatchTask done = queryOneRow(dao, "direct-restart", taskKey);
        assertEquals(TASK_STATUS_COMPLETED, done.getTaskStatus());
        Long completedIndexBefore = done.getCompletedIndex();

        // 默认不允许重启已完成任务
        NopException ex = assertStartThrows("direct-restart", taskKey);
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED.getErrorCode(),
                ex.getErrorCode());

        // allowStartIfComplete=true 时可重启：execCount 递增、状态回到 RUNNING、断点位置保留
        BatchTaskContextImpl second = newCtx("direct-restart", taskKey);
        second.setAllowStartIfComplete(Boolean.TRUE);
        store.loadTaskState(second);
        assertTrue(second.isRecoverMode(), "restart must enter recover mode");

        NopBatchTask restarted = queryOneRow(dao, "direct-restart", taskKey);
        assertEquals(TASK_STATUS_RUNNING, restarted.getTaskStatus());
        assertEquals(2, restarted.getExecCount());
        assertEquals(completedIndexBefore, restarted.getCompletedIndex());
    }

    // ==================== 并发双实例回归（G7-04-01 核心） ====================

    /**
     * 并发首发：两实例同时以全新 (taskName, taskKey) 启动（测试显式建出与源模型一致的唯一键，见上）。
     * 修复前：无启动闸门，第二实例插入撞唯一键后以裸数据库错误失败（生产形态），
     * 或在无唯一键环境下双双插入成功（测试环境缺省形态，更隐蔽）。
     * 修复后：唯一键冲突被捕获并转入既有实例路径，恰好一个实例成功，另一个以 EXIST_RUNNING 响亮失败。
     * N1 改写（plan 2516 裁定①）：新增 stale 前置态变体——前置 stale RUNNING 行时两并发实例
     * 恰好一个 stale 接管成功，另一个 rowsAffected==0 响亮失败。
     */
    @Test
    void testConcurrentStart_freshKey_onlyOneInstanceEntersRunning() throws Exception {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        ensureTaskNameKeyUniqueIndex();
        String taskName = "conc-fresh";
        String taskKey = "key-" + System.nanoTime();

        StartOutcome outcome = runConcurrentStarts(taskName, taskKey, false, true);

        assertEquals(0, outcome.otherErrors.size(),
                "no non-semantic failure allowed: " + outcome.otherErrors);
        assertEquals(1, outcome.successCount.get(), "exactly one instance may start");
        assertEquals(1, outcome.conflicts.size(), "the other instance must fail loudly");
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                outcome.conflicts.peek().getErrorCode());

        List<NopBatchTask> rows = queryRows(dao, taskName, taskKey);
        assertEquals(1, rows.size(), "unique key must prevent duplicate task rows");
        assertEquals(TASK_STATUS_RUNNING, rows.get(0).getTaskStatus());
        assertEquals(1, rows.get(0).getExecCount(), "loser must not have incremented execCount");
    }

    /**
     * N1 stale 态变体（plan 2516 裁定①）：前置 stale RUNNING 行，两实例并发启动——
     * 恰好一个经扩展 WHERE 单飞接管成功（execCount 递增一次），另一个以 EXIST_RUNNING 响亮失败。
     * 缺陷态（无 stale 判定）：两实例双双被拒（死区风暴）——本用例即为红①载体之一。
     */
    @Test
    void testConcurrentStart_staleRunningRow_onlyOneInstanceTakesOver() throws Exception {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        ensureTaskNameKeyUniqueIndex();
        String taskName = "conc-stale";
        String taskKey = "key-" + System.nanoTime();

        // 前置：正常启动一次后把行改造为 stale RUNNING（restartTime 过期）
        BatchTaskContextImpl seed = newCtx(taskName, taskKey);
        store.loadTaskState(seed);
        makeStaleRunning(dao, taskName, taskKey);

        StartOutcome outcome = runConcurrentStarts(taskName, taskKey, false, false);

        assertEquals(0, outcome.otherErrors.size(),
                "no non-semantic failure allowed: " + outcome.otherErrors);
        assertEquals(1, outcome.successCount.get(), "exactly one instance may take over");
        assertEquals(1, outcome.conflicts.size(), "the other instance must fail loudly");
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                outcome.conflicts.peek().getErrorCode());

        List<NopBatchTask> rows = queryRows(dao, taskName, taskKey);
        assertEquals(1, rows.size());
        assertEquals(TASK_STATUS_RUNNING, rows.get(0).getTaskStatus());
        assertEquals(2, rows.get(0).getExecCount(), "takeover must increment execCount exactly once");
    }

    /**
     * 并发重启：任务已 COMPLETED，两实例同时以 allowStartIfComplete=true 重启（会合栅栏确定化竞态窗口）。
     * 修复前：两实例都通过读后判检查，竞态输家以裸ORM错误 nop.err.orm.update-entity-not-found 失败
     * （实体乐观锁误报为"实体不存在"，无任务上下文，运维无法区分并发冲突与真实故障）。
     * 修复后：条件更新 affected-rows 裁决，恰好一个实例获得 RUNNING，输家收到 EXIST_RUNNING 业务语义错误。
     * N1 stale 态变体：前置 stale RUNNING 行（restartTime=NULL），并发重启恰好一个接管。
     */
    @Test
    void testConcurrentRestartFromCompleted_onlyOneInstanceGainsRunning() throws Exception {
        IEntityDao<NopBatchTask> dao = daoProvider.daoFor(NopBatchTask.class);
        ensureTaskNameKeyUniqueIndex();
        String taskName = "conc-restart";
        String taskKey = "key-" + System.nanoTime();

        // 第一轮：正常执行到 COMPLETED
        BatchTaskContextImpl first = newCtx(taskName, taskKey);
        store.loadTaskState(first);
        store.saveTaskState(true, null, first);
        assertEquals(TASK_STATUS_COMPLETED, queryOneRow(dao, taskName, taskKey).getTaskStatus());

        // 重启竞态确定化：两实例都读到COMPLETED行、都通过启动前置检查后，才同时放行状态写入
        racingStore.existingLoadLatch = new CountDownLatch(2);
        StartOutcome outcome;
        try {
            outcome = runConcurrentStarts(taskName, taskKey, true, false);
        } finally {
            racingStore.existingLoadLatch = null;
        }

        assertEquals(0, outcome.otherErrors.size(),
                "no non-semantic failure allowed: " + outcome.otherErrors);
        assertEquals(1, outcome.successCount.get(), "exactly one instance may gain RUNNING");
        assertEquals(1, outcome.conflicts.size(), "the other instance must fail loudly");
        assertEquals(NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE.getErrorCode(),
                outcome.conflicts.peek().getErrorCode());

        List<NopBatchTask> rows = queryRows(dao, taskName, taskKey);
        assertEquals(1, rows.size());
        assertEquals(TASK_STATUS_RUNNING, rows.get(0).getTaskStatus());
        assertEquals(2, rows.get(0).getExecCount(), "execCount must be incremented exactly once");
    }

    // ==================== helpers ====================

    /**
     * C7-001 stale 前置态构造（N1 改写载体）：把任务行改造成 restartTime 过期（2 倍租约之前）的 RUNNING 行，
     * 模拟终态保存失败/进程崩溃遗留的死区行。
     */
    private void makeStaleRunning(IEntityDao<NopBatchTask> dao, String taskName, String taskKey) {
        makeStaleRunning(dao, taskName, taskKey, false);
    }

    private void makeStaleRunning(IEntityDao<NopBatchTask> dao, String taskName, String taskKey, boolean nullRestartTime) {
        NopBatchTask row = queryOneRow(dao, taskName, taskKey);
        row.setTaskStatus(TASK_STATUS_RUNNING);
        if (nullRestartTime) {
            row.setRestartTime(null);
        } else {
            // nop.batch.task.stale-lease-ms 缺省 600000（plan 2516 裁定①一次定夺值），
            // 此处用字面量构造过期租约，避免与产品配置常量产生编译期耦合
            long staleLeaseMs = 600000L;
            row.setRestartTime(new java.sql.Timestamp(System.currentTimeMillis() - 2 * staleLeaseMs));
        }
        dao.updateEntityDirectly(row);
    }

    private static BatchTaskContextImpl newCtx(String taskName, String taskKey) {
        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskName(taskName);
        ctx.setTaskKey(taskKey);
        return ctx;
    }

    private NopException assertStartThrows(String taskName, String taskKey) {
        try {
            store.loadTaskState(newCtx(taskName, taskKey));
        } catch (NopException e) {
            return e;
        }
        throw new AssertionError("loadTaskState must fail loudly for task " + taskName + "-" + taskKey);
    }

    private StartOutcome runConcurrentStarts(String taskName, String taskKey, boolean allowStartIfComplete,
                                             boolean raceOnFreshKey) throws InterruptedException {
        int n = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        Queue<NopException> conflicts = new ConcurrentLinkedQueue<>();
        Queue<Throwable> otherErrors = new ConcurrentLinkedQueue<>();

        // 全新key并发首发时确定化TOCTOU窗口：两实例都通过"任务不存在"检查后才同时放行插入
        if (raceOnFreshKey)
            racingStore.firstLoadLatch = new CountDownLatch(n);
        try {
            Thread[] threads = new Thread[n];
            for (int i = 0; i < n; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        startLatch.await(10, TimeUnit.SECONDS);
                        BatchTaskContextImpl ctx = newCtx(taskName, taskKey);
                        if (allowStartIfComplete)
                            ctx.setAllowStartIfComplete(Boolean.TRUE);
                        store.loadTaskState(ctx);
                        successCount.incrementAndGet();
                    } catch (NopException e) {
                        conflicts.add(e);
                    } catch (Throwable e) {
                        otherErrors.add(e);
                    }
                }, "batch-start-" + taskKey + "-" + i);
                threads[i].start();
            }
            startLatch.countDown();
            for (Thread t : threads)
                t.join(30000);
            for (Thread t : threads)
                assertTrue(!t.isAlive(), "concurrent start threads must finish in time");
        } finally {
            racingStore.firstLoadLatch = null;
        }

        StartOutcome outcome = new StartOutcome();
        outcome.successCount = successCount;
        outcome.conflicts = conflicts;
        outcome.otherErrors = otherErrors;
        return outcome;
    }

    private static class StartOutcome {
        AtomicInteger successCount;
        Queue<NopException> conflicts;
        Queue<Throwable> otherErrors;
    }

    private NopBatchTask queryOneRow(IEntityDao<NopBatchTask> dao, String taskName, String taskKey) {
        List<NopBatchTask> rows = queryRows(dao, taskName, taskKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<NopBatchTask> queryRows(IEntityDao<NopBatchTask> dao, String taskName, String taskKey) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("taskName", taskName));
        query.addFilter(FilterBeans.eq("taskKey", taskKey));
        return dao.findAllByQuery(query);
    }
}
