package io.nop.batch.dao.cleanup;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.batch.dao.NopBatchDaoConfigs;
import io.nop.batch.dao.NopBatchDaoConstants;
import io.nop.batch.dao.entity.NopBatchFile;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.batch.dao.entity.NopBatchTaskVar;
import io.nop.batch.dao.entity._gen._NopBatchFile;
import io.nop.batch.dao.entity._gen._NopBatchRecordResult;
import io.nop.batch.dao.entity._gen._NopBatchTask;
import io.nop.batch.dao.entity._gen._NopBatchTaskVar;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.batch.dao.NopBatchDaoErrors.ARG_CONFIG_KEY;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_CONFIG_VALUE;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_LEDGER_CONFIG_INVALID;

/**
 * 1.7-C7-003（plan 2502）：batch 三账本表保留期清理组件（形态 A 周期低频接入）。
 * <p>
 * 参数语义 SSOT = ai-dev/design/concurrency/ledger-retention-policy.md §3.4 成员 2 行
 * （与 nop-retry 1.6-C7-001 同设计同参——批次对齐声明 batch-proposal §四 L314）。清理谓词
 * 时间锚列显式落名：
 * <ul>
 * <li>{@code nop_batch_task}：锚列 endTime、谓词 taskStatus 为终态
 * （COMPLETED/FAILED/CANCELLED/KILLED，即 taskStatus >= TASK_STATUS_COMPLETED）</li>
 * <li>{@code nop_batch_task_var} / {@code nop_batch_file}：级联随任务行（batchTaskId IN），无独立 TTL</li>
 * <li>{@code nop_batch_record_result}：锚列 createTime（独立 TTL 通道）+ 终态级联双通道</li>
 * </ul>
 * 生命周期接线（防 C7-005「清理 API 零生产装配」空壳形态）：IoC 容器创建单例 bean →
 * {@link LifeCycleSupport} {@code @PostConstruct start()} → doStart 周期调度（与
 * nop-sys cleanupExpiredEvents 同构：timer 只触发、任务体 hop worker 载体）。错配置
 * （负保留期/非正批删上限/周期低于 60s 下限）启动期 fail-fast；retention-days=0 显式禁用。
 * 清理体任何 Throwable 由绝缘层（IPeriodicTaskListener）观测并重臂，失败 ERROR 显式可见
 * + 失败计数——不静默吞掉。
 */
