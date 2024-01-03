/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.core.model.graph.dag.Dag;
import jakarta.annotation.Nonnull;

import java.util.List;

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;

public interface IWorkflowModel extends IComponentModel, ITagSetSupport {
    String getWfName();

    long getWfVersion();

    String getWfGroup();

    @Nonnull
    List<? extends IWorkflowActionModel> getActions();

    IWorkflowActionModel getAction(String actionName);

    default IWorkflowActionModel requireAction(String actionName) {
        IWorkflowActionModel actionModel = getAction(actionName);
        if (actionModel == null)
            throw new NopException(ERR_WF_UNKNOWN_ACTION)
                    .param(ARG_WF_NAME, getWfName())
                    .param(ARG_WF_VERSION, getWfVersion())
                    .param(ARG_ACTION_NAME, actionName);
        return actionModel;
    }

    IWorkflowStepModel getStep(String stepName);

    default IWorkflowStepModel requireStep(String stepName) {
        IWorkflowStepModel stepModel = getStep(stepName);
        if (stepModel == null)
            throw new NopException(ERR_WF_UNKNOWN_STEP)
                    .param(ARG_WF_NAME, getWfName())
                    .param(ARG_WF_VERSION, getWfVersion())
                    .param(ARG_STEP_NAME, stepName);
        return stepModel;
    }

    @Nonnull
    List<? extends IWorkflowStepModel> getSteps();

    IWorkflowStepModel getStartStep();

    @Nonnull
    IWorkflowStartModel getStart();

    IWorkflowEndModel getEnd();

    Dag getDag();

    String getDiagram();

    boolean isAllowStepLoop();

    int getPriority();
}
