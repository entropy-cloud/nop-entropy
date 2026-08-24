package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.helper.JobFireStateMachine;
import io.nop.job.dao.helper.JobScheduleStateMachine;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * plan 340 §2.5 (P2-4): 周期性对账 {@code schedule.activeFireCount} 与 live 非 terminal fire 计数，收敛
 * 完成/取消路径在 {@code @SingleSession} 约束下无法路径内重试导致的计数漂移。
 *
 * <p><b>刻意 non-{@code @SingleSession}</b>：现有 5 个 scanner（planner/dispatcher/completion/timeout/worker）
 * 均 {@code @SingleSession}（{@link AbstractBatchScanner} 注释约定），对账器是 deliberate 例外——它必须在独立
 * 会话里读 live（非缓存）fire 计数才能正确重算，这正是它存在的意义。{@code @SingleSession} 下
 * {@code requireEntityById} 返回缓存实体，reload 无法读到最新数据，路径内重试无意义（见
 * {@code JobFireStoreImpl.completeFireAndUpdateSchedule} 的注释）。
 *
 * <p><b>写回风暴防护</b>：单次扫描每 schedule 至多一次写回；遇版本冲突不重试（warn + 留待下个周期），
 * 避免热 schedule 上的写竞争。漂移是低频事件（仅并发版本冲突时累积），对账器以扫描间隔（复用 planner 的
 * {@code scan-interval-ms}）为节拍最终一致。
 */
public class JobScheduleCounterReconciler extends AbstractBatchScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobScheduleCounterReconciler.class);

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @InjectValue("@cfg:nop.job.coordinator.planner.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        applyScanIntervalMs(scanIntervalMs);
    }

    @InjectValue("@cfg:nop.job.coordinator.reconciler.batch-size|100")
    public void setBatchSize(int batchSize) {
        applyBatchSize(batchSize);
    }

    /**
     * 单批：取最多 {@code batchSize} 个 ENABLED 且 {@code activeFireCount>0} 的 schedule，逐个对账。
     * 返回 false（每周期一批；漂移低频，无需同一周期内多批 drain）。
     *
     * <p>不标注 {@code @SingleSession}——见类 javadoc 的 deviation 说明。
     */
    @Override
    protected boolean scanBatch() {
        List<NopJobSchedule> schedules = fetchSchedulesToReconcile();
        for (NopJobSchedule schedule : schedules) {
            try {
                reconcileSchedule(schedule);
            } catch (Exception e) {
                LOG.warn("nop.job.reconcile.schedule-failed:scheduleId={}",
                        schedule.getJobScheduleId(), e);
            }
        }
        return false;
    }

    private List<NopJobSchedule> fetchSchedulesToReconcile() {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopJobSchedule.PROP_NAME_scheduleStatus,
                _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        query.addFilter(FilterBeans.gt(NopJobSchedule.PROP_NAME_activeFireCount, 0));
        query.setLimit(batchSize);
        query.addOrderField(NopJobSchedule.PROP_NAME_jobScheduleId, false);
        return scheduleDao().findAllByQuery(query);
    }

    private void reconcileSchedule(NopJobSchedule stale) {
        String scheduleId = stale.getJobScheduleId();
        // reload fresh schedule to get the latest version + activeFireCount
        NopJobSchedule fresh = scheduleDao().getEntityById(scheduleId);
        if (fresh == null) {
            return;
        }
        if (!JobScheduleStateMachine.isEnabled(fresh.getScheduleStatus())) {
            return;
        }
        int recorded = fresh.getActiveFireCount() == null ? 0 : fresh.getActiveFireCount();
        // check2 [P3-1]: 移除 recorded<=0 早退——本 schedule 已因 stale 读取的 activeFireCount>0
        // 被选中，reload 后计数可能已被并发写回压到 0（甚至仍偏小），按 actual 统一重算，
        // 与其余减一处路径的 Math.max 下限保护配合。（fetch 侧仍保留 gt(0) 过滤：向下漂移到
        // 0/负值且无 stale 正读的 schedule 无法在无全表扫描的前提下被发现，由下限保护兜底。）
        long actual = countActiveFires(scheduleId);
        if (actual == recorded) {
            return;
        }
        // mismatch — fix drift (single write-back, no retry on conflict)
        fresh.setActiveFireCount((int) actual);
        if (!scheduleDao().tryUpdateWithVersionCheck(fresh)) {
            LOG.warn("nop.job.reconcile.schedule-version-conflict:scheduleId={},recorded={},actual={}",
                    scheduleId, recorded, actual);
            return;
        }
        LOG.info("nop.job.reconcile.active-fire-count-corrected:scheduleId={},recorded={},actual={}",
                scheduleId, recorded, actual);
    }

    private long countActiveFires(String scheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopJobFire.PROP_NAME_jobScheduleId, scheduleId));
        query.addFilter(FilterBeans.in(NopJobFire.PROP_NAME_fireStatus,
                JobFireStateMachine.ACTIVE_STATUSES));
        return fireDao().countByQuery(query);
    }

    private IOrmEntityDao<NopJobSchedule> scheduleDao() {
        return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
    }

    private IOrmEntityDao<NopJobFire> fireDao() {
        return (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
    }
}
