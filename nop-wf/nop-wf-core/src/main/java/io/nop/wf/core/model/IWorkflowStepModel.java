/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

public interface IWorkflowStepModel {
    String getName();

    String getSpecialType();

    WfStepType getType();

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

    /**
     * 树形结构中本步骤的父节点。因为存在join的情况，所有有可能有多个父节点。
     *
     * @return
     */
    List<? extends IWorkflowStepModel> getPrevSteps();

    /**
     * 树形结构中本步骤的子节点。
     *
     * @return
     */
    List<? extends IWorkflowStepModel> getNextSteps();

    boolean isNextToEnd();

    boolean isNextToAssigned();

    boolean isFinallyToEnd();

    /**
     * 父节点如果是internal节点，则继续向前查找父节点
     *
     * @return
     */
    List<? extends IWorkflowStepModel> getPrevNormalSteps();

    List<? extends IWorkflowStepModel> getNextNormalSteps();

    /**
     * 在树形结构中向上查找到控制节点。控制节点是距离step最近的单一父节点。如果一个步骤具有两个父节点，则要继续向上查找，直到找到所有父分支共有的父节点。
     * 因为起始步骤是唯一的，所以除了起始步骤之外，其他步骤一定可以找到一个控制节点。
     *
     * @return
     */
    IWorkflowStepModel getControlStep();

    boolean hasPrevStep(String stepName);

    boolean hasNextStep(String stepName);

    boolean hasAncestorStep(String stepName);

    boolean hasDescendantStep(String stepName);

    Set<String> getAncestorStepNames();

    Set<String> getDescendantStepNames();

    Set<String> getPrevStepNames();

    Set<String> getNextStepNames();

    Set<String> getPrevNormalStepNames();

    Set<String> getNextNormalStepNames();

    int getTopoOrder();

    Set<String> getWaitStepNames();

    String getJoinTargetStep();

    Set<String> getWaitSignals();
}
