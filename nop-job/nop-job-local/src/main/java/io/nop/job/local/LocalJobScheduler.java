package io.nop.job.local;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.JobDetail;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.JobState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core.trigger.OnceTrigger;
import io.nop.job.core.trigger.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.nop.job.api.JobApiErrors.ARG_BEAN_NAME;
import static io.nop.job.api.JobApiErrors.ERR_JOB_BEAN_NOT_FOUND;
import static io.nop.job.api.JobApiErrors.ARG_JOB_NAME;
import static io.nop.job.api.JobApiErrors.ERR_JOB_SCHEDULER_NOT_ACTIVE;
import static io.nop.job.api.JobApiErrors.ERR_JOB_ALREADY_EXISTS;

/**
 * 轻量级内存调度器，适用于单机嵌入式场景。
 * 对于分布式集群场景，请使用 coordinator/worker 架构下的分布式调度器。
 */
public class LocalJobScheduler implements IJobScheduler {
    static final Logger LOG = LoggerFactory.getLogger(LocalJobScheduler.class);

    private final IScheduledExecutor executor;
    private final Function<String, IJobInvoker> invokerResolver;

    private final ConcurrentHashMap<String, ScheduledJob> jobs = new ConcurrentHashMap<>();
    private volatile boolean active;

    public LocalJobScheduler(IScheduledExecutor executor, Function<String, IJobInvoker> invokerResolver) {
        this.executor = Guard.notNull(executor, "executor");
        this.invokerResolver = Guard.notNull(invokerResolver, "invokerResolver");
    }

    // ---- IJobScheduler ----

    @Override
    public List<String> getJobNames() {
        return new ArrayList<>(jobs.keySet());
    }

    @Override
    public JobDetail getJobDetail(String jobName) {
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return null;
        // CRA 8.46 R4-06 W-01 (1.4-MA-02): read under the per-job monitor — the same
        // lock discipline as getJobState. The spec reference (update() may swap it
        // wholesale) and the plain long counters are only guaranteed to be seen as
        // ONE consistent snapshot inside the monitor (write-side unlock → read-side
        // lock establishes happens-before); unlocked CHM get alone carries no such
        // guarantee, so a concurrent observer could read stale/counter-ordered
        // fields. toJobDetail is a pure reader — the locked call is microseconds.
        synchronized (job) {
            return job.toJobDetail();
        }
    }

