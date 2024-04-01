/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.lang.xml.XNode;
import io.nop.task.TaskConstants;
import io.nop.task.model._gen._TaskStepModel;
import io.nop.task.utils.TaskFlowModelHelper;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class TaskStepModel extends _TaskStepModel {
    public TaskStepModel() {

    }

    public String getFullStepType() {
        if (getExtType() == null)
            return getType();
        return getType() + ':' + getExtType();
    }

    public abstract String getType();

    public boolean isUseParentScope() {
        return false;
    }

    public void addWaitStep(String waitStep) {
        Set<String> waitSteps = getWaitSteps();
        if (waitSteps == null || waitSteps.isEmpty()) {
            waitSteps = new LinkedHashSet<>();
            setWaitSteps(waitSteps);
        }
        waitSteps.add(waitStep);
    }

    public void addWaitErrorStep(String waitStep) {
        Set<String> waitSteps = getWaitErrorSteps();
        if (waitSteps == null || waitSteps.isEmpty()) {
            waitSteps = new LinkedHashSet<>();
            setWaitErrorSteps(waitSteps);
        }
        waitSteps.add(waitStep);
    }

    public String getGraphqlOperationType() {
        String operationType = (String) prop_get(TaskConstants.ATTR_GRAPHQL_OPERATION_TYPE);
        if (TaskConstants.OPERATION_TYPE_MUTATION.equals(operationType))
            return TaskConstants.ATTR_GRAPHQL_OPERATION_TYPE;
        return TaskConstants.OPERATION_TYPE_QUERY;
    }

    public XNode getInputSchemaNode() {
        return TaskFlowModelHelper.getInputsSchemaNode(this);
    }

    public XNode getOutputsSchemaNode() {
        return TaskFlowModelHelper.getOutputsSchemaNode(this);
    }

    public void normalize() {

    }
}
