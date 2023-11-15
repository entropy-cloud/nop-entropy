/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.commons.collections.KeyedList;
import io.nop.wf.core.model._gen._WfStepModel;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WfStepModel extends _WfStepModel implements IWorkflowStepModel, Comparable<WfStepModel> {
    private KeyedList<WfActionModel> actions = KeyedList.emptyList();

    private int stepIndex;
    private boolean nextToEnd;
    private boolean eventuallyToEnd;
    private boolean nextToEmpty;
    private boolean eventuallyToEmpty;

    private boolean nextToAssigned;
    private boolean eventuallyToAssigned;
    private List<WfStepModel> transitionToSteps;
    private List<WfStepModel> transitionFromSteps;
    private Set<String> transitionToStepNames;
    private Set<String> transitionFromStepNames;

    public WfStepModel() {

    }

    @Override
    public int compareTo(WfStepModel o) {
        return Integer.compare(stepIndex, o.getStepIndex());
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
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

    public boolean isEventuallyToAssigned() {
        return eventuallyToAssigned;
    }

    public void setEventuallyToAssigned(boolean eventuallyToAssigned) {
        this.eventuallyToAssigned = eventuallyToAssigned;
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
    public boolean isEventuallyToEmpty() {
        return eventuallyToEmpty;
    }

    public void setEventuallyToEmpty(boolean eventuallyToEmpty) {
        this.eventuallyToEmpty = eventuallyToEmpty;
    }

    @Override
    public boolean isEventuallyToEnd() {
        return eventuallyToEnd;
    }

    public void setEventuallyToEnd(boolean eventuallyToEnd) {
        this.eventuallyToEnd = eventuallyToEnd;
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

    /**
     * join步骤才具有waitStepNames属性
     */
    @Override
    public Set<String> getWaitStepNames() {
        return null;
    }
}