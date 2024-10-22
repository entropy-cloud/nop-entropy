/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.impl;

import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.IWfRuntime;
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

    /**
     * 对于join步骤，这里返回join正在等待的处于运行状态的步骤集合
     */
    List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWfRuntime wfRt);

    IWorkflowStepImplementor getExecGroupFirstStep();
}
