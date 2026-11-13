package io.nop.job.coordinator.engine;

import io.nop.job.core.JobCoreErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_SCHEDULE_DELETED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TIMEOUT;

public class JobTimeoutCheckerImpl extends AbstractBatchScanner implements IJobTimeoutChecker {
    static final Logger LOG = LoggerFactory.getLogger(JobTimeoutCheckerImpl.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IJobCancelHandler cancelHandler;
    private IJobAlarmHandler alarmHandler;
    private INamingService namingService;
    private RpcPollTaskManager rpcPollTaskManager;
    private volatile String workerServiceName;
    private final AtomicBoolean livenessDisabledWarned = new AtomicBoolean(false);
    // CRA 8.46 R4-07 W-03 (adjudication A.3): liveness 观测计数（镜像 2496 计数器
    // 家族形态——零平台 metric 注册接线，owner doc §五 观测行承载）
    private final java.util.concurrent.atomic.AtomicLong suspiciousDeferredCount =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong suspiciousStaleForcedCount =
            new java.util.concurrent.atomic.AtomicLong();
    private long dispatchTimeoutMs = 300000;
    private long executionTimeoutMs = -1;
    private long taskDispatchWaitTimeoutMs = 600000;

    // CRA 8.46 R4-07 W-03 (1.4-C7-003, adjudication A): SUSPICIOUS 复查顺延窗口的
    // stale 上限（毫秒）。>0 启用（缺省 60000=12 轮@5s scan-interval）；≤0 装配期
    // fail-fast——防护面静默禁用即伪防线。stale 锚=task.updateTime（SUSPICIOUS 行
    // 写入后无后续写点：迟到结果被 isConcurrentlyFinalized 拒绝、强转前无 cancel）。
    private long suspiciousStaleMs = 60000;

    @Inject
    public void setTaskStore(IJobTaskStore taskStore) {
        this.taskStore = taskStore;
    }
    @InjectValue("@cfg:nop.job.coordinator.timeout.suspicious-stale-ms|60000")
    public void setSuspiciousStaleMs(long ms) {
        // CRA 8.46 R4-07 W-03: ≤0 = 防护面静默禁用即伪防线——装配期 fail-fast
        if (ms <= 0) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.coordinator.timeout.suspicious-stale-ms")
                    .param(JobCoreErrors.ARG_VALUE, ms);
        }
        this.suspiciousStaleMs = ms;
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

    /**
     * check2 [P1-1]: 存活链依赖的发现服务。此前为无 {@code @Inject} 的普通 setter 且所有 beans.xml
     * 均未装配该 property，默认部署下恒为 null，worker 崩溃后 RUNNING 任务/fire 的
     * SUSPICIOUS→TIMEOUT 回收链整体失效。改为 {@code @Inject @Nullable} 按类型可选注入：
     * 容器中存在 {@link INamingService} bean（如 SysDaoNamingService/NacosNamingService）即接线，
     * 不存在时不阻断启动（回退 null，链路关闭并 WARN 一次）。
     */
    @Inject
    public void setNamingService(@Nullable INamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * plan 2513 1.4-C4-001 裁定①(c)：rpcPoll 条目 DB 终态回传通道。checker 置 task TIMEOUT
     * 终态后经 manager package-private resolveFromAuthority 回传 in-memory future——否则
     * 「DB 已 TIMEOUT 而 rpcPoll entry future 悬挂」的原缺陷形态残留。同包直连零跨模块 API；
     * 未装配（null，如单元测试直构）时回传通道关闭，checker 置终态主流程不受影响。
     */
    @Inject
    public void setRpcPollTaskManager(RpcPollTaskManager rpcPollTaskManager) {
        this.rpcPollTaskManager = rpcPollTaskManager;
    }

    /**
     * 裁定①(c) 回传降级路径：回传失败不阻断 checker 置终态主流程，ERROR 留痕非静默（rule 24）。
     */
    private void notifyPollManagerTaskTimeout(NopJobTask task) {
        if (rpcPollTaskManager == null) {
            return;
        }
        try {
            rpcPollTaskManager.resolveFromAuthority(task.getJobTaskId(),
                    JobFireResult.ERROR(new ErrorBean(ERR_JOB_TIMEOUT.getErrorCode())));
        } catch (Exception e) {
            LOG.error("nop.job.timeout.poll-manager-notify-failed:taskId={}", task.getJobTaskId(), e);
        }
    }

    /** 容器接线验证（同 getTaskBuilders 的 test-access 先例）：存活链是否实际装配。 */
    INamingService getNamingService() {
        return namingService;
    }

    /**
     * check2 [P1-1]: 存活检测发现的服务名。默认空 = 复用 {@code AppConfig.appName()}（旧行为）；
     * worker 独立部署（appName 与 coordinator 不同）时通过本配置指定 worker 侧服务名。
     */
    @InjectValue("@cfg:nop.job.coordinator.worker-service-name|")
    public void setWorkerServiceName(String workerServiceName) {
        this.workerServiceName = workerServiceName;
    }

    @InjectValue("@cfg:nop.job.coordinator.timeout.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        applyScanIntervalMs(scanIntervalMs);
    }

    @InjectValue("@cfg:nop.job.coordinator.timeout.batch-size|100")
    public void setBatchSize(int batchSize) {
        applyBatchSize(batchSize);
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
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.coordinator.task-dispatch-wait-timeout-ms")
                    .param(JobCoreErrors.ARG_VALUE, taskDispatchWaitTimeoutMs);
        }
        this.taskDispatchWaitTimeoutMs = taskDispatchWaitTimeoutMs;
    }

    @Override
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.timeout.scan-failed", e);
    }

