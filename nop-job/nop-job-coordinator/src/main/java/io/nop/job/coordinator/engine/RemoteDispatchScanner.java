package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.core.AbstractBatchScanner;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

import static io.nop.job.core.JobCoreErrors.ARG_TASK_ID;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST;

/**
 * remote 模式（dispatchMode=remote）执行器（plan 2254）：coordinator 侧第 5 个 scanner。
 * <p>
 * 两段式（先轮询后认领）：
 * <ul>
 *   <li><b>轮询段</b>：按分区游标扫描 RUNNING remote 任务，批量 {@code getJobStatus}，
 *       终态写回 DB task；RUNNING 忽略（可透传进度）；NOT_FOUND → FAILED（remote-task-lost）。</li>
 *   <li><b>认领段</b>：扫描 WAITING remote 任务，乐观锁认领（WAITING→CLAIMED→RUNNING），
 *       {@code startJob} 远程启动；连接失败 → FAILED（remote-invoke-failed）。</li>
 * </ul>
 * 状态权威始终在 DB：轮询责任随分区隔离（无 in-memory 状态），认领者崩溃由 TimeoutChecker
 * 墙钟超时兜底（remote 任务不参与 worker-liveness 链）。
 */
public class RemoteDispatchScanner extends AbstractBatchScanner {
    static final Logger LOG = LoggerFactory.getLogger(RemoteDispatchScanner.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IRemoteTaskClient remoteTaskClient;
    private long lockTimeoutMs = 60000;

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
    public void setRemoteTaskClient(IRemoteTaskClient remoteTaskClient) {
        this.remoteTaskClient = remoteTaskClient;
    }

    @InjectValue("@cfg:nop.job.remote-dispatch.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        applyScanIntervalMs(scanIntervalMs);
    }

    @InjectValue("@cfg:nop.job.remote-dispatch.batch-size|100")
    public void setBatchSize(int batchSize) {
        applyBatchSize(batchSize);
    }

    @InjectValue("@cfg:nop.job.remote-dispatch.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        if (lockTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.remote-dispatch.lock-timeout-ms must be >= 1000, got " + lockTimeoutMs);
        }
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @Override
    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.remote-dispatch.scan-failed", e);
    }

    @Override
    @SingleSession
    protected boolean scanBatch() {
        IntRangeSet partitions = resolvePartitions();
        boolean moreRunning = pollRunningTasks(batchSize, partitions);
        boolean moreWaiting = claimAndStartTasks(batchSize, partitions);
        return moreRunning || moreWaiting;
    }

    // ---- 轮询段 ----

    private boolean pollRunningTasks(int limit, IntRangeSet partitions) {
        List<NopJobTask> tasks = taskStore.fetchRemoteRunningTasks(limit, partitions, null, null);
        if (tasks.isEmpty()) {
            return false;
        }
        for (NopJobTask task : tasks) {
            try {
                pollTask(task);
            } catch (Exception e) {
                // 单任务轮询失败不影响本批其余任务（per-task 隔离）
                LOG.warn("nop.job.remote-dispatch.poll-failed:taskId={}", task.getJobTaskId(), e);
            }
        }
        return tasks.size() >= limit;
    }

    private void pollTask(NopJobTask task) {
        NopJobFire fire = fireStore.loadFire(task.getJobFireId());
        if (fire == null) {
            return;
        }
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        TaskStatusBean status = remoteTaskClient.getJobStatus(schedule, fire, task);

        NopJobTask fresh = taskStore.loadTask(task.getJobTaskId());
        Integer currentStatus = fresh.getTaskStatus();
        if (currentStatus == null || JobTaskStateMachine.isConcurrentlyFinalized(currentStatus)) {
            // 并发终结（取消/超时回收）：worker 迟到的终态查询结果丢弃
            return;
        }

        switch (status.getTaskStatus()) {
            case TaskStatusBean.STATUS_RUNNING: {
                // 忽略；details 进度可选透传（V1 不做透传，保持最小）
                break;
            }
            case TaskStatusBean.STATUS_SUCCESS: {
                completeTask(fresh, _NopJobCoreConstants.TASK_STATUS_SUCCESS, status);
                break;
            }
            case TaskStatusBean.STATUS_FAILURE: {
                completeTaskWithError(fresh, status.getError() != null
                                ? status.getError().getErrorCode() : ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(),
                        status.getError() != null ? status.getError().getDescription() : null);
                break;
            }
            case TaskStatusBean.STATUS_CANCELLED: {
                completeTask(fresh, _NopJobCoreConstants.TASK_STATUS_CANCELED, status);
                break;
            }
            case TaskStatusBean.STATUS_TIMEOUT: {
                completeTask(fresh, _NopJobCoreConstants.TASK_STATUS_TIMEOUT, status);
                break;
            }
            case TaskStatusBean.STATUS_NOT_FOUND: {
                completeTaskWithError(fresh,
                        ERR_JOB_REMOTE_TASK_LOST.getErrorCode(), ERR_JOB_REMOTE_TASK_LOST.getDescription());
                break;
            }
            default: {
                // STATUS_UNKNOWN：本轮忽略，下轮重查
                break;
            }
        }
    }

    // ---- 认领段 ----

    private boolean claimAndStartTasks(int limit, IntRangeSet partitions) {
        List<NopJobTask> tasks = taskStore.fetchRemoteWaitingTasks(limit, partitions);
        if (tasks.isEmpty()) {
            return false;
        }
        List<NopJobTask> locked = taskStore.tryLockTasksForExecute(tasks, AppConfig.hostId(), lockTimeoutMs);
        if (!locked.isEmpty()) {
            LOG.info("nop.job.remote-dispatch.claimed:count={}", locked.size());
        }
        for (NopJobTask task : locked) {
            try {
                startTask(task);
            } catch (Exception e) {
                LOG.warn("nop.job.remote-dispatch.start-failed:taskId={}", task.getJobTaskId(), e);
            }
        }
        return tasks.size() >= limit;
    }

    private void startTask(NopJobTask task) {
        NopJobFire fire = fireStore.loadFire(task.getJobFireId());
        if (fire == null) {
            return;
        }
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());

        NopJobTask running = taskStore.loadTask(task.getJobTaskId());
        if (!JobTaskStateMachine.isClaimed(running.getTaskStatus())) {
            return;
        }

        long now = scheduleStore.getCurrentTime();
        running.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        running.setStartTime(new Timestamp(now));
        running.setWorkerInstanceId(AppConfig.hostId());
        // CLAIMED→RUNNING CAS：失败说明已被并发回收（超时检查器/取消），跳过执行
        boolean acquired = taskStore.updateTask(running);
        if (!acquired) {
            LOG.warn("nop.job.remote-dispatch.claim-cas-failed-skip-invoke:taskId={}",
                    running.getJobTaskId());
            return;
        }

        try {
            String remoteTaskId = remoteTaskClient.startJob(schedule, fire, running);
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                completeTaskWithError(running, ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(),
                        "startJob returned empty taskId");
            }
        } catch (NopException e) {
            completeTaskWithError(running, e.getErrorCode(), e.getDescription());
        } catch (Exception e) {
            completeTaskWithError(running, ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), e);
        }
    }

    // ---- 写回辅助 ----

    private void completeTask(NopJobTask task, int taskStatus, TaskStatusBean status) {
        NopJobTask fresh = taskStore.loadTask(task.getJobTaskId());
        if (JobTaskStateMachine.isConcurrentlyFinalized(fresh.getTaskStatus())) {
            return;
        }
        long now = scheduleStore.getCurrentTime();
        fresh.setTaskStatus(taskStatus);
        fresh.setEndTime(new Timestamp(now));
        if (fresh.getStartTime() != null) {
            fresh.setDurationMs(Math.max(now - fresh.getStartTime().getTime(), 0L));
        }
        if (status.getError() != null) {
            fresh.setErrorCode(status.getError().getErrorCode());
            fresh.setErrorMessage(status.getError().getDescription());
        }
        if (!taskStore.updateTask(fresh)) {
            LOG.warn("nop.job.remote-dispatch.complete-update-conflict:taskId={}", fresh.getJobTaskId());
        }
    }

    private void completeTaskWithError(NopJobTask task, String errorCode, String errorMessage) {
        NopJobTask fresh = taskStore.loadTask(task.getJobTaskId());
        if (JobTaskStateMachine.isConcurrentlyFinalized(fresh.getTaskStatus())) {
            return;
        }
        long now = scheduleStore.getCurrentTime();
        fresh.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_FAILED);
        fresh.setEndTime(new Timestamp(now));
        if (fresh.getStartTime() != null) {
            fresh.setDurationMs(Math.max(now - fresh.getStartTime().getTime(), 0L));
        }
        fresh.setErrorCode(errorCode);
        fresh.setErrorMessage(errorMessage);
        if (!taskStore.updateTask(fresh)) {
            LOG.warn("nop.job.remote-dispatch.fail-update-conflict:taskId={}", fresh.getJobTaskId());
        }
        LOG.warn("nop.job.remote-dispatch.task-failed:taskId={},errorCode={},errorMessage={}",
                fresh.getJobTaskId(), errorCode, errorMessage);
    }

    private void completeTaskWithError(NopJobTask task, String errorCode, Exception e) {
        completeTaskWithError(task, errorCode, e.getMessage());
        LOG.error("nop.job.remote-dispatch.task-failed-details:taskId={}", task.getJobTaskId(), e);
    }
}
