/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.api.core.util.Guard;
import io.nop.commons.collections.KeyedList;
import io.nop.wf.core.model._gen._WfStepModel;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WfStepModel extends _WfStepModel implements IWorkflowStepModel, Comparable<WfStepModel> {
    private KeyedList<WfActionModel> actions = KeyedList.emptyList();

    private int stepIndex;
    private boolean finallyToEnd;
    private boolean nextToEnd;
    private boolean nextToAssigned;

    private boolean nextToEmpty;

    private boolean finallyToEmpty;

    private List<WfStepModel> transitionToSteps;
    private List<WfStepModel> transitionFromSteps;
    private Set<String> transitionToStepNames;
    private Set<String> transitionFromStepNames;

    private Set<String> nextStepNames;

    private Set<String> prevStepNames;

    private Set<String> nextNormalStepNames;
    private Set<String> prevNormalStepNames;
    private List<WfStepModel> nextSteps;
    private List<WfStepModel> prevSteps;

    private List<WfStepModel> prevNormalSteps;

    private List<WfStepModel> nextNormalSteps;

    private Set<String> ancestorStepNames = Collections.emptySet();

    private Set<String> descendantStepNames = Collections.emptySet();

    private WfStepModel controlStep;

    public WfStepModel() {

    }

    @Override
    public int compareTo(WfStepModel o) {
        return Integer.compare(stepIndex, o.getStepIndex());
    }

    /**
     * 只有WfSubFlowModel具有start配置
     *
     * @return
     */
    public WfSubFlowStartModel getStart() {
        return null;
    }

    @Override
    public WfStepType getType() {
        return WfStepType.step;
    }

    @Nonnull
    @Override
    public List<WfActionModel> getActions() {
        return actions;
    }

    public void setActions(List<WfActionModel> actions) {
        this.actions = KeyedList.fromList(actions, WfActionModel::getName);
    }

    @Override
    public IWorkflowActionModel getAction(String actionName) {
        return actions.getByKey(actionName);
    }

    /**
     * 只有join步骤具有joinType属性
     */
    public WfJoinType getJoinType() {
        return null;
    }

    @Override
    public List<WfStepModel> getTransitionToSteps() {
        return transitionToSteps;
    }

    public void setTransitionToSteps(List<WfStepModel> transitionToSteps) {
        this.transitionToSteps = transitionToSteps;
        this.transitionToStepNames = getStepNames(transitionToSteps);
    }

    @Override
    public List<WfStepModel> getTransitionFromSteps() {
        return transitionFromSteps;
    }

    public void setTransitionFromSteps(List<WfStepModel> transitionFromSteps) {
        this.transitionFromSteps = transitionFromSteps;
        this.transitionFromStepNames = getStepNames(transitionFromSteps);
    }

    @Override
    public Set<String> getTransitionToStepNames() {
        return transitionToStepNames;
    }

    @Override
    public Set<String> getTransitionFromStepNames() {
        return transitionFromStepNames;
    }

    @Override
    public List<WfStepModel> getPrevSteps() {
        return prevSteps;
    }

    public void setPrevSteps(List<WfStepModel> prevSteps) {
        this.prevSteps = prevSteps;
        if (this.prevSteps == this.transitionFromSteps) {
            this.prevStepNames = this.transitionFromStepNames;
        } else {
            this.prevStepNames = getStepNames(prevSteps);
        }
    }

    @Override
    public List<WfStepModel> getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(List<WfStepModel> nextSteps) {
        this.nextSteps = nextSteps;
        if (this.nextSteps == this.transitionToSteps) {
            this.nextStepNames = this.transitionToStepNames;
        } else {
            this.nextStepNames = getStepNames(nextSteps);
        }
    }

    static Set<String> getStepNames(Collection<WfStepModel> list) {
        return list.stream().map(WfStepModel::getName).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isNextToEmpty() {
        return nextToEmpty;
    }

    public void setNextToEmpty(boolean nextToEmpty) {
        this.nextToEmpty = nextToEmpty;
    }

    @Override
    public boolean isFinallyToEmpty() {
        return finallyToEmpty;
    }

    public void setFinallyToEmpty(boolean finallyToEmpty) {
        this.finallyToEmpty = finallyToEmpty;
    }

    @Override
    public boolean isFinallyToEnd() {
        return finallyToEnd;
    }

    public void setFinallyToEnd(boolean finallyToEnd) {
        this.finallyToEnd = finallyToEnd;
    }

    /**
     * 本步骤可以立刻迁移到结束步骤
     */
    @Override
    public boolean isNextToEnd() {
        return nextToEnd;
    }

    public void setNextToEnd(boolean nextToEnd) {
        this.nextToEnd = nextToEnd;
    }

    @Override
    public boolean isNextToAssigned() {
        return nextToAssigned;
    }

    public void setNextToAssigned(boolean nextToAssigned) {
        this.nextToAssigned = nextToAssigned;
    }

    @Override
    public List<WfStepModel> getPrevNormalSteps() {
        return prevNormalSteps;
    }

    public void setPrevNormalSteps(List<WfStepModel> prevNormalSteps) {
        this.prevNormalSteps = prevNormalSteps;
        if (this.prevNormalSteps == this.prevSteps) {
            this.prevNormalStepNames = this.prevStepNames;
        } else {
            this.prevNormalStepNames = getStepNames(prevNormalSteps);
        }
    }

    @Override
    public List<WfStepModel> getNextNormalSteps() {
        return nextNormalSteps;
    }

    public void setNextNormalSteps(List<WfStepModel> nextNormalSteps) {
        this.nextNormalSteps = nextNormalSteps;
        if (this.nextNormalSteps == this.nextSteps) {
            this.nextNormalStepNames = this.nextStepNames;
        } else {
            this.nextNormalStepNames = getStepNames(nextNormalSteps);
        }
    }

    @Override
    public WfStepModel getControlStep() {
        return controlStep;
    }

    public void setControlStep(WfStepModel controlStep) {
        this.controlStep = controlStep;
    }

    @Override
    public boolean hasPrevStep(String stepName) {
        return getPrevStepNames().contains(stepName);
    }

    @Override
    public boolean hasNextStep(String stepName) {
        return getNextStepNames().contains(stepName);
    }

    @Override
    public boolean hasPrevNormalStep(String stepName) {
        return getPrevNormalStepNames().contains(stepName);
    }

    @Override
    public boolean hasNextNormalStep(String stepName) {
        return getNextNormalStepNames().contains(stepName);
    }

    @Override
    public boolean hasAncestorStep(String stepName) {
        return getAncestorStepNames().contains(stepName);
    }

    @Override
    public boolean hasDescendantStep(String stepName) {
        return getDescendantStepNames().contains(stepName);
    }

    @Override
    public Set<String> getAncestorStepNames() {
        return ancestorStepNames;
    }

    public void setAncestorStepNames(Set<String> ancestorStepNames) {
        this.ancestorStepNames = Guard.notEmpty(ancestorStepNames, "ancestorStepNames");
    }

    @Override
    public Set<String> getDescendantStepNames() {
        return descendantStepNames;
    }

    public void setDescendantStepNames(Set<String> descendantStepNames) {
        this.descendantStepNames = Guard.notEmpty(descendantStepNames, "descendantStepNames");
    }

    @Override
    public Set<String> getPrevStepNames() {
        return prevStepNames;
    }

    @Override
    public Set<String> getNextStepNames() {
        return nextStepNames;
    }

    @Override
    public Set<String> getPrevNormalStepNames() {
        return prevNormalStepNames;
    }

    @Override
    public Set<String> getNextNormalStepNames() {
        return nextNormalStepNames;
    }

    @Override
    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    /**
     * join步骤才具有waitStepNames属性
     */
    @Override
    public Set<String> getWaitStepNames() {
        return null;
    }
}