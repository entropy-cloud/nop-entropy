package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TASK_ATTRIBUTE_MISSING;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_CANCELED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TIMEOUT;

/**
 * 三段式远程执行 invoker（plan 2254，executorKind=rpcPoll，bean 名 nopJobInvoker_rpcPoll）。
 * <p>
 * 长任务（longTask）处理：单次 RPC 必须在一定时间内返回，因此把一次远程执行拆成
 * start/poll/cancel 三段——{@code invokeAsync} 先 {@code startJob} 远程启动（worker 立即
 * 返回 taskId=DB jobTaskId），随后内部轮询循环周期调 {@code getJobStatus} 直至终态并
 * resolve promise；{@code cancelAsync} 经 {@code cancelJob} 远程中断（best-effort）。
 * <p>
 * 对外就是一个普通 {@link IJobInvoker}：scanner（{@code JobWorkerScannerImpl}）零感知
 * 远程细节，只做"认领 → invokeAsync → promise 写回"。状态权威始终在 DB——认领者崩溃后
 * 轮询自然停止，由 TimeoutChecker 墙钟超时兜底回收。
 */
public class RemoteJobInvoker implements IJobInvoker {
    static final Logger LOG = LoggerFactory.getLogger(RemoteJobInvoker.class);

    private static final ScheduledExecutorService POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nop-job-rpcPoll");
        t.setDaemon(true);
        return t;
    });

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IRpcPollTaskClient rpcPollTaskClient;
    private long pollIntervalMs = 5000;

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
    public void setRpcPollTaskClient(IRpcPollTaskClient rpcPollTaskClient) {
        this.rpcPollTaskClient = rpcPollTaskClient;
    }

    @InjectValue("@cfg:nop.job.remote.poll-interval-ms|5000")
    public void setPollIntervalMs(long pollIntervalMs) {
        if (pollIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.remote.poll-interval-ms must be >= 1000, got " + pollIntervalMs);
        }
        this.pollIntervalMs = pollIntervalMs;
    }

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        try {
            NopJobTask task = loadTask(jobCtx);
            NopJobFire fire = fireStore.loadFire(task.getJobFireId());
            if (fire == null) {
                future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_TASK_LOST)));
                return future;
            }
            NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());

            // 段 1：启动（start）——worker 立即返回 taskId=DB jobTaskId
            String remoteTaskId = rpcPollTaskClient.startJob(schedule, fire, task);
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_INVOKE_FAILED)));
                return future;
            }

            // 段 2：轮询（poll）——周期 getJobStatus 直至终态
            schedulePolling(future, jobCtx, schedule, fire, task);
        } catch (Exception e) {
            LOG.warn("nop.job.remote.start-failed:taskId={}", jobCtx.getAttributes().get("jobTaskId"), e);
            future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_INVOKE_FAILED)));
        }
        return future;
    }

    private void schedulePolling(CompletableFuture<JobFireResult> future, IJobExecutionContext jobCtx,
                                 NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
        long deadline = computeDeadline(schedule);
        java.util.concurrent.ScheduledFuture<?> pollHandle = POLL_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (future.isDone()) {
                return;
            }
            try {
                // 并发终结（TimeoutChecker 墙钟回收/取消链已置终态）：写回会被 scanner 丢弃，停止轮询
                NopJobTask fresh = taskStore.loadTask(task.getJobTaskId());
                if (fresh == null || JobTaskStateMachine.isConcurrentlyFinalized(fresh.getTaskStatus())) {
                    future.complete(JobFireResult.ERROR(toError(ERR_JOB_CANCELED)));
                    return;
                }

                if (deadline > 0 && System.currentTimeMillis() >= deadline) {
                    // 超时：段 3 取消 + resolve ERROR（timeout）
                    cancelRemote(fire, fresh);
                    future.complete(JobFireResult.ERROR(toError(ERR_JOB_TIMEOUT)));
                    return;
                }
                if (jobCtx.getCancelToken() != null && jobCtx.getCancelToken().isCancelled()) {
                    cancelRemote(fire, fresh);
                    future.complete(JobFireResult.ERROR(toError(ERR_JOB_CANCELED)));
                    return;
                }

                TaskStatusBean status = rpcPollTaskClient.getJobStatus(schedule, fire, fresh);
                switch (status.getTaskStatus()) {
                    case TaskStatusBean.STATUS_RUNNING:
                    case TaskStatusBean.STATUS_UNKNOWN: {
                        // 继续轮询
                        break;
                    }
                    case TaskStatusBean.STATUS_SUCCESS: {
                        future.complete(JobFireResult.CONTINUE);
                        break;
                    }
                    case TaskStatusBean.STATUS_FAILURE: {
                        future.complete(JobFireResult.ERROR(status.getError() != null
                                ? status.getError() : toError(ERR_JOB_REMOTE_INVOKE_FAILED)));
                        break;
                    }
                    case TaskStatusBean.STATUS_CANCELLED: {
                        future.complete(JobFireResult.ERROR(toError(ERR_JOB_CANCELED)));
                        break;
                    }
                    case TaskStatusBean.STATUS_TIMEOUT: {
                        future.complete(JobFireResult.ERROR(toError(ERR_JOB_TIMEOUT)));
                        break;
                    }
                    case TaskStatusBean.STATUS_NOT_FOUND: {
                        future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_TASK_LOST)));
                        break;
                    }
                    default: {
                        break;
                    }
                }
            } catch (Exception e) {
                // 瞬态失败（网络等）：本轮忽略，下轮重查；连续失败由 TimeoutChecker 墙钟兜底
                LOG.warn("nop.job.remote.poll-failed:taskId={}", task.getJobTaskId(), e);
            }
        }, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);

        // future终态后必须取消周期任务：单线程POLL_EXECUTOR的队列里每轮只跳过不退出，
        // 每次rpcPoll执行都会永久累积一个空转调度项，长期运行不可逆劣化
        future.whenComplete((r, e) -> pollHandle.cancel(false));
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        try {
            NopJobTask task = loadTask(jobCtx);
            NopJobFire fire = fireStore.loadFire(task.getJobFireId());
            if (fire == null) {
                return CompletableFuture.completedFuture(false);
            }
            NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
            // 段 3：取消（cancel）——best-effort，DB 状态以乐观锁为准
            return CompletableFuture.completedFuture(rpcPollTaskClient.cancelJob(schedule, fire, task));
        } catch (Exception e) {
            LOG.warn("nop.job.remote.cancel-failed", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    private void cancelRemote(NopJobFire fire, NopJobTask task) {
        try {
            NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
            rpcPollTaskClient.cancelJob(schedule, fire, task);
        } catch (Exception e) {
            LOG.warn("nop.job.remote.cancel-best-effort-failed:taskId={}", task.getJobTaskId(), e);
        }
    }

    private NopJobTask loadTask(IJobExecutionContext jobCtx) {
        Object taskId = jobCtx.getAttributes().get("jobTaskId");
        if (taskId == null) {
            throw new NopException(ERR_JOB_TASK_ATTRIBUTE_MISSING).param(ARG_CONFIG_NAME, "jobTaskId");
        }
        NopJobTask task = taskStore.loadTask(String.valueOf(taskId));
        if (task == null) {
            throw new NopException(ERR_JOB_REMOTE_TASK_LOST).param(ARG_CONFIG_NAME, String.valueOf(taskId));
        }
        return task;
    }

    private long computeDeadline(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return System.currentTimeMillis() + timeoutSeconds * 1000L;
        }
        return 0L;
    }

    private static ErrorBean toError(ErrorCode errorCode) {
        return new ErrorBean(errorCode.getErrorCode());
    }

    private static ErrorBean toError(ErrorCode errorCode, String description) {
        return new ErrorBean(errorCode.getErrorCode()).description(description);
    }
}