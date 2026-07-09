package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.coordinator.metrics.EmptyJobDispatcherMetrics;
import io.nop.job.coordinator.metrics.IJobDispatcherMetrics;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.job.core.JobCoreErrors.ARG_DISPATCH_MODE;
import static io.nop.job.core.JobCoreErrors.ARG_JOB_FIRE_ID;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;

public class JobDispatcherScannerImpl extends AbstractBatchScanner implements IJobDispatcherScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobDispatcherScannerImpl.class);
    static final String TASK_BUILDER_PREFIX = "nopJobTaskBuilder_";

    private IJobFireStore fireStore;
    private IJobTaskBuilder defaultTaskBuilder;
    private IJobScheduleStore scheduleStore;
    private IJobDispatcherMetrics dispatcherMetrics = new EmptyJobDispatcherMetrics();
    private JobPartitionResolver partitionResolver;
    private IWorkerLoadProvider workerLoadProvider;
    private long lockTimeoutMs = 60000;
    private long noWorkerBackoffMs = 30000;

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

    /**
     * AR-96：可选注入 worker load provider，用于在 scanOnce 批处理作用域内做 per-scan 缓存
     * （bestFit 派发的服务发现 + 聚合不随 fire 数线性增长）。未启用 bestFit 时可不注入。
     */
    @Inject
    public void setWorkerLoadProvider(@jakarta.annotation.Nullable IWorkerLoadProvider workerLoadProvider) {
        this.workerLoadProvider = workerLoadProvider;
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

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.max-scan-loops|1000")
    public void setMaxScanLoops(int maxScanLoops) {
        if (maxScanLoops < 1) {
            throw new IllegalArgumentException(
                    "nop.job.coordinator.dispatcher.max-scan-loops must be >= 1, got " + maxScanLoops);
        }
        this.maxScanLoops = maxScanLoops;
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
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.dispatcher.scan-failed", e);
    }

    @Override
    protected void scanOnce() {
        super.scanOnce();
    }

    @Override
    @SingleSession
    protected boolean scanBatch() {
        IntRangeSet partitions = partitionResolver != null ? partitionResolver.resolvePartitions() : null;
        var fires = fireStore.fetchWaitingFires(batchSize, partitions);
        if (fires.isEmpty()) {
            return false;
        }

        dispatcherMetrics.onWaitingFires(fires.size());

        var locked = fireStore.tryLockFiresForDispatch(fires, AppConfig.hostId(), lockTimeoutMs);

        int conflictCount = fires.size() - locked.size();
        if (conflictCount > 0) {
            dispatcherMetrics.onDispatchConflicts(conflictCount);
        }

        int dispatchedCount = 0;
        if (workerLoadProvider != null) {
            workerLoadProvider.beginScan();
        }
        try {
            for (NopJobFire fire : locked) {
                try {
                    IJobTaskBuilder builder = resolveTaskBuilder(fire);
                    List<NopJobTask> tasks = builder.buildTasks(fire);
                    NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
                    for (NopJobTask task : tasks) {
                        if (task.getCostCpu() == null) {
                            task.setCostCpu(normalizeCost(schedule.getTaskCostCpu()));
                        }
                        if (task.getCostMemory() == null) {
                            task.setCostMemory(normalizeCost(schedule.getTaskCostMemory()));
                        }
                        if (task.getPriority() == null) {
                            task.setPriority(normalizeCost(schedule.getPriority()));
                        }
                    }
                    fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
                    dispatchedCount++;
                } catch (NopException e) {
                    if (isNoFittingWorker(e)) {
                        long backoffUntil = scheduleStore.getCurrentTime() + Math.max(noWorkerBackoffMs, 1L);
                        boolean reverted = fireStore.revertDispatchingFireToWaiting(fire, backoffUntil);
                        LOG.warn("nop.job.dispatcher.no-fitting-worker-revert:fireId={},backoffMs={},reverted={}",
                                fire.getJobFireId(), noWorkerBackoffMs, reverted);
                    } else {
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
        } finally {
            if (workerLoadProvider != null) {
                workerLoadProvider.endScan();
            }
        }

        return fires.size() >= batchSize;
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
            // AR-87: explicit failure instead of silent fallback to single. A configured dispatchMode
            // (e.g. "bestFit"/"partition") with a missing bean is a config error (module not loaded, bean
            // name typo). Fail fast (caught by per-fire isolation) rather than silently degrading.
            throw new NopException(ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED)
                    .param(ARG_DISPATCH_MODE, dispatchMode)
                    .param(ARG_JOB_FIRE_ID, fire.getJobFireId());
        }
        // dispatchMode ∈ {null, blank, "single"}: keep executorKind → default fallback (guards the
        // rpcBroadcast-via-executorKind legal route, see TestJobDispatcherScannerRouting).
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

    private static int normalizeCost(Integer value) {
        return value != null ? value : 0;
    }
}
