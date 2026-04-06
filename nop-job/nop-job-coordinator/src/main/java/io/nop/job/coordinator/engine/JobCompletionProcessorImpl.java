package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JobCompletionProcessorImpl implements IJobCompletionProcessor {
    static final Logger LOG = LoggerFactory.getLogger(JobCompletionProcessorImpl.class);

    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobTaskStore taskStore;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private IntRangeSet assignedPartitions;
    private volatile boolean running;
    private Future<?> scanFuture;

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    @Inject
    public void setTaskStore(IJobTaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @InjectValue("@cfg:nop.job.coordinator.completion.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.completion.batch-size|100")
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitions != null && !partitions.isEmpty()) {
            this.assignedPartitions = IntRangeSet.parse(partitions);
        }
    }

    @Override
    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        scanFuture = getExecutor().scheduleWithFixedDelay(this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stopScanning() {
        running = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
    }

    @SingleSession
    protected void doScan() {
        if (!running) {
            return;
        }

        scanOnce();
    }

    void scanOnce() {
        try {
            List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, assignedPartitions);
            for (NopJobFire fire : fires) {
                tryCompleteFire(fire);
            }
        } catch (Exception e) {
            LOG.error("nop.job.completion.scan-failed", e);
        }
    }

    private void tryCompleteFire(NopJobFire fire) {
        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        if (tasks.isEmpty()) {
            return;
        }

        Integer finalFireStatus = resolveFinalFireStatus(tasks);
        if (finalFireStatus == null) {
            return;
        }

        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        Timestamp fireStartTime = earliestStartTime(tasks, fire.getStartTime());
        Timestamp fireEndTime = latestEndTime(tasks, new Timestamp(scheduleStore.getCurrentTime()));
        FireCompletionDecision completionDecision = resolveCompletionDecision(tasks);

        fire.setFireStatus(finalFireStatus);
        fire.setStartTime(fireStartTime);
        fire.setEndTime(fireEndTime);
        fire.setDurationMs(calculateDuration(fireStartTime, fireEndTime));

        NopJobTask errorTask = findFirstErrorTask(tasks);
        if (errorTask != null) {
            fire.setErrorCode(errorTask.getErrorCode());
            fire.setErrorMessage(errorTask.getErrorMessage());
        } else {
            fire.setErrorCode(null);
            fire.setErrorMessage(null);
        }
        fire.setUpdatedBy("system");
        fire.setUpdateTime(new Timestamp(scheduleStore.getCurrentTime()));

        schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
        schedule.setLastEndTime(fireEndTime);
        schedule.setLastFireStatus(finalFireStatus);
        if (completionDecision.completed) {
            schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
            schedule.setNextFireTime(null);
        } else if (completionDecision.nextScheduleTime != null) {
            schedule.setNextFireTime(completionDecision.nextScheduleTime);
        } else if (isFixedDelay(schedule)) {
            schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, fireEndTime));
        }
        schedule.setUpdatedBy("system");
        schedule.setUpdateTime(new Timestamp(scheduleStore.getCurrentTime()));

        fireStore.completeFireAndUpdateSchedule(fire, schedule);
    }

    private FireCompletionDecision resolveCompletionDecision(List<NopJobTask> tasks) {
        Timestamp nextScheduleTime = null;
        for (NopJobTask task : tasks) {
            String resultPayload = task.getResultPayload();
            if (resultPayload == null || resultPayload.isEmpty()) {
                continue;
            }

            Map<String, Object> payload = JsonTool.parseMap(resultPayload);
            if (Boolean.TRUE.equals(payload.get("completed"))) {
                return new FireCompletionDecision(true, null);
            }

            Timestamp taskNextScheduleTime = toTimestamp(payload.get("nextScheduleTime"));
            if (taskNextScheduleTime != null
                    && (nextScheduleTime == null || taskNextScheduleTime.before(nextScheduleTime))) {
                nextScheduleTime = taskNextScheduleTime;
            }
        }
        return new FireCompletionDecision(false, nextScheduleTime);
    }

    private Integer resolveFinalFireStatus(List<NopJobTask> tasks) {
        boolean hasPendingTask = false;
        boolean hasTimeoutTask = false;
        boolean hasFailedTask = false;
        boolean hasCanceledTask = false;

        for (NopJobTask task : tasks) {
            Integer taskStatus = task.getTaskStatus();
            if (taskStatus == null || taskStatus == _NopJobCoreConstants.TASK_STATUS_WAITING
                    || taskStatus == _NopJobCoreConstants.TASK_STATUS_CLAIMED
                    || taskStatus == _NopJobCoreConstants.TASK_STATUS_RUNNING) {
                hasPendingTask = true;
                continue;
            }
            if (taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT) {
                hasTimeoutTask = true;
            } else if (taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED) {
                hasFailedTask = true;
            } else if (taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED) {
                hasCanceledTask = true;
            }
        }

        if (hasPendingTask) {
            return null;
        }
        if (hasTimeoutTask) {
            return _NopJobCoreConstants.FIRE_STATUS_TIMEOUT;
        }
        if (hasFailedTask) {
            return _NopJobCoreConstants.FIRE_STATUS_FAILED;
        }
        if (hasCanceledTask) {
            return _NopJobCoreConstants.FIRE_STATUS_CANCELED;
        }
        return _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }

    private NopJobTask findFirstErrorTask(List<NopJobTask> tasks) {
        for (NopJobTask task : tasks) {
            Integer taskStatus = task.getTaskStatus();
            if (taskStatus != null && taskStatus != _NopJobCoreConstants.TASK_STATUS_SUCCESS) {
                return task;
            }
        }
        return null;
    }

    private Timestamp earliestStartTime(List<NopJobTask> tasks, Timestamp fallback) {
        Timestamp result = fallback;
        for (NopJobTask task : tasks) {
            Timestamp startTime = task.getStartTime();
            if (startTime == null) {
                continue;
            }
            if (result == null || startTime.before(result)) {
                result = startTime;
            }
        }
        return result;
    }

    private Timestamp latestEndTime(List<NopJobTask> tasks, Timestamp fallback) {
        Timestamp result = null;
        for (NopJobTask task : tasks) {
            Timestamp endTime = task.getEndTime();
            if (endTime == null) {
                continue;
            }
            if (result == null || endTime.after(result)) {
                result = endTime;
            }
        }
        return result == null ? fallback : result;
    }

    private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Math.max(endTime.getTime() - startTime.getTime(), 0L);
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
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr(schedule.getCronExpr());
        spec.setRepeatInterval(defaultLong(schedule.getRepeatIntervalMs()));
        spec.setRepeatFixedDelay(isFixedDelay(schedule));
        spec.setMaxExecutionCount(defaultInt(schedule.getMaxExecutionCount()));
        spec.setMinScheduleTime(toTime(schedule.getMinScheduleTime()));
        spec.setMaxScheduleTime(toTime(schedule.getMaxScheduleTime()));
        spec.setMisfireThreshold(defaultInt(schedule.getMisfireThresholdMs()));
        spec.setUseDefaultCalendar(schedule.getUseDefaultCalendar() != null && schedule.getUseDefaultCalendar() == 1);
        spec.setPauseCalendars(Collections.emptyList());
        spec.setMaxFailedCount(0);
        return spec;
    }

    private ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return new ITriggerEvalContext() {
            @Override
            public long getFireCount() {
                return defaultLong(schedule.getFireCount());
            }

            @Override
            public long getLastScheduledTime() {
                return toTime(schedule.getLastFireTime());
            }

            @Override
            public long getLastEndTime() {
                return toTime(schedule.getLastEndTime());
            }

            @Override
            public long getMinScheduleTime() {
                return toTime(schedule.getMinScheduleTime());
            }

            @Override
            public long getMaxScheduleTime() {
                return toTime(schedule.getMaxScheduleTime());
            }

            @Override
            public long getMaxExecutionCount() {
                return defaultInt(schedule.getMaxExecutionCount());
            }

            @Override
            public boolean isScheduleCompleted() {
                return schedule.getScheduleStatus() != null
                        && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
            }
        };
    }

    private boolean isFixedDelay(NopJobSchedule schedule) {
        return schedule.getTriggerType() != null
                && schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY;
    }

    private Timestamp toTimestamp(Object value) {
        if (value instanceof Number) {
            long time = ((Number) value).longValue();
            return time > 0 ? new Timestamp(time) : null;
        }
        return null;
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

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }

    private static final class FireCompletionDecision {
        private final boolean completed;
        private final Timestamp nextScheduleTime;

        private FireCompletionDecision(boolean completed, Timestamp nextScheduleTime) {
            this.completed = completed;
            this.nextScheduleTime = nextScheduleTime;
        }
    }
}
