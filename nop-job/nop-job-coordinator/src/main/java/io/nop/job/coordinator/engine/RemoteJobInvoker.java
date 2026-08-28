package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TASK_ATTRIBUTE_MISSING;

/**
 * 三段式远程执行 invoker（plan 2254，executorKind=rpcPoll，bean 名 nopJobInvoker_rpcPoll）。
 * <p>
 * 长任务（longTask）处理：单次 RPC 必须在一定时间内返回，因此把一次远程执行拆成
 * start/poll/cancel 三段——{@code invokeAsync} 先异步 {@code startJob} 远程启动（worker
 * 立即返回 taskId=DB jobTaskId），成功后把轮询**委托给集中式 {@link RpcPollTaskManager}**
 * （单注册表管理全部在途 call，定期批量 getJobStatus，自动过滤取消/超时条目）；
 * {@code cancelAsync} 经异步 {@code cancelJob} 远程中断（best-effort）。
 * <p>
 * 客户端 {@link IRpcPollTaskClient} 三方法均为异步：invoker 与轮询循环都不阻塞等待
 * RPC 完成——单个挂起的 getJobStatus 不再独占轮询线程（check2 P1-2 语义升级）。
 * <p>
 * 对外就是一个普通 {@link IJobInvoker}：scanner（{@code JobWorkerScannerImpl}）零感知
 * 远程细节，只做"认领 → invokeAsync → promise 写回"。状态权威始终在 DB——认领者崩溃后
 * 轮询自然停止，由 TimeoutChecker 墙钟超时兜底回收。
 */
public class RemoteJobInvoker implements IJobInvoker {
    static final Logger LOG = LoggerFactory.getLogger(RemoteJobInvoker.class);

    private IJobTaskStore taskStore;
    private IJobFireStore fireStore;
    private IJobScheduleStore scheduleStore;
    private IRpcPollTaskClient rpcPollTaskClient;
    private RpcPollTaskManager pollTaskManager;

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

    @Inject
    public void setPollTaskManager(RpcPollTaskManager pollTaskManager) {
        this.pollTaskManager = pollTaskManager;
    }

    RpcPollTaskManager getPollTaskManager() {
        return pollTaskManager;
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
            if (pollTaskManager == null) {
                throw new NopException(ERR_JOB_REMOTE_INVOKE_FAILED)
                        .param(ARG_CONFIG_NAME, "pollTaskManager")
                        .param("description", "RpcPollTaskManager is not configured for RemoteJobInvoker");
            }

            // 段 1：启动（start）——worker 立即返回 taskId=DB jobTaskId（异步，不阻塞）；
            // cancelToken 透传底层 RPC：jobCtx 已取消时中止在途启动调用
            rpcPollTaskClient.startJob(schedule, fire, task, jobCtx.getCancelToken())
                    .whenComplete((remoteTaskId, err) -> {
                if (err != null) {
                    // check2 [P3-5]: NopException 携带的语义错误码（SERVICE_NAME_REQUIRED 等）
                    // 原样透传，不再统一抹平为 ERR_JOB_REMOTE_INVOKE_FAILED
                    future.complete(JobFireResult.ERROR(toError(err)));
                } else if (remoteTaskId == null || remoteTaskId.isBlank()) {
                    future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_INVOKE_FAILED)));
                } else {
                    // 段 2：轮询（poll）——委托集中式 RpcPollTaskManager 管理（周期 getJobStatus
                    // 直至终态；取消/超时/并发终结由管理器过滤并 resolve）
                    pollTaskManager.register(schedule, fire, task, jobCtx.getCancelToken(), future);
                }
            });
        } catch (Exception e) {
            LOG.warn("nop.job.remote.start-failed:taskId={}", jobCtx.getAttributes().get("jobTaskId"), e);
            future.complete(JobFireResult.ERROR(toError(e)));
        }
        return future;
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
            // 段 3：取消（cancel）——best-effort，DB 状态以乐观锁为准（异步，不阻塞）；
            // cancelToken 透传底层 RPC（与 DB 模式 RpcJobInvoker.cancelAsync 一致）
            return rpcPollTaskClient.cancelJob(schedule, fire, task, jobCtx.getCancelToken())
                    .exceptionally(e -> {
                        LOG.warn("nop.job.remote.cancel-failed:taskId={}", task.getJobTaskId(), e);
                        return false;
                    });
        } catch (Exception e) {
            LOG.warn("nop.job.remote.cancel-failed", e);
            return CompletableFuture.completedFuture(false);
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

    private static ErrorBean toError(ErrorCode errorCode) {
        return new ErrorBean(errorCode.getErrorCode());
    }

    /** check2 [P3-5]: 保留 NopException 的原始错误码与描述，非 NopException 回退笼统错误码。 */
    private static ErrorBean toError(Throwable e) {
        if (e instanceof NopException) {
            String code = ((NopException) e).getErrorCode();
            if (code != null) {
                return new ErrorBean(code).description(e.getMessage());
            }
        }
        return toError(ERR_JOB_REMOTE_INVOKE_FAILED);
    }
}