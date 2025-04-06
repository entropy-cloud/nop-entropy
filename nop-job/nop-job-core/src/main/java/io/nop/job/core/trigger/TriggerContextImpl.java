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
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.NopJobCoreConstants;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerContextImpl extends JobInstanceState implements ITriggerContext {
    private IJobScheduleStore jobStore;
    private long maxExecutionCount;
    private long minScheduleTime;
    private long maxScheduleTime;
    private long maxFailedCount;

    public TriggerContextImpl(String jobName, ITriggerSpec spec) {
        update(spec);
        setJobName(jobName);
        setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_UNKNOWN);
    }

    public TriggerContextImpl(IJobInstanceState state) {
        super(state);
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
    }

    @Override
    public void onInstanceBeginExecute(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
        this.setExecBeginTime(currentTime);
        this.setExecCount(getExecCount() + 1);
        onChange();
    }

    @Override
    public void onInstanceSuccess(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_SUCCESS);
        this.setExecEndTime(currentTime);
        this.setExecFailCount(0);
        onChange();
    }

    @Override
    public void onBeginFireNow(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
        this.setExecBeginTime(currentTime);
        this.setExecCount(getExecCount() + 1);
        onChange();
    }

    @Override
    public void onEndFireNow(long currentTime) {
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_SUCCESS);
        this.setExecEndTime(currentTime);
        this.setExecFailCount(0);
        onChange();
    }

    @Override
    public void onJobFinished(long currentTime) {
        if (isJobFinished())
            return;

        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED);
        this.setExecBeginTime(currentTime);
        onChange();
    }

    @Override
    public void onInstanceFailed(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setExecEndTime(currentTime);
        this.setLastError(error);

        if (!isJobFinished()) {
            if (this.getMaxFailedCount() > 0 && this.getExecFailCount() >= this.getMaxFailedCount()) {
                this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FAILED);
            }
        }
        onChange();
    }

    @Override
    public void onJobFailed(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setLastError(error);

        if (!isJobFinished()) {
            this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FAILED);
            this.setExecBeginTime(currentTime);
        }
        onChange();
    }

    @Override
    public void onInstanceCancelled(long currentTime) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_EXEC_CANCELLED);
        this.onChange();
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
    }

    @Override
    public void onJobKilled(long currentTime) {
        this.setExecEndTime(currentTime);
        this.setInstanceStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_KILLED);
        this.onChange();
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
