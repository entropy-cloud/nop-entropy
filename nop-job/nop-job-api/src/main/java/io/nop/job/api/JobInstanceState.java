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
public class JobInstanceState implements IJobInstanceState {
    private String instanceId;
    private String jobDefId;
    private String jobGroup;
    private String jobName;
    private long jobVersion;
    private Map<String, Object> jobParams;


    private long scheduledExecTime;

    private long execCount = 1;

    private long execBeginTime;
    private long execEndTime;
    private long execFailCount;
    private ErrorBean execError;


    private int instanceStatus;

    private String lastInstanceId;

    private boolean onceTask;
    private boolean manualFire;
    private String firedBy;
    private long changeVersion;

    private Map<String, Object> attributes;

    public JobInstanceState() {
    }

    public JobInstanceState(IJobInstanceState state) {
        this.instanceId = state.getInstanceId();

        this.jobGroup = state.getJobGroup();
        this.jobName = state.getJobName();
        this.jobVersion = state.getJobVersion();
        this.jobParams = state.getJobParams();

        this.execCount = state.getExecCount();
        this.scheduledExecTime = state.getScheduledExecTime();
        this.execBeginTime = state.getExecBeginTime();
        this.execEndTime = state.getExecEndTime();
        this.execFailCount = state.getExecFailCount();
        this.execError = state.getExecError();

        this.instanceStatus = state.getInstanceStatus();

        this.onceTask = state.isOnceTask();
        this.manualFire = state.isManualFire();
        this.firedBy = state.getFiredBy();
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
    public int getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(int instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String getJobDefId() {
        return jobDefId;
    }

    public void setJobDefId(String jobDefId) {
        this.jobDefId = jobDefId;
    }

    @Override
    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    @Override
    public Map<String, Object> getJobParams() {
        return jobParams;
    }

    public void setJobParams(Map<String, Object> jobParams) {
        this.jobParams = jobParams;
    }

    @Override
    public boolean isOnceTask() {
        return onceTask;
    }

    public void setOnceTask(boolean onceTask) {
        this.onceTask = onceTask;
    }

    @Override
    public boolean isManualFire() {
        return manualFire;
    }

    public void setManualFire(boolean manualFire) {
        this.manualFire = manualFire;
    }

    @Override
    public String getFiredBy() {
        return firedBy;
    }

    public void setFiredBy(String firedBy) {
        this.firedBy = firedBy;
    }

    @Override
    public String getLastInstanceId() {
        return lastInstanceId;
    }

    public void setLastInstanceId(String lastInstanceId) {
        this.lastInstanceId = lastInstanceId;
    }

    @Override
    public long getScheduledExecTime() {
        return scheduledExecTime;
    }

    public void setScheduledExecTime(long scheduledExecTime) {
        this.scheduledExecTime = scheduledExecTime;
    }

    public void setExecError(ErrorBean execError) {
        this.execError = execError;
    }

    @Override
    public long getExecCount() {
        return execCount;
    }

    public void setExecCount(long execCount) {
        this.execCount = execCount;
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
    public long getChangeVersion() {
        return changeVersion;
    }

    public void setChangeVersion(long changeVersion) {
        this.changeVersion = changeVersion;
    }

    @Override
    public ErrorBean getExecError() {
        return execError;
    }

    public void setLastError(ErrorBean lastError) {
        this.execError = lastError;
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
