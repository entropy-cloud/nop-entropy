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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.job.dao.entity._gen._NopJobSchedule.*;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_OVERLAID;

public class JobScheduleStoreImpl implements IJobScheduleStore {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_DISPATCHING = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CLAIMED = 10;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_CANCELED = 60;

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
        query.addFilter(FilterBeans.eq(PROP_NAME_scheduleStatus, SCHEDULE_STATUS_ENABLED));
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
        schedule.setNextFireTime(nextFireTime);
        schedule.setUpdatedBy("system");
        schedule.setUpdateTime(new Timestamp(now));
        scheduleDao().updateEntityDirectly(schedule);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                             Integer lastFireStatus) {
        fireDao().saveEntityDirectly(fire);

        schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
        schedule.setActiveFireCount(defaultInt(schedule.getActiveFireCount()) + 1);
        schedule.setLastFireTime(fire.getScheduledFireTime());
        schedule.setNextFireTime(nextFireTime);
        if (lastFireStatus != null) {
            schedule.setLastFireStatus(lastFireStatus);
        }
        scheduleDao().updateEntityDirectly(schedule);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                              Integer lastFireStatus) {
        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp cancelTime = new Timestamp(now);
        List<NopJobFire> activeFires = findActiveFires(schedule.getJobScheduleId());

        for (NopJobFire activeFire : activeFires) {
            cancelFire(activeFire, cancelTime);
            cancelTasks(activeFire.getJobFireId(), cancelTime);
        }

        fireDao().saveEntityDirectly(fire);

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
        scheduleDao().updateEntityDirectly(schedule);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void insertManualFire(NopJobSchedule schedule, NopJobFire fire) {
        long now = scheduleDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
        Timestamp updateTime = new Timestamp(now);
        List<NopJobFire> activeFires = findActiveFires(schedule.getJobScheduleId());

        if (isDiscard(schedule) && !activeFires.isEmpty()) {
            return;
        }

        if (isOverlay(schedule)) {
            for (NopJobFire activeFire : activeFires) {
                cancelFire(activeFire, updateTime);
                cancelTasks(activeFire.getJobFireId(), updateTime);
            }
        }

        fireDao().saveEntityDirectly(fire);

        schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
        schedule.setActiveFireCount(isOverlay(schedule) ? 1 : defaultInt(schedule.getActiveFireCount()) + 1);
        if (isOverlay(schedule) && !activeFires.isEmpty()) {
            schedule.setLastEndTime(updateTime);
            schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
        }
        schedule.setUpdatedBy("system");
        schedule.setUpdateTime(updateTime);
        scheduleDao().updateEntityDirectly(schedule);
    }

    @Override
    public NopJobSchedule loadSchedule(String jobScheduleId) {
        return scheduleDao().requireEntityById(jobScheduleId);
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

    private IOrmEntityDao<NopJobSchedule> scheduleDao() {
        return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
    }

    private IOrmEntityDao<NopJobFire> fireDao() {
        return (IOrmEntityDao<NopJobFire>) daoProvider.daoFor(NopJobFire.class);
    }

    private IOrmEntityDao<NopJobTask> taskDao() {
        return (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
    }

    private List<NopJobFire> findActiveFires(String jobScheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobFire.PROP_NAME_jobScheduleId, jobScheduleId));
        query.addFilter(FilterBeans.in(_NopJobFire.PROP_NAME_fireStatus,
                List.of(FIRE_STATUS_WAITING, FIRE_STATUS_DISPATCHING, FIRE_STATUS_RUNNING)));
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

    private void cancelFire(NopJobFire fire, Timestamp cancelTime) {
        fire.setFireStatus(FIRE_STATUS_CANCELED);
        fire.setEndTime(cancelTime);
        fire.setDurationMs(calculateDuration(fire.getStartTime(), cancelTime));
        fire.setErrorCode(ERR_JOB_OVERLAID.getErrorCode());
        fire.setErrorMessage(ERR_JOB_OVERLAID.getDescription());
        fire.setUpdatedBy("system");
        fire.setUpdateTime(cancelTime);
        fireDao().updateEntityDirectly(fire);
    }

    private void cancelTasks(String jobFireId, Timestamp cancelTime) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(_NopJobTask.PROP_NAME_jobFireId, jobFireId));
        List<NopJobTask> tasks = taskDao().findAllByQuery(query);
        for (NopJobTask task : tasks) {
            if (isTaskFinished(task.getTaskStatus())) {
                continue;
            }

            task.setTaskStatus(TASK_STATUS_CANCELED);
            task.setEndTime(cancelTime);
            task.setDurationMs(calculateDuration(task.getStartTime(), cancelTime));
            task.setErrorCode(ERR_JOB_OVERLAID.getErrorCode());
            task.setErrorMessage(ERR_JOB_OVERLAID.getDescription());
            task.setUpdatedBy("system");
            task.setUpdateTime(cancelTime);
            taskDao().updateEntityDirectly(task);
        }
    }

    private boolean isTaskFinished(Integer taskStatus) {
        return taskStatus != null
                && taskStatus != TASK_STATUS_WAITING
                && taskStatus != TASK_STATUS_CLAIMED
                && taskStatus != TASK_STATUS_RUNNING;
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