    // ========== Plan 338: per-cycle cursor state ==========
    //
    // Three sub-scans each own a Cursor registered via newCursor(). The base class
    // AbstractBatchScanner.scanOnce() automatically resets all registered cursors at
    // the start of each scheduling cycle, so this subclass does NOT override
    // onCycleStart() — cursor lifecycle is fully managed by the base class.

    private final Cursor taskCursor = newCursor();
    private final Cursor fireCursor = newCursor();
    private final Cursor waitingCursor = newCursor();

    @Override
    @SingleSession
    protected boolean scanBatch() {
        IntRangeSet partitions = resolvePartitions();
        // Sub-scan 1: task timeouts. drainBatch handles fetch/process/cursor-advance/markDrained.
        drainBatch(taskCursor,
                (t, i) -> taskStore.fetchRunningTasks(batchSize, partitions, t, i),
                this::scanTaskTimeouts,
                NopJobTask::getStartTime, NopJobTask::getJobTaskId);
        // Sub-scan 2: dispatch timeouts. Skip when disabled.
        if (dispatchTimeoutMs > 0) {
            drainBatch(fireCursor,
                    (t, i) -> fireStore.fetchDispatchingFires(batchSize, partitions, t, i),
                    this::processDispatchTimeouts,
                    NopJobFire::getStartTime, NopJobFire::getJobFireId);
        } else {
            fireCursor.markDrained();
        }
        // Sub-scan 3: stale waiting task reset. Skip when disabled.
        if (taskDispatchWaitTimeoutMs > 0) {
            long deadline = scheduleStore.getCurrentTime() - taskDispatchWaitTimeoutMs;
            drainBatch(waitingCursor,
                    (t, i) -> taskStore.resetStaleWaitingTasks(batchSize, partitions, deadline, t, i),
                    this::processStaleWaitingResets,
                    NopJobTask::getCreateTime, NopJobTask::getJobTaskId);
        } else {
            waitingCursor.markDrained();
        }
        return !(taskCursor.isDrained() && fireCursor.isDrained() && waitingCursor.isDrained());
    }

