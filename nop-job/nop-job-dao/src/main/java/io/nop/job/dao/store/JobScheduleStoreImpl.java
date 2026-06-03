package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity._gen._NopJobFire;
import io.nop.job.dao.entity._gen._NopJobTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.job.dao.entity._gen._NopJobSchedule.*;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_FIRE_STATUS_CONFLICT;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_OVERLAID;

import io.nop.api.core.exceptions.NopException;

public class JobScheduleStoreImpl implements IJobScheduleStore {
    static final Logger LOG = LoggerFactory.getLogger(JobScheduleStoreImpl.class);

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) {
        IOrmEntityDao<NopJobSchedule> dao = scheduleDao();
        long now = dao.getDbEstimatedClock().getMinCurrentTimeMillis();

        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_scheduleStatus, _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        query.addFilter(FilterBeans.le(PROP_NAME_nextFireTime, now));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_nextFireTime, false);
        query.addOrderField(PROP_NAME_jobScheduleId, false);
        return dao.findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId,
                                                        long lockTimeoutMs) {
        if (schedules == null || schedules.isEmpty()) {
            return Collections.emptyList();
        }

        IOrmEntityDao<NopJobSchedule> dao = scheduleDao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp lockTime = new Timestamp(now + Math.max(lockTimeoutMs, 1));

        for (NopJobSchedule schedule : schedules) {
            schedule.setNextFireTime(lockTime);
        }
        return dao.tryUpdateManyWithVersionCheck(schedules);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {
        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp updateTime = new Timestamp(now);

        updateScheduleWithRetry(schedule,
                () -> {
                    schedule.setNextFireTime(nextFireTime);
                    schedule.setUpdatedBy("system");
                    schedule.setUpdateTime(updateTime);
                },
                () -> {
                },
                "skip");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                              Integer lastFireStatus) {
        if (hasWaitingFire(schedule.getJobScheduleId(), fire.getScheduledFireTime(), fire.getTriggerSource())) {
            LOG.info("nop.job.schedule.skip-duplicate-fire:scheduleId={},scheduledFireTime={}",
                    schedule.getJobScheduleId(), fire.getScheduledFireTime());
            updateScheduleWithRetry(schedule,
                    () -> {
                        schedule.setNextFireTime(nextFireTime);
                        schedule.setUpdatedBy("system");
                        schedule.setUpdateTime(new Timestamp(scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis()));
                    },
                    () -> {
                    },
                    "skip-duplicate-fire");
            return;
        }

        fireDao().saveEntityDirectly(fire);

        updateScheduleWithRetry(schedule,
                () -> {
                    schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
                    schedule.setActiveFireCount(defaultInt(schedule.getActiveFireCount()) + 1);
                    schedule.setLastFireTime(fire.getScheduledFireTime());
                    schedule.setNextFireTime(nextFireTime);
                    if (lastFireStatus != null) {
                        schedule.setLastFireStatus(lastFireStatus);
                    }
                },
                () -> {
                    NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
                    schedule.setVersion(fresh.getVersion());
                    schedule.setFireCount(fresh.getFireCount());
                    schedule.setActiveFireCount(fresh.getActiveFireCount());
                },
                "insertFire");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                              Integer lastFireStatus) {
        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cancelTime = new Timestamp(now);
        List<NopJobFire> activeFires = findActiveFires(schedule.getJobScheduleId());

        int actualCancelledCount = 0;
        for (NopJobFire activeFire : activeFires) {
            try {
                boolean cancelled = cancelFire(activeFire, cancelTime);
                if (cancelled) {
                    actualCancelledCount++;
                }
                cancelTasks(activeFire.getJobFireId(), cancelTime);
            } catch (Exception e) {
                LOG.warn("nop.job.schedule.cancel-fire-failed:fireId={}", activeFire.getJobFireId(), e);
            }
        }

        fireDao().saveEntityDirectly(fire);

        final int cancelledCount = actualCancelledCount;
        updateScheduleWithRetry(schedule,
                () -> {
                    schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + cancelledCount);
                    schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + cancelledCount);
                    schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
                    schedule.setActiveFireCount(1);
                    schedule.setLastFireTime(fire.getScheduledFireTime());
                    if (!activeFires.isEmpty()) {
                        schedule.setLastEndTime(cancelTime);
                    }
                    schedule.setNextFireTime(nextFireTime);
                    if (lastFireStatus != null) {
                        schedule.setLastFireStatus(lastFireStatus);
                    }
                    schedule.setUpdatedBy("system");
                    schedule.setUpdateTime(cancelTime);
                },
                () -> {
                    NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
                    schedule.setVersion(fresh.getVersion());
                    schedule.setTotalFireCount(fresh.getTotalFireCount());
                    schedule.setFailFireCount(fresh.getFailFireCount());
                    schedule.setFireCount(fresh.getFireCount());
                    schedule.setActiveFireCount(fresh.getActiveFireCount());
                },
                "overlayFire");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {
        List<NopJobFire> failedFires = findFailedFires(schedule.getJobScheduleId());

        if (failedFires.isEmpty()) {
            long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
            Timestamp fireTime = new Timestamp(now);

            NopJobFire newFire = new NopJobFire();
            newFire.setJobScheduleId(schedule.getJobScheduleId());
            newFire.setNamespaceId(schedule.getNamespaceId());
            newFire.setGroupId(schedule.getGroupId());
            newFire.setJobName(schedule.getJobName());
            newFire.setScheduledFireTime(fireTime);
            newFire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
            newFire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY);
            newFire.setRetryPolicyId(schedule.getRetryPolicyId());
            newFire.setJobParamsSnapshot(schedule.getJobParams());
            newFire.setCreatedBy("system");
            newFire.setCreateTime(fireTime);
            newFire.setUpdatedBy("system");
            newFire.setUpdateTime(fireTime);
            newFire.setPartitionIndex(schedule.getPartitionIndex());
            newFire.setExecutorKind(schedule.getExecutorKind());

            fireDao().saveEntityDirectly(newFire);

            updateScheduleWithRetry(schedule,
                    () -> {
                        schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
                        schedule.setActiveFireCount(defaultInt(schedule.getActiveFireCount()) + 1);
                        schedule.setLastFireTime(fireTime);
                        schedule.setNextFireTime(nextFireTime);
                        schedule.setUpdatedBy("system");
                        schedule.setUpdateTime(fireTime);
                    },
                    () -> {
                        NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
                        schedule.setVersion(fresh.getVersion());
                        schedule.setFireCount(fresh.getFireCount());
                        schedule.setActiveFireCount(fresh.getActiveFireCount());
                    },
                    "recovery-new-fire");
            return;
        }

        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp recoveryTime = new Timestamp(now);

        NopJobFire failedFire = failedFires.get(0);
        failedFire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        failedFire.setErrorCode(null);
        failedFire.setErrorMessage(null);
        failedFire.setEndTime(null);
        failedFire.setDurationMs(null);
        failedFire.setJobParamsSnapshot(schedule.getJobParams());
        failedFire.setRetryPolicyId(schedule.getRetryPolicyId());
        failedFire.setUpdatedBy("system");
        failedFire.setUpdateTime(recoveryTime);

        NopJobFire freshFire = fireDao().requireEntityById(failedFire.getJobFireId());
        Integer currentFireStatus = freshFire.getFireStatus();
        if (currentFireStatus == null || (currentFireStatus != _NopJobCoreConstants.FIRE_STATUS_FAILED && currentFireStatus != _NopJobCoreConstants.FIRE_STATUS_TIMEOUT)) {
            LOG.info("nop.job.schedule.recovery-skip-fire-no-longer-failed:fireId={},status={}",
                    failedFire.getJobFireId(), currentFireStatus);
            return;
        }

        freshFire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        freshFire.setErrorCode(null);
        freshFire.setErrorMessage(null);
        freshFire.setEndTime(null);
        freshFire.setDurationMs(null);
        freshFire.setJobParamsSnapshot(schedule.getJobParams());
        freshFire.setRetryPolicyId(schedule.getRetryPolicyId());
        freshFire.setUpdatedBy("system");
        freshFire.setUpdateTime(recoveryTime);

        List<NopJobFire> updatedFires = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(freshFire));
        if (updatedFires.isEmpty()) {
            LOG.warn("nop.job.schedule.recovery-fire-version-conflict:fireId={}", failedFire.getJobFireId());
            return;
        }

        resetFailedTasks(failedFire.getJobFireId(), recoveryTime);

        updateScheduleWithRetry(schedule,
                () -> {
                    schedule.setActiveFireCount(defaultInt(schedule.getActiveFireCount()) + 1);
                    schedule.setNextFireTime(nextFireTime);
                    schedule.setUpdatedBy("system");
                    schedule.setUpdateTime(recoveryTime);
                },
                () -> {
                    NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
                    schedule.setVersion(fresh.getVersion());
                    schedule.setActiveFireCount(fresh.getActiveFireCount());
                },
                "recovery-reuse-failed");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) {
        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp updateTime = new Timestamp(now);
        List<NopJobFire> activeFires = findActiveFires(schedule.getJobScheduleId());

        if (isDiscard(schedule) && !activeFires.isEmpty()) {
            return false;
        }

        if (isOverlay(schedule)) {
            for (NopJobFire activeFire : activeFires) {
                try {
                    cancelFire(activeFire, updateTime);
                    cancelTasks(activeFire.getJobFireId(), updateTime);
                } catch (Exception e) {
                    LOG.warn("nop.job.schedule.cancel-fire-failed:fireId={}", activeFire.getJobFireId(), e);
                }
            }
        }

        fireDao().saveEntityDirectly(fire);

        int cancelledCount = 0;
        if (isOverlay(schedule)) {
            for (NopJobFire af : activeFires) {
                NopJobFire fresh = fireDao().requireEntityById(af.getJobFireId());
                if (fresh.getFireStatus() != null && fresh.getFireStatus() == _NopJobCoreConstants.FIRE_STATUS_CANCELED) {
                    cancelledCount++;
                }
            }
        }

        final int finalCancelledCount = cancelledCount;
        updateScheduleWithRetry(schedule,
                () -> {
                    if (finalCancelledCount > 0) {
                        schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + finalCancelledCount);
                        schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + finalCancelledCount);
                    }

                    schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
                    schedule.setActiveFireCount(isOverlay(schedule) ? 1 : defaultInt(schedule.getActiveFireCount()) + 1);
                    if (isOverlay(schedule) && !activeFires.isEmpty()) {
                        schedule.setLastEndTime(updateTime);
                        schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
                    }
                    schedule.setUpdatedBy("system");
                    schedule.setUpdateTime(updateTime);
                },
                () -> {
                    NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
                    schedule.setVersion(fresh.getVersion());
                    schedule.setTotalFireCount(fresh.getTotalFireCount());
                    schedule.setFailFireCount(fresh.getFailFireCount());
                    schedule.setFireCount(fresh.getFireCount());
                    schedule.setActiveFireCount(fresh.getActiveFireCount());
                },
                "insertManualFire");
        return true;
    }

    @Override
    public NopJobSchedule loadSchedule(String jobScheduleId) {
        return scheduleDao().requireEntityById(jobScheduleId);
    }

    @Override
    public NopJobSchedule tryLoadSchedule(String jobScheduleId) {
        return scheduleDao().getEntityById(jobScheduleId);
    }

    @Override
    public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(PROP_NAME_jobScheduleId, new ArrayList<>(scheduleIds)));
        List<NopJobSchedule> schedules = scheduleDao().findAllByQuery(query);
        Map<String, NopJobSchedule> result = new HashMap<>(schedules.size());
        for (NopJobSchedule schedule : schedules) {
            result.put(schedule.getJobScheduleId(), schedule);
        }
        return result;
    }

    @Override
    public long getCurrentTime() {
        return scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
    }

    private void addPartitionFilter(QueryBean query, IntRangeSet partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : partitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(PROP_NAME_partitionIndex, range.getOffset(), range.getLast()));
        }
        query.addFilter(FilterBeans.or(rangeFilters));
    }

    private void updateScheduleWithRetry(NopJobSchedule schedule, Runnable fieldSetter,
                                          Runnable fieldRefresher, String retryContext) {
        for (int attempt = 0; attempt < 5; attempt++) {
            fieldSetter.run();

            List<NopJobSchedule> updated = scheduleDao().tryUpdateManyWithVersionCheck(
                    Collections.singletonList(schedule));
            if (!updated.isEmpty()) return;

            fieldRefresher.run();
        }
        throw new NopException(ERR_JOB_FIRE_STATUS_CONFLICT)
                .param("scheduleId", schedule.getJobScheduleId())
                .param("reason", "Failed to update schedule after " + retryContext + ", 5 retries exhausted");
    }

    private IOrmEntityDao<NopJobSchedule> scheduleDao() {
        return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
    }

    private IOrmEntityDao<NopJobFire> fireDao() {
        return (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
    }

    private IOrmEntityDao<NopJobTask> taskDao() {
        return (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
    }

    private boolean hasWaitingFire(String scheduleId, Timestamp scheduledFireTime, Integer triggerSource) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_jobScheduleId, scheduleId));
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_WAITING));
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_scheduledFireTime, scheduledFireTime));
        if (triggerSource != null) {
            query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_triggerSource, triggerSource));
        }
        query.setLimit(1);
        return !fireDao().findAllByQuery(query).isEmpty();
    }

    private List<NopJobFire> findActiveFires(String jobScheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_jobScheduleId, jobScheduleId));
        query.addFilter(FilterBeans.in(_NopJobFire.PROP_NAME_fireStatus,
                List.of(_NopJobCoreConstants.FIRE_STATUS_WAITING, _NopJobCoreConstants.FIRE_STATUS_DISPATCHING, _NopJobCoreConstants.FIRE_STATUS_RUNNING)));
        query.addOrderField(_NopJobFire.PROP_NAME_scheduledFireTime, false);
        query.addOrderField(_NopJobFire.PROP_NAME_jobFireId, false);
        return fireDao().findAllByQuery(query);
    }

    private boolean isDiscard(NopJobSchedule schedule) {
        return schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_DISCARD;
    }

    private boolean isOverlay(NopJobSchedule schedule) {
        return schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_OVERLAY;
    }

    private List<NopJobFire> findFailedFires(String jobScheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_jobScheduleId, jobScheduleId));
        query.addFilter(FilterBeans.in(_NopJobFire.PROP_NAME_fireStatus,
                List.of(_NopJobCoreConstants.FIRE_STATUS_FAILED, _NopJobCoreConstants.FIRE_STATUS_TIMEOUT)));
        query.addOrderField(_NopJobFire.PROP_NAME_scheduledFireTime, false);
        query.addOrderField(_NopJobFire.PROP_NAME_jobFireId, false);
        query.setLimit(1);
        return fireDao().findAllByQuery(query);
    }

    private void resetFailedTasks(String jobFireId, Timestamp recoveryTime) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobTask.PROP_NAME_jobFireId, jobFireId));
        List<NopJobTask> tasks = taskDao().findAllByQuery(query);
        for (NopJobTask task : tasks) {
            if (!isTaskFailed(task.getTaskStatus())) {
                continue;
            }
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setStartTime(null);
            task.setEndTime(null);
            task.setDurationMs(null);
            task.setErrorCode(null);
            task.setErrorMessage(null);
            task.setUpdatedBy("system");
            task.setUpdateTime(recoveryTime);
            List<NopJobTask> updated = taskDao().tryUpdateManyWithVersionCheck(Collections.singletonList(task));
            if (updated.isEmpty()) {
                LOG.warn("nop.job.schedule.reset-task-version-conflict:taskId={}", task.getJobTaskId());
            }
        }
    }

    private boolean isTaskFailed(Integer taskStatus) {
        return taskStatus != null
                && (taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
    }

    private boolean cancelFire(NopJobFire fire, Timestamp cancelTime) {
        NopJobFire fresh = fireDao().requireEntityById(fire.getJobFireId());
        Integer currentStatus = fresh.getFireStatus();
        if (currentStatus != null && (currentStatus == _NopJobCoreConstants.FIRE_STATUS_CANCELED
                || currentStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT
                || currentStatus == _NopJobCoreConstants.FIRE_STATUS_SUCCESS
                || currentStatus == _NopJobCoreConstants.FIRE_STATUS_FAILED)) {
            return false;
        }

        fresh.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
        fresh.setEndTime(cancelTime);
        fresh.setDurationMs(calculateDuration(fresh.getStartTime(), cancelTime));
        fresh.setErrorCode(ERR_JOB_OVERLAID.getErrorCode());
        fresh.setErrorMessage(ERR_JOB_OVERLAID.getDescription());
        fresh.setUpdatedBy("system");
        fresh.setUpdateTime(cancelTime);
        List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fresh));
        if (updated.isEmpty()) {
            LOG.warn("nop.job.schedule.cancel-fire-version-conflict:fireId={}", fire.getJobFireId());
            return false;
        }
        return true;
    }

    private void cancelTasks(String jobFireId, Timestamp cancelTime) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobTask.PROP_NAME_jobFireId, jobFireId));
        List<NopJobTask> tasks = taskDao().findAllByQuery(query);
        for (NopJobTask task : tasks) {
            if (isTaskFinished(task.getTaskStatus())) {
                continue;
            }

            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
            task.setEndTime(cancelTime);
            task.setDurationMs(calculateDuration(task.getStartTime(), cancelTime));
            task.setErrorCode(ERR_JOB_OVERLAID.getErrorCode());
            task.setErrorMessage(ERR_JOB_OVERLAID.getDescription());
            task.setUpdatedBy("system");
            task.setUpdateTime(cancelTime);
            List<NopJobTask> updated = taskDao().tryUpdateManyWithVersionCheck(Collections.singletonList(task));
            if (updated.isEmpty()) {
                LOG.warn("nop.job.schedule.cancel-task-version-conflict:taskId={}", task.getJobTaskId());
            }
        }
    }

    private boolean isTaskFinished(Integer taskStatus) {
        return taskStatus != null
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_WAITING
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_RUNNING;
    }

    private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Math.max(endTime.getTime() - startTime.getTime(), 0L);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
