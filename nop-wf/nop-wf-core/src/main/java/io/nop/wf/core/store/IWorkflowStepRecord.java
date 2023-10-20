/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;

import java.sql.Timestamp;

public interface IWorkflowStepRecord {
    String getStepId();

    String getStepName();

    Integer getStatus();

    String getWfId();

    void transitToStatus(int status);

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

    void setJoinValue(String targetStep, String joinValue);

    void setActor(IWfActor actor);

    void setOwner(IWfActor owner);

    void setFromAction(String fromAction);

    Timestamp getDueTime();

    void setDueTime(Timestamp dueTime);

    void setSubWfRef(WfReference wfRef);

    void setLastAction(String lastAction);

    void setFinishTime(Timestamp time);

    void setAppState(String appState);

    void setCaller(IWfActor caller);
}