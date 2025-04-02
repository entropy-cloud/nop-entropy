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
import io.nop.job.api.IJobScheduleStore;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.TriggerState;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.NopJobCoreConstants;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerContextImpl extends TriggerState implements ITriggerContext {
    private IJobScheduleStore jobStore;
    private boolean deactivated;
    private long maxExecutionCount;
    private long minScheduleTime;
    private long maxScheduleTime;
    private long maxFailedCount;

    public TriggerContextImpl() {
        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
    }

    public TriggerContextImpl(String jobName, ITriggerSpec spec) {
        this(spec);
        setJobName(jobName);
    }

    public TriggerContextImpl(ITriggerSpec spec) {
        update(spec);
        setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING);
    }

    public TriggerContextImpl(ITriggerState state) {
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
        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_WAITING);
        this.setNextScheduleTime(nextExecutionTime);
        onChange();
    }

    @Override
    public void onBeginExecute(long currentTime) {
        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_SUSPENDED);
        this.setExecBeginTime(currentTime);
        this.setLastExecutionId(newExecutionId());
        onChange();
    }

    @Override
    public void onEndExecute(long currentTime) {
        // 如果是fireNow触发，则会设置nextTriggerStatus为triggerState此前的status。
        // 因此在PAUSED状态下由fireNow触发后仍然会恢复为PAUSED状态。
        int status = NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING;

        // 只有错误恢复后的第一次执行是recoverMode
        this.setRecoverMode(false);

        this.setTriggerStatus(status);
        this.setLastScheduleTime(this.getNextScheduleTime());
        this.setExecEndTime(currentTime);
        this.setExecutionCount(getExecutionCount() + 1);
        onChange();
    }

    @Override
    public void onCompleted(long currentTime) {
        if (isJobFinished())
            return;

        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED);
        this.setExecBeginTime(currentTime);
        onChange();
    }

    @Override
    public void onException(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setLastError(error);

        if (!isJobFinished()) {
            if (this.getMaxFailedCount() > 0 && this.getExecFailCount() >= this.getMaxFailedCount()) {
                this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FAILED);
                this.setExecBeginTime(currentTime);
            }
        }
        onChange();
    }

    @Override
    public void onError(long currentTime, ErrorBean error) {
        this.setExecFailCount(getExecFailCount() + 1);
        this.setLastError(error);

        if (!isJobFinished()) {
            this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FAILED);
            this.setExecBeginTime(currentTime);
        }
        onChange();
    }

    @Override
    public void onCancel(long currentTime) {
        if (isJobFinished())
            return;

        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_KILLED);
        this.setExecBeginTime(currentTime);
        onChange();
    }

    @Override
    public void onPaused(long currentTime) {
        if (isJobFinished())
            return;

        this.setTriggerStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_SUSPENDED);
        onChange();
    }

    @Override
    public void onBeginFireNow(long currentTime) {

    }

    @Override
    public void onEndFireNow(long currentTime) {

    }

    protected String newExecutionId() {
        return StringHelper.generateUUID();
    }

    protected void onChange() {
        if (jobStore != null && !deactivated) {
            jobStore.saveTriggerState(this);
        }
    }

    @Override
    public void deactivate() {
        this.deactivated = true;
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
