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
import io.nop.job.api.TriggerStatus;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerContext;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerContextImpl extends TriggerState implements ITriggerContext {
    private IJobScheduleStore jobStore;
    private boolean deactivated;

    public TriggerContextImpl() {
        this.setTriggerStatus(TriggerStatus.SCHEDULING);
    }

    public TriggerContextImpl(String jobName, ITriggerSpec spec) {
        this(spec);
        setJobName(jobName);
    }

    public TriggerContextImpl(ITriggerSpec spec) {
        super(spec);
        setTriggerStatus(TriggerStatus.SCHEDULING);
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
        this.setTriggerStatus(TriggerStatus.SCHEDULING);
        this.setNextScheduleTime(nextExecutionTime);
        onChange();
    }

    @Override
    public void onBeginExecute(long currentTime) {
        this.setTriggerStatus(TriggerStatus.EXECUTING);
        this.setLastExecutionStartTime(currentTime);
        if (getFirstExecutionTime() <= 0)
            this.setFirstExecutionTime(currentTime);
        this.setLastExecutionId(newExecutionId());
        onChange();
    }

    @Override
    public void onEndExecute(long currentTime) {
        // 如果是fireNow触发，则会设置nextTriggerStatus为triggerState此前的status。
        // 因此在PAUSED状态下由fireNow触发后仍然会恢复为PAUSED状态。
        TriggerStatus status = getNextTriggerStatus();
        if (status == null) {
            status = TriggerStatus.SCHEDULING;
        } else {
            setNextTriggerStatus(null);
        }

        // 只有错误恢复后的第一次执行是recoverMode
        this.setRecoverMode(false);

        this.setTriggerStatus(status);
        this.setLastScheduleTime(this.getNextScheduleTime());
        this.setLastExecutionEndTime(currentTime);
        this.setExecutionCount(getExecutionCount() + 1);
        onChange();
    }

    @Override
    public void onCompleted(long currentTime) {
        if (isDone())
            return;

        this.setTriggerStatus(TriggerStatus.COMPLETED);
        this.setCompletionTime(currentTime);
        onChange();
    }

    @Override
    public void onException(long currentTime, ErrorBean error) {
        this.setConsecutiveFailedCount(getConsecutiveFailedCount() + 1);
        this.setTotalFailedCount(getTotalFailedCount() + 1);
        this.setLastError(error);

        if (!isDone()) {
            if (this.getMaxFailedCount() > 0 && this.getConsecutiveFailedCount() >= this.getMaxFailedCount()) {
                this.setTriggerStatus(TriggerStatus.ERROR);
                this.setCompletionTime(currentTime);
            }
        }
        onChange();
    }

    @Override
    public void onError(long currentTime, ErrorBean error) {
        this.setConsecutiveFailedCount(getConsecutiveFailedCount() + 1);
        this.setTotalFailedCount(getTotalFailedCount() + 1);
        this.setLastError(error);

        if (!isDone()) {
            this.setTriggerStatus(TriggerStatus.ERROR);
            this.setCompletionTime(currentTime);
        }
        onChange();
    }

    @Override
    public void onCancel(long currentTime) {
        if (isDone())
            return;

        this.setTriggerStatus(TriggerStatus.CANCELLED);
        this.setCompletionTime(currentTime);
        onChange();
    }

    @Override
    public void onPaused(long currentTime) {
        if (isDone())
            return;

        this.setTriggerStatus(TriggerStatus.PAUSED);
        onChange();
    }

    @Override
    public void onFireNow(long currentTime) {
        TriggerStatus status = getTriggerStatus();
        setNextTriggerStatus(status);
        setNextScheduleTime(currentTime);
        onChange();
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

    public void update(TriggerSpec spec) {
        this.setMaxExecutionCount(spec.getMaxExecutionCount());
        this.setMaxScheduleTime(spec.getMaxScheduleTime());
        this.setMinScheduleTime(spec.getMinScheduleTime());
        this.setMaxFailedCount(spec.getMaxFailedCount());
    }
}
