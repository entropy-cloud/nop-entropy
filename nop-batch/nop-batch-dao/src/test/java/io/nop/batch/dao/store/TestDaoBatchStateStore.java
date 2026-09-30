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
     * 并发重启：任务已 COMPLETED，两实例同时以 allowStartIfComplete=true 重启（会合栅栏确定化竞态窗口）。
     * 修复前：两实例都通过读后判检查，竞态输家以裸ORM错误 nop.err.orm.update-entity-not-found 失败
     * （实体乐观锁误报为"实体不存在"，无任务上下文，运维无法区分并发冲突与真实故障）。
     * 修复后：条件更新 affected-rows 裁决，恰好一个实例获得 RUNNING，输家收到 EXIST_RUNNING 业务语义错误。
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
