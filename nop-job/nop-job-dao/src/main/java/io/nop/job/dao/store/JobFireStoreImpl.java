package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity._gen._NopJobTask;
import io.nop.job.dao.helper.TriggerSpecHelper;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.job.dao.entity._gen._NopJobFire.*;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_CANCELED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_FIRE_STATUS_CONFLICT;

public class JobFireStoreImpl implements IJobFireStore {
    static final Logger LOG = LoggerFactory.getLogger(JobFireStoreImpl.class);

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_WAITING));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_scheduledFireTime, false);
        query.addOrderField(PROP_NAME_jobFireId, false);
        return fireDao().findAllByQuery(query);
    }

    @Override
    public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_RUNNING));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_scheduledFireTime, false);
        query.addOrderField(PROP_NAME_jobFireId, false);
        return fireDao().findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                                    long lockTimeoutMs) {
        if (fires == null || fires.isEmpty()) {
            return Collections.emptyList();
        }

        long now = fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp startTime = new Timestamp(now);
        for (NopJobFire fire : fires) {
            fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING);
            fire.setDispatchInstanceId(dispatchInstanceId);
            fire.setStartTime(startTime);
        }
        return fireDao().tryUpdateManyWithVersionCheck(fires);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
        NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
        if (currentFire.getFireStatus() == null || currentFire.getFireStatus() != _NopJobCoreConstants.FIRE_STATUS_DISPATCHING) {
            return;
        }

        currentFire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_RUNNING);
        List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(currentFire));
        if (updated.isEmpty()) {
            throw new NopException(ERR_JOB_FIRE_STATUS_CONFLICT)
                    .param("jobFireId", fire.getJobFireId())
                    .param("expectedStatus", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING);
        }

        for (NopJobTask task : tasks) {
            taskDao().saveEntityDirectly(task);
        }
    }

    private static final Set<Integer> TERMINAL_FIRE_STATUSES = Set.of(
            _NopJobCoreConstants.FIRE_STATUS_SUCCESS,
            _NopJobCoreConstants.FIRE_STATUS_FAILED,
            _NopJobCoreConstants.FIRE_STATUS_CANCELED,
            _NopJobCoreConstants.FIRE_STATUS_TIMEOUT
    );

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
        NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
        if (TERMINAL_FIRE_STATUSES.contains(currentFire.getFireStatus())) {
            return;
        }

        List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fire));
        if (updated.isEmpty()) {
            return;
        }

        NopJobSchedule baseline = scheduleDao().requireEntityById(schedule.getJobScheduleId());

        for (int attempt = 0; attempt < 5; attempt++) {
            int origActiveFireCount = baseline.getActiveFireCount();
            long origFireCount = defaultLong(baseline.getFireCount());
            long origTotalFireCount = defaultLong(baseline.getTotalFireCount());
            long origSuccessFireCount = defaultLong(baseline.getSuccessFireCount());
            long origFailFireCount = defaultLong(baseline.getFailFireCount());

            int activeDelta = schedule.getActiveFireCount() - origActiveFireCount;
            long fireCountDelta = schedule.getFireCount() - origFireCount;
            Long totalTarget = schedule.getTotalFireCount();
            Long successTarget = schedule.getSuccessFireCount();
            Long failTarget = schedule.getFailFireCount();

            List<NopJobSchedule> updatedSchedules = scheduleDao().tryUpdateManyWithVersionCheck(
                    Collections.singletonList(schedule));
            if (!updatedSchedules.isEmpty()) {
                return;
            }
            baseline = scheduleDao().requireEntityById(schedule.getJobScheduleId());
            schedule.setVersion(baseline.getVersion());
            schedule.setActiveFireCount(baseline.getActiveFireCount() + activeDelta);
            schedule.setFireCount(defaultLong(baseline.getFireCount()) + fireCountDelta);
            if (totalTarget != null) {
                schedule.setTotalFireCount(defaultLong(baseline.getTotalFireCount()) + (totalTarget - origTotalFireCount));
            }
            if (successTarget != null) {
                schedule.setSuccessFireCount(defaultLong(baseline.getSuccessFireCount()) + (successTarget - origSuccessFireCount));
            }
            if (failTarget != null) {
                schedule.setFailFireCount(defaultLong(baseline.getFailFireCount()) + (failTarget - origFailFireCount));
            }
        }
        throw new NopException(ERR_JOB_FIRE_STATUS_CONFLICT)
                .param("jobFireId", fire.getJobFireId())
                .param("reason", "Failed to update schedule after 5 optimistic lock retries");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public boolean cancelFire(String jobFireId) {
        NopJobFire fire = fireDao().requireEntityById(jobFireId);
        List<NopJobTask> tasks = findTasksByFireId(jobFireId);
        if (!isCancelableFire(fire, tasks)) {
            return false;
        }

        NopJobSchedule schedule = scheduleDao().requireEntityById(fire.getJobScheduleId());
        Timestamp cancelTime = new Timestamp(fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis());

        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
        fire.setEndTime(cancelTime);
        fire.setDurationMs(calculateDuration(fire.getStartTime(), cancelTime));
        fire.setErrorCode(ERR_JOB_CANCELED.getErrorCode());
        fire.setErrorMessage(ERR_JOB_CANCELED.getDescription());
        fire.setUpdatedBy("system");
        fire.setUpdateTime(cancelTime);
        List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fire));
        if (updated.isEmpty()) {
            return false;
        }

        for (NopJobTask task : tasks) {
            if (isTaskFinished(task.getTaskStatus())) {
                continue;
            }

            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
            task.setEndTime(cancelTime);
            task.setDurationMs(calculateDuration(task.getStartTime(), cancelTime));
            task.setErrorCode(ERR_JOB_CANCELED.getErrorCode());
            task.setErrorMessage(ERR_JOB_CANCELED.getDescription());
            task.setUpdatedBy("system");
            task.setUpdateTime(cancelTime);

            List<NopJobTask> updatedTasks = taskDao().tryUpdateManyWithVersionCheck(
                    Collections.singletonList(task));
            if (updatedTasks.isEmpty()) {
                NopJobTask freshTask = taskDao().requireEntityById(task.getJobTaskId());
                if (isTaskFinished(freshTask.getTaskStatus())) {
                    LOG.debug("nop.job.cancel.task-already-terminal:taskId={},status={}",
                            task.getJobTaskId(), freshTask.getTaskStatus());
                    continue;
                }
                task.setVersion(freshTask.getVersion());
                taskDao().tryUpdateManyWithVersionCheck(Collections.singletonList(task));
            }
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
            schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + 1);
            schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + 1);
            schedule.setLastEndTime(cancelTime);
            schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
            if (shouldAdvanceFixedDelaySchedule(schedule, fire)) {
                schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, cancelTime));
            }
            schedule.setUpdatedBy("system");
            schedule.setUpdateTime(cancelTime);

            List<NopJobSchedule> updatedSchedules = scheduleDao().tryUpdateManyWithVersionCheck(
                    Collections.singletonList(schedule));
            if (!updatedSchedules.isEmpty()) {
                return true;
            }

            NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
            schedule.setVersion(fresh.getVersion());
            schedule.setActiveFireCount(fresh.getActiveFireCount());
            schedule.setTotalFireCount(fresh.getTotalFireCount());
            schedule.setFailFireCount(fresh.getFailFireCount());
        }
        throw new NopException(ERR_JOB_FIRE_STATUS_CONFLICT)
                .param("jobFireId", fire.getJobFireId())
                .param("reason", "Failed to update schedule after cancel fire, 5 retries exhausted");
    }

    @Override
    public NopJobFire loadFire(String jobFireId) {
        return fireDao().requireEntityById(jobFireId);
    }

    @Override
    public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) {
        if (fireIds == null || fireIds.isEmpty()) {
            return Collections.emptyMap();
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(PROP_NAME_jobFireId, new ArrayList<>(fireIds)));
        List<NopJobFire> fires = fireDao().findAllByQuery(query);
        Map<String, NopJobFire> result = new HashMap<>(fires.size());
        for (NopJobFire fire : fires) {
            result.put(fire.getJobFireId(), fire);
        }
        return result;
    }

    @Override
    public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_startTime, false);
        return fireDao().findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void updateRetryRecordId(String jobFireId, String retryRecordId) {
        NopJobFire fire = fireDao().requireEntityById(jobFireId);
        fire.setRetryRecordId(retryRecordId);
        fireDao().updateEntityDirectly(fire);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
        NopJobFire fire = fireDao().requireEntityById(jobFireId);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_FAILED);
        fire.setEndTime(new Timestamp(fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis()));
        fire.setDurationMs(calculateDuration(fire.getStartTime(), fire.getEndTime()));
        fire.setErrorCode(errorCode);
        fire.setErrorMessage(errorMessage);
        fire.setUpdatedBy("system");
        fire.setUpdateTime(fire.getEndTime());
        fireDao().updateEntityDirectly(fire);
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

    private IOrmEntityDao<NopJobFire> fireDao() {
        return (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
    }

    private IOrmEntityDao<NopJobTask> taskDao() {
        return (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
    }

    private IOrmEntityDao<NopJobSchedule> scheduleDao() {
        return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
    }

    private List<NopJobTask> findTasksByFireId(String jobFireId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobTask.PROP_NAME_jobFireId, jobFireId));
        query.addOrderField(_NopJobTask.PROP_NAME_taskNo, false);
        query.addOrderField(_NopJobTask.PROP_NAME_jobTaskId, false);
        return taskDao().findAllByQuery(query);
    }

    private boolean isCancelableFire(NopJobFire fire, List<NopJobTask> tasks) {
        Integer fireStatus = fire.getFireStatus();
        if (fireStatus == null) {
            return false;
        }
        if (fireStatus == _NopJobCoreConstants.FIRE_STATUS_WAITING || fireStatus == _NopJobCoreConstants.FIRE_STATUS_DISPATCHING) {
            return true;
        }
        if (fireStatus != _NopJobCoreConstants.FIRE_STATUS_RUNNING) {
            return false;
        }
        if (tasks.isEmpty()) {
            return true;
        }
        for (NopJobTask task : tasks) {
            if (!isTaskFinished(task.getTaskStatus())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaskFinished(Integer taskStatus) {
        return taskStatus != null
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_WAITING
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_RUNNING;
    }

    private boolean shouldAdvanceFixedDelaySchedule(NopJobSchedule schedule, NopJobFire fire) {
        return fire.getTriggerSource() != null
                && fire.getTriggerSource() == _NopJobCoreConstants.TRIGGER_SOURCE_SCHEDULE
                && schedule.getTriggerType() != null
                && schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY;
    }

    private Timestamp calculateFixedDelayNextFireTime(NopJobSchedule schedule, Timestamp fireEndTime) {
        NopJobSchedule evalSchedule = schedule.cloneInstance();
        evalSchedule.setLastEndTime(fireEndTime);

        long next = JobTriggerCalculator.calculateNextFireTime(
                toTriggerSpec(evalSchedule),
                toEvalContext(evalSchedule),
                fireEndTime.getTime()
        );
        return next <= 0 ? null : new Timestamp(next);
    }

    private TriggerSpec toTriggerSpec(NopJobSchedule schedule) {
        return TriggerSpecHelper.toTriggerSpec(schedule);
    }

    private ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return TriggerSpecHelper.toEvalContext(schedule);
    }

    private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Math.max(endTime.getTime() - startTime.getTime(), 0L);
    }

    private long toTime(Timestamp value) {
        return value == null ? 0L : value.getTime();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
