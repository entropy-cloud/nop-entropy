package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
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
import java.util.HashMap;
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
    private JobPartitionResolver partitionResolver;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
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

            for (NopJobTask task : tasks) {
                tryMarkTimeout(task, fireMap, scheduleMap);
            }
        } catch (Exception e) {
            LOG.error("nop.job.timeout.scan-failed", e);
        }
    }

    private void tryMarkTimeout(NopJobTask task, Map<String, NopJobFire> fireMap,
                                Map<String, NopJobSchedule> scheduleMap) {
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

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
