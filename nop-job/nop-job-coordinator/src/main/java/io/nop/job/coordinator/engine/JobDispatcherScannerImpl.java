package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.coordinator.metrics.EmptyJobDispatcherMetrics;
import io.nop.job.coordinator.metrics.IJobDispatcherMetrics;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.job.core.JobCoreErrors.ARG_DISPATCH_MODE;
import static io.nop.job.core.JobCoreErrors.ARG_JOB_FIRE_ID;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;

public class JobDispatcherScannerImpl implements IJobDispatcherScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobDispatcherScannerImpl.class);
    static final String TASK_BUILDER_PREFIX = "nopJobTaskBuilder_";

    private IJobFireStore fireStore;
    private IJobTaskBuilder defaultTaskBuilder;
    private IJobScheduleStore scheduleStore;
    private IJobDispatcherMetrics dispatcherMetrics = new EmptyJobDispatcherMetrics();
    private JobPartitionResolver partitionResolver;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long lockTimeoutMs = 60000;
    private long noWorkerBackoffMs = 30000;
    private volatile boolean running;
    private Future<?> scanFuture;

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    @Inject
    public void setDefaultTaskBuilder(IJobTaskBuilder defaultTaskBuilder) {
        this.defaultTaskBuilder = defaultTaskBuilder;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setDispatcherMetrics(IJobDispatcherMetrics dispatcherMetrics) {
        this.dispatcherMetrics = dispatcherMetrics;
    }

    @Inject
    public void setPartitionResolver(JobPartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.dispatcher.scan-interval-ms must be >= 1000, got " + scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.batch-size|100")
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "nop.job.dispatcher.batch-size must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        if (lockTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.dispatcher.lock-timeout-ms must be >= 1000, got " + lockTimeoutMs);
        }
        this.lockTimeoutMs = lockTimeoutMs;
    }

    /**
     * no-fitting-worker（worker 满载的正常瞬态）回退后的 backoff 窗口（AR-86）。回退时把 fire 的
     * startTime 置为 now + backoffMs，{@code fetchWaitingFires} 在该窗口内跳过此 fire，避免
     * DISPATCHING→WAITING→DISPATCHING 紧循环。默认 30000ms。{@code 0} 表示不 backoff（每轮重试）。
     */
    @InjectValue("@cfg:nop.job.coordinator.no-worker-backoff-ms|30000")
    public void setNoWorkerBackoffMs(long noWorkerBackoffMs) {
        if (noWorkerBackoffMs < 0) {
            throw new IllegalArgumentException(
                    "nop.job.coordinator.no-worker-backoff-ms must be >= 0, got " + noWorkerBackoffMs);
        }
        this.noWorkerBackoffMs = noWorkerBackoffMs;
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
            var fires = fireStore.fetchWaitingFires(batchSize, partitions);
            if (fires.isEmpty()) {
                return;
            }

            dispatcherMetrics.onWaitingFires(fires.size());

            var locked = fireStore.tryLockFiresForDispatch(fires, AppConfig.hostId(), lockTimeoutMs);

            int conflictCount = fires.size() - locked.size();
            if (conflictCount > 0) {
                dispatcherMetrics.onDispatchConflicts(conflictCount);
            }

            int dispatchedCount = 0;
            for (NopJobFire fire : locked) {
                // per-fire isolation (AR-86): a single fire's failure (deleted schedule, no-fitting-worker,
                // etc.) must NOT abort the remaining fires in this batch — they were already pre-locked to
                // DISPATCHING and would otherwise sit stuck until the 5-min dispatch-timeout.
                try {
                    IJobTaskBuilder builder = resolveTaskBuilder(fire);
                    List<NopJobTask> tasks = builder.buildTasks(fire);
                    NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
                    for (NopJobTask task : tasks) {
                        // Normalize null → 0: schedule cost/priority are nullable Integer.
                        // Without this, dispatcher overwrites builder values with null,
                        // which causes worker fit-check NPE (AR-84) and SQL SUM to skip
                        // rows (AR-95). Single point of normalization; builder paths that
                        // already set non-null values are unaffected.
                        task.setCostCpu(normalizeCost(schedule.getTaskCostCpu()));
                        task.setCostMemory(normalizeCost(schedule.getTaskCostMemory()));
                        task.setPriority(normalizeCost(schedule.getPriority()));
                    }
                    fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
                    dispatchedCount++;
                } catch (NopException e) {
                    if (isNoFittingWorker(e)) {
                        // transient (all workers currently full): revert DISPATCHING→WAITING with backoff
                        // so the fire is retried later, instead of being stranded DISPATCHING until the
                        // dispatch-timeout marks it FAILED (fail-vs-defer).
                        long backoffUntil = scheduleStore.getCurrentTime() + Math.max(noWorkerBackoffMs, 1L);
                        boolean reverted = fireStore.revertDispatchingFireToWaiting(fire, backoffUntil);
                        LOG.warn("nop.job.dispatcher.no-fitting-worker-revert:fireId={},backoffMs={},reverted={}",
                                fire.getJobFireId(), noWorkerBackoffMs, reverted);
                    } else {
                        // e.g. schedule deleted (requireEntityById threw): leave fire DISPATCHING; the
                        // timeout checker's scanDispatchTimeouts will recycle it (TIMEOUT/FAILED).
                        LOG.error("nop.job.dispatcher.fire-dispatch-failed:fireId={}", fire.getJobFireId(), e);
                    }
                    dispatcherMetrics.onFireDispatchFailed(1);
                } catch (Exception e) {
                    LOG.error("nop.job.dispatcher.fire-dispatch-failed:fireId={}", fire.getJobFireId(), e);
                    dispatcherMetrics.onFireDispatchFailed(1);
                }
            }

            if (dispatchedCount > 0) {
                dispatcherMetrics.onFiresDispatched(dispatchedCount);
            }
        } catch (Exception e) {
            LOG.error("nop.job.dispatcher.scan-failed", e);
        }
    }

    private static boolean isNoFittingWorker(NopException e) {
        String code = e.getErrorCode();
        return code != null && code.equals(ERR_JOB_NO_FITTING_WORKER.getErrorCode());
    }

    IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
        String dispatchMode = fire.getDispatchMode();
        if (dispatchMode != null && !dispatchMode.isBlank() && !"single".equals(dispatchMode)) {
            String beanName = TASK_BUILDER_PREFIX + dispatchMode;
            Object bean = BeanContainer.tryGetBean(beanName);
            if (bean instanceof IJobTaskBuilder) {
                return (IJobTaskBuilder) bean;
            }
        }
        String executorKind = fire.getExecutorKind();
        if (executorKind != null && !executorKind.isBlank()) {
            String beanName = TASK_BUILDER_PREFIX + executorKind;
            Object bean = BeanContainer.tryGetBean(beanName);
            if (bean instanceof IJobTaskBuilder) {
                return (IJobTaskBuilder) bean;
            }
        }
        return defaultTaskBuilder;
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }

    private static int normalizeCost(Integer value) {
        return value != null ? value : 0;
    }
}