    @Override
    public void addJob(JobSpec spec, boolean allowUpdate) {
        checkActive();
        LOG.info("nop.job.add-job:jobName={}", spec.getJobName());

        IJobInvoker invoker = invokerResolver.apply(spec.getJobInvoker());
        if (invoker == null) {
            // 配置期fail-fast：bean未注册时若放行，错误推迟到首个触发期且NPE无jobInvoker上下文
            throw new NopException(ERR_JOB_BEAN_NOT_FOUND).param(ARG_BEAN_NAME, spec.getJobInvoker());
        }
        ITrigger trigger;
        if (spec.getTriggerSpec() == null) {
            trigger = new OnceTrigger(-1);
        } else {
            trigger = TriggerBuilder.buildTrigger(spec.getTriggerSpec(), null);
        }

        SimpleJobState state = new SimpleJobState(spec.getTriggerSpec());

        ScheduledJob existing = jobs.get(spec.getJobName());
        if (existing != null) {
            if (!allowUpdate) {
                throw new NopException(ERR_JOB_ALREADY_EXISTS)
                        .param(ARG_JOB_NAME, spec.getJobName());
            }
            synchronized (existing) {
                // re-check: removeJob may have removed this entry between get and lock
                if (!jobs.containsKey(spec.getJobName())) {
                    // job was removed; fall through to create-and-schedule below
                } else {
                    existing.update(spec, invoker, trigger, state);
                    rescheduleAfterUpdate(existing);
                    return;
                }
            }
        }

        // New job, or the existing entry was removed while we waited for the lock
        // (that entry-branch race falls through here). putIfAbsent prevents two
        // threads from creating duplicate ScheduledJobs for the same name; the
        // raced loop above resolves lost races (W-02/W-07) and the monitor below
        // protects scheduleNext against a concurrent removeJob or deactivate.
        ScheduledJob job = new ScheduledJob(spec, invoker, trigger, state);
        ScheduledJob raced = jobs.putIfAbsent(spec.getJobName(), job);
        int racedRemovals = 0;
        while (raced != null) {
            boolean updateHandled = false;
            // Another thread inserted first — treat as update.
            synchronized (raced) {
                if (!jobs.containsKey(spec.getJobName())) {
                    // CRA 8.46 R4-06 W-02 (1.4-MA-03): a concurrent removeJob won the
                    // race while we waited for the monitor (removeJob removes from the
                    // map BEFORE taking the lock, so the in-lock re-check fails). The
                    // old code silently returned — the registration was lost without a
                    // trace while addJob reported success, although the existing-entry
                    // branch handles the very same race by falling through and
                    // rebuilding. WARN + a SINGLE fall-through retry below; a second
                    // consecutive removal is the remove-wins terminal state (an
                    // adversarial remove loop ends the same way) — logged, never
                    // silent.
                    LOG.warn("nop.job.add-job-raced-removed:jobName={}", spec.getJobName());
                } else {
                    // CRA 8.46 R4-06 W-07 (1.4-C2-004): the raced re-check PASSED —
                    // the job exists. The allowUpdate contract (owner doc §1.2:
                    // false = already-exists throws) must hold here exactly like on
                    // the entry existing-branch path; the old code updated
                    // unconditionally, so two concurrent first-time registrations
                    // with allowUpdate=false silently overwrote each other's spec.
                    // Throw BEFORE any mutation: our own putIfAbsent already lost
                    // (raced was returned) and we touched nothing in the map — no
                    // rollback needed.
                    if (!allowUpdate) {
                        throw new NopException(ERR_JOB_ALREADY_EXISTS)
                                .param(ARG_JOB_NAME, spec.getJobName());
                    }
                    raced.update(spec, invoker, trigger, state);
                    rescheduleAfterUpdate(raced);
                    updateHandled = true;
                }
            }
            if (updateHandled) {
                return;
            }
            if (racedRemovals >= 1) {
                // Second consecutive raced removal: remove-wins terminal state —
                // the job does not exist by the remover's own semantics; keep the
                // second WARN as the caller-visible trace (never silent).
                LOG.warn("nop.job.add-job-raced-removed:jobName={}", spec.getJobName());
                return;
            }
            racedRemovals++;
            raced = jobs.putIfAbsent(spec.getJobName(), job);
        }

        // New job (fresh insert, or the W-02 retry re-insert won): putIfAbsent
        // prevents two threads from creating duplicate ScheduledJobs for the same
        // name; synchronized(job) then protects scheduleNext against a concurrent
        // removeJob or deactivate.
        synchronized (job) {
            if (!active) {
                // deactivate ran after our putIfAbsent; clean up
                cancelScheduledFire(job);
                jobs.remove(spec.getJobName(), job);
                return;
            }
            scheduleNext(job);
        }
    }

    @Override
    public boolean removeJob(String jobName) {
        checkActive();
        LOG.info("nop.job.remove-job:jobName={}", jobName);
        ScheduledJob job = jobs.remove(jobName);
        if (job == null) {
            LOG.info("nop.job.remove-job-not-exists:jobName={}", jobName);
            return false;
        }
        synchronized (job) {
            cancelScheduledFire(job);
            cancelRunning(job);
        }
        return true;
    }

    @Override
    public JobState getJobState(String jobName) {
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return null;
        synchronized (job) {
            return job.state.toJobState();
        }
    }

