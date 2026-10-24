package io.nop.job.worker.engine;

import io.nop.job.core.JobCoreErrors;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobFireStateMachine;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.worker.capacity.IWorkerCapacityProvider;
import io.nop.job.worker.metrics.IJobWorkerMetrics;
import io.nop.job.worker.metrics.JobWorkerMetricsImpl;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_RETURNED_NULL;

public class JobWorkerScannerImpl extends AbstractBatchScanner implements IJobWorkerScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobWorkerScannerImpl.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobInvokerResolver invokerResolver;
    private IJobExecutionContextBuilder executionContextBuilder;
    private IWorkerCapacityProvider capacityProvider;
    private IJobWorkerMetrics workerMetrics = new JobWorkerMetricsImpl();
    private long lockTimeoutMs = 60000;
    private int maxConcurrency = 0;
    private boolean enforceAttribution = false;
    private IntRangeSet assignedPartitions;

    /**
     * MA-06（plan 2496，设计文档 §3.6 裁定⑥）：worker 侧 invoke 看门狗阈值（毫秒），默认 0 = 无执行超时。
     * {@code schedule.timeoutSeconds > 0} 时仍优先（沿用 checker 既有取值顺序）。看门狗是<b>宽松观测</b>：
     * 阈值超限时只记 ERROR 日志 + in-flight 年龄 gauge，永不 complete/cancel invoke future、永不杀派发链；
     * coordinator checker 链是任务终结的唯一仲裁者。
     */
    private long invokeTimeoutMs = 0;

    /**
     * 在飞 invoke 注册表（taskId → 派发开始毫秒时间戳）：in-flight 年龄 gauge 与看门狗观测的事实源。
     * 派发时登记、{@code handleExecutionResult} 回调时移除。
     */
    private final ConcurrentHashMap<String, Long> inFlightInvokeStartMillis = new ConcurrentHashMap<>();

    private final AtomicLong invokeTimeoutObservedCount = new AtomicLong();
    private volatile long lastInvokeTimeoutObservedMillis;

    public JobWorkerScannerImpl() {
        // in-flight 年龄 gauge（C7-002 观测面）：采样时计算最老在飞年龄；registry 为默认 SimpleMeterRegistry，
        // 同名 gauge 重复注册由 micrometer 保留首个，无异常
        io.micrometer.core.instrument.MeterRegistry registry =
                io.nop.commons.metrics.GlobalMeterRegistry.instance();
        if (registry != null) {
            io.micrometer.core.instrument.Gauge.builder("nop.job.worker.invoke.inflight.age.max",
                            inFlightInvokeStartMillis,
                            m -> oldestInFlightAgeMillis(System.currentTimeMillis()))
                    .register(registry);
        }
    }

    @InjectValue("@cfg:nop.job.worker.invoke-timeout-ms|0")
    public void setInvokeTimeoutMs(long invokeTimeoutMs) {
        if (invokeTimeoutMs < 0) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.worker.invoke-timeout-ms")
                    .param(JobCoreErrors.ARG_VALUE, invokeTimeoutMs);
        }
        this.invokeTimeoutMs = invokeTimeoutMs;
    }

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
        applyScanIntervalMs(scanIntervalMs);
    }

    @InjectValue("@cfg:nop.job.worker.batch-size|100")
    public void setBatchSize(int batchSize) {
        applyBatchSize(batchSize);
    }

    @InjectValue("@cfg:nop.job.worker.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        if (lockTimeoutMs < 1000) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.worker.lock-timeout-ms")
                    .param(JobCoreErrors.ARG_VALUE, lockTimeoutMs);
        }
        this.lockTimeoutMs = lockTimeoutMs;
    }

    /**
     * Worker overrides base's {@code setAssignedPartitions} to parse partitions into a local
     * {@code IntRangeSet} field rather than going through {@code JobPartitionResolver}.
     * Worker does not use the base's partitionResolver mechanism.
     */
    @Override
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
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.worker.scan-failed", e);
    }

    @Override
    @SingleSession
    protected boolean scanBatch() {
        int effectiveBatchSize = batchSize;

        // 1. Count-based ceiling (backward-compatible hard cap)
        if (maxConcurrency > 0) {
            long runningCount = taskStore.countInFlightTasks(AppConfig.hostId());
            int remaining = maxConcurrency - (int) runningCount;
            if (remaining <= 0) {
                workerMetrics.onRejected((int) runningCount);
                return false;
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
            return false;
        }

        // 3. Fetch candidates, client-side fit filter
        List<NopJobTask> candidates = taskStore.fetchWaitingTasks(
                batchSize, assignedPartitions, AppConfig.hostId(), enforceAttribution);
        if (candidates.isEmpty()) {
            return false;
        }

        String myHostId = AppConfig.hostId();
        List<NopJobTask> tasks = new ArrayList<>();
        for (NopJobTask task : candidates) {
            if (tasks.size() >= effectiveBatchSize)
                break;
            // Defensive null → 0 normalization (second line of defense): historical
            // task rows persisted before the dispatcher normalization (AR-95) may
            // still have null cost. Auto-unboxing a null Integer here would throw
            // NPE and abort the entire scan batch (AR-84).
            int costCpu = normalizeCost(task.getCostCpu());
            int costMemory = normalizeCost(task.getCostMemory());
            ResourceVector cost = new ResourceVector(costCpu, costMemory);

            // AR-83 double-count fix: WAITING tasks already attributed to this worker
            // (workerInstanceId == myHostId) are already included in myReserved (WAITING
            // is in RESERVED_TASK_STATUSES). Claiming them adds no new net load, so add
            // their own cost back to undo the double-count. An idle worker must be able
            // to claim a self-attributed task whose cost approaches capacity (the defect:
            // such a task was previously never claimed because its cost was counted twice,
            // making any task with cost > capacity/2 unclaimable).
            boolean selfAttributed = myHostId.equals(task.getWorkerInstanceId());
            ResourceVector available = selfAttributed ? myRemaining.add(cost) : myRemaining;
            if (available.fits(cost)) {
                tasks.add(task);
                // Decrement remaining for genuinely new (non-self-attributed) load so
                // cumulative claims within one scan do not exceed capacity. Self-attributed
                // claims are already accounted in reserved, so no decrement needed.
                if (!selfAttributed) {
                    myRemaining = myRemaining.subtract(cost);
                }
            }
        }

        if (tasks.isEmpty()) {
            // AR-93: candidates existed but none fit this worker's remaining capacity (or all exceed
            // the window). Emit an observable signal so starvation/stall is diagnosable, distinct
            // from the no-candidates case above (which is normal idle).
            LOG.warn("nop.job.worker.no-fitting-candidate:candidateCount={},remainingCpu={},remainingMem={}",
                    candidates.size(), myRemaining.getCpu(), myRemaining.getMemory());
            workerMetrics.onRejected(0);
            return false;
        }

        // 4. CAS grab (unchanged)
        List<NopJobTask> lockedTasks = taskStore.tryLockTasksForExecute(tasks, AppConfig.hostId(), lockTimeoutMs);
        if (!lockedTasks.isEmpty()) {
            workerMetrics.onTasksClaimed(lockedTasks.size());
        }
        // per-task isolation (AR-86): a single task's loadFire/loadSchedule failure must not abort the
        // remaining already-claimed tasks in this batch.
        for (NopJobTask task : lockedTasks) {
            try {
                executeTask(task);
            } catch (Exception e) {
                LOG.warn("nop.job.worker.task-execute-failed:taskId={}", task.getJobTaskId(), e);
                workerMetrics.onTaskExecuteFailed(1);
            }
        }

        return !lockedTasks.isEmpty() && candidates.size() >= batchSize;
    }

    private void executeTask(NopJobTask task) {
        // 任务已CAS认领为CLAIMED后，fire/schedule加载失败不得只warn：任务会永久滞留CLAIMED
        // （无任何回收路径覆盖该态——liveness链/超时检查/stale重置均不命中），所属fire永远RUNNING。
        // 失败终态化释放整个fire链路
        NopJobFire fire;
        NopJobSchedule schedule;
        try {
            fire = fireStore.loadFire(task.getJobFireId());
            schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        } catch (NopException e) {
            LOG.error("nop.job.worker.task-load-failed:taskId={},fireId={}", task.getJobTaskId(),
                    task.getJobFireId(), e);
            // 与外层per-task隔离catch同口径记录metric（AR-86：失败不静默）
            workerMetrics.onTaskExecuteFailed(1);
            completeTaskWithFailure(task, e.getErrorCode(), e.getDescription());
            return;
        } catch (Exception e) {
            LOG.error("nop.job.worker.task-load-failed:taskId={},fireId={}", task.getJobTaskId(),
                    task.getJobFireId(), e);
            workerMetrics.onTaskExecuteFailed(1);
            completeTaskWithFailure(task, null, e.toString());
            return;
        }
        IJobInvoker invoker;
        try {
            invoker = invokerResolver.resolveInvoker(schedule, fire);
        } catch (NopException e) {
            LOG.error("nop.job.worker.invoker-resolve-failed", e);
            completeTaskWithFailure(task, e.getErrorCode(), e.getDescription());
            return;
        }

        NopJobTask runningTask = taskStore.loadTask(task.getJobTaskId());
        if (!JobTaskStateMachine.isClaimed(runningTask.getTaskStatus())) {
            return;
        }

        // fire 已进入终态（CANCELED/FAILED 等）时其任务不应再执行：回滚认领为 WAITING，
        // 保留可认领机会并避免对已取消批次执行重复副作用（handleExecutionResult 的
        // fire-terminal 检查只覆盖执行完成后，认领后执行前无守卫会漏掉窗口内取消）
        if (JobFireStateMachine.isTerminal(fire.getFireStatus())) {
            LOG.info("nop.job.worker.fire-terminal-skip-invoke:taskId={},fireId={},fireStatus={}",
                    task.getJobTaskId(), fire.getJobFireId(), fire.getFireStatus());
            runningTask.setTaskStatus(io.nop.job.core._NopJobCoreConstants.TASK_STATUS_WAITING);
            runningTask.setWorkerInstanceId(null);
            taskStore.updateTask(runningTask);
            return;
        }

        long now = scheduleStore.getCurrentTime();
        runningTask.setTaskStatus(io.nop.job.core._NopJobCoreConstants.TASK_STATUS_RUNNING);
        runningTask.setStartTime(new Timestamp(now));
        runningTask.setWorkerInstanceId(AppConfig.hostId());
        // AR-85: check the CLAIMED→RUNNING CAS return value. If it failed the task is no longer ours
        // (timeout checker moved it to SUSPICIOUS, or another worker claimed it). Invoking now would
        // execute a task we no longer own → duplicate execution. Skip invokeAsync (aligned with
        // handleExecutionResult which also checks updateTask's return value).
        boolean acquired = taskStore.updateTask(runningTask);
        if (!acquired) {
            LOG.warn("nop.job.worker.claim-cas-failed-skip-invoke:taskId={},status={}",
                    runningTask.getJobTaskId(), runningTask.getTaskStatus());
            workerMetrics.onTaskExecuteFailed(1);
            return;
        }

        IJobExecutionContext ctx = executionContextBuilder.buildContext(schedule, fire, runningTask);
        try {
            var promise = invoker.invokeAsync(ctx);
            if (promise == null) {
                handleExecutionResult(runningTask.getJobTaskId(), null,
                        new NopException(ERR_JOB_INVOKER_RETURNED_NULL));
            } else {
                // MA-06：派发登记在飞 + 宽松看门狗（只观测不终结）；终结唯一通道仍是 invoke future
                // 自身完成 → handleExecutionResult。checker 链（coordinator 侧）保持唯一仲裁者。
                String taskId = runningTask.getJobTaskId();
                // JVM 墙钟（诊断用途，非 DB 时间语义）：autotest 环境的 CoreMetrics 时钟可被测试替换
                long startMillis = System.currentTimeMillis();
                inFlightInvokeStartMillis.put(taskId, startMillis);
                long thresholdMs = resolveInvokeWatchdogThresholdMs(schedule);
                if (thresholdMs > 0) {
                    scheduleInvokeWatchdog(taskId, thresholdMs, startMillis);
                }
                promise.whenComplete((result, err) -> {
                    inFlightInvokeStartMillis.remove(taskId);
                    handleExecutionResult(taskId, result, err);
                });
            }
        } catch (Exception e) {
            handleExecutionResult(runningTask.getJobTaskId(), null, e);
        }
    }

    /**
     * 看门狗阈值解析（设计裁定⑥）：{@code schedule.timeoutSeconds > 0} 优先（沿用 checker 既有取值顺序），
     * 否则取 {@code nop.job.worker.invoke-timeout-ms}（0 = disabled）。
     */
    long resolveInvokeWatchdogThresholdMs(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return timeoutSeconds * 1000L;
        }
        return invokeTimeoutMs;
    }

    /**
     * 宽松看门狗：阈值到期后在飞仍未终结时记 ERROR + 观测计数。永不 complete/cancel invoke future、
     * 永不改任务状态——「长任务误杀」被 plan340 契约与设计裁定⑥双重禁止。protected 供测试注入。
     */
    protected void scheduleInvokeWatchdog(String taskId, long thresholdMs, long startMillis) {
        io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer().schedule(() -> {
            checkInvokeTimeout(taskId, thresholdMs, startMillis);
            return null;
        }, thresholdMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 看门狗观测动作：仅当该任务仍在飞且年龄达到阈值时记录观测信号（ERROR 日志 + 计数/时间戳）。
     * package-private 供回归测试确定性触发。
     */
    void checkInvokeTimeout(String taskId, long thresholdMs, long startMillis) {
        Long registered = inFlightInvokeStartMillis.get(taskId);
        if (registered == null) {
            return; // 已终结（正常完成/失败），无需观测
        }
        long ageMs = System.currentTimeMillis() - startMillis;
        if (ageMs < thresholdMs) {
            return;
        }
        invokeTimeoutObservedCount.incrementAndGet();
        lastInvokeTimeoutObservedMillis = System.currentTimeMillis();
        LOG.error("nop.job.worker.invoke-timeout-observed:taskId={},ageMs={},thresholdMs={} "
                        + "-- observe-only: worker watchdog never terminates; checker chain is the sole arbiter",
                taskId, ageMs, thresholdMs);
    }

    /** 当前在飞 invoke 数（观测用）。 */
    public int getInFlightInvokeCount() {
        return inFlightInvokeStartMillis.size();
    }

    /** 最老在飞 invoke 的年龄（毫秒；无在飞时为 0）——in-flight 年龄 gauge 的取值函数。 */
    public long getOldestInFlightAgeMillis(long nowMillis) {
        return oldestInFlightAgeMillis(nowMillis);
    }

    /** 看门狗观测触发次数（宽松观测信号，测试与运维可读）。 */
    public long getInvokeTimeoutObservedCount() {
        return invokeTimeoutObservedCount.get();
    }

    /** 最近一次看门狗观测触发时间（毫秒；0 = 从未触发）。 */
    public long getLastInvokeTimeoutObservedMillis() {
        return lastInvokeTimeoutObservedMillis;
    }

    private long oldestInFlightAgeMillis(long nowMillis) {
        long oldest = 0;
        for (Long start : inFlightInvokeStartMillis.values()) {
            long age = nowMillis - start;
            if (age > oldest)
                oldest = age;
        }
        return oldest;
    }

    private void handleExecutionResult(String jobTaskId, io.nop.job.api.execution.JobFireResult result, Throwable err) {
        try {
            NopJobTask task = taskStore.loadTask(jobTaskId);
            Integer taskStatus = task.getTaskStatus();
            if (JobTaskStateMachine.isConcurrentlyFinalized(taskStatus)) {
                return;
            }
            if (taskStatus == null) {
                return;
            }

            NopJobFire fire = fireStore.loadFire(task.getJobFireId());
            if (fire != null && JobFireStateMachine.isTerminal(fire.getFireStatus())) {
                LOG.warn("nop.job.worker.fire-already-terminal:taskId={},fireId={},fireStatus={}",
                        jobTaskId, task.getJobFireId(), fire.getFireStatus());
                return;
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
            task.setResultPayload(buildResultPayload(update));
            boolean updated = taskStore.updateTask(task);
            if (!updated) {
                NopJobTask freshTask = taskStore.loadTask(jobTaskId);
                Integer freshStatus = freshTask.getTaskStatus();
                if (freshStatus == null
                        || JobTaskStateMachine.isConcurrentlyFinalized(freshStatus)) {
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
                freshTask.setResultPayload(buildResultPayload(update));
                if (!taskStore.updateTask(freshTask)) {
                    LOG.warn("nop.job.worker.update-task-conflict-after-retry:taskId={},status={},resultStatus={}",
                            jobTaskId, freshTask.getTaskStatus(), update.getTaskStatus());
                }
            }

            long duration = task.getStartTime() != null ? Math.max(endTime.getTime() - task.getStartTime().getTime(), 0L) : 0L;
            if (JobTaskStateMachine.isSuccess(update.getTaskStatus())) {
                workerMetrics.onTaskSuccess(duration);
            } else if (JobTaskStateMachine.isTimeout(update.getTaskStatus())) {
                workerMetrics.onTaskTimeout(duration);
            } else {
                workerMetrics.onTaskFailure(duration);
            }
        } catch (Exception e) {
            LOG.error("nop.job.worker.handle-result-failed:taskId={}", jobTaskId, e);
            failSafeTerminalize(jobTaskId, e);
        } catch (Throwable e) {
            // MA-05/C4-012 站点②：回调动作的 Error 级 Throwable 不得被 whenComplete 衍生 future 静默吞掉
            //（原缺陷 = 零日志 + task 永久滞留 RUNNING → sumReservedCost 容量永久侵蚀）
            LOG.error("nop.job.worker.handle-result-failed:taskId={}", jobTaskId, e);
            workerMetrics.onTaskExecuteFailed(1);
            failSafeTerminalize(jobTaskId, e);
        }
    }

    /**
     * MA-05 终态 fail-safe：{@code handleExecutionResult} 主流程失败（Exception/Error）时尽力把任务
     * 终态化为 FAILED，阻断「task 永久 RUNNING → 容量账本永久侵蚀」。fail-safe 自身失败只记日志，
     * 不再向外抛（回调边界内禁止二次逃逸）。
     */
    private void failSafeTerminalize(String jobTaskId, Throwable cause) {
        try {
            NopJobTask freshTask = taskStore.loadTask(jobTaskId);
            if (freshTask != null) {
                completeTaskWithFailure(freshTask, null, "handle-result-failed: " + cause);
            }
        } catch (Throwable e2) {
            LOG.error("nop.job.worker.handle-result-failsafe-failed:taskId={}", jobTaskId, e2);
        }
    }

    private void completeTaskWithFailure(NopJobTask task, String errorCode, String errorMessage) {
        NopJobTask freshTask = taskStore.loadTask(task.getJobTaskId());
        if (JobTaskStateMachine.isConcurrentlyFinalized(freshTask.getTaskStatus())) {
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
        if (!taskStore.updateTask(freshTask)) {
            LOG.warn("nop.job.worker.complete-task-failure-update-conflict:taskId={}", freshTask.getJobTaskId());
        }
    }

    private static int normalizeCost(Integer value) {
        return value != null ? value : 0;
    }

    private static String buildResultPayload(JobTaskExecutionUpdate update) {
        if (update.getNextScheduleTime() == null && !update.isCompleted()) {
            return null;
        }

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        if (update.getNextScheduleTime() != null) {
            resultPayload.put("nextScheduleTime", update.getNextScheduleTime());
        }
        if (update.isCompleted()) {
            resultPayload.put("completed", true);
        }
        return JsonTool.stringify(resultPayload);
    }
}
