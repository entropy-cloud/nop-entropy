/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.store.IWorkflowRecord;

import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_DUPLICATE_STEP_ID;

@DataBean
public class WorkflowRecordBean implements IWorkflowRecord {
    private String wfId;
    private String wfName;
    private Long wfVersion;
    private Integer status;

    private String bizObjName;
    private String bizObjId;
    private Timestamp startTime;
    private Timestamp createTime;

    private String createdBy;

    private String managerType;
    private String managerId;
    private String managerName;
    private String managerDeptId;
    private String starterId;
    private String starterName;

    private String starterDeptId;
    private String appState;
    private Timestamp endTime;

    private String parentWfName;
    private String parentWfId;
    private Long parentWfVersion;
    private String parentStepId;
    private Timestamp suspendTime;
    private Timestamp resumeTime;

    private String callerId;
    private String callerName;

    private String cancellerId;
    private String cancellerName;

    private String suspenderId;
    private String suspenderName;

    private String createrId;

    private boolean willEnd;

    private Map<String, Object> globalVars;

    private Map<String, Object> outputVars;

    private KeyedList<WorkflowStepRecordBean> steps = new KeyedList<>(WorkflowStepRecordBean::getStepId);

    public List<WorkflowStepRecordBean> getSteps() {
        return steps;
    }

    public void setSteps(List<WorkflowStepRecordBean> steps) {
        this.steps = KeyedList.fromList(steps, WorkflowStepRecordBean::getStepId);
    }

    public WorkflowStepRecordBean getStep(String stepId) {
        return steps.getByKey(stepId);
    }

    public void addStep(WorkflowStepRecordBean step) {
        if (steps.containsKey(step.getStepId()))
            throw new NopException(ERR_WF_DUPLICATE_STEP_ID)
                    .param(ARG_STEP_ID, step.getStepId()).param(ARG_STEP_NAME, step.getStepName());
        this.steps.add(step);
    }

    @Override
    public void transitToStatus(int status) {
        this.setStatus(status);
    }

    @Override
    public void setStarter(IWfActor starter) {
        if (starter != null) {
            setStarterId(starter.getActorId());
            setStarterName(starter.getActorName());
            setStarterDeptId(starter.getDeptId());
        } else {
            setStarterId(null);
            setStarterName(null);
            setStarterDeptId(null);
        }
    }

    @Override
    public void setManager(IWfActor actor) {
        if (actor != null) {
            setManagerType(actor.getActorType());
            setManagerId(actor.getActorId());
            setManagerName(actor.getActorName());
            setManagerDeptId(actor.getDeptId());
        } else {
            setManagerType(null);
            setManagerId(null);
            setManagerName(null);
            setManagerDeptId(null);
        }
    }

    @Override
    public boolean willEnd() {
        return willEnd;
    }

    @Override
    public void markEnd() {
        willEnd = true;
    }

    @Override
    public void setSuspendCaller(IWfActor caller) {
        if (caller != null) {
            setSuspenderId(caller.getActorId());
            setSuspenderName(caller.getActorName());
        } else {
            setSuspenderId(null);
            setSuspenderName(null);
        }
    }

    @Override
    public void setResumeCaller(IWfActor caller) {

    }

    @Override
    public void setCanceller(IWfActor caller) {
        if (caller != null) {
            setCancellerId(caller.getActorId());
            setCancellerName(caller.getActorName());
        } else {
            setCancellerId(null);
            setCancellerName(null);
        }
    }

    @Override
    public String getParentWfName() {
        return parentWfName;
    }

    public void setParentWfName(String parentWfName) {
        this.parentWfName = parentWfName;
    }

    @Override
    public String getWfId() {
        return wfId;
    }

    public void setWfId(String wfId) {
        this.wfId = wfId;
    }

    @Override
    public String getWfName() {
        return wfName;
    }

    public void setWfName(String wfName) {
        this.wfName = wfName;
    }

    @Override
    public Long getWfVersion() {
        return wfVersion;
    }

    public void setWfVersion(Long wfVersion) {
        this.wfVersion = wfVersion;
    }

    @Override
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String getBizObjName() {
        return bizObjName;
    }

    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    @Override
    public String getBizObjId() {
        return bizObjId;
    }

    public void setBizObjId(String bizObjId) {
        this.bizObjId = bizObjId;
    }

    @Override
    public Timestamp getStartTime() {
        return startTime;
    }

    @Override
    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    @Override
    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String getManagerType() {
        return managerType;
    }

