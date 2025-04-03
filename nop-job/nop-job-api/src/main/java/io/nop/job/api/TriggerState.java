/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;

import java.util.HashMap;
import java.util.Map;

@DataBean
public class TriggerState implements ITriggerState {
    private String lastExecutionId;
    private long lastScheduleTime;
    private long executionCount = 1;
    private long nextScheduleTime;

    private boolean recoverMode;
    private long recoverTime;

    private long execBeginTime;
    private long execEndTime;
    private long execFailCount;
    private long lastExecEndTime;
    private ErrorBean lastError;

    private String jobName;
    private long jobVersion;

    private int triggerStatus;
    private Map<String, Object> attributes;

    public TriggerState() {
    }

    public TriggerState(ITriggerState state) {
        this.lastExecutionId = state.getLastExecutionId();
        this.lastScheduleTime = state.getLastScheduleTime();
        this.executionCount = state.getExecutionCount();
        this.nextScheduleTime = state.getNextScheduleTime();

        this.execBeginTime = state.getExecBeginTime();
        this.execEndTime = state.getExecEndTime();
        this.execFailCount = state.getExecFailCount();
        this.lastError = state.getLastError();

        this.jobName = state.getJobName();
        this.jobVersion = state.getJobVersion();
        this.triggerStatus = state.getTriggerStatus();

        this.recoverMode = state.isRecoverMode();
        this.recoverTime = state.getRecoverTime();
        this.attributes = state.getAttributes() == null ? null : new HashMap<>(state.getAttributes());
    }

    @Override
    public long getJobVersion() {
        return jobVersion;
    }

    public void setJobVersion(long jobVersion) {
        this.jobVersion = jobVersion;
    }


    @Override
    public boolean isRecoverMode() {
        return recoverMode;
    }

    public void setRecoverMode(boolean recoverMode) {
        this.recoverMode = recoverMode;
    }

    @Override
    public long getRecoverTime() {
        return recoverTime;
    }

    public void setRecoverTime(long recoverTime) {
        this.recoverTime = recoverTime;
    }

    @Override
    public int getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(int triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

    @Override
    public String getLastExecutionId() {
        return lastExecutionId;
    }

    public void setLastExecutionId(String lastExecutionId) {
        this.lastExecutionId = lastExecutionId;
    }

    @Override
    public long getLastScheduleTime() {
        return lastScheduleTime;
    }

    public void setLastScheduleTime(long lastScheduleTime) {
        this.lastScheduleTime = lastScheduleTime;
    }

    @Override
    public long getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    @Override
    public long getNextScheduleTime() {
        return nextScheduleTime;
    }

    public void setNextScheduleTime(long nextScheduleTime) {
        this.nextScheduleTime = nextScheduleTime;
    }

    @Override
    public long getExecBeginTime() {
        return execBeginTime;
    }

    public void setExecBeginTime(long execBeginTime) {
        this.execBeginTime = execBeginTime;
    }

    @Override
    public long getExecEndTime() {
        return execEndTime;
    }

    public void setExecEndTime(long execEndTime) {
        this.execEndTime = execEndTime;
    }

    @Override
    public long getExecFailCount() {
        return execFailCount;
    }

    public void setExecFailCount(long execFailCount) {
        this.execFailCount = execFailCount;
    }

    @Override
    public long getLastExecEndTime() {
        return lastExecEndTime;
    }

    public void setLastExecEndTime(long lastExecEndTime) {
        this.lastExecEndTime = lastExecEndTime;
    }

    @Override
    public ErrorBean getLastError() {
        return lastError;
    }

    public void setLastError(ErrorBean lastError) {
        this.lastError = lastError;
    }

    @Override
    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
