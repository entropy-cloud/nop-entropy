package io.nop.batch.dao.cleanup;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.batch.dao.NopBatchDaoConstants;
import io.nop.batch.dao.entity.NopBatchFile;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.batch.dao.entity.NopBatchTaskVar;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.batch.dao.NopBatchDaoConstants.TASK_STATUS_COMPLETED;
import static io.nop.batch.dao.NopBatchDaoConstants.TASK_STATUS_RUNNING;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.7-C7-003（plan 2502）回归钉：batch 三账本表保留期清理。
 * <p>
 * 清理谓词时间锚列显式落名（ledger-retention-policy §3.4 成员 2 行，与 nop-retry
 * 1.6-C7-001 同设计同参）：nop_batch_task=endTime(+终态谓词 taskStatus>=COMPLETED)、
 * nop_batch_task_var/nop_batch_file=batchTaskId 级联、nop_batch_record_result=createTime
 * （独立 TTL）+ 级联双通道。断言面：终态任务行超期清理（UUID taskKey 形态）、级联、
 * 独立 TTL、未到期/非终态保留（不误伤）、幂等、doStart 生产链接线（防 C7-005 空壳）、
 * 错配置启动期 fail-fast。
 * <p>
 * 红证据（E2 回退论证 + 变异）：基线无任何清理入口（第 2 轮二态判定「连死代码都没有」，
 * 回退即断言全红）；实现面变异（purge 体置空）时超期清理断言全红，快照
 * _tmp/phase3-c7-003-red.log / _tmp/phase3-c7-003-green.log。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestDaoBatchLedgerCleanup_C7_003 extends JunitAutoTestCase {

    private static final long DAY_MS = 24L * 3600_000L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private final List<DaoBatchLedgerCleaner> liveCleaners = new java.util.ArrayList<>();

    private DaoBatchLedgerCleaner newCleaner() {
        DaoBatchLedgerCleaner cleaner = new DaoBatchLedgerCleaner();
        liveCleaners.add(cleaner);
        cleaner.setDaoProvider(daoProvider);
        return cleaner;
    }

    /**
     * ① UUID taskKey 终态任务行超期清理：task + task_var + file + record_result 级联删除；
     * 非终态（RUNNING）与未到期任务行保留。
     */
    @Test
    void testC7_003_expiredTerminalTaskCascadeCleanup() {
        // 过期终态任务（endTime 回拨 31 天）+ 全套级联行
        NopBatchTask expired = newTask("task-c7-003-expired", TASK_STATUS_COMPLETED);
        expired.setEndTime(new Timestamp(System.currentTimeMillis() - 31 * DAY_MS));
        taskDao().saveEntity(expired);
        saveCascadeRows(expired.getSid());

        // 未到期终态任务：endTime 为当前，必须保留
        NopBatchTask fresh = newTask("task-c7-003-fresh", TASK_STATUS_COMPLETED);
        fresh.setEndTime(new Timestamp(System.currentTimeMillis()));
        taskDao().saveEntity(fresh);
        saveCascadeRows(fresh.getSid());

        // 非终态 RUNNING 任务：即使 startTime 很旧也不清理
        NopBatchTask running = newTask("task-c7-003-running", TASK_STATUS_RUNNING);
        running.setStartTime(new Timestamp(System.currentTimeMillis() - 31 * DAY_MS));
        taskDao().saveEntity(running);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        DaoBatchLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 4,
                "expired task + var + file + record_result must be purged (3 cascade rows + task), deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(taskDao().getEntityById("task-c7-003-expired"), "expired terminal task must be deleted");
        assertEquals(0, countCascadeRows("task-c7-003-expired"), "cascade rows of expired task must be deleted");

        assertNotNull(taskDao().getEntityById("task-c7-003-fresh"), "unexpired terminal task must be retained");
        assertEquals(3, countCascadeRows("task-c7-003-fresh"), "cascade rows of unexpired task must be retained");
        assertNotNull(taskDao().getEntityById("task-c7-003-running"), "non-terminal (RUNNING) task must be retained");
    }

    /**
     * ② record_result 独立 TTL 通道（锚列 createTime）：任务行非终态期间的增长面也按 TTL 清理。
     */
    @Test
    void testC7_003_recordResultTtlSweepIndependentOfTaskState() {
        NopBatchTask running = newTask("task-c7-003-ttl", TASK_STATUS_RUNNING);
        running.setStartTime(new Timestamp(System.currentTimeMillis() - 31 * DAY_MS));
        taskDao().saveEntity(running);

        NopBatchRecordResult expiredResult = newRecordResult(running.getSid(), "key-c7-003-expired");
        recordResultDao().saveEntity(expiredResult);
        backdate("NopBatchRecordResult", "createTime", running.getSid(), "key-c7-003-expired", 31 * DAY_MS);

        NopBatchRecordResult freshResult = newRecordResult(running.getSid(), "key-c7-003-fresh");
        recordResultDao().saveEntity(freshResult);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        DaoBatchLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 1, "expired record_result must be swept by independent TTL channel");

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(findRecordResult(running.getSid(), "key-c7-003-expired"),
                "expired record_result must be deleted");
        assertNotNull(findRecordResult(running.getSid(), "key-c7-003-fresh"),
                "unexpired record_result must be retained (idempotency ledger within window)");
    }

    /**
     * ④ 清理幂等：重复触发无害，第二轮零删除。
     */
    @Test
    void testC7_003_purgeIdempotent() {
        NopBatchTask expired = newTask("task-c7-003-idem", TASK_STATUS_COMPLETED);
        expired.setEndTime(new Timestamp(System.currentTimeMillis() - 31 * DAY_MS));
        taskDao().saveEntity(expired);
        saveCascadeRows(expired.getSid());

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        DaoBatchLedgerCleaner cleaner = newCleaner();
        long first = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(first >= 4, "first round must delete task + cascade rows");
        long second = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertEquals(0, second, "second round must be a no-op (idempotent predicate)");
    }

    /**
     * 接线验证（rule 23）：doStart 生产生命周期链真实调度清理链，被调度命令运行时
     * 清理入口在真实 DB 链上生效（过期任务行经 scheduled command 删除——非仅 API 存在）。
     */
    @Test
    void testC7_003_lifecycleChainInvokesLedgerCleanup() {
        // 过期终态任务行：经「被调度命令」清理（端到端：doStart → command → purge → DB 删除）
        NopBatchTask expired = newTask("task-c7-003-wiring", TASK_STATUS_COMPLETED);
        expired.setEndTime(new Timestamp(System.currentTimeMillis() - 31 * DAY_MS));
        taskDao().saveEntity(expired);

        CapturingTimer timer = new CapturingTimer();
        DaoBatchLedgerCleaner cleaner = newCleaner();
        cleaner.setDaoProvider(daoProvider);
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(30);
        cleaner.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        cleaner.setLedgerDeleteBatchSize(500);

        cleaner.start();

        assertEquals("nop-batch-ledger-cleanup", timer.taskName.get(),
                "wiring: cleaner lifecycle chain must schedule the ledger cleanup chain");
        assertEquals(3600_000L, timer.delayMillis.get(), "cleanup interval passed to scheduler");

        // 触发被调度的清理命令本体：清理入口在真实 DB 链上生效
        timer.command.get().run();

        ormTemplate.flushSession();
        ormTemplate.clearSession();
        assertNull(taskDao().getEntityById("task-c7-003-wiring"),
                "expired task must be purged through the scheduled cleanup command (wiring chain)");

        cleaner.stop();
        assertTrue(timer.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /**
     * retention-days=0 为显式禁用：不调度清理链。
     */
    @Test
    void testC7_003_zeroRetentionDisablesCleanupScheduling() {
        CapturingTimer timer = new CapturingTimer();
        DaoBatchLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(0);

        cleaner.start();

        assertNull(timer.taskName.get(), "retention-days=0 must disable cleanup scheduling");
        cleaner.stop();
    }

    /**
     * fail-fast 包络（设计 §3.1 第 5 项）：错配置启动期拒绝，不降级继续。
     */
    @Test
    void testC7_003_invalidLedgerConfigFailsFast() {
        DaoBatchLedgerCleaner negativeRetention = newCleaner();
        negativeRetention.setTimer(new CapturingTimer());
        negativeRetention.setWorker(Runnable::run);
        negativeRetention.setLedgerRetentionDays(-1);
        NopException ex1 = assertThrows(NopException.class, negativeRetention::start,
                "negative retention must fail fast at startup");
        assertEquals("nop.err.batch.ledger-config-invalid", ex1.getErrorCode());

        DaoBatchLedgerCleaner zeroBatch = newCleaner();
        zeroBatch.setTimer(new CapturingTimer());
        zeroBatch.setWorker(Runnable::run);
        zeroBatch.setLedgerDeleteBatchSize(0);
        NopException ex2 = assertThrows(NopException.class, zeroBatch::start,
                "non-positive batch cap must fail fast at startup");
        assertEquals("nop.err.batch.ledger-config-invalid", ex2.getErrorCode());

        DaoBatchLedgerCleaner tinyInterval = newCleaner();
        tinyInterval.setTimer(new CapturingTimer());
        tinyInterval.setWorker(Runnable::run);
        tinyInterval.setLedgerCleanupInterval(java.time.Duration.ofSeconds(30));
        NopException ex3 = assertThrows(NopException.class, tinyInterval::start,
                "cleanup interval below 60s floor must fail fast at startup");
        assertEquals("nop.err.batch.ledger-config-invalid", ex3.getErrorCode());
    }

    // ==================== 测试载体 ====================

    /** 捕获调度参数的 timer（wiring 断言载体）。 */
    static class CapturingTimer implements IScheduledExecutor {
        final AtomicReference<String> taskName = new AtomicReference<>();
        final AtomicReference<Long> delayMillis = new AtomicReference<>();
        final AtomicReference<Runnable> command = new AtomicReference<>();
        final AtomicReference<Boolean> cancelled = new AtomicReference<>(false);

        class ObservedFuture extends CompletableFuture<Object> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                                String taskName, IPeriodicTaskListener listener) {
            this.taskName.set(taskName);
            this.delayMillis.set(unit.toMillis(delay));
            this.command.set(command);
            return new ObservedFuture();
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            this.command.set(command);
            this.delayMillis.set(unit.toMillis(delay));
            return new ObservedFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        /**
         * 生产形态为 BindScheduledExecutor 包装（timer 只触发、任务体 hop worker）；本捕获载体
         * 原样返回 this，使六参绝缘入口的调度参数（taskName/interval/command）可被直接观测——
         * Bind 载体与绝缘设施语义由平台自有测试覆盖，不在本接线断言面内。
         */
        @Override
        public IScheduledExecutor executeOn(java.util.concurrent.Executor executor) {
            return this;
        }

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return new CompletableFuture<>();
        }

        @Override
        public void destroy() {
            // 测试载体：无可释放资源
        }

        @Override
        public void refreshConfig() {
            // 测试载体：无热更面
        }

        @Override
        public String getName() {
            return "capturing-timer";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }
    }

    // ==================== Helpers ====================

    private IEntityDao<NopBatchTask> taskDao() {
        return daoProvider.daoFor(NopBatchTask.class);
    }

    private IEntityDao<NopBatchRecordResult> recordResultDao() {
        return daoProvider.daoFor(NopBatchRecordResult.class);
    }

    private IEntityDao<NopBatchTaskVar> taskVarDao() {
        return daoProvider.daoFor(NopBatchTaskVar.class);
    }

    private IEntityDao<NopBatchFile> fileDao() {
        return daoProvider.daoFor(NopBatchFile.class);
    }

    private NopBatchTask newTask(String sid, int taskStatus) {
        NopBatchTask task = taskDao().newEntity();
        task.setSid(sid);
        task.setTaskName("c7-003-task");
        task.setTaskKey(sid);
        task.setTaskStatus(taskStatus);
        task.setStartTime(new Timestamp(System.currentTimeMillis()));
        task.setExecCount(1);
        task.setCompletedIndex(-1L);
        task.setCompleteItemCount(0L);
        task.setProcessItemCount(0L);
        task.setSkipItemCount(0L);
        task.setWriteItemCount(0L);
        task.setRetryItemCount(0);
        task.setLoadRetryCount(0);
        task.setLoadSkipCount(0L);
        task.setWorkerId(AppConfig.hostId());
        return task;
    }

    private void saveCascadeRows(String taskId) {
        NopBatchTaskVar var = taskVarDao().newEntity();
        var.setBatchTaskId(taskId);
        var.setFieldName("c7-003-var");
        var.setFieldType(1);
        var.setStringValue("v");
        taskVarDao().saveEntity(var);

        NopBatchFile file = fileDao().newEntity();
        file.setSid("file-" + taskId);
        file.setFileName("c7-003-file.dat");
        file.setFilePath("/tmp/c7-003/" + taskId + ".dat");
        file.setFileLength(1L);
        file.setFileCategory("test");
        file.setFileSource("job");
        file.setProcessState("N");
        file.setAcceptDate(java.time.LocalDate.now());
        file.setBatchTaskId(taskId);
        fileDao().saveEntity(file);

        recordResultDao().saveEntity(newRecordResult(taskId, "key-c7-003"));
    }

    private NopBatchRecordResult newRecordResult(String taskId, String recordKey) {
        NopBatchRecordResult result = recordResultDao().newEntity();
        result.setBatchTaskId(taskId);
        result.setRecordKey(recordKey);
        result.setResultStatus(0);
        result.setRetryCount(0);
        result.setBatchSize(1);
        result.setHandleStatus(0);
        return result;
    }

    private NopBatchRecordResult findRecordResult(String taskId, String recordKey) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(FilterBeans.eq("batchTaskId", taskId),
                FilterBeans.eq("recordKey", recordKey)));
        List<NopBatchRecordResult> results = recordResultDao().findAllByQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }

    private int countCascadeRows(String taskId) {
        int count = 0;
        count += countByTaskId("NopBatchTaskVar", taskId);
        count += countByTaskId("NopBatchFile", taskId);
        count += countByTaskId("NopBatchRecordResult", taskId);
        return count;
    }

    private int countByTaskId(String entityName, String taskId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("batchTaskId", taskId));
        return daoProvider.dao(entityName).findAllByQuery(query).size();
    }

    private void backdate(String entityName, String propName, String taskId, String recordKey, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update " + entityName + " o set o." + propName + " = ? where o.batchTaskId = ? and o.recordKey = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), taskId, recordKey)
                .end();
        ormTemplate.executeUpdate(sql);
    }
}
