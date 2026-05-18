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
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.timeout.batch-size|100")
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatch-timeout-ms|300000")
    public void setDispatchTimeoutMs(long dispatchTimeoutMs) {
        this.dispatchTimeoutMs = dispatchTimeoutMs;
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
        } catch (Exception e) {
            LOG.error("nop.job.timeout.scan-failed", e);
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
                if (aliveWorkerIds != null) {
                    tryMarkSuspiciousIfWorkerGone(task, aliveWorkerIds);
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
        taskStore.updateTask(task);

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
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        if (schedule == null) {
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
        taskStore.updateTask(task);

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
        if (timeoutSeconds <= 0) {
            return;
        }

        long now = scheduleStore.getCurrentTime();
        long deadline = startTime.getTime() + timeoutSeconds * 1000L;
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
        taskStore.updateTask(task);
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
