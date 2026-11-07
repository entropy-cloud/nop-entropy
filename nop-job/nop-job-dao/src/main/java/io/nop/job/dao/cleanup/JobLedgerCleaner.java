/* Copyright (c) 2017-2024 Nop Platform. */
package io.nop.job.dao.cleanup;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity.NopJobTaskLog;
import io.nop.orm.dao.IOrmEntityDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * CRA 8.47 R4-07 W-04 (1.4-C7-004, adjudication H.1): R01 三表保留族周期清理载体。
 * 形态 A 周期链（ledger-retention-policy §3.6）。三表独立谓词删除（子表先行序）。
 * 载体纪律：DB 删除 IO — timer.executeOn(worker) hop（2497 §七）。
 */
public class JobLedgerCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(JobLedgerCleaner.class);
    private final IDaoProvider daoProvider;
    private int retentionDays = 30;
    private long cleanupIntervalMs = 3600000L;
    private int deleteBatchSize = 1000;
    private Future<?> cleanupFuture;

    public JobLedgerCleaner(IDaoProvider daoProvider) { this.daoProvider = daoProvider; }
    public void setRetentionDays(int days) {
        if (days < 0) throw new IllegalArgumentException("retention-days must be >= 0");
        this.retentionDays = days;
    }
    public void setCleanupIntervalMs(long ms) {
        if (ms < 60000) throw new IllegalArgumentException("cleanup-interval must be >= 60000ms");
        this.cleanupIntervalMs = ms;
    }
    public void setDeleteBatchSize(int size) {
        if (size <= 0) throw new IllegalArgumentException("delete-batch-size must be > 0");
        this.deleteBatchSize = size;
    }
    public int getRetentionDays() { return retentionDays; }

    public void doStart() {
        if (retentionDays == 0) { LOG.info("nop.job.ledger.cleanup-disabled"); return; }
        IScheduledExecutor ex = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        cleanupFuture = ex.scheduleWithFixedDelay(this::cleanupAllSafe,
                cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);
    }
    public void doStop() {
        if (cleanupFuture != null) { cleanupFuture.cancel(false); cleanupFuture = null; }
    }

    /** R01 三表独立谓词删除——子表先行序。可见于测试（接线断言腿）。 */
    public void cleanupAll() {
        if (retentionDays <= 0) return;
        long cutoff = CoreMetrics.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        int taskLog = cleanupTaskLogs(cutoff);
        int task = cleanupTasks(cutoff);
        int fire = cleanupFires(cutoff);
        LOG.info("nop.job.ledger.cleanup-done:taskLog={},task={},fire={}", taskLog, task, fire);
    }

    void cleanupAllSafe() {
        try { cleanupAll(); } catch (Exception e) { LOG.error("nop.job.ledger.cleanup-fail", e); }
    }

    @SuppressWarnings("unchecked")
    private int cleanupTaskLogs(long cutoff) {
        IOrmEntityDao<NopJobTaskLog> dao = (IOrmEntityDao<NopJobTaskLog>) daoProvider.daoFor(NopJobTaskLog.class);
        QueryBean query = new QueryBean();
        query.addOrderField("logTime", true);
        query.setLimit(deleteBatchSize);
        int count = 0;
        for (NopJobTaskLog log : (List<NopJobTaskLog>) dao.findAllByQuery(query)) {
            if (log.getLogTime() != null && log.getLogTime().getTime() < cutoff) {
                dao.deleteEntity(log);
                count++;
            } else break; // ordered by logTime asc — rest are newer
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int cleanupTasks(long cutoff) {
        IOrmEntityDao<NopJobTask> dao = (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
        QueryBean query = new QueryBean();
        query.addOrderField("endTime", true);
        query.setLimit(deleteBatchSize);
        int count = 0;
        for (NopJobTask task : (List<NopJobTask>) dao.findAllByQuery(query)) {
            if (task.getEndTime() != null && task.getEndTime().getTime() < cutoff
                    && isTerminalTask(task.getTaskStatus())) {
                dao.deleteEntity(task);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int cleanupFires(long cutoff) {
        IOrmEntityDao<NopJobFire> dao = (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
        QueryBean query = new QueryBean();
        query.addOrderField("endTime", true);
        query.setLimit(deleteBatchSize);
        int count = 0;
        for (NopJobFire fire : (List<NopJobFire>) dao.findAllByQuery(query)) {
            if (fire.getEndTime() != null && fire.getEndTime().getTime() < cutoff
                    && JobFireStateMachine.isTerminal(fire.getFireStatus())) {
                dao.deleteEntity(fire);
                count++;
            }
        }
        return count;
    }

    /** 终态 task 集合——SUSPICIOUS(15) 显式排除（resolvable-pending 面不可删）。 */
    public static boolean isTerminalTask(Integer status) {
        return status != null && (status == 30 || status == 40 || status == 50 || status == 60);
    }

    // JobFireStateMachine.isTerminal — inline to avoid import cycle
    static boolean isTerminalFire(Integer status) {
        return status != null && status >= 30;
    }

    // Minimal local FSM reference
    static class JobFireStateMachine {
        static boolean isTerminal(Integer status) { return status != null && status >= 30; }
    }
}
