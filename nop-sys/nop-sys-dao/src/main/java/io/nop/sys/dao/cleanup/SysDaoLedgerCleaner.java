package io.nop.sys.dao.cleanup;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConfigs;
import io.nop.sys.dao.NopSysErrors;
import io.nop.sys.dao.entity.NopSysChangeLog;
import io.nop.sys.dao.entity.NopSysLock;
import io.nop.sys.dao.entity._gen._NopSysChangeLog;
import io.nop.sys.dao.entity._gen._NopSysLock;
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

import static io.nop.sys.dao.NopSysErrors.ARG_CONFIG_KEY;
import static io.nop.sys.dao.NopSysErrors.ARG_CONFIG_VALUE;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_LEDGER_CONFIG_INVALID;

/**
 * 5.2-MA-04（plan 2502）：nop-sys 双面账本保留期周期清理（形态 A 周期低频接入，照抄
 * {@code SysDaoMessageService.cleanupExpiredEvents} 的 doStart 接线形态）。
 * <p>
 * 双 face（参数语义 SSOT = ai-dev/design/concurrency/ledger-retention-policy.md §3.4 成员 6 行 +
 * §3.2 nop-sys 行，多语义 face 模块 face 段必配）：
 * <ul>
 * <li>{@code nop_sys_lock}：锚列 {@code expireAt}，谓词 {@code expireAt < now - retention}——
 * 覆盖「崩溃持有者+无人争用」僵尸锁行（既有删除点均为争用触发式惰性清理，对不再被争用的
 * 资源零覆盖）；活跃锁行随续约刷新 expireAt，不会进入清理谓词</li>
 * <li>{@code nop_sys_change_log}：锚列 {@code createTime}，留存语义按设计裁定⑤——缺省 90 天
 * （全平台缺省最长，审计留痕宁长勿短）；{@code nop.sys.ledger.changelog.retention-days=0}
 * 为「永久保留」的显式 opt-in（合规场景），禁止三无状态延续</li>
 * </ul>
 * 生命周期接线（防 C7-005「清理 API 存在但零生产装配」空壳形态）：IoC 容器创建单例 bean →
 * {@link LifeCycleSupport} {@code @PostConstruct start()} → doStart 周期调度（绝缘六参入口，
 * timer 只触发、任务体 hop worker，taskName=nop-sys-ledger-cleanup）。错配置（负保留期/
 * 非正批删上限/周期低于 60s 下限）启动期 fail-fast；双 face 保留期均为 0 时不调度。
 * 清理体任何 Throwable 由绝缘层（IPeriodicTaskListener）观测并重臂，失败 ERROR 显式可见 +
 * 失败计数——不静默吞掉。
 */