    @Override
    public boolean resumeJob(String jobName) {
        checkActive();
        LOG.info("nop.job.resume-job:jobName={}", jobName);
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return false;
        synchronized (job) {
            if (job.state.internal == InternalState.SUSPENDED) {
                job.state.internal = InternalState.WAITING;
                scheduleNext(job);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean suspendJob(String jobName) {
        checkActive();
        LOG.info("nop.job.suspend-job:jobName={}", jobName);
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return false;
        synchronized (job) {
            if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED)
                return true;
            cancelScheduledFire(job);
            job.state.internal = InternalState.SUSPENDED;
        }
        return true;
    }

    @Override
    public boolean cancelJob(String jobName) {
        checkActive();
        LOG.info("nop.job.cancel-job:jobName={}", jobName);
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return false;
        synchronized (job) {
            cancelScheduledFire(job);
            cancelRunning(job);
            job.state.internal = InternalState.COMPLETED;
        }
        return true;
    }

    @Override
    public boolean fireNow(String jobName) {
        checkActive();
        LOG.info("nop.job.fire-now:jobName={}", jobName);
        ScheduledJob job = jobs.get(jobName);
        if (job == null)
            return false;
        // C2-001 裁定②：synchronized 块在两阶段之间闭合——块内完成拒绝检查（必须持锁，与并发
        // fireNow/update 互斥）+ cancelScheduledFire + 锁内阶段1，出块后锁外阶段2。
        // prepare 无放弃分支（拒绝检查均在块内前置完成），返回值语义不变（仍 true）。
        PreparedExecution prep;
        synchronized (job) {
            if (job.running != null)
                return false;
            if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED)
                return false;
            cancelScheduledFire(job);
            prep = prepareJobLocked(job);
        }
        startJobUnlocked(job, prep.ctx, prep.done);
        return true;
    }

    @Override
    public void activate() {
        active = true;
    }

    @Override
    public void deactivate() {
        active = false;
        for (ScheduledJob job : jobs.values()) {
            synchronized (job) {
                cancelScheduledFire(job);
                cancelRunning(job);
            }
        }
        jobs.clear();
    }

    // ---- internal ----

    void checkActive() {
        if (!active)
            throw new NopException(ERR_JOB_SCHEDULER_NOT_ACTIVE);
    }

    /**
     * check2 [P3-13]: 更新已存在 job 后的重调度。SUSPENDED 状态在更新时保持不变——
     * 此前 SUSPENDED 也纳入 scheduleNext，被无条件改写回 WAITING 并重新排程，
     * 配置热更新会静默撤销 suspendJob 的效果（暂停中的任务突然开始执行）。
     * 仅替换 spec/trigger，待 resumeJob 时再以新 trigger 恢复调度。
     */
    private void rescheduleAfterUpdate(ScheduledJob job) {
        if (job.state.internal == InternalState.WAITING) {
            cancelScheduledFire(job);
            scheduleNext(job);
        } else if (job.state.internal == InternalState.SUSPENDED) {
            // SUSPENDED: keep suspended; defensively cancel any lingering scheduled fire
            cancelScheduledFire(job);
        }
    }

    private void scheduleNext(ScheduledJob job) {
        scheduleNext(job, 0L);
    }

    /**
     * @param overrideNextTime >0 时以该时间为下次触发（JobFireResult契约：CONTINUE(nextScheduleTime)
     *                          且>0时忽略trigger计算结果），与分布式worker链路的
     *                          resolveCompletionDecision消费语义对齐；=0时按trigger计算
     */
    private void scheduleNext(ScheduledJob job, long overrideNextTime) {
        if (!jobs.containsKey(job.spec.getJobName())) {
            return;
        }
        long now = currentTime();
        long nextTime;
        try {
            nextTime = overrideNextTime > 0 ? overrideNextTime
                    : job.trigger.nextScheduleTime(now, job.state);
        } catch (Throwable e) {
            // 触发器计算失败（病态cron的runaway/overflow、日历迭代超限等）不得逃出whenComplete回调：
            // 逃逸后异常进入被丢弃的返回future，job永久卡在RUNNING且无任何日志。置FAILED并记error。
            // MA-05/C4-012 站点③：catch 宽度含 Error（Error 逃逸同样滞留 RUNNING 且零日志）
            LOG.error("nop.job.trigger-calc-failed:jobName={}", job.spec.getJobName(), e);
            job.state.internal = InternalState.FAILED;
            return;
        }
        if (nextTime <= 0) {
            job.state.internal = InternalState.COMPLETED;
            if (job.spec.isOnceTask()) {
                jobs.remove(job.spec.getJobName(), job);
            }
            return;
        }
        long delay = Math.max(nextTime - now, 0);
        job.state.internal = InternalState.WAITING;
        job.state.scheduledFireTime = nextTime;
        CompletableFuture<?> future = executor.schedule(() -> {
            // C2-001 裁定②：synchronized 块在两阶段之间闭合——块内前置检查 + 锁内阶段1，
            // 出块后锁外阶段2（invokeAsync 不持 per-job 监视器）
            PreparedExecution prep;
            synchronized (job) {
                job.scheduledFire = null;
                if (!active || job.state.internal != InternalState.WAITING)
                    return null;
                prep = prepareJobLocked(job);
            }
            startJobUnlocked(job, prep.ctx, prep.done);
            return null;
        }, delay, TimeUnit.MILLISECONDS);
        job.scheduledFire = future;
    }

    /**
     * C2-001 裁定②阶段1（锁内，调用方持 per-job 监视器）：状态迁移（RUNNING、lastScheduledTime）、
     * ctx 真快照构造、占位 done 创建与 {@code job.running = done} 置位。占位先于 whenComplete 注册、
     * 先于 invokeAsync（既有 :331-335 防护形态保持——防已完成 future 同步回调覆盖占位；C2-007
     * 共设计接缝：先行关系显式保序，占位前移本体归 R4-06 不代做）。
     */
    private PreparedExecution prepareJobLocked(ScheduledJob job) {
        job.state.internal = InternalState.RUNNING;
        job.state.lastScheduledTime = job.state.scheduledFireTime > 0
                ? job.state.scheduledFireTime : currentTime();
        PreparedExecution prep = new PreparedExecution(SimpleExecutionContext.snapshotOf(job.spec, job.state));
        job.running = prep.done;
        return prep;
    }

    /**
     * C2-001 裁定②阶段2（锁外，调用方已释放 per-job 监视器）：identity/状态复检 →
     * invokeAsync → whenComplete 重取锁终态迁移（MA-05 站点③ fail-safe 形态原样保持）。
     * invokeAsync 同步抛异常不逃逸锁外段——经重取锁落 FAILED 终态 + done 完结。
     */
    private void startJobUnlocked(ScheduledJob job, IJobExecutionContext ctx, CompletableFuture<?> done) {
        // 出锁段 identity/状态复检（plan 2509 §3.6 范式投影）：job 被 remove/deactivate/suspend/cancel
        // 交错时走显式放弃分支（非静默丢弃）
        if (!jobs.containsKey(job.spec.getJobName()) || job.state.internal != InternalState.RUNNING) {
            abandonAfterInterleave(job, done);
            return;
        }

        CompletionStage<JobFireResult> future;
        try {
            future = job.invoker.invokeAsync(ctx);
        } catch (Throwable e) {
            // CRA 8.46 R4-06 W-08 (1.4-C2-007 residual): Errors must take the SAME
            // path as Exceptions — without this widening a synchronous Error from
            // invokeAsync (NoClassDefFoundError/OOM/custom invoker) escaped the
            // two-phase handoff: the RUNNING placeholder (:335) was left dangling,
            // the job silently stopped scheduling, awaitIdle never idled, fireNow
            // always refused, with no FAILED terminal state and no module log.
            // Via the existing settleExecution channel the Error lands as a FAILED
            // terminal state (FAILED is terminal in local mode — no self-heal;
            // self-heal belongs to C7-006/R4-07). The 2513 four-artifact order
            // (placeholder-first / settle-relock / identity re-check + abandon /
            // fail-safe) is preserved — this only widens the catch clause.
            settleExecution(job, null, e, done);
            return;
        }

        if (future == null) {
            settleExecution(job, null, null, done);
        } else {
            future.whenComplete((result, err) -> settleExecution(job, result, err, done));
        }
    }

    /**
     * whenComplete 回调段：重取 per-job 监视器做终态迁移。MA-05/C4-012 站点③ fail-safe 形态
     * 原样保持（回调动作任何 Throwable 都不被丢弃的衍生 future 吞掉，job 保证离开 RUNNING）。
     */
    private void settleExecution(ScheduledJob job, JobFireResult result, Throwable err,
                                 CompletableFuture<?> done) {
        synchronized (job) {
            Throwable failure = err;
            try {
                job.running = null;
                handleResult(job, result, err);
            } catch (Throwable e) {
                LOG.error("nop.job.handle-result-error:jobName={}", job.spec.getJobName(), e);
                if (failure == null)
                    failure = e;
                if (job.state.internal == InternalState.RUNNING)
                    job.state.internal = InternalState.FAILED;
            }
            if (failure == null)
                done.complete(null);
            else
                done.completeExceptionally(failure);
        }
    }

    /**
     * 出锁段放弃分支（显式，rule 24 非静默）：交错方（remove/deactivate/suspend/cancel）已对 job
     * 终态化后，本论执行放弃。重取锁下清 running + 完结占位 done（否则 running 悬空使 awaitIdle
     * 永不空闲、fireNow 恒拒绝）；状态维持交错方语义（禁走 handleResult——null result 会经
     * scheduleNext 把 SUSPENDED 改写回 WAITING，静默撤销 suspendJob 效果）。
     */
    private void abandonAfterInterleave(ScheduledJob job, CompletableFuture<?> done) {
        synchronized (job) {
            LOG.warn("nop.job.start-abandoned-after-interleave:jobName={},internal={}",
                    job.spec.getJobName(), job.state.internal);
            job.running = null;
            done.complete(null);
        }
    }

    private void handleResult(ScheduledJob job, JobFireResult result, Throwable err) {
        job.state.fireCount++;
        job.state.lastEndTime = currentTime();

        if (err != null) {
            LOG.error("nop.job.execute-failed:jobName={}", job.spec.getJobName(), err);
            job.state.internal = InternalState.FAILED;
            return;
        }

        if (result != null && result.isCompleted()) {
            if (result.isErrorResult()) {
                job.state.internal = InternalState.FAILED;
                return;
            }
            job.state.internal = InternalState.COMPLETED;
            if (job.spec.isOnceTask()) {
                jobs.remove(job.spec.getJobName(), job);
            }
            return;
        }

        // Guard: only schedule next if job is still registered and not externally terminated
        if (!jobs.containsKey(job.spec.getJobName())) {
            job.state.internal = InternalState.COMPLETED;
            return;
        }
        if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED) {
            return;
        }

        scheduleNext(job, result == null ? 0L : result.getNextScheduleTime());
    }

