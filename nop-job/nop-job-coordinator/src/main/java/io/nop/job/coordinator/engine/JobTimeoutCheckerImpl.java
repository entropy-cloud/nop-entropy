package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.core._NopJobCoreConstants;
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
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JobTimeoutCheckerImpl implements IJobTimeoutChecker {
    static final Logger LOG = LoggerFactory.getLogger(JobTimeoutCheckerImpl.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobCancelHandler cancelHandler;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private IntRangeSet assignedPartitions;
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
            List<NopJobTask> tasks = taskStore.fetchRunningTasks(batchSize, assignedPartitions);
            for (NopJobTask task : tasks) {
                tryMarkTimeout(task);
            }
        } catch (Exception e) {
            LOG.error("nop.job.timeout.scan-failed", e);
        }
    }

    private void tryMarkTimeout(NopJobTask task) {
        Timestamp startTime = task.getStartTime();
        if (startTime == null) {
            return;
        }

        NopJobFire fire = fireStore.loadFire(task.getJobFireId());
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
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
        task.setErrorCode("JOB_TIMEOUT");
        task.setErrorMessage("Job task timed out");
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
