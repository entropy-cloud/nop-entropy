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
import java.util.List;
import java.util.Set;

public class WfStepModel extends _WfStepModel implements IWorkflowStepModel {
    private final KeyedList<WfActionModel> actions = KeyedList.emptyList();

    public WfStepModel() {

    }

    public WfSubFlowStartModel getStart(){
        return null;
    }

    @Override
    public WfStepType getType() {
        return WfStepType.step;
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowActionModel> getActions() {
        return actions;
    }

    @Override
    public IWorkflowActionModel getAction(String actionName) {
        return actions.getByKey(actionName);
    }

    public WfJoinType getJoinType() {
        return null;
    }

    @Override
    public List<? extends IWorkflowStepModel> getPrevSteps() {
        return null;
    }

    @Override
    public List<? extends IWorkflowStepModel> getNextSteps() {
        return null;
    }

    @Override
    public boolean isNextToEnd() {
        return false;
    }

    @Override
    public boolean isNextToAssigned() {
        return false;
    }

    @Override
    public boolean isFinallyToEnd() {
        return false;
    }

    @Override
    public List<? extends IWorkflowStepModel> getPrevNormalSteps() {
        return null;
    }

    @Override
    public List<? extends IWorkflowStepModel> getNextNormalSteps() {
        return null;
    }

    @Override
    public IWorkflowStepModel getControlStep() {
        return null;
    }

    @Override
    public boolean hasPrevStep(String stepName) {
        return false;
    }

    @Override
    public boolean hasNextStep(String stepName) {
        return false;
    }

    @Override
    public boolean hasAncestorStep(String stepName) {
        return false;
    }

    @Override
    public boolean hasDescendantStep(String stepName) {
        return false;
    }

    @Override
    public Set<String> getAncestorStepNames() {
        return null;
    }

    @Override
    public Set<String> getDescendantStepNames() {
        return null;
    }

    @Override
    public Set<String> getPrevStepNames() {
        return null;
    }

    @Override
    public Set<String> getNextStepNames() {
        return null;
    }

    @Override
    public Set<String> getPrevNormalStepNames() {
        return null;
    }

    @Override
    public Set<String> getNextNormalStepNames() {
        return null;
    }

    @Override
    public int getTopoOrder() {
        return 0;
    }

    @Override
    public Set<String> getWaitStepNames() {
        return null;
    }
}
