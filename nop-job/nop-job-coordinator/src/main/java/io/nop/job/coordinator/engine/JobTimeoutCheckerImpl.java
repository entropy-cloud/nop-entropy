package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.core._NopJobCoreConstants;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_SCHEDULE_DELETED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TIMEOUT;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JobTimeoutCheckerImpl implements IJobTimeoutChecker {
    static final Logger LOG = LoggerFactory.getLogger(JobTimeoutCheckerImpl.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobCancelHandler cancelHandler;
    private IJobAlarmHandler alarmHandler;
    private INamingService namingService;
    private JobPartitionResolver partitionResolver;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long dispatchTimeoutMs = 300000;
    private long executionTimeoutMs = -1;
    private long taskDispatchWaitTimeoutMs = 600000;
    private volatile boolean running;
    private Future<?> scanFuture;

    @Inject
    public void setTaskStore(IJobTaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    @Inject
    public void setCancelHandler(IJobCancelHandler cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    public void setAlarmHandler(IJobAlarmHandler alarmHandler) {
        this.alarmHandler = alarmHandler;
    }

    public void setNamingService(INamingService namingService) {
        this.namingService = namingService;
    }

    @Inject
    public void setPartitionResolver(JobPartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    @InjectValue("@cfg:nop.job.coordinator.timeout.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.timeout.scan-interval-ms must be >= 1000, got " + scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.timeout.batch-size|100")
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "nop.job.timeout.batch-size must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatch-timeout-ms|300000")
    public void setDispatchTimeoutMs(long dispatchTimeoutMs) {
        this.dispatchTimeoutMs = dispatchTimeoutMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.execution-timeout-ms|-1")
    public void setExecutionTimeoutMs(long executionTimeoutMs) {
        this.executionTimeoutMs = executionTimeoutMs;
    }

    /**
     * WAITING-task 派发超时回收窗口（AR-88）。超过此窗口仍滞留 WAITING（含归因给已下线
     * worker、或被 AR-83 double-count 卡死的）的任务被重派发（workerInstanceId 置 null）。
     * 默认 600000ms（10 分钟），远大于 scan-interval，避免误杀正常等待。{@code <=0} 表示禁用。
     */
    @InjectValue("@cfg:nop.job.coordinator.task-dispatch-wait-timeout-ms|600000")
    public void setTaskDispatchWaitTimeoutMs(long taskDispatchWaitTimeoutMs) {
        if (taskDispatchWaitTimeoutMs > 0 && taskDispatchWaitTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.coordinator.task-dispatch-wait-timeout-ms must be >= 1000 or <= 0 (disabled), got "
                            + taskDispatchWaitTimeoutMs);
        }
        this.taskDispatchWaitTimeoutMs = taskDispatchWaitTimeoutMs;
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
            scanTaskTimeouts(partitions);
            scanDispatchTimeouts(partitions);
            scanStaleWaitingTasks(partitions);
        } catch (Exception e) {
            LOG.error("nop.job.timeout.scan-failed", e);
        }
    }

    /**
     * WAITING-task 派发超时回收（AR-88）。把滞留超过 {@code taskDispatchWaitTimeoutMs} 的
     * WAITING 任务重派发（workerInstanceId 置 null），含归因给已下线 worker 的任务。
     */
    private void scanStaleWaitingTasks(IntRangeSet partitions) {
        if (taskDispatchWaitTimeoutMs <= 0) {
            return;
        }
        long now = scheduleStore.getCurrentTime();
        long deadline = now - taskDispatchWaitTimeoutMs;
        int reset = taskStore.resetStaleWaitingTasks(batchSize, partitions, deadline);
        if (reset > 0) {
            LOG.info("nop.job.timeout.stale-waiting-task-reset:count={},waitTimeoutMs={}",
                    reset, taskDispatchWaitTimeoutMs);
        }
    }

    private void scanTaskTimeouts(IntRangeSet partitions) {
        List<NopJobTask> tasks = taskStore.fetchRunningTasks(batchSize, partitions);
        if (tasks.isEmpty()) {
            return;
        }

        Set<String> fireIds = new HashSet<>();
        for (NopJobTask task : tasks) {
            String fireId = task.getJobFireId();
            if (fireId != null) {
                fireIds.add(fireId);
            }
        }

        Map<String, NopJobFire> fireMap = fireIds.isEmpty()
                ? Collections.emptyMap()
                : fireStore.batchLoadFires(fireIds);

        Set<String> scheduleIds = new HashSet<>();
        for (NopJobFire fire : fireMap.values()) {
            String scheduleId = fire.getJobScheduleId();
            if (scheduleId != null) {
                scheduleIds.add(scheduleId);
            }
        }

        Map<String, NopJobSchedule> scheduleMap = scheduleIds.isEmpty()
                ? Collections.emptyMap()
                : scheduleStore.batchLoadSchedules(scheduleIds);

        Set<String> aliveWorkerIds = resolveAliveWorkerIds();

        for (NopJobTask task : tasks) {
            try {
                int statusBefore = task.getTaskStatus() != null ? task.getTaskStatus() : 0;
                if (aliveWorkerIds != null) {
                    tryMarkSuspiciousIfWorkerGone(task, aliveWorkerIds);
                }
                if (task.getTaskStatus() != null && task.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS
                        && statusBefore != _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
                    continue;
                }
                tryMarkTimeout(task, fireMap, scheduleMap);
            } catch (Exception e) {
                LOG.error("nop.job.timeout.task-check-failed:taskId={}", task.getJobTaskId(), e);
            }
        }
    }

    private Set<String> resolveAliveWorkerIds() {
        if (namingService == null) {
            return null;
        }
        try {
            String svcName = AppConfig.appName();
            List<ServiceInstance> instances = namingService.getInstances(svcName);
            if (instances == null || instances.isEmpty()) {
                return null;
            }
            Set<String> alive = new HashSet<>();
            for (ServiceInstance inst : instances) {
                if (inst.isHealthy() && inst.isEnabled()) {
                    alive.add(inst.getInstanceId());
                }
            }
            return alive;
        } catch (Exception e) {
            LOG.warn("nop.job.timeout.resolve-workers-failed", e);
            return null;
        }
    }

    private void tryMarkSuspiciousIfWorkerGone(NopJobTask task, Set<String> aliveWorkerIds) {
        if (task.getTaskStatus() == null || task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_RUNNING) {
            return;
        }

        String workerId = task.getWorkerInstanceId();
        if (workerId == null || aliveWorkerIds.contains(workerId)) {
            return;
        }

        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        task.setUpdatedBy("system");
        task.setUpdateTime(new Timestamp(scheduleStore.getCurrentTime()));
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.worker-suspicious-update-conflict:taskId={}", task.getJobTaskId());
        }

        LOG.info("nop.job.timeout.worker-suspicious:taskId={},workerId={}", task.getJobTaskId(), workerId);
    }
    private void scanDispatchTimeouts(IntRangeSet partitions) {
        if (dispatchTimeoutMs <= 0) {
            return;
        }

        List<NopJobFire> dispatchingFires = fireStore.fetchDispatchingFires(batchSize, partitions);
        if (dispatchingFires.isEmpty()) {
            return;
        }

        long now = scheduleStore.getCurrentTime();

        for (NopJobFire fire : dispatchingFires) {
            try {
                Timestamp startTime = fire.getStartTime();
                if (startTime == null) {
                    continue;
                }

                long deadline = startTime.getTime() + dispatchTimeoutMs;
                if (now < deadline) {
                    continue;
                }

                tryMarkDispatchTimeout(fire, now);
            } catch (Exception e) {
                LOG.error("nop.job.timeout.dispatch-check-failed:fireId={}", fire.getJobFireId(), e);
            }
        }
    }

    private void tryMarkDispatchTimeout(NopJobFire fire, long now) {
        NopJobSchedule schedule = scheduleStore.tryLoadSchedule(fire.getJobScheduleId());
        if (schedule == null) {
            LOG.warn("nop.job.timeout.dispatch-schedule-deleted:fireId={}", fire.getJobFireId());
            fireStore.failFireWithoutSchedule(fire.getJobFireId(),
                    ERR_JOB_SCHEDULE_DELETED.getErrorCode(),
                    ERR_JOB_SCHEDULE_DELETED.getDescription());

            List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
            Timestamp endTime = new Timestamp(now);
            for (NopJobTask task : tasks) {
                if (task.getTaskStatus() != null
                        && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_WAITING
                        && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                        && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_RUNNING) {
                    continue;
                }
                task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
                task.setEndTime(endTime);
                task.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
                task.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
                task.setUpdatedBy("system");
                task.setUpdateTime(endTime);
                if (!taskStore.updateTask(task)) {
                    LOG.warn("nop.job.timeout.dispatch-deleted-task-update-conflict:taskId={}", task.getJobTaskId());
                }
            }
            return;
        }

        Timestamp endTime = new Timestamp(now);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
        fire.setEndTime(endTime);
        fire.setDurationMs(startTimeOrNow(fire, now));
        fire.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
        fire.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
        fire.setUpdatedBy("system");
        fire.setUpdateTime(endTime);

        schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
        schedule.setLastEndTime(endTime);
        schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
        schedule.setLastDurationMs(fire.getDurationMs());
        schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + 1);
        schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + 1);
        schedule.setUpdatedBy("system");
        schedule.setUpdateTime(endTime);

        fireStore.completeFireAndUpdateSchedule(fire, schedule);

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        for (NopJobTask task : tasks) {
            if (task.getTaskStatus() != null
                    && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_WAITING
                    && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                    && task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_RUNNING) {
                continue;
            }

            if (cancelHandler != null) {
                try {
                    cancelHandler.cancelRunningTask(schedule, fire, task);
                } catch (Exception e) {
                    LOG.warn("nop.job.timeout.dispatch-cancel-task-failed:taskId={}", task.getJobTaskId(), e);
                }
            }

            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
            task.setEndTime(endTime);
            task.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
            task.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
            task.setUpdatedBy("system");
            task.setUpdateTime(endTime);
            if (task.getStartTime() != null) {
                task.setDurationMs(Math.max(endTime.getTime() - task.getStartTime().getTime(), 0L));
            }
            if (!taskStore.updateTask(task)) {
                LOG.warn("nop.job.timeout.dispatch-task-update-conflict:taskId={}", task.getJobTaskId());
            }
        }

        if (alarmHandler != null) {
            try {
                JobAlarmEvent event = new JobAlarmEvent(
                        fire.getJobFireId(), schedule.getJobScheduleId(), schedule.getJobName(),
                        schedule.getNamespaceId(), schedule.getGroupId(), fire.getErrorCode(),
                        fire.getErrorMessage(), fire.getDurationMs());
                alarmHandler.onFireTimeout(event);
            } catch (Exception e) {
                LOG.warn("nop.job.timeout.dispatch-alarm-failed:fireId={}", fire.getJobFireId(), e);
            }
        }

        LOG.info("nop.job.timeout.dispatch-timeout:fireId={},scheduleId={},elapsed={}ms",
                fire.getJobFireId(), schedule.getJobScheduleId(), fire.getDurationMs());
    }

    private long startTimeOrNow(NopJobFire fire, long now) {
        Timestamp startTime = fire.getStartTime();
        if (startTime == null) {
            Timestamp updateTime = fire.getUpdateTime();
            startTime = updateTime;
        }
        return startTime != null ? Math.max(now - startTime.getTime(), 0L) : 0L;
    }

    private void markSuspiciousAsTimeout(NopJobTask task, Map<String, NopJobFire> fireMap,
                                          Map<String, NopJobSchedule> scheduleMap) {
        long now = scheduleStore.getCurrentTime();

        if (cancelHandler != null) {
            NopJobFire fire = fireMap.get(task.getJobFireId());
            NopJobSchedule schedule = fire != null ? scheduleMap.get(fire.getJobScheduleId()) : null;
            if (fire != null && schedule != null) {
                try {
                    cancelHandler.cancelRunningTask(schedule, fire, task);
                } catch (Exception e) {
                    LOG.warn("nop.job.timeout.cancel-failed:suspicious:taskId={}", task.getJobTaskId(), e);
                }
            }
        }

        Timestamp endTime = new Timestamp(now);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        task.setEndTime(endTime);
        Timestamp startTime = task.getStartTime();
        task.setDurationMs(startTime != null ? Math.max(now - startTime.getTime(), 0L) : 0L);
        task.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
        task.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
        task.setUpdatedBy("system");
        task.setUpdateTime(endTime);
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.suspicious-update-conflict:taskId={}", task.getJobTaskId());
        }

        LOG.info("nop.job.timeout.suspicious-to-timeout:taskId={},workerId={}", task.getJobTaskId(), task.getWorkerInstanceId());
    }

    private void tryMarkTimeout(NopJobTask task, Map<String, NopJobFire> fireMap,
                                Map<String, NopJobSchedule> scheduleMap) {
        Integer taskStatus = task.getTaskStatus();
        if (taskStatus == null) {
            return;
        }

        if (taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
            markSuspiciousAsTimeout(task, fireMap, scheduleMap);
            return;
        }

        if (taskStatus != _NopJobCoreConstants.TASK_STATUS_RUNNING) {
            return;
        }

        Timestamp startTime = task.getStartTime();
        if (startTime == null) {
            return;
        }

        NopJobFire fire = fireMap.get(task.getJobFireId());
        if (fire == null) {
            return;
        }

        NopJobSchedule schedule = scheduleMap.get(fire.getJobScheduleId());
        if (schedule == null) {
            return;
        }

        int timeoutSeconds = defaultInt(schedule.getTimeoutSeconds());
        long effectiveTimeoutMs;
        if (timeoutSeconds > 0) {
            effectiveTimeoutMs = timeoutSeconds * 1000L;
        } else if (executionTimeoutMs > 0) {
            effectiveTimeoutMs = executionTimeoutMs;
        } else {
            effectiveTimeoutMs = dispatchTimeoutMs;
        }
        if (effectiveTimeoutMs <= 0) {
            return;
        }

        long now = scheduleStore.getCurrentTime();
        long deadline = startTime.getTime() + effectiveTimeoutMs;
        if (now < deadline) {
            return;
        }

        if (cancelHandler != null) {
            try {
                cancelHandler.cancelRunningTask(schedule, fire, task);
            } catch (Exception e) {
                LOG.warn("nop.job.timeout.cancel-failed:scheduleId={},fireId={},taskId={}",
                        schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), e);
            }
        }

        Timestamp endTime = new Timestamp(now);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        task.setEndTime(endTime);
        task.setDurationMs(Math.max(endTime.getTime() - startTime.getTime(), 0L));
        task.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
        task.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
        task.setUpdatedBy("system");
        task.setUpdateTime(endTime);
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.task-update-conflict:taskId={}", task.getJobTaskId());
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
