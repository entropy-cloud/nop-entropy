/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.NopWfCoreConstants;

import java.sql.Timestamp;

public interface IWorkflowStepRecord {
    String getStepId();

    String getStepName();

    Integer getStatus();

    @JsonIgnore
    default boolean isActivated() {
        return getStatus() == NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED;
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
}