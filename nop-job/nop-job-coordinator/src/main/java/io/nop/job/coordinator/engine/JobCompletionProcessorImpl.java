package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.DateHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.api.retry.IJobRetryBridge;
import io.nop.job.api.retry.JobFireFailedEvent;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.coordinator.metrics.IJobCompletionMetrics;
import io.nop.job.coordinator.metrics.JobCompletionMetricsImpl;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.partition.JobPartitionResolver;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.TriggerSpecHelper;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class JobCompletionProcessorImpl extends AbstractBatchScanner implements IJobCompletionProcessor {
    static final Logger LOG = LoggerFactory.getLogger(JobCompletionProcessorImpl.class);

    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobTaskStore taskStore;
    private IJobCompletionMetrics completionMetrics = new JobCompletionMetricsImpl();
    private IJobRetryBridge retryBridge = new io.nop.job.coordinator.retry.NoOpJobRetryBridge();
    private IJobAlarmHandler alarmHandler = new io.nop.job.coordinator.alarm.NoOpJobAlarmHandler();
    private JobPartitionResolver partitionResolver;

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
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.completion.scan-failed", e);
    }

    @Override
    protected void scanOnce() {
        super.scanOnce();
    }

    @Override
    protected boolean scanBatch() {
        IntRangeSet partitions = partitionResolver != null ? partitionResolver.resolvePartitions() : null;
        List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
        if (fires.isEmpty()) {
            return false;
        }
        int completedCount = 0;
        for (NopJobFire fire : fires) {
            try {
                Integer status = completeSingleFire(fire.getJobFireId());
                if (status != null) {
                    completedCount++;
                }
            } catch (Exception e) {
                LOG.warn("nop.job.completion.fire-complete-failed:fireId={}", fire.getJobFireId(), e);
            }
        }
        if (completedCount > 0) {
            completionMetrics.onFiresCompleted(completedCount);
        }

        return fires.size() >= batchSize;
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @SingleSession
    protected Integer completeSingleFire(String fireId) {
        NopJobFire fire = fireStore.getFireById(fireId);
        if (fire == null)
            return null;

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fireId);
        if (tasks.isEmpty())
            return null;

        Integer finalFireStatus = resolveFinalFireStatus(tasks);
        if (finalFireStatus == null)
            return null;

        NopJobSchedule schedule = scheduleStore.tryLoadSchedule(fire.getJobScheduleId());
        if (schedule == null) {
            LOG.warn("nop.job.completion.schedule-deleted:fireId={}", fireId);
            String localized = ErrorMessageManager.instance().getLocalizedDescription(null,
                    JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getErrorCode());
            fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_FAILED);
            fire.setEndTime(new Timestamp(scheduleStore.getCurrentTime()));
            fire.setDurationMs(DateHelper.durationMs(fire.getStartTime(), fire.getEndTime()));
            fire.setErrorCode(JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getErrorCode());
            fire.setErrorMessage(localized != null ? localized
                    : JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getDescription());
            return _NopJobCoreConstants.FIRE_STATUS_FAILED;
        }

        boolean scheduleEnabled = schedule.getScheduleStatus() == null
                || schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED;
        if (!scheduleEnabled) {
            LOG.debug("nop.job.completion.schedule-not-enabled:fireId={},status={}",
                    fireId, schedule.getScheduleStatus());
        }

        Timestamp fireStartTime = earliestStartTime(tasks, fire.getStartTime());
        Timestamp fireEndTime = latestEndTime(tasks, new Timestamp(scheduleStore.getCurrentTime()));
        FireCompletionDecision completionDecision = resolveCompletionDecision(tasks, schedule);

        fire.setFireStatus(finalFireStatus);
        fire.setStartTime(fireStartTime);
        fire.setEndTime(fireEndTime);
        fire.setDurationMs(DateHelper.durationMs(fireStartTime, fireEndTime));

        NopJobTask errorTask = findMatchingErrorTask(tasks, finalFireStatus);
        if (errorTask != null) {
            fire.setErrorCode(errorTask.getErrorCode());
            fire.setErrorMessage(errorTask.getErrorMessage());
        } else {
            fire.setErrorCode(null);
            fire.setErrorMessage(null);
        }

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
        if (scheduleEnabled) {
            if (completionDecision.completed) {
                schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
                schedule.setNextFireTime(null);
            } else if (completionDecision.nextScheduleTime != null) {
                schedule.setNextFireTime(completionDecision.nextScheduleTime);
            } else if (isFixedDelay(schedule)) {
                schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, fireEndTime));
            }
        }

        // Entities are managed by @SingleSession — dirty fields flushed on
        // @Transactional commit. No need for completeFireAndUpdateSchedule.
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
            Map<String, Object> payload = task.getResultPayloadComponent().get_jsonMap();
            if (payload == null || payload.isEmpty()) {
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

    private NopJobTask findMatchingErrorTask(List<NopJobTask> tasks, Integer finalFireStatus) {
        Integer targetTaskStatus = toTaskStatus(finalFireStatus);
        if (targetTaskStatus != null) {
            for (NopJobTask task : tasks) {
                Integer taskStatus = task.getTaskStatus();
                if (taskStatus != null && taskStatus == targetTaskStatus) {
                    return task;
                }
            }
        }

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

    private Integer toTaskStatus(Integer fireStatus) {
        if (fireStatus == null) {
            return null;
        }
        if (fireStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT) {
            return _NopJobCoreConstants.TASK_STATUS_TIMEOUT;
        }
        if (fireStatus == _NopJobCoreConstants.FIRE_STATUS_FAILED) {
            return _NopJobCoreConstants.TASK_STATUS_FAILED;
        }
        if (fireStatus == _NopJobCoreConstants.FIRE_STATUS_CANCELED) {
            return _NopJobCoreConstants.TASK_STATUS_CANCELED;
        }
        return null;
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
        return ConvertHelper.toTimestamp(value);
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

    private static final class FireCompletionDecision {
        private final boolean completed;
        private final Timestamp nextScheduleTime;

        private FireCompletionDecision(boolean completed, Timestamp nextScheduleTime) {
            this.completed = completed;
            this.nextScheduleTime = nextScheduleTime;
        }
    }
}
