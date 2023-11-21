/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.model.IWorkflowModel;

import jakarta.annotation.Nonnull;
import java.util.Map;

public interface IWorkflowManager {

    IWorkflowModel parseWorkflowModel(IResource resource);

    IWorkflowModel parseWorkflowNode(XNode node);

    @Nonnull
    IWorkflow newWorkflow(String wfName, Long wfVersion);

    /**
     * @param wfId      工作流实例id
     */
    @Nonnull
    IWorkflow getWorkflow(String wfId);

    @Nonnull
    IWorkflowModel getWorkflowModel(String wfName, Long wfVersion);

    void removeModelCache(String wfName, Long wfVersion);

    void notifySubFlowEnd(@Nonnull WfReference wfRef, int status, @Nonnull WfStepReference parentStep,
                          Map<String, Object> results, @Nonnull IServiceContext context);


}