/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.api.core.exceptions.NopException;

import jakarta.annotation.Nonnull;
import java.util.List;

import static io.nop.wf.core.WfErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.WfErrors.ARG_STEP_NAME;
import static io.nop.wf.core.WfErrors.ARG_WF_NAME;
import static io.nop.wf.core.WfErrors.ARG_WF_VERSION;
import static io.nop.wf.core.WfErrors.ERR_WF_UNKNOWN_ACTION;
import static io.nop.wf.core.WfErrors.ERR_WF_UNKNOWN_STEP;

public interface IWorkflowModel {
    String getWfName();

    String getWfVersion();

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

    String getDiagram();

    boolean isAllowStepLoop();
}
