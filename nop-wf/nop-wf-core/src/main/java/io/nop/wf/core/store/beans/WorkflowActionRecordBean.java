/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;

import java.sql.Timestamp;
import java.util.List;

@DataBean
public class WorkflowActionRecordBean implements IWorkflowActionRecord {
    private String sid;
    private String wfId;
    private String stepId;
    private String actionName;
    private Timestamp execTime;
    private String opinion;
    private String callerId;
    private String callerName;

    @Override
    public void setCaller(IWfActor caller) {
        if (caller != null) {
            setCallerId(caller.getActorId());
            setCallerName(caller.getActorName());
        } else {
            setCallerId(null);
            setCallerName(null);
        }
    }

    public void setStepRecord(IWorkflowStepRecord stepRecord) {
        setWfId(stepRecord.getWfId());
        setStepId(stepRecord.getStepId());
    }

    @Override
    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    @Override
    public String getWfId() {
        return wfId;
    }

    public void setWfId(String wfId) {
        this.wfId = wfId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public Timestamp getExecTime() {
        return execTime;
    }

    public void setExecTime(Timestamp execTime) {
        this.execTime = execTime;
    }

    @Override
    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    @Override
    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    @Override
    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }
}
