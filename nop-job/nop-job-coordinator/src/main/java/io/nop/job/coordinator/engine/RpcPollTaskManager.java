package io.nop.job.coordinator.engine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
import io.nop.commons.metrics.GlobalMeterRegistry;
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
import java.util.concurrent.atomic.AtomicInteger;
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
 * 状态权威仍在 DB（scanner writeback + TimeoutChecker 维护 task 状态机）；管理器驱动轮询
 * 只看本地持有的 in-memory 信号（cancelToken + deadline + promise 状态），不查 DB——DB 侧
 * 权威终态经 TimeoutChecker 写终态时的显式回传通知（resolveFromAuthority）送达 entry
 * future，轮询循环自身零 DB 访问。
 * 取消与超时的双重保险：cancelJob 路径经 cancelFire → IJobCancelHandler.cancelRunningTask
 * → invoker.cancelAsync → RemoteJobInvoker 直接调 {@code rpcPollTaskClient.cancelJob}
 * （不走 manager）；manager 仅在条目自身的 deadline 到期时再 cancelJob 兜底。
 * <p>
 * 条目终结驱动四通道（plan 2513 1.4-C4-001 裁定①，消除默认装配下条目无界驻留）：
 * (a) 失败预算——getJobStatus 连续失败达阈值 {@code nop.job.remote.poll-fail-threshold}
 * （缺省 5）即 resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)，达阈值前按 2^(streak-1)
 * 跳轮退避（封顶 12 轮）；(b) deadline 全局兜底——schedule.timeoutSeconds 优先，否则纳入
 * 全局 {@code nop.job.coordinator.execution-timeout-ms}（与 TimeoutChecker 同源读键）；
 * (c) DB 回传——TimeoutChecker 置 TIMEOUT 终态时经 {@link #resolveFromAuthority} 回传
 * future；(d) future.whenComplete 兜底（cancelToken 通道维持现状，不作为终结依赖）。
 */
public class RpcPollTaskManager {
    static final Logger LOG = LoggerFactory.getLogger(RpcPollTaskManager.class);

    private final ConcurrentHashMap<String, PollEntry> entries = new ConcurrentHashMap<>();

    private IScheduledExecutor scheduledExecutor;

    private IRpcPollTaskClient rpcPollTaskClient;
    private long pollIntervalMs = 5000;
    /** 裁定①(a)：getJobStatus 连续失败预算阈值，超限 resolve ERROR 终结条目（有限时间终态保证）。 */
    private int pollFailThreshold = 5;
    /** 裁定①(b)：全局执行超时兜底（与 JobTimeoutCheckerImpl 同源读键，-1 = 未配置）。 */
    private long executionTimeoutMs = -1;

    /** 裁定①(e)：在途条目数 gauge（registry 未初始化时静默跳过，观测通道同绝缘层形态）。 */
    public RpcPollTaskManager() {
        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry != null) {
            Gauge.builder("nop.job.remote.poll.active-count", entries, m -> m.size())
                    .register(registry);
        }
    }

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
     * 裁定①(a)：连续失败预算阈值（缺省 5）。{@code <=0} 装配期 fail-fast——条目必须在
     * 有限时间内到达终态，无阈值即复刻无界驻留缺陷。
     */
    @InjectValue("@cfg:nop.job.remote.poll-fail-threshold|5")
    public void setPollFailThreshold(int pollFailThreshold) {
        if (pollFailThreshold <= 0) {
            throw new NopException(JobCoreErrors.ERR_JOB_CONFIG_INVALID)
                    .param(JobCoreErrors.ARG_CONFIG_KEY, "nop.job.remote.poll-fail-threshold")
                    .param(JobCoreErrors.ARG_VALUE, pollFailThreshold);
        }
        this.pollFailThreshold = pollFailThreshold;
    }

    /**
     * 裁定①(b)：全局执行超时兜底（{@code nop.job.coordinator.execution-timeout-ms}，
     * 与 {@link JobTimeoutCheckerImpl} 同源读键，单一事实源）。{@code <=0} = 未配置，
     * deadline=0 不杀（plan 340 钉死契约：未配置不回退、交 worker-liveness 链恢复）。
     */
    @InjectValue("@cfg:nop.job.coordinator.execution-timeout-ms|-1")
    public void setExecutionTimeoutMs(long executionTimeoutMs) {
        this.executionTimeoutMs = executionTimeoutMs;
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

    /** 当前注册表中待轮询的任务数（可观测/测试用；生产观测通道 = gauge nop.job.remote.poll.active-count）。 */
    int activeCount() {
        return entries.size();
    }

    /**
     * 裁定①(c)：DB 权威终态回传通道（package-private——checker 与 manager 同包直连，
     * 零跨模块 API）。{@link JobTimeoutCheckerImpl} 置 task TIMEOUT 终态后经本方法回传
     * in-memory future：entries 查表命中则 complete（first-writer-wins，与预算/deadline/
     * whenComplete 兜底竞态天然无双重终态），未命中（条目已终结或注册在远端节点）即合法
     * no-op 返回。本方法只看内存表，不查 DB。
     */
    void resolveFromAuthority(String taskId, JobFireResult result) {
        PollEntry entry = entries.get(taskId);
        if (entry == null || entry.getFuture().isDone()) {
            return;
        }
        entry.getFuture().complete(result);
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

    /**
     * 单轮轮询。整方法在站点内守卫（plan 2496 1.4-MA-05 站点①/C4-004）：任何 Throwable（含 Error）
     * 都被捕获留痕，不逃逸到周期设施边界——entry 不会因单轮失败僵尸化零日志；本轮失败后下一轮
     * 仍按周期触发（链存活由平台绝缘设施保证），deadline/cancelToken 检查每轮重跑。
     * <p>
     * plan 2513 1.4-C4-001 裁定①(a)：错误分支带失败预算——连续失败达阈值
     * {@code nop.job.remote.poll-fail-threshold} 即 resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)
     * 终结条目（显式终态，非静默清除）；达阈值前按 2^(streak-1) 跳轮退避（封顶 12 轮，约
     * 60s@5s 周期），句柄周期不重排。单轮 poll 成功（含 RUNNING/UNKNOWN 继续轮询）即清零。
     * 包私有可见性供回归测试注入 Error。
     */
    void pollOnce(PollEntry entry) {
        try {
            if (entry.getFuture().isDone()) {
                // 已终结：whenComplete 会取消句柄；调度间隙内防御性跳过
                return;
            }
            // 纯内存检查：只看本地持有的 cancelToken + deadline（跳轮退避不影响其每轮重跑）
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
            // 裁定①(a) 跳轮退避：连续失败达 2 次后按 2^(streak-1) 封顶 12 轮跳过状态查询；
            // 计满阈值的那一轮不跳轮、直接 resolve
            int skipped = entry.getSkipRounds().getAndUpdate(v -> v > 0 ? v - 1 : 0);
            if (skipped > 0) {
                return;
            }
            // 窄快照形态（裁定①(d)）：轮询请求由注册时冻结的标量快照构造，实体引用不驻留条目
            rpcPollTaskClient.getJobStatus(entry.getRequest(), entry.getCancelToken())
                    .whenComplete((status, err) -> {
                        if (err != null) {
                            handlePollFailure(entry, err);
                            return;
                        }
                        applyStatus(entry, status);
                    });
        } catch (Exception e) {
            LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), e);
        } catch (Throwable e) {
            // MA-05/C4-004：Error 级失败不得静默（原缺陷 = JDK 周期抑制 + entry 僵尸化零日志）
            LOG.error("nop.job.remote.poll-error:taskId={}", entry.getTaskId(), e);
        }
    }

    /**
     * 裁定①(a) 失败预算：streak 计满阈值 → ERROR 日志留痕 + resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)
     * （显式终态，rule 24 非静默清除）；未计满则按 2^(streak-1) 封顶 12 轮设置跳轮退避。
     * 计数（AtomicInteger）写点在本回调线程（RPC whenComplete）与 pollOnce 轮询线程（清零）
     * 两域，RMW 需原子性。
     */
    private void handlePollFailure(PollEntry entry, Throwable err) {
        int streak = entry.getFailStreak().incrementAndGet();
        incrementFailureCounter(entry.getTaskId());
        LOG.warn("nop.job.remote.poll-failed:taskId={},streak={}", entry.getTaskId(), streak, err);
        if (streak >= pollFailThreshold) {
            LOG.error("nop.job.remote.poll-budget-exceeded:taskId={},streak={}", entry.getTaskId(), streak);
            entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_REMOTE_INVOKE_FAILED)));
            return;
        }
        if (streak >= 2) {
            int skipRounds = (int) Math.min(1L << Math.min(streak - 1, 30), 12);
            entry.getSkipRounds().set(skipRounds);
        }
    }

    /** 裁定①(e)：失败计数 counter（tag taskId）；registry 未初始化时静默跳过。 */
    private void incrementFailureCounter(String taskId) {
        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry == null) {
            return;
        }
        Counter.builder("nop.job.remote.poll.failures")
                .tag("taskId", taskId)
                .register(registry)
                .increment();
    }

    /** TaskStatusBean → JobFireResult 映射；终态 resolve 后条目由 future.whenComplete 移出注册表。 */
    private void applyStatus(PollEntry entry, TaskStatusBean status) {
        switch (status.getTaskStatus()) {
            case TaskStatusBean.STATUS_RUNNING:
            case TaskStatusBean.STATUS_UNKNOWN: {
                // 裁定①(a)：单轮 poll 成功即清零失败计数（含继续轮询态），并记恢复 DEBUG
                if (entry.getFailStreak().getAndSet(0) > 0) {
                    LOG.debug("nop.job.remote.poll-recovered:taskId={}", entry.getTaskId());
                }
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
            // 窄快照形态 + 透传条目 cancelToken（与 RpcJobInvoker.cancelAsync 一致）；best-effort
            rpcPollTaskClient.cancelJob(entry.getRequest(), entry.getCancelToken())
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

    /**
     * 裁定①(b)：取值优先序 schedule.timeoutSeconds &gt; 全局 execution-timeout-ms（&gt;0 时）
     * &gt; 无（0 = 无墙钟上限）。全局键与 {@link JobTimeoutCheckerImpl} 同源读键（单一事实源），
     * 兜底位在 manager 轮询侧，与 plan 340 checker 侧钉死契约同构（未配置都不杀）。
     * 时钟源维持 CoreMetrics.currentTimeMillis() 墙钟（C7-008 本体归 R4-07，本批不代做）。
     */
    private long computeDeadline(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;
        }
        if (executionTimeoutMs > 0) {
            return CoreMetrics.currentTimeMillis() + executionTimeoutMs;
        }
        return 0L;
    }

    private static ErrorBean error(ErrorCode errorCode) {
        return new ErrorBean(errorCode.getErrorCode());
    }

    /**
     * 注册表条目：一个待轮询的远程任务及其终结条件（plan 2513 1.4-C4-001 裁定①(d)）。
     * capture 冻结面为终结/取消所需最小标量视图（taskId/scheduleId/fireId/targetHost/
     * cancelToken/deadline/future + 运行态 pollHandle/onCancelListener/失败计数/跳轮计数/
     * 轮询请求快照）——schedule/fire/task 三实体引用不驻留（实体 JSON payload 对象图不再被
     * 条目钉住，C5-005 驻留放大器消除）；轮询/取消请求由 {@link RpcPollRequest} 冻结快照构造。
     */
    static final class PollEntry {
        private final String taskId;
        private final String scheduleId;
        private final String fireId;
        private final String targetHost;
        private final ICancelToken cancelToken;
        private final CompletableFuture<JobFireResult> future;
        /** 墙钟超时 deadline（0 = 无）；超时先远程 cancelJob 再 resolve ERROR(TIMEOUT)。 */
        private final long deadline;
        /** 注册时冻结的轮询/取消请求快照（标量集，不含实体引用）。 */
        private final RpcPollRequest request;
        /** 裁定①(a)：连续失败计数（AtomicInteger——RPC 回调线程与轮询线程两域 RMW）。 */
        private final AtomicInteger failStreak = new AtomicInteger();
        /** 裁定①(a)：剩余跳轮数（达阈值前退避，2^(streak-1) 封顶 12 轮）。 */
        private final AtomicInteger skipRounds = new AtomicInteger();
        /** 该 entry 的周期轮询句柄：future 终结时 cancel(false) 停止调度。 */
        private volatile Future<?> pollHandle;
        /** 即时取消回调（挂在 cancelToken 上，appendOnCancel）：终结清理时摘除。 */
        private volatile Consumer<String> onCancelListener;

        PollEntry(String taskId, NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                  ICancelToken cancelToken, CompletableFuture<JobFireResult> future, long deadline) {
            this.taskId = taskId;
            this.scheduleId = schedule != null ? schedule.getJobScheduleId() : null;
            this.fireId = fire != null ? fire.getJobFireId() : null;
            this.targetHost = task != null ? task.getTargetHost() : null;
            this.cancelToken = cancelToken;
            this.future = future;
            this.deadline = deadline;
            this.request = RpcPollRequest.of(schedule, fire, task);
        }

        String getTaskId() {
            return taskId;
        }

        String getScheduleId() {
            return scheduleId;
        }

        String getFireId() {
            return fireId;
        }

        String getTargetHost() {
            return targetHost;
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

        RpcPollRequest getRequest() {
            return request;
        }

        AtomicInteger getFailStreak() {
            return failStreak;
        }

        AtomicInteger getSkipRounds() {
            return skipRounds;
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
