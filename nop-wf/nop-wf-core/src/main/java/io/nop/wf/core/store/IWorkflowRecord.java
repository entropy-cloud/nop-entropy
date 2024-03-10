/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.store;

import io.nop.commons.lang.ITagSetSupport;
import io.nop.wf.api.actor.IWfActor;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public interface IWorkflowRecord extends ITagSetSupport {
    String getWfId();

    void setWfId(String wfId);

    String getWfName();

    Long getWfVersion();

    String getWorkScope();

    void setWorkScope(String workScope);

    Integer getStatus();

    void transitToStatus(int status);

    String getBizObjName();

    void setBizObjName(String bizObjName);

    String getBizObjId();

    void setBizObjId(String bizObjId);

    String getBizKey();

    void setBizKey(String bizKey);

    String getTitle();

    void setTitle(String title);

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

    void setLastOperateTime(Timestamp lastOperateTime);

    void setLastOperator(IWfActor operator);

    Set<String> getOnSignals();

    void setOnSignals(Set<String> onSignals);


    default boolean removeSignals(Set<String> signals) {
        Set<String> onSignals = getOnSignals();
        if (onSignals == null)
            return false;

        boolean b = onSignals.removeAll(signals);
        setOnSignals(onSignals);
        return b;
    }

    default boolean isAllSignalOn(Set<String> signals) {
        if (signals == null || signals.isEmpty())
            return true;

        Set<String> onSignals = getOnSignals();
        if (onSignals == null)
            return false;

        return onSignals.retainAll(signals);
    }

    default boolean isSignalOn(String signal) {
        Set<String> onSignals = getOnSignals();
        if (onSignals == null)
            return false;

        return onSignals.contains(signal);
    }

    default void addSignals(Set<String> signals) {
        Set<String> onSignals = getOnSignals();
        if (signals != null) {
            if (onSignals == null)
                onSignals = new LinkedHashSet<>();
            onSignals.addAll(signals);
            setOnSignals(onSignals);
        }
    }

    void addTag(String tag);

    void addTags(Collection<String> tags);

    void removeTag(String tag);
}
