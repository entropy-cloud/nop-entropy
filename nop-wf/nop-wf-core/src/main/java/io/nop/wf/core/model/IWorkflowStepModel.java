/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model;

import io.nop.commons.lang.ITagSetSupport;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

public interface IWorkflowStepModel extends ITagSetSupport {
    String getName();

    String getDisplayName();

    String getSpecialType();

    WfStepType getStepType();

    WfJoinType getJoinType();

    boolean isInternal();

    boolean isOptional();

    boolean isAllowWithdraw();

    boolean isAllowReject();

    int getPriority();

    String getDueAction();

    boolean isIndependent();

    IWorkflowAssignmentModel getAssignment();

    @Nonnull
    List<? extends IWorkflowActionModel> getActions();

    IWorkflowActionModel getAction(String actionName);

    List<? extends IWorkflowStepModel> getTransitionFromSteps();

    List<? extends IWorkflowStepModel> getTransitionToSteps();

    Set<String> getTransitionFromStepNames();

    Set<String> getTransitionToStepNames();

    boolean isNextToEnd();

    boolean isNextToAssigned();

    boolean isNextToEmpty();

    boolean isEventuallyToEnd();

    boolean isEventuallyToEmpty();

    boolean isEventuallyToAssigned();

    Set<String> getWaitStepNames();

    Set<String> getWaitSignals();
}
