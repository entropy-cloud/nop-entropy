/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

import io.nop.wf.api.actor.WfActorCandidatesBean;

/**
 * @author canonical_entropy@163.com
 */
public class WorkflowTransitionTarget {
    /**
     * 转移到哪个步骤
     */
    private String stepName;

    /**
     * 步骤类型
     */
    private String stepType;

    private String stepSpecialType;

    /**
     * 步骤对应的显示名称
     */
    private String stepDisplayName;

    /**
     * 没有可选的actor，或者没有选择任何actor时，是否自动忽略转移目标。如果不忽略，则会抛出异常。
     */
    private boolean ignoreNoAssign;

    /**
     * 目标步骤对应的应用状态
     */
    private String appState;

    private WfActorCandidatesBean actorCandidates;

    public String getStepSpecialType() {
        return stepSpecialType;
    }

    public void setStepSpecialType(String stepSpecialType) {
        this.stepSpecialType = stepSpecialType;
    }

    public String getAppState() {
        return appState;
    }

    public void setAppState(String appState) {
        this.appState = appState;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStepDisplayName() {
        return stepDisplayName;
    }


    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public void setStepDisplayName(String stepDisplayName) {
        this.stepDisplayName = stepDisplayName;
    }


    public String getStepType() {
        return stepType;
    }

    public boolean isIgnoreNoAssign() {
        return ignoreNoAssign;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public void setIgnoreNoAssign(boolean ignoreNoAssign) {
        this.ignoreNoAssign = ignoreNoAssign;
    }

    public WfActorCandidatesBean getActorCandidates() {
        return actorCandidates;
    }

    public void setActorCandidates(WfActorCandidatesBean actorCandidates) {
        this.actorCandidates = actorCandidates;
    }
}