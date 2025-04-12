/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.util.StringHelper;
import io.nop.job.api.IJobInstanceState;
import io.nop.job.api.IJobScheduleStore;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerHook;
import io.nop.job.core.NopJobCoreConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerContextImpl extends JobInstanceState implements ITriggerContext {
    static final Logger LOG = LoggerFactory.getLogger(TriggerContextImpl.class);

    private IJobScheduleStore jobStore;
    private ITriggerHook triggerHook;
    private long maxExecutionCount;
    private long minScheduleTime;
    private long maxScheduleTime;
    private long maxFailedCount;
    private boolean scheduleEnabled;

    public TriggerContextImpl(String jobName, ITriggerSpec spec) {
        update(spec);
        setJobName(jobName);
        setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_UNKNOWN);
    }

    public TriggerContextImpl(IJobInstanceState state) {
        super(state);
    }

    public void setTriggerHook(ITriggerHook triggerHook) {
        this.triggerHook = triggerHook;
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }

    public IJobScheduleStore getJobStore() {
        return jobStore;
    }

    public void setJobStore(IJobScheduleStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void onSchedule(long currentTime, long nextExecutionTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_WAITING);
        this.setScheduledExecTime(nextExecutionTime);
        this.setExecBeginTime(-1);
        this.setExecEndTime(-1);
        this.setExecError(null);
        this.setLastInstanceId(getInstanceId());
        this.setInstanceId(newExecutionId());
        onChange();

        LOG.info("nop.job.on-schedule:jobName={},instanceId={}", getJobName(),
                getInstanceId());
        if (triggerHook != null) {
            triggerHook.onSchedule(currentTime, nextExecutionTime, this);
        }
    }

    @Override
    public void onInstanceBeginExecute(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
        this.setExecBeginTime(currentTime);
        this.setExecCount(getExecCount() + 1);
        onChange();

        LOG.debug("nop.job.on-instance-begin-execute:jobName={},instanceId={}", getJobName(),
                getInstanceId());
        if (triggerHook != null) {
            triggerHook.onInstanceBeginExecute(currentTime, this);
        }
    }

    @Override
    public void onInstanceSuccess(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_SUCCESS);
        this.setExecEndTime(currentTime);
        this.setExecFailCount(0);
        onChange();

        LOG.debug("nop.job.on-instance-success:jobName={},instanceId={},status={}", getJobName(),
                getInstanceId(), getInstanceStatus());

        if (triggerHook != null) {
            triggerHook.onInstanceSuccess(currentTime, this);
        }
    }

    @Override
    public void onBeginFireNow(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
        this.setExecBeginTime(currentTime);
        this.setExecCount(getExecCount() + 1);
        onChange();

        LOG.info("nop.job.begin-fire-now:jobName={},instanceId={}", getJobName(),
                getInstanceId());
        if (triggerHook != null) {
            triggerHook.onBeginFireNow(currentTime, this);
        }
    }

    @Override
    public void onEndFireNow(long currentTime, JobFireResult result, Throwable err) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_SUCCESS);
        this.setExecEndTime(currentTime);
        this.setExecFailCount(0);
        onChange();


        LOG.info("nop.job.end-fire-now:jobName={},instanceId={}", getJobName(),
                getInstanceId());
        if (triggerHook != null) {
            triggerHook.onEndFireNow(currentTime, result, err, this);
        }
    }

    @Override
    public void onJobFinished(long currentTime) {
        if (isJobFinished())
            return;

        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED);
        this.setExecBeginTime(currentTime);
        onChange();

        LOG.info("nop.job.on-job-finished:jobName={},instanceId={},status={}", getJobName(),
                getInstanceId(), getInstanceStatus());

        if (triggerHook != null) {
            triggerHook.onJobFinished(currentTime, this);
        }
    }

    @Override
    public void onInstanceFailed(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setExecEndTime(currentTime);
        this.setLastError(error);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_FAILED);

        onChange();

        LOG.info("nop.job.on-instance-failed:jobName={},instanceId={},status={},error={}", getJobName(),
                getInstanceId(), getInstanceStatus(), error);

        if (triggerHook != null) {
            triggerHook.onInstanceFailed(currentTime, error, this);
        }
    }

    @Override
    public void onJobFailed(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setLastError(error);

        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FAILED);
        this.setExecEndTime(currentTime);

        onChange();

        LOG.info("nop.job.on-job-failed:jobName={},instanceId={},status={},error={}", getJobName(),
                getInstanceId(), getInstanceStatus(), error);

        if (triggerHook != null) {
            triggerHook.onJobFailed(currentTime, error, this);
        }
    }

    @Override
    public void onInstanceCancelled(long currentTime) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_CANCELLED);
        this.onChange();

        LOG.info("nop.job.on-instance-cancelled:jobName={},instanceId={},status={}", getJobName(),
                getInstanceId(), getInstanceStatus());

        if (triggerHook != null) {
            triggerHook.onInstanceCancelled(currentTime, this);
        }
    }

    @Override
    public void onInstanceTimeout(long currentTime) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_TIMEOUT);
        this.onChange();
    }

    @Override
    public void onJobSuspended(long currentTime) {
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_SUSPENDED);
        this.onChange();

        LOG.info("nop.job.on-job-suspended:jobName={},instanceId={},status={}", getJobName(),
                getInstanceId(), getInstanceStatus());

        if (triggerHook != null) {
            triggerHook.onJobSuspended(currentTime, this);
        }
    }

    @Override
    public void onJobKilled(long currentTime) {
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_KILLED);
        this.onChange();

        LOG.info("nop.job.on-job-killed:jobName={},instanceId={},status={}", getJobName(),
                getInstanceId(), getInstanceStatus());

        if (triggerHook != null) {
            triggerHook.onJobKilled(currentTime, this);
        }
    }

    protected String newExecutionId() {
        return StringHelper.generateUUID();
    }

    protected void onChange() {
        if (jobStore != null) {
            jobStore.saveInstanceState(this);
        }
    }

    @Override
    public long getMaxExecutionCount() {
        return maxExecutionCount;
    }

    @Override
    public void setMaxExecutionCount(long maxExecutionCount) {
        this.maxExecutionCount = maxExecutionCount;
    }

    @Override
    public long getMinScheduleTime() {
        return minScheduleTime;
    }

    @Override
    public void setMinScheduleTime(long minScheduleTime) {
        this.minScheduleTime = minScheduleTime;
    }

    @Override
    public long getMaxScheduleTime() {
        return maxScheduleTime;
    }

    @Override
    public void setMaxScheduleTime(long maxScheduleTime) {
        this.maxScheduleTime = maxScheduleTime;
    }

    @Override
    public long getMaxFailedCount() {
        return maxFailedCount;
    }

    public void setMaxFailedCount(long maxFailedCount) {
        this.maxFailedCount = maxFailedCount;
    }

    public void update(ITriggerSpec spec) {
        this.setMaxExecutionCount(spec.getMaxExecutionCount());
        this.setMaxScheduleTime(spec.getMaxScheduleTime());
        this.setMinScheduleTime(spec.getMinScheduleTime());
        this.setMaxFailedCount(spec.getMaxFailedCount());
    }
}
