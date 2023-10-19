package io.nop.wf.core.store.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowStepRecord;

import java.sql.Timestamp;

@DataBean
public class WorkflowStepRecordBean implements IWorkflowStepRecord {
    private String stepId;

    private String stepName;
    private Integer status;
    private String actorType;
    private String actorId;

    private String actorName;
    private String actorDeptId;
    private String ownerId;
    private String ownerName;
    private String callerId;
    private String callerName;
    private String assignerId;
    private String assignerName;
    private Timestamp createTime;
    private Boolean isRead;

    private Timestamp readTime;

    private String fromAction;

    private Timestamp dueTime;

    private String lastAction;
    private Timestamp finishTime;
    private String appState;

    private String subWfName;

    private String subWfVersion;

    private String subWfId;

    @Override
    public void transitToStatus(int status) {
        setStatus(status);
    }


    @Override
    public void setJoinValue(String targetStep, String joinValue) {

    }


    @Override
    public void setActor(IWfActor actor) {
        if (actor != null) {
            setActorType(actor.getType());
            setActorId(actor.getActorId());
            setActorName(actor.getName());
            setActorDeptId(actor.getDeptId());
        } else {
            setActorType(null);
            setActorId(null);
            setActorName(null);
            setActorDeptId(null);
        }
    }

    @Override
    public void setOwner(IWfActor owner) {
        if (owner != null) {
            setOwnerId(owner.getActorId());
            setOwnerName(owner.getName());
        } else {
            setOwnerId(null);
            setOwnerName(null);
        }
    }

    @Override
    public void setSubWfRef(WfReference wfRef) {
        if (wfRef != null) {
            setSubWfId(wfRef.getWfId());
            setSubWfVersion(wfRef.getWfVersion());
            setSubWfName(wfRef.getWfName());
        } else {
            setSubWfId(null);
            setSubWfVersion(null);
            setSubWfName(null);
        }
    }

    @Override
    public void setCaller(IWfActor caller) {
        if (caller != null) {
            setCallerId(caller.getActorId());
            setCallerName(caller.getName());
        } else {
            setCallerId(null);
            setCallerName(null);
        }
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    @Override
    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    @Override
    public String getActorDeptId() {
        return actorDeptId;
    }

    public void setActorDeptId(String actorDeptId) {
        this.actorDeptId = actorDeptId;
    }

    @Override
    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    @Override
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

    @Override
    public String getAssignerId() {
        return assignerId;
    }

    public void setAssignerId(String assignerId) {
        this.assignerId = assignerId;
    }

    public String getAssignerName() {
        return assignerName;
    }

    public void setAssignerName(String assignerName) {
        this.assignerName = assignerName;
    }

    @Override
    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean read) {
        isRead = read;
    }

    @Override
    public Timestamp getReadTime() {
        return readTime;
    }

    @Override
    public void setReadTime(Timestamp readTime) {
        this.readTime = readTime;
    }

    public String getFromAction() {
        return fromAction;
    }

    @Override
    public void setFromAction(String fromAction) {
        this.fromAction = fromAction;
    }

    @Override
    public Timestamp getDueTime() {
        return dueTime;
    }

    @Override
    public void setDueTime(Timestamp dueTime) {
        this.dueTime = dueTime;
    }

    public String getLastAction() {
        return lastAction;
    }

    @Override
    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public Timestamp getFinishTime() {
        return finishTime;
    }

    @Override
    public void setFinishTime(Timestamp finishTime) {
        this.finishTime = finishTime;
    }

    public String getAppState() {
        return appState;
    }

    @Override
    public void setAppState(String appState) {
        this.appState = appState;
    }

    public String getSubWfName() {
        return subWfName;
    }

    public void setSubWfName(String subWfName) {
        this.subWfName = subWfName;
    }

    public String getSubWfVersion() {
        return subWfVersion;
    }

    public void setSubWfVersion(String subWfVersion) {
        this.subWfVersion = subWfVersion;
    }

    public String getSubWfId() {
        return subWfId;
    }

    public void setSubWfId(String subWfId) {
        this.subWfId = subWfId;
    }
}