    /**
     * 处理一批 DISPATCHING fire：逐条检查派发超时（startTime + dispatchTimeoutMs 已到），命中则 mark timeout。
     * 单 fire 异常被 try/catch 隔离，不中断整批。
     */
    private void processDispatchTimeouts(List<NopJobFire> dispatchingFires) {
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

    /**
     * 处理一批 stale WAITING reset 结果：仅记 INFO 日志（reset 操作已在 store 层完成）。
     */
    private void processStaleWaitingResets(List<NopJobTask> reset) {
        int count = reset.size();
        if (count > 0) {
            LOG.info("nop.job.timeout.stale-waiting-task-reset:count={},waitTimeoutMs={}",
                    count, taskDispatchWaitTimeoutMs);
        }
    }

    /**
     * 处理一批 RUNNING_LIKE task：批量预取 fire + schedule，逐条尝试 mark suspicious/timeout。
     * Cursor 推进在 caller 处基于本批 list 的最后一条，不依赖单条处理成功与否。
     */
    private void scanTaskTimeouts(List<NopJobTask> tasks) {
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
                if (JobTaskStateMachine.isSuspicious(task.getTaskStatus())
                        && !JobTaskStateMachine.isSuspicious(statusBefore)) {
                    continue;
                }
                // CRA 8.46 R4-07 W-03 (1.4-C7-003, adjudication A.1): SUSPICIOUS
                // 复查顺延——强转前复查本轮存活集：worker 已恢复则顺延本轮（不
                // cancel 不强转——消除「注册中心瞬态抖动→健康 worker 在途执行被
                // 误杀」的 5s 棘轮窗）；顺延受 stale 上限约束（60s 有界终局）。
                // aliveWorkerIds==null（liveness 禁用轮）按裁定 A 决策 2 不变式：
                // 本轮不复查不强转，仅 stale 超限强转。真实消失场景两轮窗不变。
                if (JobTaskStateMachine.isSuspicious(task.getTaskStatus())) {
                    boolean defer;
                    if (aliveWorkerIds != null
                            && task.getWorkerInstanceId() != null
                            && aliveWorkerIds.contains(task.getWorkerInstanceId())) {
                        defer = true;
                    } else if (aliveWorkerIds != null) {
                        defer = false; // 既有强转路径原样（复查不命中）
                    } else {
                        defer = true; // liveness 禁用轮：不复查不强转（F-M1 决策 2 不变式）
                    }
                    long updateTime = task.getUpdateTime() != null
                            ? task.getUpdateTime().getTime() : 0L;
                    // clock source = scheduleStore.getCurrentTime()（既有 max 时钟——
                    // C7-008 零新时钟源纪律，与 scanTaskTimeouts 同源）
                    boolean stale = suspiciousStaleMs > 0
                            && scheduleStore.getCurrentTime() - updateTime > suspiciousStaleMs;
                    if (stale) {
                        suspiciousStaleForcedCount.incrementAndGet();
                        // stale 强转：fall through to tryMarkTimeout (SUSPICIOUS branch)
                    } else if (defer) {
                        suspiciousDeferredCount.incrementAndGet();
                        continue;
                    }
                }
                tryMarkTimeout(task, fireMap, scheduleMap);
            } catch (Exception e) {
                LOG.error("nop.job.timeout.task-check-failed:taskId={}", task.getJobTaskId(), e);
            }
        }
    }

    private Set<String> resolveAliveWorkerIds() {
        if (namingService == null) {
            // check2 [P1-1]: 存活链未接线时提示一次——无超时配置的部署下 RUNNING 任务将无法回收
            if (livenessDisabledWarned.compareAndSet(false, true)) {
                LOG.warn("nop.job.timeout.worker-liveness-disabled:namingService not configured; "
                        + "RUNNING tasks of crashed workers can only be recovered via timeout configs "
                        + "(schedule.timeoutSeconds / nop.job.coordinator.execution-timeout-ms)");
            }
            return null;
        }
        try {
            String svcName = StringHelper.isEmpty(workerServiceName) ? AppConfig.appName() : workerServiceName;
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
            // CRA 8.46 R4-07 W-03 (adjudication A.2): 「非空但全部 unhealthy」→ null
            // （发现服务本轮不可信，禁用 liveness 判定）——与既有三处 null 分支语义
            // 对齐；空集非 null 会使集群全部在途 task 被判「worker 全灭」棘轮标记。
            if (alive.isEmpty()) {
                return null;
            }
            return alive;
        } catch (Exception e) {
            LOG.warn("nop.job.timeout.resolve-workers-failed", e);
            return null;
        }
    }

    /** CRA 8.46 R4-07 W-03: SUSPICIOUS 顺延次数（liveness 观测面）。 */
    public long getSuspiciousDeferredCount() {
        return suspiciousDeferredCount.get();
    }

    /** CRA 8.46 R4-07 W-03: stale 超限强制强转次数（顺延上界兜底观测面）。 */
    public long getSuspiciousStaleForcedCount() {
        return suspiciousStaleForcedCount.get();
    }

    private void tryMarkSuspiciousIfWorkerGone(NopJobTask task, Set<String> aliveWorkerIds) {
        Integer status = task.getTaskStatus();
        if (!JobTaskStateMachine.isInFlight(status)) {
            return;
        }

        String workerId = task.getWorkerInstanceId();
        if (workerId == null || aliveWorkerIds.contains(workerId)) {
            return;
        }

        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.worker-suspicious-update-conflict:taskId={}", task.getJobTaskId());
        }

        LOG.info("nop.job.timeout.worker-suspicious:taskId={},workerId={}", task.getJobTaskId(), workerId);
    }

    private void tryMarkDispatchTimeout(NopJobFire fire, long now) {
        NopJobSchedule schedule = scheduleStore.tryLoadSchedule(fire.getJobScheduleId());
        if (schedule == null) {
            LOG.warn("nop.job.timeout.dispatch-schedule-deleted:fireId={}", fire.getJobFireId());
            String localized = ErrorMessageManager.instance().getLocalizedDescription(null,
                    ERR_JOB_SCHEDULE_DELETED.getErrorCode());
            boolean fireFailed = fireStore.failFireWithoutSchedule(fire.getJobFireId(),
                    ERR_JOB_SCHEDULE_DELETED.getErrorCode(),
                    localized != null ? localized : ERR_JOB_SCHEDULE_DELETED.getDescription());
            if (!fireFailed) {
                // Bug C fix对称防护：fire被并发推进（如dispatcher已置RUNNING）时不再连带取消任务，
                // 避免出现RUNNING fire + 全CANCELED tasks的状态漂移
                return;
            }

            List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
            Timestamp endTime = new Timestamp(now);
            for (NopJobTask task : tasks) {
                if (JobTaskStateMachine.isFinished(task.getTaskStatus())) {
                    continue;
                }
                task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
                task.setEndTime(endTime);
                // check2 [P3-12]: schedule 已删除分支的任务行错误码与 fire 层一致用
                // ERR_JOB_SCHEDULE_DELETED（此前误写 ERR_JOB_TIMEOUT，按任务错误码聚合统计时
                // 把"调度定义被删"误报为"执行超时"）
                task.setErrorCode(ERR_JOB_SCHEDULE_DELETED.getErrorCode());
                task.setErrorMessage(ERR_JOB_SCHEDULE_DELETED.getDescription());
                if (!taskStore.updateTask(task)) {
                    LOG.warn("nop.job.timeout.dispatch-deleted-task-update-conflict:taskId={}", task.getJobTaskId());
                }
            }
            return;
        }

        Timestamp endTime = new Timestamp(now);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
        fire.setEndTime(endTime);
        fire.setDurationMs(computeDispatchElapsedMs(fire, now));
        fire.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());
        fire.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());

        schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
        schedule.setLastEndTime(endTime);
        schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
        schedule.setLastDurationMs(fire.getDurationMs());
        schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + 1);
        schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + 1);

        FireScheduleOutcome outcome = fireStore.completeFireAndUpdateSchedule(fire, schedule);

        // Bug C fix: if fire wasn't actually updated (version conflict), skip task
        // cancellation to avoid ending up with RUNNING fire + CANCELED tasks.
        if (!outcome.fireUpdated()) {
            return;
        }
        if (!outcome.scheduleUpdated()) {
            LOG.warn("nop.job.timeout.schedule-counter-not-updated:scheduleId={},fireId={}",
                    schedule.getJobScheduleId(), fire.getJobFireId());
        }

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        for (NopJobTask task : tasks) {
            if (JobTaskStateMachine.isFinished(task.getTaskStatus())) {
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

    // plan 340 §2.11: renamed from startTimeOrNow (the method returns an elapsed duration, not a
    // timestamp) and dropped the updateTime fallback — a DISPATCHING fire always has startTime set
    // by tryLockFiresForDispatch; a stale startTime on a reverted fire would otherwise yield a
    // nonsensical (negative-then-maxed-to-0) duration via the old updateTime fallback.
    private long computeDispatchElapsedMs(NopJobFire fire, long now) {
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
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.suspicious-update-conflict:taskId={}", task.getJobTaskId());
        }

        // 裁定①(c)：TIMEOUT 写点①（worker-liveness SUSPICIOUS→TIMEOUT 链）回传 rpcPoll entry future
        notifyPollManagerTaskTimeout(task);

        LOG.info("nop.job.timeout.suspicious-to-timeout:taskId={},workerId={}", task.getJobTaskId(), task.getWorkerInstanceId());
    }

    private void tryMarkTimeout(NopJobTask task, Map<String, NopJobFire> fireMap,
                                Map<String, NopJobSchedule> scheduleMap) {
        Integer taskStatus = task.getTaskStatus();
        if (taskStatus == null) {
            return;
        }

        if (JobTaskStateMachine.isSuspicious(taskStatus)) {
            markSuspiciousAsTimeout(task, fireMap, scheduleMap);
            return;
        }

        // CRA 8.46 R4-07 W-10 (1.4-C7-011, adjudication H.2): CLAIMED 行同应用墙钟
        // （startTime=claim 时刻写入 :109-112——R7 时钟口径）。仅作用已配超时形态
        // （下方三选一对 CLAIMED 同段生效；未配超时的 CLAIMED/RUNNING 均不入墙钟
        // ——plan 340 :615 钉语义不受损）。C10-006 肯定性回收（同 diff）：crash 残留
        // CLAIMED 行经此路径转 TIMEOUT（worker 存活与否与墙钟正交——R7）。
        if (!JobTaskStateMachine.isRunning(taskStatus)
                && !JobTaskStateMachine.isClaimed(taskStatus)) {
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
            // plan 340 §2.1 (P1-1): no execution timeout configured — do not fall back to
            // dispatchTimeoutMs (that is the "fire stuck in DISPATCHING" timeout, semantically
            // unrelated to task wall-clock). Long-running tasks without a per-schedule timeout
            // are instead recovered by the worker-liveness chain (SUSPICIOUS -> TIMEOUT) when
            // their worker disappears.
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
        if (!taskStore.updateTask(task)) {
            LOG.warn("nop.job.timeout.task-update-conflict:taskId={}", task.getJobTaskId());
        }

        // 裁定①(c)：TIMEOUT 写点②（schedule.timeoutSeconds / 全局 execution-timeout-ms 墙钟链）回传 rpcPoll entry future
        notifyPollManagerTaskTimeout(task);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
