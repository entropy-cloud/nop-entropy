package io.nop.job.coordinator.engine;

import io.nop.job.core.JobCoreErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_CANCELED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TIMEOUT;

/**
 * rpcPoll 集中式轮询管理器（executorKind=rpcPoll，bean 名 nopRpcPollTaskManager）。
 * <p>
 * 职责：**集中管理所有需要周期轮询的远程任务**（startJob 已成功、promise 仍挂起）。
 * 每个 entry 在平台 {@link IScheduledExecutor} 上独立注册自己的
 * {@code scheduleWithFixedDelay}（周期 {@code nop.job.remote.poll-interval-ms}），到期即发起
 * {@code getJobStatus}；任务终结（终态 resolve / 取消 / 墙钟超时）后取消调度句柄并移出
 * 注册表——不再由单一调度循环批量扫描全表派发：
 * <ul>
 *   <li>**每 entry 独立节拍**：{@code taskId → PollEntry} 注册表仍是全部在途轮询的事实源；
 *       每个 entry capture 注册时的 schedule/fire/task/cancelToken/deadline（**不查 DB**），
 *       并持有自己的调度句柄，register 即调度、终结即 cancel；fixed-delay 保证单个 entry
 *       相邻两次 poll 不重叠；</li>
 *   <li>**平台 executor**：调度统一走 {@link IScheduledExecutor}（beans.xml 注入专用池
 *       nopRpcPollScheduledExecutor，池大小 {@code nop.job.remote.poll-threads}）；未注入时
 *       fallback {@link GlobalExecutors#globalTimer()}（与 AbstractPollingLeaderElector
 *       同一模式），管理器不再自建 ScheduledExecutorService；</li>
 *   <li>**取消即时感知**：cancelToken 非 null 时经 {@code appendOnCancel} 挂即时回调——令牌被
 *       取消的瞬间 cancelRemote + resolve ERROR(CANCELED)，不等下一轮询周期；轮询内的
 *       isCancelled 检查保留为兜底（覆盖无回调能力的令牌实现）；future.whenComplete 兜底
 *       cancel 句柄并摘除监听器；</li>
 *   <li>**异步无阻塞**：客户端 {@link IRpcPollTaskClient} 三方法均为异步，多个 entry 的
 *       getJobStatus 并发在途，getJobStatus 阻塞不影响其他 entry 的节拍。</li>
 * </ul>
 * 状态权威仍在 DB（scanner writeback + TimeoutChecker 维护 task 状态机），但管理器驱动轮询
 * 只看本地持有的 in-memory 信号（cancelToken + deadline + promise 状态），不查 DB。
 * 取消与超时的双重保险：cancelJob 路径经 cancelFire → IJobCancelHandler.cancelRunningTask
 * → invoker.cancelAsync → RemoteJobInvoker 直接调 {@code rpcPollTaskClient.cancelJob}
 * （不走 manager）；manager 仅在条目自身的 deadline 到期时再 cancelJob 兜底。
 */
public class RpcPollTaskManager {
    static final Logger LOG = LoggerFactory.getLogger(RpcPollTaskManager.class);

    private final ConcurrentHashMap<String, PollEntry> entries = new ConcurrentHashMap<>();

    private IScheduledExecutor scheduledExecutor;

    private IRpcPollTaskClient rpcPollTaskClient;
    private long pollIntervalMs = 5000;

    @Inject
    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @Inject
    public void setRpcPollTaskClient(IRpcPollTaskClient rpcPollTaskClient) {
        this.rpcPollTaskClient = rpcPollTaskClient;
    }