    private void cancelScheduledFire(ScheduledJob job) {
        CompletableFuture<?> sf = job.scheduledFire;
        if (sf != null) {
            sf.cancel(false);
            job.scheduledFire = null;
        }
    }

    private void cancelRunning(ScheduledJob job) {
        // running CompletionStage cannot be cancelled in general;
        // the job will complete and handleResult will see deactivated state
    }

    protected long currentTime() {
        return CoreMetrics.currentTimeMillis();
    }

    // ---- 等待辅助方法 ----
    // 主要用于测试和嵌入式场景，阻塞等待 job 进入稳定状态，避免竞态导致的断言不稳定。

    /**
     * 等待 job 进入指定状态。job 不存在时仅当 state 为 null 才返回 true。
     */
    public boolean awaitState(String jobName, JobState state, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = CoreMetrics.nanoTime() + unit.toNanos(timeout);
        do {
            ScheduledJob job = jobs.get(jobName);
            if (job == null) {
                if (state == null)
                    return true;
            } else {
                synchronized (job) {
                    if (job.state.toJobState() == state)
                        return true;
                }
            }
            long remaining = deadlineNanos - CoreMetrics.nanoTime();
            if (remaining <= 0)
                break;
            Thread.sleep(Math.min(POLL_INTERVAL_MS, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))));
        } while (true);
        return false;
    }

    /**
     * 等待 job 当前没有正在执行的实例（{@code running == null}）。job 不存在视为已空闲。
     */
    public boolean awaitIdle(String jobName, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = CoreMetrics.nanoTime() + unit.toNanos(timeout);
        do {
            ScheduledJob job = jobs.get(jobName);
            if (job == null)
                return true;
            synchronized (job) {
                if (job.running == null)
                    return true;
            }
            long remaining = deadlineNanos - CoreMetrics.nanoTime();
            if (remaining <= 0)
                break;
            Thread.sleep(Math.min(POLL_INTERVAL_MS, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))));
        } while (true);
        return false;
    }

    /**
     * 等待 job 的累计触发次数达到 {@code minCount}。
     */
    public boolean awaitFireCount(String jobName, long minCount, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = CoreMetrics.nanoTime() + unit.toNanos(timeout);
        do {
            ScheduledJob job = jobs.get(jobName);
            if (job != null) {
                synchronized (job) {
                    if (job.state.fireCount >= minCount)
                        return true;
                }
            }
            long remaining = deadlineNanos - CoreMetrics.nanoTime();
            if (remaining <= 0)
                break;
            Thread.sleep(Math.min(POLL_INTERVAL_MS, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))));
        } while (true);
        return false;
    }

    private static final long POLL_INTERVAL_MS = 5L;

    // ---- inner classes ----

    static class ScheduledJob {
        final SimpleJobState state;
        JobSpec spec;
        IJobInvoker invoker;
        ITrigger trigger;
        CompletableFuture<?> scheduledFire;
        CompletionStage<?> running;

        ScheduledJob(JobSpec spec, IJobInvoker invoker, ITrigger trigger, SimpleJobState state) {
            this.spec = spec;
            this.invoker = invoker;
            this.trigger = trigger;
            this.state = state;
        }

        void update(JobSpec spec, IJobInvoker invoker, ITrigger trigger, SimpleJobState state) {
            this.trigger = trigger;
            this.state.copyFrom(state);
            if (this.running == null) {
                this.spec = spec;
                this.invoker = invoker;
            }
        }

        JobDetail toJobDetail() {
            JobDetail detail = new JobDetail();
            detail.setJobSpec(spec);
            JobInstanceState inst = new JobInstanceState();
            inst.setJobName(spec.getJobName());
            inst.setJobGroup(spec.getJobGroup());
            inst.setExecCount(state.fireCount);
            inst.setScheduledExecTime(state.lastScheduledTime);
            inst.setExecEndTime(state.lastEndTime);
            detail.setInstanceState(inst);
            return detail;
        }
    }

    /** 阶段1产物：锁内构造的 ctx 快照 + 占位 done，交阶段2锁外消费（C2-001 裁定②）。 */
    private static final class PreparedExecution {
        final IJobExecutionContext ctx;
        final CompletableFuture<?> done = new CompletableFuture<>();

        PreparedExecution(IJobExecutionContext ctx) {
            this.ctx = ctx;
        }
    }

    static class SimpleJobState implements ITriggerEvalContext {
        long fireCount;
        long lastScheduledTime;
        long lastEndTime;
        long minScheduleTime;
        long maxScheduleTime;
        long maxExecutionCount;
        long scheduledFireTime;
        boolean completed;
        InternalState internal = InternalState.WAITING;

        SimpleJobState(ITriggerSpec spec) {
            if (spec != null) {
                this.maxExecutionCount = spec.getMaxExecutionCount();
                this.minScheduleTime = spec.getMinScheduleTime();
                this.maxScheduleTime = spec.getMaxScheduleTime();
            }
        }

        void copyFrom(SimpleJobState other) {
            this.minScheduleTime = other.minScheduleTime;
            this.maxScheduleTime = other.maxScheduleTime;
            this.maxExecutionCount = other.maxExecutionCount;
        }

        @Override public long getFireCount() { return fireCount; }
        @Override public long getLastScheduledTime() { return lastScheduledTime; }
        @Override public long getLastEndTime() { return lastEndTime; }
        @Override public long getMinScheduleTime() { return minScheduleTime; }
        @Override public long getMaxScheduleTime() { return maxScheduleTime; }
        @Override public long getMaxExecutionCount() { return maxExecutionCount; }
        @Override public boolean isScheduleCompleted() { return completed || internal == InternalState.COMPLETED; }

        JobState toJobState() {
            switch (internal) {
                case WAITING: return JobState.WAITING;
                case RUNNING: return JobState.RUNNING;
                case SUSPENDED: return JobState.SUSPENDED;
                case COMPLETED: return JobState.COMPLETED;
                case FAILED: return JobState.FAILED;
                default: return JobState.WAITING;
            }
        }
    }

    enum InternalState {
        WAITING, RUNNING, SUSPENDED, COMPLETED, FAILED
    }

    /**
     * C2-001 裁定② ctx 真快照：构造时复制 ctx 消费字段为不可变态（jobName/jobVersion/jobGroup/
     * jobParams/fireCount/lastScheduledTime/lastEndTime/min/max/maxExecutionCount/completed/
     * internal=RUNNING），invokeAsync 消费快照零共享可变态——交错方 update（仅 running==null 时
     * 换绑 spec）改写 spec/trigger 不影响在途调用。防回归缝（plan 2513 裁定③ 缝5）：本类不得持有
     * ScheduledJob/SimpleJobState/JobSpec 引用，新增字段必须同步快照清单。
     */
    static class SimpleExecutionContext implements IJobExecutionContext {
        private final String jobName;
        private final long jobVersion;
        private final String jobGroup;
        private final Map<String, Object> jobParams;
        private final long fireCount;
        private final long lastScheduledTime;
        private final long lastEndTime;
        private final long minScheduleTime;
        private final long maxScheduleTime;
        private final long maxExecutionCount;
        private final boolean completed;
        private final boolean onceTask;
        private final InternalState internal;

        SimpleExecutionContext(JobSpec spec, SimpleJobState state) {
            Map<String, Object> params = spec.getJobParams();
            this.jobName = spec.getJobName();
            this.jobVersion = spec.getJobVersion();
            this.jobGroup = spec.getJobGroup();
            this.jobParams = params == null ? null : new LinkedHashMap<>(params);
            this.fireCount = state.fireCount;
            this.lastScheduledTime = state.lastScheduledTime;
            this.lastEndTime = state.lastEndTime;
            this.minScheduleTime = state.minScheduleTime;
            this.maxScheduleTime = state.maxScheduleTime;
            this.maxExecutionCount = state.maxExecutionCount;
            this.completed = state.completed || state.internal == InternalState.COMPLETED;
            this.onceTask = spec.isOnceTask();
            this.internal = InternalState.RUNNING;
        }

        static SimpleExecutionContext snapshotOf(JobSpec spec, SimpleJobState state) {
            return new SimpleExecutionContext(spec, state);
        }

        @Override public String getJobDefId() { return null; }
        @Override public String getJobName() { return jobName; }
        @Override public long getJobVersion() { return jobVersion; }
        @Override public String getJobGroup() { return jobGroup; }
        @Override public Map<String, Object> getJobParams() { return jobParams; }
        @Override public String getInstanceId() { return String.valueOf(fireCount); }
        @Override public long getExecCount() { return fireCount; }
        @Override public long getScheduledExecTime() { return lastScheduledTime; }
        @Override public long getExecBeginTime() { return lastScheduledTime; }
        @Override public long getExecEndTime() { return lastEndTime; }
        @Override public boolean isOnceTask() { return onceTask; }
        @Override public boolean isManualFire() { return false; }
        @Override public String getFiredBy() { return null; }
        @Override public long getChangeVersion() { return 0; }
        @Override public long getExecFailCount() { return 0; }
        @Override public int getInstanceStatus() { return 0; }
        @Override public io.nop.api.core.beans.ErrorBean getExecError() { return null; }
        @Override public String getLastInstanceId() { return null; }
        @Override public Map<String, Object> getAttributes() { return null; }
        @Override public void setAttributes(Map<String, Object> attributes) {}
        @Override public long getMinScheduleTime() { return minScheduleTime; }
        @Override public long getMaxScheduleTime() { return maxScheduleTime; }
        @Override public long getMaxExecutionCount() { return maxExecutionCount; }
        @Override public long getMaxFailedCount() { return 0; }
        @Override public boolean isJobFinished() { return completed; }
        @Override public boolean isInstanceRunning() { return internal == InternalState.RUNNING; }
        @Override public boolean isScheduleEnabled() { return !completed; }
    }
}
