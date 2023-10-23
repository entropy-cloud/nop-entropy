/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.engine.IWorkflowEngine;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.store.IWorkflowModelStore;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.wf.core.store.WfModelParser;

import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.util.Map;

import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_WF_INSTANCE;

public class WorkflowManagerImpl implements IWorkflowManager {
    private IWorkflowStore workflowStore;
    private IWorkflowEngine workflowEngine;
    private IWorkflowModelStore workflowModelStore;

    @Inject
    public void setWorkflowEngine(IWorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @Inject
    public void setWorkflowModelStore(IWorkflowModelStore versionStore) {
        this.workflowModelStore = versionStore;
    }

    @Override
    public IWorkflowModel parseWorkflowModel(IResource resource) {
        return WfModelParser.parseWorkflowModel(resource);
    }

    @Override
    public IWorkflowModel parseWorkflowNode(XNode node) {
        return WfModelParser.parseWorkflowNode(node);
    }

    @Override
    public IResource getModelResource(String wfName, Long wfVersion) {
        return workflowModelStore.getModelResource(wfName, wfVersion);
    }

    @Nonnull
    @Override
    public IWorkflow newWorkflow(String wfName, Long wfVersion) {
        IWorkflowModel wfModel = getWorkflowModel(wfName, wfVersion);
        IWorkflowRecord wfRecord = workflowStore.newWfRecord(wfModel);
        return new WorkflowImpl(workflowEngine, workflowStore, wfModel, wfRecord);
    }

    @Nonnull
    @Override
    public IWorkflow getWorkflow(String wfId) {
        IWorkflowRecord wfRecord = workflowStore.getWfRecord(null, null, wfId);
        if (wfRecord == null)
            throw new NopException(ERR_WF_MISSING_WF_INSTANCE).param(ARG_WF_ID, wfId);

        IWorkflowModel wfModel = getWorkflowModel(wfRecord.getWfName(), wfRecord.getWfVersion());
        return new WorkflowImpl(workflowEngine, workflowStore, wfModel, wfRecord);
    }

    @Nonnull
    @Override
    public IWorkflowModel getWorkflowModel(String wfName, Long wfVersion) {
        return workflowModelStore.getModel(wfName, wfVersion);
    }

    @Override
    public void removeModelCache(String wfName, Long wfVersion) {
        workflowModelStore.removeModelCache(wfName, wfVersion);
    }

    @Override
    public void notifySubFlowEnd(@Nonnull WfReference wfRef, int status, @Nonnull WfStepReference parentStep, Map<String, Object> results, @Nonnull IEvalScope scope) {
        workflowEngine.notifySubFlowEnd(wfRef, status, parentStep, results, scope);
    }
}