    @InjectValue("@cfg:nop.job.remote.poll-interval-ms|5000")
    public void setPollIntervalMs(long pollIntervalMs) {
        if (pollIntervalMs < 1000) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.remote.poll-interval-ms")
                    .param(JobCoreErrors.ARG_VALUE, pollIntervalMs);
        }
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * 注册一个待轮询的远程任务（startJob 已成功返回 remoteTaskId=DB jobTaskId）：
     * 在 {@link IScheduledExecutor} 上为其注册独立的 fixed-delay 周期 getJobStatus，
     * 直至 promise 终结（终态 resolve / 取消 / 超时——见 {@link #pollOnce}）。
     * cancelToken 非 null 时经 {@code appendOnCancel} 挂即时取消回调（被取消瞬间
     * 远程中断并 resolve，见 {@link #onCancelToken}）；轮询内的 isCancelled 检查保留为兜底。
     *
     * @param schedule    用于构造 deadline（schedule.timeoutSeconds）和请求 header
     * @param task        capture 时冻结 targetHost/jobTaskId 等，请求 header 与 cancelJob 都直接复用
     * @param cancelToken 取消令牌（来自 jobCtx；为 null 表示无令牌）；被取消则远程 cancelJob
     *                    并 resolve ERROR(CANCELED)——即时回调触发，轮询兜底
     * @param future      最终 JobFireResult 承载者（complete 后条目自动取消句柄并移出注册表）
     */
    public void register(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                         ICancelToken cancelToken, CompletableFuture<JobFireResult> future) {
        PollEntry entry = new PollEntry(task.getJobTaskId(), schedule, fire, task, cancelToken, future,
                computeDeadline(schedule));
        entries.put(entry.getTaskId(), entry);
        // 即时取消回调：先注册再调度，令牌已取消时 appendOnCancel 立即触发（提前于首个 poll tick）；
        // 监听器存入 entry，终结清理时 removeOnCancel 防泄漏
        if (cancelToken != null) {
            Consumer<String> onCancel = reason -> onCancelToken(entry);
            entry.setOnCancelListener(onCancel);
            cancelToken.appendOnCancel(onCancel);
        }
        // 每 entry 独立注册：fixed-delay 保证该任务相邻两次 poll 不重叠；
        // pollOnce 整体 try/catch，不会因异常终止后续调度
        Future<?> handle = scheduledExecutor().scheduleWithFixedDelay(
                () -> pollOnce(entry), pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        entry.setPollHandle(handle);
        // future 终结（终态/取消/超时）后取消调度句柄并移出注册表
        future.whenComplete((r, e) -> cancelEntry(entry));
        // future 在 handle 设置前已终结（含 appendOnCancel 立即触发的路径）的兜底：避免空转句柄泄漏
        if (future.isDone()) {
            cancelEntry(entry);
        }
    }

    /** 当前注册表中待轮询的任务数（可观测/测试用）。 */
    int activeCount() {
        return entries.size();
    }

    private void cancelEntry(PollEntry entry) {
        Future<?> handle = entry.getPollHandle();
        if (handle != null) {
            handle.cancel(false);
        }
        // 摘除即时取消监听器，防令牌生命周期长于条目时泄漏；异常不阻断后续清理
        ICancelToken cancelToken = entry.getCancelToken();
        Consumer<String> onCancel = entry.getOnCancelListener();
        if (cancelToken != null && onCancel != null) {
            try {
                cancelToken.removeOnCancel(onCancel);
            } catch (Exception e) {
                LOG.warn("nop.job.remote.remove-cancel-listener-failed:taskId={}", entry.getTaskId(), e);
            }
        }
        entries.remove(entry.getTaskId(), entry);
    }

    /**
     * cancelToken 即时取消回调（appendOnCancel）：令牌被取消的瞬间远程中断并 resolve
     * ERROR(CANCELED)，不等下一轮询周期（轮询内的 isCancelled 检查保留为兜底，覆盖
     * 不支持回调的令牌实现）。回调运行在触发取消的线程上，防御性隔离异常。
     */
    private void onCancelToken(PollEntry entry) {
        try {
            if (entry.getFuture().isDone()) {
                return;
            }
            cancelRemote(entry);
            entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_CANCELED)));
        } catch (Exception e) {
            LOG.warn("nop.job.remote.cancel-listener-failed:taskId={}", entry.getTaskId(), e);
        }
    }

    private IScheduledExecutor scheduledExecutor() {
        IScheduledExecutor executor = this.scheduledExecutor;
        return executor != null ? executor : GlobalExecutors.globalTimer();
    }

