package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.util.DateHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity._gen._NopJobTask;
import io.nop.job.dao.helper.JobQueryHelper;
import io.nop.job.dao.helper.JobStatusHelper;
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
        // Backoff filter (AR-86): skip WAITING fires whose startTime (reused as backoff-until after a
        // no-fitting-worker revert) is still in the future. Never-dispatched fires have startTime=null
        // and must always be eligible.
        long now = fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        query.addFilter(FilterBeans.or(
                FilterBeans.isNull(PROP_NAME_startTime),
                FilterBeans.le(PROP_NAME_startTime, new Timestamp(now))
        ));
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
        query.addOrderField(PROP_NAME_scheduledFireTime, false);
        query.addOrderField(PROP_NAME_jobFireId, false);
        return fireDao().findAllByQuery(query);
    }

    @Override
    public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_RUNNING));
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
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
        if (!fireDao().tryUpdateWithVersionCheck(currentFire)) {
            throw new NopException(ERR_JOB_FIRE_STATUS_CONFLICT)
                    .param("jobFireId", fire.getJobFireId())
                    .param("expectedStatus", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING);
        }

        for (NopJobTask task : tasks) {
            taskDao().saveEntityDirectly(task);
        }
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
        // Bug A fix: removed requireEntityById + isTerminalFire short-circuit
        // (it returned the @SingleSession-cached entity already modified by caller)

        if (!fireDao().tryUpdateWithVersionCheck(fire)) {
            return;
        }

        // Under @SingleSession, requireEntityById returns the same cached entity as the
        // schedule parameter. The entity is already dirty with the caller's counter
        // modifications, so tryUpdateWithVersionCheck will flush them in the new transaction.
        // No retry loop: with @SingleSession, requireEntityById cannot load fresh data
        // (cache always returns the same object), making retries pointless.
        scheduleDao().tryUpdateWithVersionCheck(schedule);
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
        fire.setDurationMs(DateHelper.durationMs(fire.getStartTime(), cancelTime));
        fire.setErrorCode(ERR_JOB_CANCELED.getErrorCode());
        fire.setErrorMessage(ERR_JOB_CANCELED.getDescription());
        if (!fireDao().tryUpdateWithVersionCheck(fire)) {
            return false;
        }

        for (NopJobTask task : tasks) {
            if (JobStatusHelper.isFinishedTask(task.getTaskStatus())) {
                continue;
            }

            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
            task.setEndTime(cancelTime);
            task.setDurationMs(DateHelper.durationMs(task.getStartTime(), cancelTime));
            task.setErrorCode(ERR_JOB_CANCELED.getErrorCode());
            task.setErrorMessage(ERR_JOB_CANCELED.getDescription());

            if (!taskDao().tryUpdateWithVersionCheck(task)) {
                NopJobTask freshTask = taskDao().requireEntityById(task.getJobTaskId());
                if (JobStatusHelper.isFinishedTask(freshTask.getTaskStatus())) {
                    LOG.debug("nop.job.cancel.task-already-terminal:taskId={},status={}",
                            task.getJobTaskId(), freshTask.getTaskStatus());
                    continue;
                }
                task.setVersion(freshTask.getVersion());
                if (!taskDao().tryUpdateWithVersionCheck(task)) {
                    LOG.warn("nop.job.cancel.task-update-failed-after-retry:taskId={}", task.getJobTaskId());
                }
            }
        }

        // Under @SingleSession, schedule is the same cached entity. It's already
        // dirty with the caller's counter modifications (set at lines 188-195 below).
        // Single try (same reasoning as completeFireAndUpdateSchedule — with
        // @SingleSession, requireEntityById returns the same cached entity, making
        // retries pointless).
        schedule.setActiveFireCount(Math.max(0, defaultInt(schedule.getActiveFireCount()) - 1));
        schedule.setTotalFireCount(Math.max(0, defaultLong(schedule.getTotalFireCount()) + 1));
        schedule.setFailFireCount(Math.max(0, defaultLong(schedule.getFailFireCount()) + 1));
        schedule.setLastEndTime(cancelTime);
        schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
        if (shouldAdvanceFixedDelaySchedule(schedule, fire)) {
            schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, cancelTime));
        }
        if (!scheduleDao().tryUpdateWithVersionCheck(schedule)) {
            LOG.warn("nop.job.cancel.schedule-update-conflict:fireId={}", fire.getJobFireId());
        }
        return true;
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
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
        query.addOrderField(PROP_NAME_startTime, false);
        return fireDao().findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public boolean revertDispatchingFireToWaiting(NopJobFire fire, long backoffUntilMs) {
        NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
        if (currentFire.getFireStatus() == null
                || currentFire.getFireStatus() != _NopJobCoreConstants.FIRE_STATUS_DISPATCHING) {
            return false;
        }
        currentFire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        currentFire.setDispatchInstanceId(null);
        // Reuse startTime as the "earliest re-dispatch" marker while WAITING (no-fitting-worker backoff).
        currentFire.setStartTime(new Timestamp(backoffUntilMs));
        return fireDao().tryUpdateWithVersionCheck(currentFire);
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
        fire.setDurationMs(DateHelper.durationMs(fire.getStartTime(), fire.getEndTime()));
        fire.setErrorCode(errorCode);
        fire.setErrorMessage(errorMessage);
        fireDao().updateEntityDirectly(fire);
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
        if (!JobStatusHelper.isActiveFire(fireStatus)) {
            return false;
        }
        if (fireStatus != _NopJobCoreConstants.FIRE_STATUS_RUNNING) {
            return true;
        }
        if (tasks.isEmpty()) {
            return true;
        }
        for (NopJobTask task : tasks) {
            if (!JobStatusHelper.isFinishedTask(task.getTaskStatus())) {
                return true;
            }
        }
        return false;
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