public class SysDaoLedgerCleaner extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoLedgerCleaner.class);

    public static final String TASK_NAME = "nop-sys-ledger-cleanup";

    private IDaoProvider daoProvider;
    private IScheduledExecutor timer;
    private java.util.concurrent.Executor worker;

    // null = 未显式注入，回退 NopSysDaoConfigs 声明的键值（缺省值唯一事实源在 Configs 接口）
    private Integer lockRetentionDays;
    private Integer changelogRetentionDays;
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

    public void setLockRetentionDays(Integer lockRetentionDays) {
        this.lockRetentionDays = lockRetentionDays;
    }

    public void setChangelogRetentionDays(Integer changelogRetentionDays) {
        this.changelogRetentionDays = changelogRetentionDays;
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
        int lockDays = resolveLockRetentionDays();
        int changelogDays = resolveChangelogRetentionDays();
        long intervalMillis = resolveLedgerCleanupInterval().toMillis();
        int batchSize = resolveLedgerDeleteBatchSize();

        if (lockDays < 0) {
            throw new NopException(ERR_SYS_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.sys.ledger.lock.retention-days")
                    .param(ARG_CONFIG_VALUE, lockDays);
        }
        if (changelogDays < 0) {
            throw new NopException(ERR_SYS_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.sys.ledger.changelog.retention-days")
                    .param(ARG_CONFIG_VALUE, changelogDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_SYS_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.sys.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < NopSysDaoConfigs.SYS_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_SYS_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.sys.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (lockDays == 0 && changelogDays == 0) {
            LOG.info("nop.sys.ledger.cleanup-disabled:lock=0,changelog=0(permanent opt-in)");
            return;
        }

        if (timer == null)
            timer = GlobalExecutors.globalTimer();
        if (worker == null)
            worker = GlobalExecutors.globalWorker();

        long lockRetentionMillis = lockDays * 24L * 3600_000L;
        long changelogRetentionMillis = changelogDays * 24L * 3600_000L;
        cleanupFuture = timer.executeOn(worker).scheduleWithFixedDelay(
                () -> purgeExpiredLedgerRows(lockRetentionMillis, changelogRetentionMillis, batchSize),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                TASK_NAME, new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.sys.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.sys.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.sys.ledger.cleanup-started:lockRetentionDays={},changelogRetentionDays={}"
                + ",intervalMillis={},batchSize={}", lockDays, changelogDays, intervalMillis, batchSize);
    }

    @Override
    protected void doStop() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
            LOG.info("nop.sys.ledger.cleanup-stopped");
        }
    }

    /**
     * 单轮双面清扫（5.2-MA-04）。清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     *
     * @param lockRetentionMillis      lock 面保留期（毫秒）；0 = 该面禁用（跳过）
     * @param changelogRetentionMillis changelog 面保留期（毫秒）；0 = 永久保留 opt-in（跳过该面）
     * @param batchSize                单轮单表删除上限
     * @return 本轮删除行数（双面合计）
     */
    public long purgeExpiredLedgerRows(long lockRetentionMillis, long changelogRetentionMillis, int batchSize) {
        long now = lockDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        long total = 0L;

        // ① lock 面：锚列 expireAt，谓词 expireAt < now - retention
        //（僵尸锁行——崩溃持有者+无人争用；活跃锁随续约刷新 expireAt 不入谓词）
        if (lockRetentionMillis > 0) {
            total += purgeExpiredLocks(new Timestamp(now - lockRetentionMillis), batchSize);
        }

        // ② changelog 面：锚列 createTime，留存语义=裁定⑤（90d 缺省 / 0=永久保留显式 opt-in）
        if (changelogRetentionMillis > 0) {
            total += purgeExpiredChangeLogs(new Timestamp(now - changelogRetentionMillis), batchSize);
        }

        return total;
    }

    /**
     * 锚列逐表显式落名（设计 §3.1 第 7 项：禁止隐式选列）：lock=expireAt（Timestamp 列），
     * changelog=createTime（Timestamp 列）。
     */
    private long purgeExpiredLocks(Timestamp cutoff, int batchSize) {
        IEntityDao<NopSysLock> dao = lockDao();
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        pick.addFilter(FilterBeans.lt(_NopSysLock.PROP_NAME_expireAt, cutoff));
        List<NopSysLock> expired = dao.findAllByQuery(pick);
        if (expired.isEmpty())
            return 0L;

        // 复合主键 (lockName, lockGroup)：按拾取集精确批删（or-of-and 对），不越拾取集删除
        List<io.nop.api.core.beans.TreeBean> pairs = new ArrayList<>(expired.size());
        for (NopSysLock lock : expired) {
            pairs.add(FilterBeans.and(
                    FilterBeans.eq(_NopSysLock.PROP_NAME_lockName, lock.getLockName()),
                    FilterBeans.eq(_NopSysLock.PROP_NAME_lockGroup, lock.getLockGroup())));
        }
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.or(pairs));
        return dao.deleteByQuery(delete);
    }

    private long purgeExpiredChangeLogs(Timestamp cutoff, int batchSize) {
        IEntityDao<NopSysChangeLog> dao = changelogDao();
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        pick.addFilter(FilterBeans.lt(_NopSysChangeLog.PROP_NAME_changeTime, cutoff));
        List<NopSysChangeLog> expired = dao.findAllByQuery(pick);
        if (expired.isEmpty())
            return 0L;

        List<Object> sids = new ArrayList<>(expired.size());
        for (NopSysChangeLog log : expired) {
            sids.add(log.getSid());
        }
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(_NopSysChangeLog.PROP_NAME_sid, sids));
        return dao.deleteByQuery(delete);
    }

    private int resolveLockRetentionDays() {
        return lockRetentionDays != null ? lockRetentionDays
                : NopSysDaoConfigs.CFG_SYS_LEDGER_LOCK_RETENTION_DAYS.get();
    }

    private int resolveChangelogRetentionDays() {
        return changelogRetentionDays != null ? changelogRetentionDays
                : NopSysDaoConfigs.CFG_SYS_LEDGER_CHANGELOG_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : NopSysDaoConfigs.CFG_SYS_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : NopSysDaoConfigs.CFG_SYS_LEDGER_DELETE_BATCH_SIZE.get();
    }

    private IEntityDao<NopSysLock> lockDao() {
        return daoProvider.daoFor(NopSysLock.class);
    }

    private IEntityDao<NopSysChangeLog> changelogDao() {
        return daoProvider.daoFor(NopSysChangeLog.class);
    }
}
