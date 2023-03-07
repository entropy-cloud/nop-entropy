/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.impl;

import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.store.IWorkflowStore;

import java.util.List;

public interface IWorkflowStepImplementor extends IWorkflowStep {
    @Override
    IWorkflowImplementor getWorkflow();

    default IWorkflowStore getStore() {
        return getWorkflow().getStore();
    }

    @Override
    List<? extends IWorkflowStepImplementor> getPrevNormalStepsInTree();

    @Override
    List<? extends IWorkflowStepImplementor> getPrevSteps();

    @Override
    List<? extends IWorkflowStepImplementor> getNextSteps();

    @Override
    List<? extends IWorkflowStepImplementor> getJoinWaitSteps();

}
