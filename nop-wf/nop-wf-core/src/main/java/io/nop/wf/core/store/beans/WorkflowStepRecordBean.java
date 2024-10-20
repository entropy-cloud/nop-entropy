/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.store.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@DataBean
public class WorkflowStepRecordBean implements IWorkflowStepRecord {
    private String stepId;

    private String stepName;

    private String displayName;

    private String wfId;
    private Integer status;

    private String actorModelId;
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

    private String joinGroup;

    private Integer execOrder;

    private Integer execCount;

    private Integer voteWeight;
    private String appState;

    private String subWfName;

    private Long subWfVersion;

    private String subWfId;

    private Integer subWfResultStatus;

    private String execGroup;

    private Set<String> tagSet;

    private KeyedList<WorkflowActionRecordBean> actions = new KeyedList<>(WorkflowActionRecordBean::getSid);

    private List<WorkflowStepLinkBean> nextStepLinks;

    public WorkflowStepLinkBean addNextStepLink(String nextStepId) {
        WorkflowStepLinkBean link = new WorkflowStepLinkBean();
        link.setWfId(getWfId());
        link.setStepId(getStepId());
        link.setNextStepId(nextStepId);

        if (nextStepLinks == null)
            this.nextStepLinks = new ArrayList<>();
        this.nextStepLinks.add(link);
        return link;
    }

    public WorkflowActionRecordBean getAction(String actionId) {
        return actions.getByKey(actionId);
    }

    public List<WorkflowActionRecordBean> getActions() {
        return actions;
    }

    public void setActions(List<WorkflowActionRecordBean> actions) {
        this.actions = KeyedList.fromList(actions, WorkflowActionRecordBean::getSid);
    }

    public void addAction(WorkflowActionRecordBean action) {
        if (action.getSid() == null)
            action.setSid(StringHelper.generateUUID());
        this.actions.add(action);
    }

    @Override
    public void transitToStatus(int status) {
        setStatus(status);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public Integer getExecOrder() {
        return execOrder;
    }

    @Override
    public void setExecOrder(Integer execOrder) {
        this.execOrder = execOrder;
    }

    @Override
    public Integer getExecCount() {
        return execCount;
    }

    @Override
    public void setExecCount(Integer execCount) {
        this.execCount = execCount;
    }

    @Override
    public Integer getVoteWeight() {
        return voteWeight;
    }

    @Override
    public void setVoteWeight(Integer voteWeight) {
        this.voteWeight = voteWeight;
    }

    @Override
    public String getJoinGroup() {
        return joinGroup;
    }

    @Override
    public void setJoinGroup(String joinGroup) {
        this.joinGroup = joinGroup;
    }

    public String getActorModelId() {
        return actorModelId;
    }

    public void setActorModelId(String actorModelId) {
        this.actorModelId = actorModelId;
    }

    @Override
    public String getExecGroup() {
        return execGroup;
    }

    @Override
    public void setExecGroup(String execGroup) {
        this.execGroup = execGroup;
    }

    public void setWfRecord(IWorkflowRecord wfRecord) {
        if (wfRecord.getWfId() == null) {
            wfRecord.setWfId(StringHelper.generateUUID());
        }
        setWfId(wfRecord.getWfId());
    }

    @Override
    public void setActor(IWfActor actor) {
        if (actor != null) {
            setActorType(actor.getActorType());
            setActorId(actor.getActorId());
            setActorName(actor.getActorName());
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
            setOwnerName(owner.getActorName());
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
            setCallerName(caller.getActorName());
        } else {
            setCallerId(null);
            setCallerName(null);
        }
    }

    @Override
    public void setAssigner(IWfActor assigner) {
        if(assigner != null){
            setAssignerId(assigner.getActorId());
            setAssignerName(assigner.getActorName());
        }else{
            setAssignerId(null);
            setAssignerName(null);
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

    public Long getSubWfVersion() {
        return subWfVersion;
    }

    public void setSubWfVersion(Long subWfVersion) {
        this.subWfVersion = subWfVersion;
    }

    public String getSubWfId() {
        return subWfId;
    }

    public void setSubWfId(String subWfId) {
        this.subWfId = subWfId;
    }

    @Override
    public String getWfId() {
        return wfId;
    }

    public void setWfId(String wfId) {
        this.wfId = wfId;
    }

    @Override
    public Integer getSubWfResultStatus() {
        return subWfResultStatus;
    }

    @Override
    public void setSubWfResultStatus(Integer subWfResultStatus) {
        this.subWfResultStatus = subWfResultStatus;
    }

    public List<WorkflowStepLinkBean> getNextStepLinks() {
        return nextStepLinks;
    }

    public void setNextStepLinks(List<WorkflowStepLinkBean> nextStepLinks) {
        this.nextStepLinks = nextStepLinks;
    }


    @Override
    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        this.tagSet = tagSet;
    }

    @Override
    public void addTag(String tag) {
        this.tagSet = TagsHelper.add(getTagSet(), tag);
    }

    @Override
    public void addTags(Collection<String> tags) {
        this.tagSet = TagsHelper.merge(getTagSet(), tags);
    }

    @Override
    public void removeTag(String tag) {
        this.tagSet = TagsHelper.remove(getTagSet(), tag);
    }
}
