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
import io.nop.job.core.ITriggerExecutor;
import io.nop.job.core.NopJobCoreConstants;
import io.nop.job.core.trigger.OnceTrigger;
import io.nop.job.core.trigger.TriggerBuilder;
import io.nop.job.core.trigger.TriggerContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.job.api.JobApiErrors.ARG_JOB_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_ALREADY_EXISTS;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DEACTIVATED_SCHEDULER_NOT_ALLOW_OPERATION;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_EMPTY_INVOKER_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVALID_JOB_NAME;

public class DefaultJobScheduler implements IJobScheduler {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobScheduler.class);

    private final Map<String, JobExecution> jobs = new ConcurrentHashMap<>();

    private final ITriggerExecutor executor;

    private final Function<String, IJobInvoker> invokerFactory;

    private IJobScheduleStore jobStore;

    private ICalendar defaultCalendar;

    private ICancellable cancelFetch; //NOSONAR

    private volatile boolean deactivated;


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
    public synchronized void activate() {
        deactivate();

        deactivated = false;

        if (jobStore != null) {
            cancelFetch = jobStore.fetchPersistJobs(jobDetail -> {
                try {
                    addJob(jobDetail.getJobSpec(), jobDetail.getTriggerState(), true);
                } catch (Exception e) {
                    LOG.error("nop.err.job.process-schedule-event-fail", e);
                }
            });
        }
    }

    @Override
    public synchronized void deactivate() {
        deactivated = true;
        ICancellable cancelFetch = this.cancelFetch;
        if (cancelFetch != null) {
            cancelFetch.cancel();
            this.cancelFetch = null;
        }

        Iterator<JobExecution> it = jobs.values().iterator();
        while (it.hasNext()) {
            JobExecution execution = it.next();
            it.remove();
            execution.deactivate();
        }
    }

    void checkActivated() {
        if (deactivated)
            throw new NopException(ERR_JOB_DEACTIVATED_SCHEDULER_NOT_ALLOW_OPERATION);
    }

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
        } else {
            if (jobStore != null)
                return jobStore.loadJobDetail(jobName);
        }
        return null;
    }

    @Override
    public void addJob(JobSpec spec, boolean allowUpdate) {
        addJob(spec, null, allowUpdate);
    }

    private void addJob(JobSpec spec, ITriggerState triggerState, boolean allowUpdate) {
        checkActivated();
        LOG.info("nop.job.add-job:jobName={}", spec.getJobName());

        ResolvedJobSpec resolved = resolveJobSpec(spec);
        String jobName = resolved.getJobName();

        JobExecution execution = jobs.computeIfAbsent(jobName, k -> createJobExecution(resolved, triggerState));
        boolean created = execution.getJobSpec() == resolved;

        if (created) {
            synchronized (execution) { //NOSONAR
                tryStartTrigger(execution);
            }
        } else {
            updateJob(execution, resolved, allowUpdate);
        }
    }

    private void updateJob(JobExecution execution, ResolvedJobSpec resolved, boolean allowUpdate) {
        String jobName = resolved.getJobName();
        JobSpec spec = resolved.getJobSpec();

        synchronized (execution) { //NOSONAR
            // 忽略旧版本
            if (execution.getJobSpec().getVersion() > spec.getVersion()) {
                LOG.info("nop.job.ignore-obsolete-job-spec:jobName={},version={},currentVer={}", spec.getJobName(),
                        spec.getVersion(), execution.getJobSpec().getVersion());
                return;
            }

            if (!allowUpdate)
                throw new NopException(ERR_JOB_ALREADY_EXISTS).param(ARG_JOB_NAME, jobName);

            LOG.info("nop.job.update-job-spec:jobName={},version={},currentVer={}", spec.getJobName(),
                    spec.getVersion(), execution.getJobSpec().getVersion());

            execution.setJobSpec(resolved);
            // 如果当前有实例正在运行，可能看不见修改后的JobSpec
            tryStartTrigger(execution);
        }
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
        LOG.info("nop.job.remove-job:jobName={}", jobName);

        JobExecution execution = jobs.remove(jobName);
        if (execution != null) {
            synchronized (execution) {
                execution.deactivate();
                return true;
            }
        }
        LOG.info("nop.job.remove-job-not-exists:jobName={}", jobName);
        return false;
    }

    @Override
    public int getTriggerStatus(String jobName) {
        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) {//NOSONAR
                return execution.getTriggerStatus();
            }
        }
        return NopJobCoreConstants.JOB_INSTANCE_STATUS_CREATED;
    }

    @Override
    public boolean resumeJob(String jobName) {
        checkActivated();

        LOG.info("nop.job.start-job:jobName={}", jobName);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                if (execution.isClosed())
                    return false;

                if (execution.getTriggerStatus() == NopJobCoreConstants.JOB_INSTANCE_STATUS_SUSPENDED) {
                    tryStartTrigger(execution);
                    return true;
                }
            }
        }
        LOG.info("nop.job.start-job-not-exists:jobName={}", jobName);
        return false;
    }

    void tryStartTrigger(JobExecution execution) {
        boolean active = !execution.isClosed() && execution.isActive();
        if (!active)
            return;

        execution.startTrigger(executor, () -> {
            onTriggerCompleted(execution);
        });
    }

    private void onTriggerCompleted(JobExecution execution) {
        if (execution.getJobSpec().isRemoveWhenDone() && execution.isDone()) {
            if (jobs.remove(execution.getJobName(), execution))
                execution.deactivate();
        }
    }

    @Override
    public boolean pauseJob(String jobName) {
        checkActivated();
        LOG.info("nop.job.pause-job:jobName={}", jobName);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                if (execution.isClosed())
                    return true;

                execution.pauseTrigger();
                return true;
            }
        }

        LOG.info("nop.job.pause-job-not-exists:jobName={}", jobName);
        return false;
    }

    @Override
    public boolean cancelJob(String jobName) {
        checkActivated();
        LOG.info("nop.job.cancel-job:jobName={}", jobName);

        JobExecution execution = jobs.get(jobName);
        if (execution != null) {
            synchronized (execution) { //NOSONAR
                if (execution.isClosed())
                    return true;

                execution.cancelTrigger();
                return true;
            }
        }

        LOG.info("nop.job.cancel-job-not-exists:jobName={}", jobName);
        return false;
    }

    @Override
    public boolean fireNow(String jobName) {
        checkActivated();

        LOG.info("nop.job.fireNow:jobName={}", jobName);

        JobExecution execution = makeJobExecution(jobName);
        if (execution == null) {
            LOG.info("nop.job.fireNow-not-exists:jobName={}", jobName);
            return false;
        }

        synchronized (execution) { //NOSONAR
            if (execution.isClosed()) {
                LOG.info("nop.job.fireNow-execution-closed:jobName={}", jobName);
                return false;
            }

            execution.fireNow(executor, () -> onTriggerCompleted(execution));
        }

        return true;
    }

    synchronized JobExecution makeJobExecution(String jobName) {
        JobExecution execution = jobs.get(jobName);
        if (execution == null && jobStore != null) {
            JobDetail jobDetail = jobStore.loadJobDetail(jobName);
            if (jobDetail != null) {
                ResolvedJobSpec resolved = resolveJobSpec(jobDetail.getJobSpec());
                execution = jobs.computeIfAbsent(jobName, k -> createJobExecution(resolved, null));
            }
        }
        return execution;
    }
}