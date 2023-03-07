/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.job.api.spec.ITriggerSpec;

import java.util.HashMap;
import java.util.Map;

@DataBean
public class TriggerState implements ITriggerState {
    private String lastExecutionId;
    private long lastScheduleTime;
    private long lastExecutionStartTime;
    private long lastExecutionEndTime;
    private long executionCount = 1;
    private long nextScheduleTime;

    private long firstExecutionTime;

    private boolean recoverMode;
    private long recoverTime;

    private long minScheduleTime;
    private long maxScheduleTime;
    private long maxExecutionCount;
    private int maxFailedCount;

    private long completionTime;
    private long totalFailedCount;
    private long consecutiveFailedCount;
    private ErrorBean lastError;

    private String jobName;
    private long jobVersion;
    private long epoch;

    private TriggerStatus triggerStatus;
    private TriggerStatus nextTriggerStatus;
    private Map<String, Object> attributes;

    public TriggerState() {
    }

    public TriggerState(ITriggerSpec spec) {
        this.minScheduleTime = spec.getMinScheduleTime();
        this.maxScheduleTime = spec.getMaxScheduleTime();
        this.maxExecutionCount = spec.getMaxExecutionCount();
        this.maxFailedCount = spec.getMaxFailedCount();
    }

    public TriggerState(ITriggerState state) {
        this.lastExecutionId = state.getLastExecutionId();
        this.lastScheduleTime = state.getLastScheduleTime();
        this.lastExecutionStartTime = state.getLastExecutionStartTime();
        this.lastExecutionEndTime = state.getLastExecutionEndTime();
        this.executionCount = state.getExecutionCount();
        this.nextScheduleTime = state.getNextScheduleTime();

        this.firstExecutionTime = state.getFirstExecutionTime();

        this.minScheduleTime = state.getMinScheduleTime();
        this.maxScheduleTime = state.getMaxScheduleTime();
        this.maxExecutionCount = state.getMaxExecutionCount();
        this.maxFailedCount = state.getMaxFailedCount();

        this.completionTime = state.getCompletionTime();
        this.totalFailedCount = state.getTotalFailedCount();
        this.consecutiveFailedCount = state.getConsecutiveFailedCount();
        this.lastError = state.getLastError();

        this.jobName = state.getJobName();
        this.jobVersion = state.getJobVersion();
        this.epoch = state.getEpoch();
        this.triggerStatus = state.getTriggerStatus();

        this.nextTriggerStatus = state.getNextTriggerStatus();
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
    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
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
    public TriggerStatus getNextTriggerStatus() {
        return nextTriggerStatus;
    }

    public void setNextTriggerStatus(TriggerStatus nextTriggerStatus) {
        this.nextTriggerStatus = nextTriggerStatus;
    }

    @Override
    public TriggerStatus getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(TriggerStatus triggerStatus) {
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
    public long getLastExecutionStartTime() {
        return lastExecutionStartTime;
    }

    public void setLastExecutionStartTime(long lastExecutionStartTime) {
        this.lastExecutionStartTime = lastExecutionStartTime;
    }

    @Override
    public long getLastExecutionEndTime() {
        return lastExecutionEndTime;
    }

    public void setLastExecutionEndTime(long lastExecutionEndTime) {
        this.lastExecutionEndTime = lastExecutionEndTime;
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
    public long getFirstExecutionTime() {
        return firstExecutionTime;
    }

    public void setFirstExecutionTime(long firstExecutionTime) {
        this.firstExecutionTime = firstExecutionTime;
    }

    @Override
    public long getMinScheduleTime() {
        return minScheduleTime;
    }

    public void setMinScheduleTime(long minScheduleTime) {
        this.minScheduleTime = minScheduleTime;
    }

    @Override
    public long getMaxScheduleTime() {
        return maxScheduleTime;
    }

    public void setMaxScheduleTime(long maxScheduleTime) {
        this.maxScheduleTime = maxScheduleTime;
    }

    @Override
    public long getMaxExecutionCount() {
        return maxExecutionCount;
    }

    public void setMaxExecutionCount(long maxExecutionCount) {
        this.maxExecutionCount = maxExecutionCount;
    }

    public int getMaxFailedCount() {
        return maxFailedCount;
    }

    public void setMaxFailedCount(int maxFailedCount) {
        this.maxFailedCount = maxFailedCount;
    }

    @Override
    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    @Override
    public long getTotalFailedCount() {
        return totalFailedCount;
    }

    public void setTotalFailedCount(long totalFailedCount) {
        this.totalFailedCount = totalFailedCount;
    }

    @Override
    public long getConsecutiveFailedCount() {
        return consecutiveFailedCount;
    }

    public void setConsecutiveFailedCount(long consecutiveFailedCount) {
        this.consecutiveFailedCount = consecutiveFailedCount;
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