    private void pollOnce(PollEntry entry) {
        try {
            if (entry.getFuture().isDone()) {
                // 已终结：whenComplete 会取消句柄；调度间隙内防御性跳过
                return;
            }
            // 纯内存检查：只看本地持有的 cancelToken + deadline
            long now = CoreMetrics.currentTimeMillis();
            if (entry.getDeadline() > 0 && now >= entry.getDeadline()) {
                // 超时：段 3 取消 + resolve ERROR（timeout）
                cancelRemote(entry);
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_TIMEOUT)));
                return;
            }
            if (entry.getCancelToken() != null && entry.getCancelToken().isCancelled()) {
                cancelRemote(entry);
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_CANCELED)));
                return;
            }
            // 透传条目 cancelToken：token 在 getJobStatus 在途时被取消 → 框架中止该 RPC（无需等读超时）
            rpcPollTaskClient.getJobStatus(entry.getSchedule(), entry.getFire(), entry.getTask(), entry.getCancelToken())
                    .whenComplete((status, err) -> {
                        if (err != null) {
                            // 瞬态失败（网络等）：本轮忽略，下轮重查；连续失败由 TimeoutChecker 墙钟兜底
                            LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), err);
                            return;
                        }
                        applyStatus(entry, status);
                    });
        } catch (Exception e) {
            LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), e);
        }
    }

    /** TaskStatusBean → JobFireResult 映射；终态 resolve 后条目由 future.whenComplete 移出注册表。 */
    private void applyStatus(PollEntry entry, TaskStatusBean status) {
        switch (status.getTaskStatus()) {
            case TaskStatusBean.STATUS_RUNNING:
            case TaskStatusBean.STATUS_UNKNOWN: {
                // 继续轮询
                break;
            }
            case TaskStatusBean.STATUS_SUCCESS: {
                entry.getFuture().complete(JobFireResult.CONTINUE);
                break;
            }
            case TaskStatusBean.STATUS_FAILURE: {
                entry.getFuture().complete(JobFireResult.ERROR(status.getError() != null
                        ? status.getError() : error(ERR_JOB_REMOTE_INVOKE_FAILED)));
                break;
            }
            case TaskStatusBean.STATUS_CANCELLED: {
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_CANCELED)));
                break;
            }
            case TaskStatusBean.STATUS_TIMEOUT: {
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_TIMEOUT)));
                break;
            }
            case TaskStatusBean.STATUS_NOT_FOUND: {
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_REMOTE_TASK_LOST)));
                break;
            }
            default: {
                break;
            }
        }
    }

    private void cancelRemote(PollEntry entry) {
        try {
            // 透传条目 cancelToken（与 RpcJobInvoker.cancelAsync 一致）；best-effort
            rpcPollTaskClient.cancelJob(entry.getSchedule(), entry.getFire(), entry.getTask(), entry.getCancelToken())
                    .whenComplete((ok, err) -> {
                        if (err != null) {
                            LOG.warn("nop.job.remote.cancel-best-effort-failed:taskId={}",
                                    entry.getTaskId(), err);
                        }
                    });
        } catch (Exception e) {
            LOG.warn("nop.job.remote.cancel-best-effort-failed:taskId={}", entry.getTaskId(), e);
        }
    }

    private static long computeDeadline(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;
        }
        return 0L;
    }

    private static ErrorBean error(ErrorCode errorCode) {
        return new ErrorBean(errorCode.getErrorCode());
    }

    /** 注册表条目：一个待轮询的远程任务及其终结条件（cancelToken/deadline/future，全部 capture 时冻结）。 */
    static final class PollEntry {
        private final String taskId;
        private final NopJobSchedule schedule;
        private final NopJobFire fire;
        private final NopJobTask task;
        private final ICancelToken cancelToken;
        private final CompletableFuture<JobFireResult> future;
        /** 墙钟超时 deadline（0 = 无）；超时先远程 cancelJob 再 resolve ERROR(TIMEOUT)。 */
        private final long deadline;
        /** 该 entry 的周期轮询句柄：future 终结时 cancel(false) 停止调度。 */
        private volatile Future<?> pollHandle;
        /** 即时取消回调（挂在 cancelToken 上，appendOnCancel）：终结清理时摘除。 */
        private volatile Consumer<String> onCancelListener;

        PollEntry(String taskId, NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                  ICancelToken cancelToken, CompletableFuture<JobFireResult> future, long deadline) {
            this.taskId = taskId;
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
            this.cancelToken = cancelToken;
            this.future = future;
            this.deadline = deadline;
        }

        String getTaskId() {
            return taskId;
        }

        NopJobSchedule getSchedule() {
            return schedule;
        }

        NopJobFire getFire() {
            return fire;
        }

        NopJobTask getTask() {
            return task;
        }

        ICancelToken getCancelToken() {
            return cancelToken;
        }

        CompletableFuture<JobFireResult> getFuture() {
            return future;
        }

        long getDeadline() {
            return deadline;
        }

        Future<?> getPollHandle() {
            return pollHandle;
        }

        void setPollHandle(Future<?> pollHandle) {
            this.pollHandle = pollHandle;
        }

        Consumer<String> getOnCancelListener() {
            return onCancelListener;
        }

        void setOnCancelListener(Consumer<String> onCancelListener) {
            this.onCancelListener = onCancelListener;
        }
    }
}