public class DaoBatchLedgerCleaner extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(DaoBatchLedgerCleaner.class);

    private IDaoProvider daoProvider;
    private IScheduledExecutor timer;
    private java.util.concurrent.Executor worker;

    // null = 未显式注入，回退 NopBatchDaoConfigs 声明的键值（缺省值唯一事实源在 Configs 接口）
    private Integer ledgerRetentionDays;
    private Duration ledgerCleanupInterval;
    private Integer ledgerDeleteBatchSize;

    private Future<?> cleanupFuture;
    private final AtomicLong cleanupFailureCount = new AtomicLong();

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setWorker(java.util.concurrent.Executor worker) {
        this.worker = worker;
    }

    public void setLedgerRetentionDays(Integer ledgerRetentionDays) {
        this.ledgerRetentionDays = ledgerRetentionDays;
    }

    public void setLedgerCleanupInterval(Duration ledgerCleanupInterval) {
        this.ledgerCleanupInterval = ledgerCleanupInterval;
    }

    public void setLedgerDeleteBatchSize(Integer ledgerDeleteBatchSize) {
        this.ledgerDeleteBatchSize = ledgerDeleteBatchSize;
    }

    public long getCleanupFailureCount() {
        return cleanupFailureCount.get();
    }

    @Override
    protected void doStart() {
        int retentionDays = resolveLedgerRetentionDays();
        long intervalMillis = resolveLedgerCleanupInterval().toMillis();
        int batchSize = resolveLedgerDeleteBatchSize();

        if (retentionDays < 0) {
            throw new NopException(ERR_BATCH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.batch.ledger.retention-days")
                    .param(ARG_CONFIG_VALUE, retentionDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_BATCH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.batch.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < NopBatchDaoConfigs.BATCH_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_BATCH_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.batch.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (retentionDays == 0) {
            LOG.info("nop.batch.ledger.cleanup-disabled");
            return;
        }

        if (timer == null)
            timer = GlobalExecutors.globalTimer();
        if (worker == null)
            worker = GlobalExecutors.globalWorker();

        long retentionMillis = retentionDays * 24L * 3600_000L;
        cleanupFuture = timer.executeOn(worker).scheduleWithFixedDelay(() -> purgeExpiredLedgerRows(
                        retentionMillis, batchSize),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                "nop-batch-ledger-cleanup", new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.batch.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.batch.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.batch.ledger.cleanup-started:retentionDays={},intervalMillis={},batchSize={}",
                retentionDays, intervalMillis, batchSize);
    }

    @Override
    protected void doStop() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
            LOG.info("nop.batch.ledger.cleanup-stopped");
        }
    }

    /**
     * 单轮清理（1.7-C7-003）。清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     *
     * @return 本轮删除行数（task/var/file/record_result 合计）
     */
    public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
        long now = taskDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cutoff = new Timestamp(now - retentionTimeMillis);
        long total = 0L;

        // ① 终态任务行（COMPLETED/FAILED/CANCELLED/KILLED 且 endTime < cutoff）：
        // record_result / task_var / file 按 batchTaskId IN 级联删除，再删任务行
        QueryBean taskQuery = new QueryBean();
        taskQuery.setLimit(batchSize);
        taskQuery.addFilter(FilterBeans.ge(_NopBatchTask.PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_COMPLETED));
        taskQuery.addFilter(FilterBeans.lt(_NopBatchTask.PROP_NAME_endTime, cutoff));
        List<NopBatchTask> expiredTasks = taskDao().findAllByQuery(taskQuery);
        if (!expiredTasks.isEmpty()) {
            List<String> taskIds = new ArrayList<>(expiredTasks.size());
            for (NopBatchTask task : expiredTasks) {
                taskIds.add(task.getSid());
            }
            total += deleteByBatchTaskIds(_NopBatchRecordResult.PROP_NAME_batchTaskId, recordResultDao(), taskIds);
            total += deleteByBatchTaskIds(_NopBatchTaskVar.PROP_NAME_batchTaskId, taskVarDao(), taskIds);
            total += deleteByBatchTaskIds(_NopBatchFile.PROP_NAME_batchTaskId, fileDao(), taskIds);

            QueryBean taskDelete = new QueryBean();
            taskDelete.addFilter(FilterBeans.in(_NopBatchTask.PROP_NAME_sid, taskIds));
            total += taskDao().deleteByQuery(taskDelete);
        }

        // ② record_result 独立 TTL 通道（锚列 createTime）：覆盖任务行长期非终态期间的增长面。
        // 有界拾取后逐行直接删除（复合主键 PK 删除，不经 session 归属检查）。
        QueryBean resultQuery = new QueryBean();
        resultQuery.setLimit(batchSize);
        resultQuery.addFilter(FilterBeans.lt(_NopBatchRecordResult.PROP_NAME_createTime, cutoff));
        List<NopBatchRecordResult> expiredResults = recordResultDao().findAllByQuery(resultQuery);
        for (NopBatchRecordResult result : expiredResults) {
            recordResultDao().deleteEntityDirectly(result);
            total++;
        }
        return total;
    }

    private long deleteByBatchTaskIds(String propName, IEntityDao<?> dao, List<String> taskIds) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(propName, taskIds));
        return dao.deleteByQuery(query);
    }

    private int resolveLedgerRetentionDays() {
        return ledgerRetentionDays != null ? ledgerRetentionDays
                : NopBatchDaoConfigs.CFG_BATCH_LEDGER_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : NopBatchDaoConfigs.CFG_BATCH_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : NopBatchDaoConfigs.CFG_BATCH_LEDGER_DELETE_BATCH_SIZE.get();
    }

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
}
