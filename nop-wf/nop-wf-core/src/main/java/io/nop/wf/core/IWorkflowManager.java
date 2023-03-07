/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.model.IWorkflowModel;

import javax.annotation.Nonnull;
import java.util.Map;

public interface IWorkflowManager {

    IWorkflowModel parseWorkflowModel(IResource resource);

    IWorkflowModel parseWorkflowNode(XNode node);

    IResource getModelResource(String wfName, String wfVersion);

    @Nonnull
    IWorkflow newWorkflow(String wfName, String wfVersion);

    /**
     * 如果使用缺省存储，则wfName和wfVersion都可以为空
     *
     * @param wfName    任意为空，则采用缺省recordStore
     * @param wfVersion
     * @param wfId      工作流实例id
     * @return
     */
    @Nonnull
    IWorkflow getWorkflow(String wfId);

    @Nonnull
    IWorkflowModel getWorkflowModel(String wfName, String wfVersion);

    void removeModelCache(String wfName, String wfVersion);

    void notifySubFlowEnd(@Nonnull WfReference wfRef, int status, @Nonnull WfStepReference parentStep,
                          Map<String, Object> results, @Nonnull IEvalScope scope);


}