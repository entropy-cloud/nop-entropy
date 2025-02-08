/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.scheduler;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.IJobInvoker;
import io.nop.job.api.IJobScheduleStore;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.JobDetail;
import io.nop.job.api.TriggerStatus;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerExecution;
import io.nop.job.core.ITriggerExecutor;
import io.nop.job.core.trigger.OnceTrigger;
import io.nop.job.core.trigger.TriggerBuilder;
import io.nop.job.core.trigger.TriggerContextImpl;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.job.api.JobApiErrors.ARG_JOB_NAME;
import static io.nop.job.core.JobCoreErrors.ARG_CURRENT_EPOCH;
import static io.nop.job.core.JobCoreErrors.ARG_EPOCH;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_ALREADY_EXISTS;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DEACTIVATED_SCHEDULER_NOT_ALLOW_OPERATION;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_EMPTY_INVOKER_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVALID_JOB_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_SCHEDULER_EPOCH_EXPIRED;

public class DefaultJobScheduler implements IJobScheduler {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobScheduler.class);

    private final Map<String, JobExecution> jobs = new ConcurrentHashMap<>();

    private final ITriggerExecutor executor;

    private final Function<String, IJobInvoker> invokerFactory;

    private IJobScheduleStore jobStore;

    private ICalendar defaultCalendar;

    private volatile ICancellable cancelFetch; //NOSONAR

    private boolean deactivated;

    private long epoch;

    public DefaultJobScheduler(ITriggerExecutor executor, Function<String, IJobInvoker> invokerFactory) {
        this.executor = Guard.notNull(executor, "triggerExecutor");
        this.invokerFactory = Guard.notNull(invokerFactory, "jobInvokerFactory");
    }

    public void setDefaultCalendar(ICalendar defaultCalendar) {
        this.defaultCalendar = defaultCalendar;
    }

    public void setJobScheduleStore(IJobScheduleStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void activate(long epoch) {
        if (this.epoch < epoch) {
            throw new NopException(ERR_JOB_SCHEDULER_EPOCH_EXPIRED).param(ARG_EPOCH, epoch).param(ARG_CURRENT_EPOCH, epoch);
        }

        if (this.epoch == epoch) {
            if (!this.deactivated)
                return;
        }

        deactivate();
        this.epoch = epoch;

        deactivated = false;

        if (jobStore != null) {
            cancelFetch = jobStore.fetchPersistJobs(jobDetail -> {
                try {
                    addJob(jobDetail.getJobSpec(), jobDetail.getTriggerState());
                } catch (Exception e) {
                    LOG.error("nop.err.job.process-schedule-event-fail", e);
                }
            });
        }
    }

    @Override
    public void deactivate() {
        deactivated = true;
        ICancellable cancelFetch = this.cancelFetch;
        if (cancelFetch != null) {
            cancelFetch.cancel();
            this.cancelFetch = null;
        }

        Iterator<JobExecution> it = jobs.values().iterator();
        while (it.hasNext()) {
            JobExecution execution = it.next();
            execution.deactivate();
            it.remove();
        }
    }

    void checkActivated() {
        if (deactivated)
            throw new NopException(ERR_JOB_DEACTIVATED_SCHEDULER_NOT_ALLOW_OPERATION);
    }

//    private CompletableFuture<Void> getAllTriggerPromises() {
//        List<CompletionStage<Void>> futures = new ArrayList<>();
//        for (JobExecution execution : jobs.values()) {
//            ITriggerExecution triggerExec = execution.getTriggerExecution();
//            if (triggerExec != null) {
//                futures.add(triggerExec.getFinishPromise());
//            }
//        }
//        return FutureHelper.waitAll(futures);
//    }

    @Override
    public List<String> getJobNames() {
        return new ArrayList<>(jobs.keySet());
    }

    @Override
    public JobDetail getJobDetail(String jobName) {
        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) {
                return execution.toJobDetail();
            }
        }
        return null;
    }

    @Override
    public void addJob(JobSpec spec, boolean allowUpdate) {
        checkActivated();
        LOG.info("nop.job.add-job:jobName={},epoch={}", spec.getJobName(), epoch);

        ResolvedJobSpec resolved = resolveJobSpec(spec);
        String jobName = resolved.getJobName();

        JobExecution execution = jobs.get(jobName);
        boolean created = false;
        if (execution == null) {
            execution = createJobExecution(resolved);
            JobExecution oldExec = jobs.putIfAbsent(jobName, execution);
            if (oldExec == null) {
                created = true;
            } else {
                execution = oldExec;
            }
        }

        if (created) {
            synchronized (execution) { //NOSONAR
                boolean active = execution.getTriggerStatus().isActive();
                if (active) {
                    startTrigger(execution);
                }
            }
        } else {
            updateJob(execution, resolved, true);
        }
    }

    void addJob(JobSpec jobSpec, ITriggerState triggerState) {
        ResolvedJobSpec resolved = resolveJobSpec(jobSpec);
        String jobName = resolved.getJobName();
        LOG.info("nop.job.add-persist-job:jobName={},epoch={},status={}", jobName, epoch,
                triggerState.getTriggerStatus());

        JobExecution execution = jobs.get(jobName);
        if (execution == null) {
            execution = createJobExecution(resolved, triggerState);
            JobExecution oldExec = jobs.putIfAbsent(jobName, execution);
            if (oldExec == null) {
                boolean active = execution.getTriggerStatus().isActive();
                if (active) {
                    startTrigger(execution);
                }
            } else {
                execution = oldExec;
            }
        }
        updateJob(execution, resolved, true);
    }

    private void updateJob(JobExecution execution, ResolvedJobSpec resolved, boolean allowUpdate) {
        String jobName = resolved.getJobName();
        JobSpec spec = resolved.getJobSpec();

        synchronized (execution) { //NOSONAR
            // 忽略旧版本
            if (execution.getJobSpec().getJobSpec().getVersion() > spec.getVersion()) {
                LOG.info("nop.job.ignore-obsolete-job-spec:jobName={},version={},currentVer={}", spec.getJobName(),
                        spec.getVersion(), execution.getJobSpec().getJobSpec().getVersion());
                return;
            }

            if (!allowUpdate)
                throw new NopException(ERR_JOB_ALREADY_EXISTS).param(ARG_JOB_NAME, jobName);

            LOG.info("nop.job.update-job-spec:jobName={},version={},currentVer={}", spec.getJobName(),
                    spec.getVersion(), execution.getJobSpec().getJobSpec().getVersion());

            execution.setJobSpec(resolved);
            boolean active = execution.getTriggerStatus().isActive();
            execution.pauseTrigger().thenRun(() -> {
                synchronized (execution) { //NOSONAR
                    if (active) {
                        if (!execution.getTriggerStatus().isDone()) {
                            startTrigger(execution);
                        }
                    }
                }
            });

        }
    }

    JobExecution createJobExecution(ResolvedJobSpec jobSpec) {
        return createJobExecution(jobSpec, null);
    }

    JobExecution createJobExecution(ResolvedJobSpec jobSpec, ITriggerState triggerState) {
        JobExecution execution = new JobExecution();
        execution.setJobSpec(jobSpec);
        execution.setTriggerContext(createTriggerContext(jobSpec.getJobSpec(), triggerState));
        return execution;
    }

    protected ITriggerContext createTriggerContext(JobSpec jobSpec, ITriggerState triggerState) {
        TriggerContextImpl context;
        if (triggerState != null) {
            context = new TriggerContextImpl(triggerState);
        } else {
            context = new TriggerContextImpl();
        }
        context.setJobName(jobSpec.getJobName());
        context.setJobVersion(jobSpec.getVersion());
        context.setEpoch(epoch);
        context.setJobStore(jobStore);
        return context;
    }

    ResolvedJobSpec resolveJobSpec(JobSpec spec) {
        validateJobSpec(spec);

        IJobInvoker invoker = invokerFactory.apply(spec.getJobInvoker());

        ITrigger trigger;
        if (spec.getTriggerSpec() == null) {
            trigger = new OnceTrigger(-1);
        } else {
            trigger = TriggerBuilder.buildTrigger(spec.getTriggerSpec(), defaultCalendar);
        }

        return new ResolvedJobSpec(spec, invoker, trigger);
    }

    protected void validateJobSpec(JobSpec spec) {
        String jobName = spec.getJobName();
        if (!StringHelper.isValidClassName(jobName))
            throw new NopException(ERR_JOB_INVALID_JOB_NAME).param(ARG_JOB_NAME, jobName);

        String invoker = spec.getJobInvoker();
        if (StringHelper.isEmpty(invoker))
            throw new NopException(ERR_JOB_EMPTY_INVOKER_NAME).param(ARG_JOB_NAME, jobName);
    }

    @Override
    public boolean removeJob(String jobName) {
        checkActivated();
        LOG.info("nop.job.remove-job:jobName={},epoch={}", jobName, epoch);

        JobExecution execution = jobs.remove(jobName);
        if (execution != null) {
            synchronized (execution) {
                execution.cancelTrigger();
                return true;
            }
        }
        LOG.info("nop.job.remove-job-not-exists:jobName={},epoch={}", jobName, epoch);
        return false;
    }

    @Nullable
    @Override
    public TriggerStatus getTriggerStatus(String jobName) {
        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) {//NOSONAR
                return execution.getTriggerStatus();
            }
        }
        return null;
    }

    @Override
    public boolean resumeJob(String jobName) {
        checkActivated();

        LOG.info("nop.job.start-job:jobName={},epoch={}", jobName, epoch);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                if (execution.getTriggerStatus() == TriggerStatus.PAUSED) {
                    return startTrigger(execution);
                }
            }
        }
        LOG.info("nop.job.start-job-not-exists:jobName={},epoch={}", jobName, epoch);
        return false;
    }

    boolean startTrigger(JobExecution execution) {
        String jobName = execution.getJobSpec().getJobName();
        ITriggerExecution triggerExec = execution.getTriggerExecution();
        if (triggerExec == null) {
            return execution.startTrigger(executor, () -> {
                if (execution.getJobSpec().isRemoveWhenDone() && execution.getTriggerStatus().isDone()) {
                    jobs.remove(jobName, execution);
                }
            });
        }
        return true;
    }

    @Override
    public boolean pauseJob(String jobName) {
        checkActivated();
        LOG.info("nop.job.pause-job:jobName={},epoch={}", jobName, epoch);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                execution.pauseTrigger();
                return true;
            }
        }

        LOG.info("nop.job.pause-job-not-exists:jobName={},epoch={}", jobName, epoch);
        return false;
    }

    @Override
    public boolean cancelJob(String jobName) {
        checkActivated();
        LOG.info("nop.job.cancel-job:jobName={},epoch={}", jobName, epoch);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                execution.cancelTrigger();
                return true;
            }
        }

        LOG.info("nop.job.cancel-job-not-exists:jobName={},epoch={}", jobName, epoch);
        return false;
    }

    @Override
    public boolean fireNow(String jobName) {
        checkActivated();

        LOG.info("nop.job.fireNow:jobName={},epoch={}", jobName, epoch);

        JobExecution execution = jobs.get(jobName);
        if (execution == null) {
            LOG.info("nop.job.fireNow-not-exists:jobName={},epoch={}", jobName, epoch);
            return false;
        }

        synchronized (execution) { //NOSONAR
            ITriggerExecution trigger = execution.getTriggerExecution();
            if (trigger != null) {
                return trigger.fireNow();
            }

            execution.fireNow(executor);
        }

        return true;
    }
}