package io.nop.job.core;

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
        ITrigger trigger;
        if (spec.getTriggerSpec() == null) {
            trigger = new io.nop.job.core.trigger.OnceTrigger(-1);
        } else {
            trigger = io.nop.job.core.trigger.TriggerBuilder.buildTrigger(spec.getTriggerSpec(), null);
        }

        SimpleJobState state = new SimpleJobState(spec.getTriggerSpec());

        ScheduledJob existing = jobs.get(spec.getJobName());
        if (existing != null) {
            if (!allowUpdate) {
                throw new NopException(ERR_JOB_ALREADY_EXISTS)
                        .param(ARG_JOB_NAME, spec.getJobName());
            }
            synchronized (existing) {
                existing.update(spec, invoker, trigger, state);
                if (existing.state.internal == InternalState.WAITING || existing.state.internal == InternalState.SUSPENDED) {
                    cancelScheduledFire(existing);
                    scheduleNext(existing);
                }
            }
        } else {
            ScheduledJob job = new ScheduledJob(spec, invoker, trigger, state);
            jobs.put(spec.getJobName(), job);
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

    private void scheduleNext(ScheduledJob job) {
        if (!jobs.containsKey(job.spec.getJobName())) {
            return;
        }
        long now = currentTime();
        long nextTime = job.trigger.nextScheduleTime(now, job.state);
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
            job.running = future.whenComplete((result, err) -> {
                synchronized (job) {
                    job.running = null;
                    handleResult(job, result, err);
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

        scheduleNext(job);
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
