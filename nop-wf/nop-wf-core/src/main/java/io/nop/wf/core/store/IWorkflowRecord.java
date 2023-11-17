/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.wf.api.actor.IWfActor;

import java.sql.Timestamp;

public interface IWorkflowRecord {
    String getWfId();

    void setWfId(String wfId);

    String getWfName();

    Long getWfVersion();

    Integer getStatus();

    void transitToStatus(int status);

    String getBizObjName();

    void setBizObjName(String bizObjName);

    String getBizObjId();

    void setBizObjId(String bizObjId);

    Timestamp getStartTime();

    void setStartTime(Timestamp startTime);

    Timestamp getCreateTime();

    String getCreatedBy();

    String getManagerType();

    String getManagerId();

    String getManagerDeptId();

    String getStarterId();

    String getCreaterId();

    void setStarter(IWfActor starter);

    void setManager(IWfActor actor);

    void setAppState(String appState);

    void setEndTime(Timestamp endTime);

    String getParentWfName();

    void setParentWfName(String parentWfName);

    void setParentWfVersion(Long parentWfVersion);

    void setParentWfId(String wfId);

    void setParentStepId(String stepId);

    Long getParentWfVersion();

    String getParentWfId();

    String getParentStepId();

    boolean willEnd();

    /**
     * 表姐为需要被结束
     */
    void markEnd();

    void setSuspendTime(Timestamp time);

    void setResumeTime(Timestamp time);

    void setSuspendCaller(IWfActor caller);

    void setResumeCaller(IWfActor caller);

    void setCanceller(IWfActor caller);
}