    public void setManagerType(String managerType) {
        this.managerType = managerType;
    }

    @Override
    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    @Override
    public String getManagerDeptId() {
        return managerDeptId;
    }

    public void setManagerDeptId(String managerDeptId) {
        this.managerDeptId = managerDeptId;
    }

    @Override
    public String getStarterId() {
        return starterId;
    }

    public void setStarterId(String starterId) {
        this.starterId = starterId;
    }

    public String getStarterName() {
        return starterName;
    }

    public void setStarterName(String starterName) {
        this.starterName = starterName;
    }

    public String getAppState() {
        return appState;
    }

    @Override
    public void setAppState(String appState) {
        this.appState = appState;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    @Override
    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    @Override
    public String getParentWfId() {
        return parentWfId;
    }

    public void setParentWfId(String parentWfId) {
        this.parentWfId = parentWfId;
    }

    @Override
    public Long getParentWfVersion() {
        return parentWfVersion;
    }

    public void setParentWfVersion(Long parentWfVersion) {
        this.parentWfVersion = parentWfVersion;
    }

    @Override
    public String getParentStepId() {
        return parentStepId;
    }

    public void setParentStepId(String parentStepId) {
        this.parentStepId = parentStepId;
    }

    public Timestamp getSuspendTime() {
        return suspendTime;
    }

    @Override
    public void setSuspendTime(Timestamp suspendTime) {
        this.suspendTime = suspendTime;
    }

    public Timestamp getResumeTime() {
        return resumeTime;
    }

    @Override
    public void setResumeTime(Timestamp resumeTime) {
        this.resumeTime = resumeTime;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public String getCancellerId() {
        return cancellerId;
    }

    public void setCancellerId(String cancellerId) {
        this.cancellerId = cancellerId;
    }

    public String getCancellerName() {
        return cancellerName;
    }

    public void setCancellerName(String cancellerName) {
        this.cancellerName = cancellerName;
    }

    public String getSuspenderId() {
        return suspenderId;
    }

    public void setSuspenderId(String suspenderId) {
        this.suspenderId = suspenderId;
    }

    public String getSuspenderName() {
        return suspenderName;
    }

    public void setSuspenderName(String suspenderName) {
        this.suspenderName = suspenderName;
    }

    @Override
    public String getCreaterId() {
        return createrId;
    }

    public void setCreaterId(String createrId) {
        this.createrId = createrId;
    }

    public Map<String, Object> getGlobalVars() {
        return globalVars;
    }

    public void setGlobalVars(Map<String, Object> globalVars) {
        this.globalVars = globalVars;
    }

    @JsonIgnore
    public Set<String> getOnSignals() {
        if (globalVars == null)
            return null;

        Set<String> signals = new LinkedHashSet<>();
        globalVars.forEach((name, value) -> {
            if (name.startsWith(NopWfCoreConstants.SIGNAL_PREFIX)) {
                if (ConvertHelper.toTruthy(value)) {
                    signals.add(name.substring(NopWfCoreConstants.SIGNAL_PREFIX.length()));
                }
            }
        });
        return signals;
    }

    public boolean removeSignals(Set<String> signals) {
        if (globalVars == null)
            return false;

        boolean ret = false;
        if (signals != null) {
            for (String signal : signals) {
                if (globalVars.remove(NopWfCoreConstants.SIGNAL_PREFIX + signal) != null)
                    ret = true;
            }
        }

        return ret;
    }

    public boolean isAllSignalOn(Set<String> signals) {
        if (signals == null || signals.isEmpty())
            return true;

        if (globalVars == null)
            return false;

        for (String signal : signals) {
            boolean b = ConvertHelper.toTruthy(globalVars.get(NopWfCoreConstants.SIGNAL_PREFIX + signal));
            if (!b)
                return false;
        }
        return true;
    }

    public boolean isSignalOn(String signal) {
        if (globalVars == null)
            return false;

        return ConvertHelper.toTruthy(globalVars.get(NopWfCoreConstants.SIGNAL_PREFIX + signal));
    }

    public void addSignals(Set<String> signals) {
        if (signals != null) {
            for (String signal : signals) {
                globalVars.put(NopWfCoreConstants.SIGNAL_PREFIX + signal, Boolean.TRUE);
            }
        }
    }

    public Map<String, Object> getOutputVars() {
        return outputVars;
    }

    public void setOutputVars(Map<String, Object> outputVars) {
        this.outputVars = outputVars;
    }
}
