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
        return job == null ? null : job.toJobDetail();
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

        // New job or existing was removed while we waited for the lock.
        // putIfAbsent prevents two threads from creating duplicate ScheduledJobs
        // for the same name; synchronized(job) then protects scheduleNext against
        // a concurrent removeJob or deactivate.
        ScheduledJob job = new ScheduledJob(spec, invoker, trigger, state);
        ScheduledJob raced = jobs.putIfAbsent(spec.getJobName(), job);
        if (raced != null) {
            // Another thread inserted first — treat as update.
            synchronized (raced) {
                if (!jobs.containsKey(spec.getJobName())) {
                    return;
                }
                raced.update(spec, invoker, trigger, state);
                rescheduleAfterUpdate(raced);
            }
        } else {
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
        synchronized (job) {
            if (job.running != null)
                return false;
            if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED)
                return false;
            cancelScheduledFire(job);
            executeJob(job);
        }
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
        } catch (Exception e) {
            // 触发器计算异常（病态cron的runaway/overflow、日历迭代超限等）不得逃出whenComplete回调：
            // 逃逸后异常进入被丢弃的返回future，job永久卡在RUNNING且无任何日志。置FAILED并记error
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
            synchronized (job) {
                job.scheduledFire = null;
                if (!active || job.state.internal != InternalState.WAITING)
                    return null;
                executeJob(job);
            }
            return null;
        }, delay, TimeUnit.MILLISECONDS);
        job.scheduledFire = future;
    }

    private void executeJob(ScheduledJob job) {
        job.state.internal = InternalState.RUNNING;
        job.state.lastScheduledTime = job.state.scheduledFireTime > 0
                ? job.state.scheduledFireTime : currentTime();
        SimpleExecutionContext ctx = new SimpleExecutionContext(job.spec, job.state);

        CompletionStage<JobFireResult> future;
        try {
            future = job.invoker.invokeAsync(ctx);
        } catch (Exception e) {
            handleResult(job, null, e);
            return;
        }

        if (future == null) {
            handleResult(job, null, null);
        } else {
            // 先用占位 future 占住 job.running，再注册回调。否则当 invoker 返回的是
            // 已完成的 future 时，whenComplete 会同步执行回调把 job.running 置 null，
            // 随后的外层赋值又把它覆盖回非空，导致 job.running 永远清不掉。
            final CompletableFuture<?> done = new CompletableFuture<>();
            job.running = done;
            future.whenComplete((result, err) -> {
                synchronized (job) {
                    job.running = null;
                    handleResult(job, result, err);
                    if (err == null)
                        done.complete(null);
                    else
                        done.completeExceptionally(err);
                }
            });
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
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
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
            long remaining = deadlineNanos - System.nanoTime();
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
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        do {
            ScheduledJob job = jobs.get(jobName);
            if (job == null)
                return true;
            synchronized (job) {
                if (job.running == null)
                    return true;
            }
            long remaining = deadlineNanos - System.nanoTime();
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
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        do {
            ScheduledJob job = jobs.get(jobName);
            if (job != null) {
                synchronized (job) {
                    if (job.state.fireCount >= minCount)
                        return true;
                }
            }
            long remaining = deadlineNanos - System.nanoTime();
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

    static class SimpleExecutionContext implements IJobExecutionContext {
        private final JobSpec spec;
        private final SimpleJobState state;

        SimpleExecutionContext(JobSpec spec, SimpleJobState state) {
            this.spec = spec;
            this.state = state;
        }

        @Override public String getJobDefId() { return null; }
        @Override public String getJobName() { return spec.getJobName(); }
        @Override public long getJobVersion() { return spec.getJobVersion(); }
        @Override public String getJobGroup() { return spec.getJobGroup(); }
        @Override public Map<String, Object> getJobParams() { return spec.getJobParams(); }
        @Override public String getInstanceId() { return String.valueOf(state.fireCount); }
        @Override public long getExecCount() { return state.fireCount; }
        @Override public long getScheduledExecTime() { return state.lastScheduledTime; }
        @Override public long getExecBeginTime() { return state.lastScheduledTime; }
        @Override public long getExecEndTime() { return state.lastEndTime; }
        @Override public boolean isOnceTask() { return spec.isOnceTask(); }
        @Override public boolean isManualFire() { return false; }
        @Override public String getFiredBy() { return null; }
        @Override public long getChangeVersion() { return 0; }
        @Override public long getExecFailCount() { return 0; }
        @Override public int getInstanceStatus() { return 0; }
        @Override public io.nop.api.core.beans.ErrorBean getExecError() { return null; }
        @Override public String getLastInstanceId() { return null; }
        @Override public Map<String, Object> getAttributes() { return null; }
        @Override public void setAttributes(Map<String, Object> attributes) {}
        @Override public long getMinScheduleTime() { return state.getMinScheduleTime(); }
        @Override public long getMaxScheduleTime() { return state.getMaxScheduleTime(); }
        @Override public long getMaxExecutionCount() { return state.getMaxExecutionCount(); }
        @Override public long getMaxFailedCount() { return 0; }
        @Override public boolean isJobFinished() { return state.isScheduleCompleted(); }
        @Override public boolean isInstanceRunning() { return state.internal == InternalState.RUNNING; }
        @Override public boolean isScheduleEnabled() { return !state.isScheduleCompleted(); }
    }
}
