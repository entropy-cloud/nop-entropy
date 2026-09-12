package io.nop.job.coordinator.engine;

import io.nop.job.core.JobCoreErrors;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.coordinator.metrics.IJobDispatcherMetrics;
import io.nop.job.coordinator.metrics.JobDispatcherMetricsImpl;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ARG_DISPATCH_MODE;
import static io.nop.job.core.JobCoreErrors.ARG_JOB_FIRE_ID;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_AVAILABLE_INSTANCE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;

public class JobDispatcherScannerImpl extends AbstractBatchScanner implements IJobDispatcherScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobDispatcherScannerImpl.class);
    static final String SINGLE_DISPATCH_MODE = "single";

    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private Map<String, IJobTaskBuilder> taskBuilders = Map.of();
    private IJobDispatcherMetrics dispatcherMetrics = new JobDispatcherMetricsImpl();
    private IWorkerLoadProvider workerLoadProvider;
    private long lockTimeoutMs = 60000;
    private long noWorkerBackoffMs = 30000;

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    /**
     * 按 dispatchMode 路由的 task builder 注册表（key = 去前缀 bean id，如 "single"/"partition"/"broadcast"/"bestFit"），
     * 由 IoC 容器经 {@code <ioc:collect-beans as-map="true" name-prefix="nopJobTaskBuilder_" .../>} 注入，不经运行时
     * bean 名拼接。plan 339：executorKind 不再参与 coordinator 侧路由。
     */
    public void setTaskBuilders(Map<String, IJobTaskBuilder> taskBuilders) {
        this.taskBuilders = taskBuilders != null ? taskBuilders : Map.of();
    }

    Map<String, IJobTaskBuilder> getTaskBuilders() {
        return taskBuilders;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setDispatcherMetrics(IJobDispatcherMetrics dispatcherMetrics) {
        this.dispatcherMetrics = dispatcherMetrics;
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
        applyScanIntervalMs(scanIntervalMs);
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.batch-size|100")
    public void setBatchSize(int batchSize) {
        applyBatchSize(batchSize);
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.max-scan-loops|1000")
    public void setMaxScanLoops(int maxScanLoops) {
        applyMaxScanLoops(maxScanLoops);
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        if (lockTimeoutMs < 1000) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.dispatcher.lock-timeout-ms")
                    .param(JobCoreErrors.ARG_VALUE, lockTimeoutMs);
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
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.coordinator.no-worker-backoff-ms")
                    .param(JobCoreErrors.ARG_VALUE, noWorkerBackoffMs);
        }
        this.noWorkerBackoffMs = noWorkerBackoffMs;
    }

    @Override
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.dispatcher.scan-failed", e);
    }

    @Override
    @SingleSession
    protected boolean scanBatch() {
        IntRangeSet partitions = resolvePartitions();
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
                    // check2 [P3-11]: fire 已被并发流转出 DISPATCHING 时 store 返回 false（未插入
                    // 任务行），不计入 dispatchedCount/onFiresDispatched，debug 留痕静默跳过
                    if (fireStore.insertTasksAndMarkFireDispatching(fire, tasks)) {
                        dispatchedCount++;
                    } else {
                        LOG.debug("nop.job.dispatcher.dispatch-skipped-not-dispatching:fireId={}",
                                fire.getJobFireId());
                    }
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

    /**
     * 瞬态无worker的两种形态同等回退重试（revert-to-waiting + backoff）：bestFit无匹配worker与
     * broadcast/partition无健康实例（滚动发布、目标服务整体重启的典型瞬态）。后者原只记error，
     * fire停留DISPATCHING等300s超时被判TIMEOUT丢弃，造成任务漏执行。
     */
    private static boolean isNoFittingWorker(NopException e) {
        String code = e.getErrorCode();
        return code != null && (code.equals(ERR_JOB_NO_FITTING_WORKER.getErrorCode())
                || code.equals(ERR_JOB_NO_AVAILABLE_INSTANCE.getErrorCode()));
    }

    /**
     * plan 339：dispatchMode 是 coordinator 侧唯一路由键（single/null/blank 均归一为 "single"），
     * executorKind 不参与 coordinator 路由（仅 worker 侧 invoker 选择）。未知 dispatchMode 显式
     * fail-fast（AR-87），不静默降级。
     */
    IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
        String dispatchMode = fire.getDispatchMode();
        if (dispatchMode == null || dispatchMode.isBlank()) {
            dispatchMode = SINGLE_DISPATCH_MODE;
        }
        IJobTaskBuilder builder = taskBuilders.get(dispatchMode);
        if (builder == null) {
            throw new NopException(ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED)
                    .param(ARG_DISPATCH_MODE, dispatchMode)
                    .param(ARG_JOB_FIRE_ID, fire.getJobFireId());
        }
        return builder;
    }

    private static int normalizeCost(Integer value) {
        return value != null ? value : 0;
    }
}
