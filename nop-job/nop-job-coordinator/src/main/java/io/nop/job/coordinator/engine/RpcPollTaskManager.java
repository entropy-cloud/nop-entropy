package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_CANCELED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_TIMEOUT;

/**
 * rpcPoll 集中式轮询管理器（executorKind=rpcPoll，bean 名 nopRpcPollTaskManager）。
 * <p>
 * 职责：**集中管理所有需要周期轮询的远程任务**（startJob 已成功、promise 仍挂起），
 * 由单一调度循环定期（{@code nop.job.remote.poll-interval-ms}）批量发起 {@code getJobStatus}，
 * 任务终结（终态 resolve / 取消 / 墙钟超时）后自动移出注册表。此前每个任务由
 * {@code RemoteJobInvoker} 各自持有 {@code scheduleWithFixedDelay} 周期任务——任务多了之后
 * 调度句柄/空转项随任务数线性堆积，且轮询节拍分散在各调用点；集中到单一注册表后：
 * <ul>
 *   <li>**单表管理**：{@code taskId → PollEntry} 注册表是全部在途轮询的事实源；每个
 *       entry capture 注册时的 schedule/fire/task/cancelToken/deadline，**不再每 tick
 *       查询 DB**——本地持有的 in-memory 信号就是驱动轮询循环的全部依据；</li>
 *   <li>**自动过滤**：每一轮只处理"未取消（cancelToken）且未超时（墙钟 deadline）且
 *       promise 未终结"的条目，终结即移除（future.whenComplete 清注册表）；</li>
 *   <li>**异步无阻塞**：客户端 {@link IRpcPollTaskClient} 三方法均为异步，管理器在同一
 *       注册表上并发出多个在途 getJobStatus，不再"一个轮询线程阻塞在单个 RPC 上"
 *       （check2 P1-2 语义从"线程池兜底"升级为"异步接口天然不占线程"）；</li>
 *   <li>**单点节拍**：调度循环 + 小型线程池（{@code nop.job.remote.poll-threads}，默认 2），
 *       daemon 线程随进程退出（无 shutdown 钩子，与原静态执行器生命周期一致）。</li>
 * </ul>
 * 状态权威仍在 DB（scanner writeback + TimeoutChecker 维护 task 状态机），但管理器驱动轮询
 * 循环只看本地持有的 in-memory 信号（cancelToken + deadline + promise 状态），不再每任务
 * 每周期 SELECT DB——"管理所有要 poll 的调用，定期发起 poll"的诉求就是纯内存的状态机。
 * 取消与超时的双重保险：cancelJob 路径经 cancelFire → IJobCancelHandler.cancelRunningTask
 * → invoker.cancelAsync → RemoteJobInvoker 直接调 {@code rpcPollTaskClient.cancelJob}
 * （不走 manager）；manager 仅在条目自身的 deadline 到期时再 cancelJob 兜底。
 */
public class RpcPollTaskManager {
    static final Logger LOG = LoggerFactory.getLogger(RpcPollTaskManager.class);

    private final ConcurrentHashMap<String, PollEntry> entries = new ConcurrentHashMap<>();

    private volatile ScheduledExecutorService pollExecutor;

    private IRpcPollTaskClient rpcPollTaskClient;
    private long pollIntervalMs = 5000;
    private int pollThreads = 2;

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

    @InjectValue("@cfg:nop.job.remote.poll-threads|2")
    public void setPollThreads(int pollThreads) {
        if (pollThreads < 1 || pollThreads > 32) {
            throw new IllegalArgumentException(
                    "nop.job.remote.poll-threads must be within [1,32], got " + pollThreads);
        }
        this.pollThreads = pollThreads;
    }

    /**
     * 注册一个待轮询的远程任务（startJob 已成功返回 remoteTaskId=DB jobTaskId）。
     * 管理器接管其周期 getJobStatus，直至 promise 终结（终态 resolve / 取消 / 超时——
     * 见 {@link #pollOnce}）。
     *
     * @param schedule    用于构造 deadline（schedule.timeoutSeconds）和请求 header
     * @param task        capture 时冻结 targetHost/jobTaskId 等，请求 header 与 cancelJob 都直接复用
     * @param cancelToken 取消令牌（来自 jobCtx；为 null 表示无令牌）；每轮轮询前检查，
     *                    已取消则先远程 cancelJob 再 resolve ERROR(CANCELED)
     * @param future      最终 JobFireResult 承载者（complete 后条目自动移出注册表）
     */
    public void register(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                         ICancelToken cancelToken, CompletableFuture<JobFireResult> future) {
        PollEntry entry = new PollEntry(task.getJobTaskId(), schedule, fire, task, cancelToken, future,
                computeDeadline(schedule));
        entries.put(entry.getTaskId(), entry);
        // future 终结（终态/取消/超时）后必须移出注册表：单轮只处理活跃条目
        future.whenComplete((r, e) -> entries.remove(entry.getTaskId(), entry));
        ensureStarted();
    }

    /** 当前注册表中待轮询的任务数（可观测/测试用）。 */
    int activeCount() {
        return entries.size();
    }

    private void ensureStarted() {
        ScheduledExecutorService executor = pollExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = pollExecutor;
                if (executor == null) {
                    executor = Executors.newScheduledThreadPool(pollThreads, r -> {
                        Thread t = new Thread(r, "nop-job-rpcPoll");
                        t.setDaemon(true);
                        return t;
                    });
                    // 单一调度循环：每轮扫注册表，跳过已终结条目，其余派发到池中执行 pollOnce
                    executor.scheduleWithFixedDelay(this::pollLoop, pollIntervalMs, pollIntervalMs,
                            TimeUnit.MILLISECONDS);
                    pollExecutor = executor;
                }
            }
        }
    }

    private ScheduledExecutorService pollExecutor() {
        ensureStarted();
        return pollExecutor;
    }

    private void pollLoop() {
        for (PollEntry entry : entries.values()) {
            if (entry.getFuture().isDone()) {
                // 已由 whenComplete 移除；ConcurrentHashMap 弱一致迭代下防御性跳过
                continue;
            }
            // 派发到线程池：pollOnce 不阻塞（异步 getJobStatus），但防御性隔离慢实现
            pollExecutor().execute(() -> pollOnce(entry));
        }
    }

    private void pollOnce(PollEntry entry) {
        try {
            if (entry.getFuture().isDone()) {
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
    }
}