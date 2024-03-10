/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.NopWfCoreConstants;

import java.sql.Timestamp;
import java.util.Collection;

public interface IWorkflowStepRecord extends ITagSetSupport {
    String getStepId();

    String getStepName();

    String getAppState();

    Integer getStatus();

    @JsonIgnore
    default boolean isActivated() {
        return getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED
                && getStatus() < NopWfCoreConstants.WF_STEP_STATUS_COMPLETED;
    }

    @JsonIgnore
    default boolean isWaiting() {
        return getStatus() == NopWfCoreConstants.WF_STEP_STATUS_WAITING;
    }

    String getWfId();

    @JsonIgnore
    default boolean isHistory() {
        return getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND;
    }

    void transitToStatus(int status);

    Integer getVoteWeight();

    void setVoteWeight(Integer voteWeight);

    Double getExecOrder();

    void setExecOrder(Double execOrder);

    String getActorModelId();

    void setActorModelId(String actorModelId);

    String getActorType();

    String getActorId();

    String getActorDeptId();

    String getOwnerId();

    String getCallerId();

    String getAssignerId();

    Timestamp getCreateTime();

    Boolean getIsRead();

    void setIsRead(Boolean isRead);

    Timestamp getReadTime();

    void setReadTime(Timestamp dateTime);

    String getJoinGroup();

    void setJoinGroup(String joinGroup);

    void setActor(IWfActor actor);

    void setOwner(IWfActor owner);

    void setAssigner(IWfActor assigner);

    void setFromAction(String fromAction);

    String getFromAction();

    Timestamp getDueTime();

    void setDueTime(Timestamp dueTime);

    void setSubWfRef(WfReference wfRef);

    void setLastAction(String lastAction);

    void setFinishTime(Timestamp time);

    void setAppState(String appState);

    void setCaller(IWfActor caller);

    String getSubWfName();

    Long getSubWfVersion();

    String getSubWfId();

    void setSubWfResultStatus(Integer status);

    Integer getSubWfResultStatus();

    String getStepGroup();

    void setStepGroup(String stepGroup);

    void addTag(String tag);

    void addTags(Collection<String> tags);

    void removeTag(String tag);
}