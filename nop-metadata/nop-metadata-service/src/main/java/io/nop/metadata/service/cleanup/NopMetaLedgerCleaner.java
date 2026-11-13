package io.nop.metadata.service.cleanup;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity._gen._NopMetaCatalog;
import io.nop.metadata.dao.entity._gen._NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity._gen._NopMetaProfilingResult;
import io.nop.metadata.dao.entity._gen._NopMetaQualityResult;
import io.nop.metadata.dao.entity._gen._NopMetaQualityScore;
import io.nop.metadata.dao.entity._gen._NopMetaReconciliationResult;
import io.nop.metadata.service.NopMetadataLedgerConfigs;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfStatusHistory;
import io.nop.wf.dao.entity.NopWfStepInstance;
import io.nop.wf.dao.entity.NopWfOutput;
import io.nop.wf.dao.entity.NopWfVar;
import io.nop.wf.dao.entity._gen._NopWfInstance;
import io.nop.wf.dao.entity._gen._NopWfStatusHistory;
import io.nop.wf.dao.entity._gen._NopWfStepInstance;
import io.nop.wf.dao.entity._gen._NopWfOutput;
import io.nop.wf.dao.entity._gen._NopWfVar;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.nop.metadata.service.NopMetadataErrors.ARG_CONFIG_KEY;
import static io.nop.metadata.service.NopMetadataErrors.ARG_CONFIG_VALUE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_LEDGER_CONFIG_INVALID;

/**
 * 5.5-W-02（plan 2502）：nop-metadata 六张 append-only 时序表保留期清理（形态 A 周期低频接入，
 * deleteByQuery 先例语句形态）。
 * <p>
 * 六表 = nop_meta_quality_result / nop_meta_quality_score / nop_meta_catalog /
 * nop_meta_profiling_result / nop_meta_reconciliation_result / nop_meta_model_changed_event
 * （mediumtext 前后双快照，无消费者——七面中最重的账本行）。cron 检查点 + auto-score +
 * 24 处事件发布点以机器速率写入、人工清理以人速进行——速率差即无界增长（5.5 审计 C7-004/
 * C7-101），机器速率写入面禁止人工 purge action 作为唯一载体（设计 §3.3 形态 C 判据）。
 * <p>
 * 参数语义 SSOT = ai-dev/design/concurrency/ledger-retention-policy.md §3.4 成员 4 行 +
 * §3.2 nop-metadata 行（键名/缺省值照抄，模块不发明）。清理谓词时间锚列显式落名：六表锚列
 * 均 {@code createTime}，谓词 {@code createTime < now - retention}——保留窗口内的
 * 「历史事实语义」（orm 注释钉死的 insert-only/不覆盖旧行）不受破坏，窗口外行整行清扫。
 * 有界拾取 + IN 批删（§3.1 第 3 项），谓词幂等（重复触发无害）。
 * <p>
 * <b>第七面（W-02 承接，plan 2528 裁定⑩——plan 2502 裁定④=选③边界声明的 R3-05 兑现）</b>：
 * C7-101 衍生 qualityBreachApproval 告警工作流实例（nop-wf 侧 {@link NopWfInstance}）第七清扫通道
 * 已并入本组件（{@link #purgeExpiredWfInstances}，与六表同一调度链/同一包络/同一参数集——七面同窗，
 * 零新配置键、零 nop-wf 产品 diff）。谓词：通道 1 终态 TTL（status ∈ 终态集 且 endTime 超窗）+
 * 通道 2 CREATED 零步残量孤儿（超窗 + 零 step 复核）；活跃审批实例（ACTIVATED/SUSPENDED/WAITING/
 * CREATED 已含 step）永不进删除集。nop-wf 侧仍零产品改动——拾取/删除均为既有 dao 消费面。
 * <p>
 * <b>G2 契约注记（设计 §3.5 差异条款②）</b>：nop_meta_quality_result 的保留窗即 R3-05
 * W-28 reJudge 可复用的历史窗上界；若需更长复用视野，参数修订只走设计文档
 * ledger-retention-policy.md，不得在 nop-metadata 内另设第二套保留语义。
 * <p>
 * 生命周期接线（防 C7-005「清理 API 存在但零生产装配」空壳形态）：IoC 容器创建单例 bean →
 * {@link LifeCycleSupport} {@code @PostConstruct start()} → doStart 周期调度（绝缘六参入口，
 * timer 只触发、任务体 hop worker，taskName=nop-metadata-ledger-cleanup）。错配置（负保留期/
 * 非正批删上限/周期低于 60s 下限）启动期 fail-fast；retention-days=0 显式禁用。清理体任何
 * Throwable 由绝缘层（IPeriodicTaskListener）观测并重臂，失败 ERROR 显式可见 + 失败计数——
 * 不静默吞掉。ORM 模型零触碰（不新增 cascadeDelete/索引，retention 全部走既有实体删除语句）。
 */
