package io.nop.job.worker.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.worker.capacity.IWorkerCapacityProvider;
import io.nop.job.worker.metrics.EmptyJobWorkerMetrics;
import io.nop.job.worker.metrics.IJobWorkerMetrics;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_RETURNED_NULL;

public class JobWorkerScannerImpl implements IJobWorkerScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobWorkerScannerImpl.class);
    static final int OVERFETCH_FACTOR = 3;

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobInvokerResolver invokerResolver;
    private IJobExecutionContextBuilder executionContextBuilder;
    private IWorkerCapacityProvider capacityProvider;
    private IJobWorkerMetrics workerMetrics = new EmptyJobWorkerMetrics();
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long lockTimeoutMs = 60000;
    private int maxConcurrency = 0;
    private boolean enforceAttribution = false;
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
    public void setInvokerResolver(IJobInvokerResolver invokerResolver) {
        this.invokerResolver = invokerResolver;
    }

    @Inject
    public void setExecutionContextBuilder(IJobExecutionContextBuilder executionContextBuilder) {
        this.executionContextBuilder = executionContextBuilder;
    }

    public void setWorkerMetrics(IJobWorkerMetrics workerMetrics) {
        this.workerMetrics = workerMetrics;
    }

    @Inject
    public void setCapacityProvider(IWorkerCapacityProvider capacityProvider) {
        this.capacityProvider = capacityProvider;
    }

    @InjectValue("@cfg:nop.job.worker.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.worker.scan-interval-ms must be >= 1000, got " + scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.worker.batch-size|100")
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "nop.job.worker.batch-size must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.worker.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        if (lockTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.worker.lock-timeout-ms must be >= 1000, got " + lockTimeoutMs);
        }
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @InjectValue("@cfg:nop.job.worker.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitions != null && !partitions.isEmpty()) {
            this.assignedPartitions = IntRangeSet.parse(partitions);
        }
    }

    @InjectValue("@cfg:nop.job.worker.max-concurrency|0")
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    @InjectValue("@cfg:nop.job.fetch.enforce-attribution|false")
    public void setEnforceAttribution(boolean enforceAttribution) {
        this.enforceAttribution = enforceAttribution;
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
            int effectiveBatchSize = batchSize;

            // 1. Count-based ceiling (backward-compatible hard cap)
            if (maxConcurrency > 0) {
                long runningCount = taskStore.countInFlightTasks(AppConfig.hostId());
                int remaining = maxConcurrency - (int) runningCount;
                if (remaining <= 0) {
                    workerMetrics.onRejected((int) runningCount);
                    return;
                }
                effectiveBatchSize = Math.min(batchSize, remaining);
            }

            // 2. Resource-based check
            ResourceVector myCapacity = capacityProvider.getMyCapacity();
            ResourceVector myReserved = taskStore.sumReservedCost(AppConfig.hostId());
            ResourceVector myRemaining = myCapacity.subtract(myReserved);
            if (myRemaining.isZeroOrNegative()) {
                LOG.warn("nop.job.worker.resource-exhausted:cpu={},mem={}",
                        myRemaining.getCpu(), myRemaining.getMemory());
                workerMetrics.onRejected(0);
                return;
            }

            // 3. Overfetch candidates, client-side fit filter
            int overfetchBatchSize = effectiveBatchSize * OVERFETCH_FACTOR;
            List<NopJobTask> candidates = taskStore.fetchWaitingTasks(
                    overfetchBatchSize, assignedPartitions, AppConfig.hostId(), enforceAttribution);
            if (candidates.isEmpty()) {
                return;
            }

            List<NopJobTask> tasks = new ArrayList<>();
            for (NopJobTask task : candidates) {
                if (tasks.size() >= effectiveBatchSize)
                    break;
                if (myRemaining.fits(new ResourceVector(task.getCostCpu(), task.getCostMemory()))) {
                    tasks.add(task);
                }
            }

            if (tasks.isEmpty()) {
                return;
            }

            // 4. CAS grab (unchanged)
            List<NopJobTask> lockedTasks = taskStore.tryLockTasksForExecute(tasks, AppConfig.hostId(), lockTimeoutMs);
            if (!lockedTasks.isEmpty()) {
                workerMetrics.onTasksClaimed(lockedTasks.size());
            }
            for (NopJobTask task : lockedTasks) {
                executeTask(task);
            }
        } catch (Exception e) {
            LOG.error("nop.job.worker.scan-failed", e);
        }
    }

    private void executeTask(NopJobTask task) {
        NopJobFire fire = fireStore.loadFire(task.getJobFireId());
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        IJobInvoker invoker;
        try {
            invoker = invokerResolver.resolveInvoker(schedule, fire);
        } catch (NopException e) {
            LOG.error("nop.job.worker.invoker-resolve-failed", e);
            completeTaskWithFailure(task, e.getErrorCode(), e.getDescription());
            return;
        }

        NopJobTask runningTask = taskStore.loadTask(task.getJobTaskId());
        if (runningTask.getTaskStatus() == null
                || runningTask.getTaskStatus() != io.nop.job.core._NopJobCoreConstants.TASK_STATUS_CLAIMED) {
            return;
        }

        long now = scheduleStore.getCurrentTime();
        runningTask.setTaskStatus(io.nop.job.core._NopJobCoreConstants.TASK_STATUS_RUNNING);
        runningTask.setStartTime(new Timestamp(now));
        runningTask.setWorkerInstanceId(AppConfig.hostId());
        runningTask.setUpdatedBy("system");
        runningTask.setUpdateTime(new Timestamp(now));
        taskStore.updateTask(runningTask);

        IJobExecutionContext ctx = executionContextBuilder.buildContext(schedule, fire, runningTask);
        try {
            var promise = invoker.invokeAsync(ctx);
            if (promise == null) {
                handleExecutionResult(runningTask.getJobTaskId(), null,
                        new NopException(ERR_JOB_INVOKER_RETURNED_NULL));
            } else {
                promise.whenComplete((result, err) -> handleExecutionResult(runningTask.getJobTaskId(), result, err));
            }
        } catch (Exception e) {
            handleExecutionResult(runningTask.getJobTaskId(), null, e);
        }
    }

    private void handleExecutionResult(String jobTaskId, io.nop.job.api.execution.JobFireResult result, Throwable err) {
        try {
            NopJobTask task = taskStore.loadTask(jobTaskId);
            Integer taskStatus = task.getTaskStatus();
            if (taskStatus != null
                    && (taskStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_TIMEOUT
                    || taskStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_CANCELED
                    || taskStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_SUSPICIOUS)) {
                return;
            }
            if (taskStatus == null) {
                return;
            }

            NopJobFire fire = fireStore.loadFire(task.getJobFireId());
            if (fire != null && fire.getFireStatus() != null) {
                int fs = fire.getFireStatus();
                if (fs == io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_CANCELED
                        || fs == io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_TIMEOUT
                        || fs == io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_FAILED
                        || fs == io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_SUCCESS) {
                    LOG.warn("nop.job.worker.fire-already-terminal:taskId={},fireId={},fireStatus={}",
                            jobTaskId, task.getJobFireId(), fs);
                    return;
                }
            }

            JobTaskExecutionUpdate update = executionContextBuilder.buildResultUpdate(task, result, err);
            Timestamp endTime = new Timestamp(scheduleStore.getCurrentTime());

            task.setTaskStatus(update.getTaskStatus());
            task.setEndTime(endTime);
            if (task.getStartTime() != null) {
                task.setDurationMs(Math.max(endTime.getTime() - task.getStartTime().getTime(), 0L));
            }
            if (update.getError() != null) {
                task.setErrorCode(update.getError().getErrorCode());
                task.setErrorMessage(update.getError().getDescription());
            } else {
                task.setErrorCode(null);
                task.setErrorMessage(null);
            }
            if (update.getNextScheduleTime() != null || update.isCompleted()) {
                Map<String, Object> resultPayload = new LinkedHashMap<>();
                if (update.getNextScheduleTime() != null) {
                    resultPayload.put("nextScheduleTime", update.getNextScheduleTime());
                }
                if (update.isCompleted()) {
                    resultPayload.put("completed", true);
                }
                task.setResultPayload(JsonTool.stringify(resultPayload));
            }
            task.setUpdatedBy("system");
            task.setUpdateTime(endTime);
            boolean updated = taskStore.updateTask(task);
            if (!updated) {
                NopJobTask freshTask = taskStore.loadTask(jobTaskId);
                Integer freshStatus = freshTask.getTaskStatus();
                if (freshStatus == null
                        || freshStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_TIMEOUT
                        || freshStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_CANCELED
                        || freshStatus == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
                    return;
                }

                freshTask.setTaskStatus(update.getTaskStatus());
                freshTask.setEndTime(endTime);
                if (freshTask.getStartTime() != null) {
                    freshTask.setDurationMs(Math.max(endTime.getTime() - freshTask.getStartTime().getTime(), 0L));
                }
                if (update.getError() != null) {
                    freshTask.setErrorCode(update.getError().getErrorCode());
                    freshTask.setErrorMessage(update.getError().getDescription());
                } else {
                    freshTask.setErrorCode(null);
                    freshTask.setErrorMessage(null);
                }
                if (update.getNextScheduleTime() != null || update.isCompleted()) {
                    Map<String, Object> resultPayload = new LinkedHashMap<>();
                    if (update.getNextScheduleTime() != null) {
                        resultPayload.put("nextScheduleTime", update.getNextScheduleTime());
                    }
                    if (update.isCompleted()) {
                        resultPayload.put("completed", true);
                    }
                    freshTask.setResultPayload(JsonTool.stringify(resultPayload));
                }
                freshTask.setUpdatedBy("system");
                freshTask.setUpdateTime(endTime);
                if (!taskStore.updateTask(freshTask)) {
                    LOG.warn("nop.job.worker.update-task-conflict-after-retry:taskId={},status={},resultStatus={}",
                            jobTaskId, freshTask.getTaskStatus(), update.getTaskStatus());
                }
            }

            long duration = task.getStartTime() != null ? Math.max(endTime.getTime() - task.getStartTime().getTime(), 0L) : 0L;
            if (update.getTaskStatus() == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_SUCCESS) {
                workerMetrics.onTaskSuccess(duration);
            } else if (update.getTaskStatus() == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_TIMEOUT) {
                workerMetrics.onTaskTimeout(duration);
            } else {
                workerMetrics.onTaskFailure(duration);
            }
        } catch (Exception e) {
            LOG.error("nop.job.worker.handle-result-failed:taskId={}", jobTaskId, e);
        }
    }

    private void completeTaskWithFailure(NopJobTask task, String errorCode, String errorMessage) {
        NopJobTask freshTask = taskStore.loadTask(task.getJobTaskId());
        if (freshTask.getTaskStatus() != null
                && (freshTask.getTaskStatus() == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_SUSPICIOUS
                || freshTask.getTaskStatus() == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_TIMEOUT
                || freshTask.getTaskStatus() == io.nop.job.core._NopJobCoreConstants.TASK_STATUS_CANCELED)) {
            return;
        }

        Timestamp endTime = new Timestamp(scheduleStore.getCurrentTime());
        freshTask.setTaskStatus(io.nop.job.core._NopJobCoreConstants.TASK_STATUS_FAILED);
        freshTask.setStartTime(endTime);
        freshTask.setEndTime(endTime);
        freshTask.setDurationMs(0L);
        freshTask.setErrorCode(errorCode);
        freshTask.setErrorMessage(errorMessage);
        freshTask.setWorkerInstanceId(AppConfig.hostId());
        freshTask.setUpdatedBy("system");
        freshTask.setUpdateTime(endTime);
        if (!taskStore.updateTask(freshTask)) {
            LOG.warn("nop.job.worker.complete-task-failure-update-conflict:taskId={}", freshTask.getJobTaskId());
        }
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
