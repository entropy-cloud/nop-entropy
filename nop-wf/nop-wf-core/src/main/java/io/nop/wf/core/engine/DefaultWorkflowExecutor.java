/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.engine;

import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class DefaultWorkflowExecutor implements IWorkflowExecutor {

    private IWorkflowManager workflowManager;

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @Override
    public <T> CompletionStage<T> execute(WfReference wfRef, IServiceContext ctx,
                                          Function<IWorkflow, CompletionStage<T>> task) {

        IWorkflow wf;
        if (StringHelper.isEmpty(wfRef.getWfId())) {
            wf = workflowManager.getWorkflow(wfRef.getWfId());
        } else {
            wf = workflowManager.newWorkflow(wfRef.getWfName(), wfRef.getWfVersion());
        }

        CompletionStage<T> ret = task.apply(wf);

        // 触发步骤的自动转换
        while (wf.runAutoTransitions(ctx)) ;

        return ret;
    }
}
