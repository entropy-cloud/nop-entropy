/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
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
                                          Function<IWorkflow, T> task) {

        IWorkflow wf;
        if (StringHelper.isEmpty(wfRef.getWfId())) {
            wf = workflowManager.newWorkflow(wfRef.getWfName(), wfRef.getWfVersion());
        } else {
            wf = workflowManager.getWorkflow(wfRef.getWfId());
        }

        T ret = task.apply(wf);

        // 触发步骤的自动转换。上限防护：allowStepLoop模型允许回退边，自动迁移条件在回退环上
        // 恒真时（条件表达式缺陷）会无限创建步骤实例并写库，死循环+事务长期不提交
        runAutoTransitionsWithLimit(wf, ctx);

        return FutureHelper.toCompletionStage(ret);
    }

    static void runAutoTransitionsWithLimit(IWorkflow wf, IServiceContext ctx) {
        int maxLoops = 10_000;
        int loops = 0;
        while (wf.runAutoTransitions(ctx)) {
            if (++loops >= maxLoops)
                throw new NopException(io.nop.wf.core.NopWfCoreErrors.ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT)
                        .param("loops", loops).param("wfId", wf.getWfId());
        }
    }
}
