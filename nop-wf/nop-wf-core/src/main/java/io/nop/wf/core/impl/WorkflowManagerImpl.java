/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowCoordinator;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.engine.IWorkflowEngine;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.wf.core.store.WfModelParser;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.Map;

import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_WF_INSTANCE;

public class WorkflowManagerImpl implements IWorkflowManager {
    private IWorkflowStore workflowStore;
    private IWorkflowEngine workflowEngine;
    private IWorkflowCoordinator workflowCoordinator;

    public void setWorkflowCoordinator(IWorkflowCoordinator workflowCoordinator) {
        this.workflowCoordinator = workflowCoordinator;
    }

    @Inject
    public void setWorkflowEngine(IWorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @PostConstruct
    public void init() {
        if (workflowCoordinator == null)
            workflowCoordinator = new WorkflowCoordinatorImpl(this);
    }

    @Override
    public IWorkflowModel parseWorkflowModel(IResource resource) {
        return WfModelParser.parseWorkflowModel(resource);
    }

    @Override
    public IWorkflowModel parseWorkflowNode(XNode node) {
        return WfModelParser.parseWorkflowNode(node);
    }

    @Nonnull
    @Override
    public IWorkflow newWorkflow(String wfName, Long wfVersion) {
        IWorkflowModel wfModel = getWorkflowModel(wfName, wfVersion);
        IWorkflowRecord wfRecord = workflowStore.newWfRecord(wfModel);
        return new WorkflowImpl(workflowEngine, workflowStore, workflowCoordinator, wfModel, wfRecord);
    }

    @Nonnull
    @Override
    public IWorkflow getWorkflow(String wfId) {
        IWorkflowRecord wfRecord = workflowStore.getWfRecord(null, null, wfId);
        if (wfRecord == null)
            throw new NopException(ERR_WF_MISSING_WF_INSTANCE).param(ARG_WF_ID, wfId);

        IWorkflowModel wfModel = getWorkflowModel(wfRecord.getWfName(), wfRecord.getWfVersion());
        return new WorkflowImpl(workflowEngine, workflowStore, workflowCoordinator, wfModel, wfRecord);
    }

    @Nonnull
    @Override
    public IWorkflowModel getWorkflowModel(String wfName, Long wfVersion) {
        String path = NopWfCoreConstants.RESOLVE_WF_NS_PREFIX + wfName;
        if (wfVersion != null)
            path += "/v" + wfVersion;

        return (IWorkflowModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    @Override
    public void removeModelCache(String wfName, Long wfVersion) {
        String path = NopWfCoreConstants.RESOLVE_WF_NS_PREFIX + wfName;
        ResourceComponentManager.instance().removeCachedModel(path);
        if (wfVersion != null) {
            path += "/v" + wfVersion;
        }
        ResourceComponentManager.instance().removeCachedModel(path);
    }

    private String buildModelPath(String wfName, Long wfVersion) {
        return ResourceVersionHelper.buildPath(NopWfCoreConstants.RESOLVE_WF_NS_PREFIX, wfName, wfVersion, NopWfCoreConstants.FILE_TYPE_XWF);
    }

    @Override
    public void notifySubFlowEnd(@Nonnull WfReference wfRef, int status, @Nonnull WfStepReference parentStep,
                                 Map<String, Object> results, @Nonnull IServiceContext context) {
        workflowCoordinator.endSubFlow(wfRef, status, parentStep, results, context);
    }
}