public class NopMetaLedgerCleaner extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(NopMetaLedgerCleaner.class);

    public static final String TASK_NAME = "nop-metadata-ledger-cleanup";

    /**
     * W-02 第七面（裁定⑩）：工作流实例终态集（常量经手写门面 {@link NopWfCoreConstants} 引用
     * 生成接口 {@code _NopWfCoreConstants :79-114}）。EXPIRED/FAILED 当前引擎无迁移路径
     * （现网不可达终态）——入谓词无害且前向完备（引擎后续扩展终态迁移时谓词自动覆盖）。
     */
    static final Set<Integer> WF_INSTANCE_TERMINAL_STATUSES = new LinkedHashSet<>(List.of(
            NopWfCoreConstants.WF_STATUS_COMPLETED,
            NopWfCoreConstants.WF_STATUS_EXPIRED,
            NopWfCoreConstants.WF_STATUS_FAILED,
            NopWfCoreConstants.WF_STATUS_KILLED));

    private IDaoProvider daoProvider;
    private IScheduledExecutor timer;
    private java.util.concurrent.Executor worker;

    // null = 未显式注入，回退 NopMetadataLedgerConfigs 声明的键值（缺省值唯一事实源在 Configs 接口）
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
            throw new NopException(ERR_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.metadata.ledger.retention-days")
                    .param(ARG_CONFIG_VALUE, retentionDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.metadata.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < NopMetadataLedgerConfigs.META_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.metadata.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (retentionDays == 0) {
            LOG.info("nop.metadata.ledger.cleanup-disabled");
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
                TASK_NAME, new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.metadata.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.metadata.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.metadata.ledger.cleanup-started:retentionDays={},intervalMillis={},batchSize={}",
                retentionDays, intervalMillis, batchSize);
    }

    @Override
    protected void doStop() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
            LOG.info("nop.metadata.ledger.cleanup-stopped");
        }
    }

    /**
     * 单轮六表清扫（5.5-W-02）。清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     * 六表锚列均 createTime（逐单表显式落名——禁止隐式选列）。
     *
     * @param retentionTimeMillis 保留期（毫秒）
     * @param batchSize           单轮单表删除上限
     * @return 本轮删除行数（六表合计）
     */
    public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
        long now = qualityResultDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cutoff = new Timestamp(now - retentionTimeMillis);
        long total = 0L;

        // 锚列 createTime（Timestamp 列）逐表显式传参（禁止隐式选列）；PK 有界拾取 + IN 批删，谓词幂等
        total += purgeByCreateTime(NopMetaQualityResult.class, _NopMetaQualityResult.PROP_NAME_qualityResultId,
                NopMetaQualityResult::getQualityResultId, _NopMetaQualityResult.PROP_NAME_createTime, cutoff, batchSize);
        total += purgeByCreateTime(NopMetaQualityScore.class, _NopMetaQualityScore.PROP_NAME_qualityScoreId,
                NopMetaQualityScore::getQualityScoreId, _NopMetaQualityScore.PROP_NAME_createTime, cutoff, batchSize);
        total += purgeByCreateTime(NopMetaCatalog.class, _NopMetaCatalog.PROP_NAME_metaCatalogId,
                NopMetaCatalog::getMetaCatalogId, _NopMetaCatalog.PROP_NAME_createTime, cutoff, batchSize);
        total += purgeByCreateTime(NopMetaProfilingResult.class, _NopMetaProfilingResult.PROP_NAME_profilingResultId,
                NopMetaProfilingResult::getProfilingResultId, _NopMetaProfilingResult.PROP_NAME_createTime, cutoff, batchSize);
        total += purgeByCreateTime(NopMetaReconciliationResult.class, _NopMetaReconciliationResult.PROP_NAME_resultId,
                NopMetaReconciliationResult::getResultId, _NopMetaReconciliationResult.PROP_NAME_createTime, cutoff, batchSize);
        total += purgeByCreateTime(NopMetaModelChangedEvent.class, _NopMetaModelChangedEvent.PROP_NAME_modelChangedEventId,
                NopMetaModelChangedEvent::getModelChangedEventId, _NopMetaModelChangedEvent.PROP_NAME_createTime, cutoff, batchSize);

        // W-02 第七面（plan 2528 裁定⑩，R3-05 承接）：nop-wf 侧告警工作流实例第七通道——
        // 与六表同一调度链/同一包络/同一参数集（七面同窗，零新配置键）
        total += purgeExpiredWfInstances(retentionTimeMillis, batchSize);

        return total;
    }

    /**
     * W-02 第七面（plan 2528 裁定⑩，R3-05 承接兑现）：qualityBreachApproval 等告警工作流实例
     * （nop-wf 侧 {@link NopWfInstance}）保留期清扫，双通道：
     * <ul>
     * <li>通道 1（终态实例 TTL）：{@code status ∈ 终态集} 且 {@code endTime < now − retention}
     *     （锚列 endTime 显式落名——终态迁移时刻，写入后不被业务回改的账龄事实列）。</li>
     * <li>通道 2（W-12 存量/崩溃窗残量孤儿）：{@code status = CREATED} 且
     *     {@code createTime < now − retention} 且<b>零 step 记录</b>（拾取后经
     *     {@link NopWfStepInstance} 按 wfId 集存在性复核）——覆盖 W-12 修复前存量孤儿与
     *     自清理失败残量；CREATED 且已有 step、SUSPENDED/WAITING/ACTIVATED 等活跃状态
     *     永不进删除集（活跃审批实例红线：审批中告警实例不可 TTL 误删）。</li>
     * </ul>
     * 子表（statusHistories/steps/outputs/globalVars，nop-wf.orm.xml {@code cascadeDelete="true"} 四关系）
     * 随父行 wfId IN 集传递清扫（ledger-retention-policy §3.1 第 7 项——子表不独立选锚、不独立 TTL）；
     * {@code works}（无 cascadeDelete）不在第七通道锚集内。有界拾取 + IN 批删（与六表通道同构），
     * 谓词幂等（重复触发无害）。
     *
     * @param retentionTimeMillis 保留期（毫秒）
     * @param batchSize           单轮单通道拾取上限
     * @return 本轮删除的工作流实例行数（子表随行清扫不计数）
     */
    public long purgeExpiredWfInstances(long retentionTimeMillis, int batchSize) {
        long now = wfInstanceDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cutoff = new Timestamp(now - retentionTimeMillis);
        long total = 0L;
        total += purgeTerminalWfInstances(cutoff, batchSize);
        total += purgeCreatedZeroStepWfInstances(cutoff, batchSize);
        return total;
    }

    /** 通道 1：终态实例 TTL（status ∈ 终态集 且 endTime 超窗）。 */
    private long purgeTerminalWfInstances(Timestamp cutoff, int batchSize) {
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        pick.addFilter(FilterBeans.and(
                FilterBeans.in(_NopWfInstance.PROP_NAME_status, WF_INSTANCE_TERMINAL_STATUSES),
                FilterBeans.lt(_NopWfInstance.PROP_NAME_endTime, cutoff)));
        return deletePickedWfInstances(pick);
    }

    /** 通道 2：CREATED 零步残量孤儿（status = CREATED 且 createTime 超窗 且零 step 复核）。 */
    private long purgeCreatedZeroStepWfInstances(Timestamp cutoff, int batchSize) {
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        pick.addFilter(FilterBeans.and(
                FilterBeans.eq(_NopWfInstance.PROP_NAME_status, NopWfCoreConstants.WF_STATUS_CREATED),
                FilterBeans.lt(_NopWfInstance.PROP_NAME_createTime, cutoff)));
        List<NopWfInstance> picked = wfInstanceDao().findAllByQuery(pick);
        if (picked.isEmpty())
            return 0L;

        // 零步复核：拾取集中已有 step 记录的实例为在途流程（start 半程推进中）——永不进删除集
        List<Object> pickedIds = new ArrayList<>(picked.size());
        for (NopWfInstance row : picked) {
            pickedIds.add(row.getWfId());
        }
        QueryBean stepProbe = new QueryBean();
        stepProbe.addFilter(FilterBeans.in(_NopWfStepInstance.PROP_NAME_wfId, pickedIds));
        Set<Object> wfIdsWithSteps = new HashSet<>();
        for (NopWfStepInstance step : daoProvider.daoFor(NopWfStepInstance.class).findAllByQuery(stepProbe)) {
            wfIdsWithSteps.add(step.getWfId());
        }
        List<Object> orphanIds = new ArrayList<>(pickedIds);
        orphanIds.removeIf(wfIdsWithSteps::contains);
        return deleteWfInstanceRowsWithChildren(orphanIds);
    }

    private long deletePickedWfInstances(QueryBean pick) {
        List<NopWfInstance> picked = wfInstanceDao().findAllByQuery(pick);
        if (picked.isEmpty())
            return 0L;
        List<Object> ids = new ArrayList<>(picked.size());
        for (NopWfInstance row : picked) {
            ids.add(row.getWfId());
        }
        return deleteWfInstanceRowsWithChildren(ids);
    }

    /** 父行 wfId IN 删除 + 四张 cascadeDelete 子表随父行主键 IN 集传递清扫（子表行不计数）。 */
    private long deleteWfInstanceRowsWithChildren(List<Object> wfIds) {
        if (wfIds.isEmpty())
            return 0L;
        deleteChildRowsByWfIds(NopWfStatusHistory.class, _NopWfStatusHistory.PROP_NAME_wfId, wfIds);
        deleteChildRowsByWfIds(NopWfStepInstance.class, _NopWfStepInstance.PROP_NAME_wfId, wfIds);
        deleteChildRowsByWfIds(NopWfOutput.class, _NopWfOutput.PROP_NAME_wfId, wfIds);
        deleteChildRowsByWfIds(NopWfVar.class, _NopWfVar.PROP_NAME_wfId, wfIds);
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(_NopWfInstance.PROP_NAME_wfId, wfIds));
        return wfInstanceDao().deleteByQuery(delete);
    }

    private <T extends IDaoEntity> long deleteChildRowsByWfIds(Class<T> entityClass, String wfIdProp,
                                                               List<Object> wfIds) {
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(wfIdProp, wfIds));
        return daoProvider.daoFor(entityClass).deleteByQuery(delete);
    }

    private IEntityDao<NopWfInstance> wfInstanceDao() {
        return daoProvider.daoFor(NopWfInstance.class);
    }

    private <T extends IDaoEntity> long purgeByCreateTime(Class<T> entityClass, String pkProp,
                                                          Function<T, Object> pkGetter, String anchorProp,
                                                          Timestamp cutoff, int batchSize) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean pick = new QueryBean();
        pick.setLimit(batchSize);
        pick.addFilter(FilterBeans.lt(anchorProp, cutoff));
        List<T> rows = dao.findAllByQuery(pick);
        if (rows.isEmpty())
            return 0L;
        List<Object> pks = new ArrayList<>(rows.size());
        for (T row : rows) {
            pks.add(pkGetter.apply(row));
        }
        QueryBean delete = new QueryBean();
        delete.addFilter(FilterBeans.in(pkProp, pks));
        return dao.deleteByQuery(delete);
    }

    private int resolveLedgerRetentionDays() {
        return ledgerRetentionDays != null ? ledgerRetentionDays
                : NopMetadataLedgerConfigs.CFG_META_LEDGER_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : NopMetadataLedgerConfigs.CFG_META_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : NopMetadataLedgerConfigs.CFG_META_LEDGER_DELETE_BATCH_SIZE.get();
    }

    private IEntityDao<NopMetaQualityResult> qualityResultDao() {
        return daoProvider.daoFor(NopMetaQualityResult.class);
    }
}
