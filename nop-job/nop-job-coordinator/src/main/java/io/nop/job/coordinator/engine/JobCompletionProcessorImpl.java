package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.api.retry.IJobRetryBridge;
import io.nop.job.api.retry.JobFireFailedEvent;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.helper.TriggerSpecHelper;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.coordinator.metrics.EmptyJobCompletionMetrics;
import io.nop.job.coordinator.metrics.IJobCompletionMetrics;
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
    private IJobCompletionMetrics completionMetrics = new EmptyJobCompletionMetrics();
    private IJobRetryBridge retryBridge = new io.nop.job.coordinator.retry.NoOpJobRetryBridge();
    private IJobAlarmHandler alarmHandler = new io.nop.job.coordinator.alarm.NoOpJobAlarmHandler();
    private JobPartitionResolver partitionResolver;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
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

    public void setCompletionMetrics(IJobCompletionMetrics completionMetrics) {
        this.completionMetrics = completionMetrics;
    }

    @Inject
    public void setRetryBridge(IJobRetryBridge retryBridge) {
        this.retryBridge = retryBridge;
    }

    @Inject
    public void setAlarmHandler(IJobAlarmHandler alarmHandler) {
        this.alarmHandler = alarmHandler;
    }

    @Inject
    public void setPartitionResolver(JobPartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    @InjectValue("@cfg:nop.job.coordinator.completion.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.completion.scan-interval-ms must be >= 1000, got " + scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.completion.batch-size|100")
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "nop.job.completion.batch-size must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitionResolver == null) {
            partitionResolver = new JobPartitionResolver();
        }
        partitionResolver.setAssignedPartitions(partitions);
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
            IntRangeSet partitions = partitionResolver != null ? partitionResolver.resolvePartitions() : null;
            List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
            int completedCount = 0;
            for (NopJobFire fire : fires) {
                try {
                    if (tryCompleteFireAndGetStatus(fire) != null) {
                        completedCount++;
                    }
                } catch (Exception e) {
                    LOG.warn("nop.job.completion.fire-complete-failed:fireId={}", fire.getJobFireId(), e);
                }
            }
            if (completedCount > 0) {
                completionMetrics.onFiresCompleted(completedCount);
            }
        } catch (Exception e) {
            LOG.error("nop.job.completion.scan-failed", e);
        }
    }

    private Integer tryCompleteFireAndGetStatus(NopJobFire fire) {
        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        if (tasks.isEmpty()) {
            return null;
        }

        Integer finalFireStatus = resolveFinalFireStatus(tasks);
        if (finalFireStatus == null) {
            return null;
        }

        NopJobSchedule schedule = scheduleStore.tryLoadSchedule(fire.getJobScheduleId());
        if (schedule == null) {
            LOG.warn("nop.job.completion.schedule-deleted:fireId={}", fire.getJobFireId());
            fireStore.failFireWithoutSchedule(fire.getJobFireId(),
                    JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getErrorCode(),
                    JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getDescription());
            return _NopJobCoreConstants.FIRE_STATUS_FAILED;
        }

        if (schedule.getScheduleStatus() != null
                && schedule.getScheduleStatus() != _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED) {
            LOG.debug("nop.job.completion.schedule-not-enabled:fireId={},status={}",
                    fire.getJobFireId(), schedule.getScheduleStatus());
        }

        Timestamp fireStartTime = earliestStartTime(tasks, fire.getStartTime());
        Timestamp fireEndTime = latestEndTime(tasks, new Timestamp(scheduleStore.getCurrentTime()));
        FireCompletionDecision completionDecision = resolveCompletionDecision(tasks, schedule);

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
        schedule.setLastDurationMs(fire.getDurationMs());
        schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + 1);
        if (finalFireStatus == _NopJobCoreConstants.FIRE_STATUS_SUCCESS) {
            schedule.setSuccessFireCount(defaultLong(schedule.getSuccessFireCount()) + 1);
        } else {
            schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + 1);
        }
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

        long duration = fire.getDurationMs() != null ? fire.getDurationMs() : 0L;
        if (finalFireStatus == _NopJobCoreConstants.FIRE_STATUS_SUCCESS) {
            completionMetrics.onFireSuccess(duration);
        } else if (finalFireStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT) {
            completionMetrics.onFireTimeout(duration);
            handleAlarmTimeout(fire, schedule, duration);
        } else {
            completionMetrics.onFireFailure(duration);
            handleRetryAndAlarm(fire, schedule, duration);
        }
        return finalFireStatus;
    }

    private void handleRetryAndAlarm(NopJobFire fire, NopJobSchedule schedule, long duration) {
        String retryPolicyId = fire.getRetryPolicyId() != null
                ? fire.getRetryPolicyId() : schedule.getRetryPolicyId();
        if (retryPolicyId != null && !retryPolicyId.isEmpty()) {
            try {
                JobFireFailedEvent event = new JobFireFailedEvent(
                        fire.getJobFireId(), fire.getJobScheduleId(), retryPolicyId,
                        schedule.getNamespaceId(), schedule.getGroupId(), schedule.getJobName(),
                        fire.getExecutorKind(), fire.getErrorCode(), fire.getErrorMessage());
                retryBridge.onFireFailed(event);
            } catch (Exception e) {
                LOG.error("nop.job.retry.bridge-failed:fireId={}", fire.getJobFireId(), e);
            }
        }
        try {
            JobAlarmEvent alarmEvent = new JobAlarmEvent(
                    fire.getJobFireId(), fire.getJobScheduleId(), schedule.getJobName(),
                    schedule.getNamespaceId(), schedule.getGroupId(), fire.getErrorCode(),
                    fire.getErrorMessage(), duration);
            alarmHandler.onFireFailed(alarmEvent);
        } catch (Exception e) {
            LOG.error("nop.job.alarm.failed:fireId={}", fire.getJobFireId(), e);
        }
    }

    private void handleAlarmTimeout(NopJobFire fire, NopJobSchedule schedule, long duration) {
        try {
            JobAlarmEvent alarmEvent = new JobAlarmEvent(
                    fire.getJobFireId(), fire.getJobScheduleId(), schedule.getJobName(),
                    schedule.getNamespaceId(), schedule.getGroupId(), fire.getErrorCode(),
                    fire.getErrorMessage(), duration);
            alarmHandler.onFireTimeout(alarmEvent);
        } catch (Exception e) {
            LOG.error("nop.job.alarm.timeout-failed:fireId={}", fire.getJobFireId(), e);
        }
    }

    private boolean isAllowResultCompletion(NopJobSchedule schedule) {
        if (schedule == null) return false;
        Map<String, Object> params = schedule.getJobParamsComponent().get_jsonMap();
        if (params == null) return false;
        return Boolean.TRUE.equals(params.get("allowResultCompletion"));
    }

    private FireCompletionDecision resolveCompletionDecision(List<NopJobTask> tasks, NopJobSchedule schedule) {
        boolean allowResultCompletion = isAllowResultCompletion(schedule);
        Timestamp nextScheduleTime = null;
        for (NopJobTask task : tasks) {
            String resultPayload = task.getResultPayload();
            if (resultPayload == null || resultPayload.isEmpty()) {
                continue;
            }

            Map<String, Object> payload;
            try {
                payload = JsonTool.parseMap(resultPayload);
            } catch (Exception e) {
                LOG.warn("nop.job.completion.malformed-result-payload:taskId={}", task.getJobTaskId(), e);
                continue;
            }

            if (allowResultCompletion && Boolean.TRUE.equals(payload.get("completed"))) {
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

    /**
     * Resolves the aggregate fire status from individual task statuses.
     * <p>
     * Priority chain: TIMEOUT &gt; FAILED &gt; CANCELED &gt; SUCCESS.
     * For broadcast fires, a single CANCELED/FAILED/TIMEOUT shard determines
     * the fire's aggregate status. Operators should inspect individual task
     * statuses for partial success details.
     * SUSPICIOUS tasks are treated as pending only while active tasks remain.
     * Once no WAITING/CLAIMED/RUNNING tasks exist, SUSPICIOUS is treated as
     * TIMEOUT (worker unreachable).
     */
    private Integer resolveFinalFireStatus(List<NopJobTask> tasks) {
        boolean hasPendingTask = false;
        boolean hasTimeoutTask = false;
        boolean hasFailedTask = false;
        boolean hasCanceledTask = false;
        boolean hasSuspiciousTask = false;

        for (NopJobTask task : tasks) {
            Integer taskStatus = task.getTaskStatus();
            if (taskStatus == null || taskStatus == _NopJobCoreConstants.TASK_STATUS_WAITING
                    || taskStatus == _NopJobCoreConstants.TASK_STATUS_CLAIMED
                    || taskStatus == _NopJobCoreConstants.TASK_STATUS_RUNNING) {
                hasPendingTask = true;
                continue;
            }
            if (taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
                hasSuspiciousTask = true;
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

        if (hasSuspiciousTask) {
            hasTimeoutTask = true;
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
        return TriggerSpecHelper.toTriggerSpec(schedule);
    }

    private ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return TriggerSpecHelper.toEvalContext(schedule);
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
