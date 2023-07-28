/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.service.impl;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.api.beans.WfCommandRequestBean;
import io.nop.wf.api.beans.WfStartRequestBean;
import io.nop.wf.api.beans.WfStartResponseBean;
import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.service.WorkflowServiceSpi;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;


@BizModel("WorkflowService")
public class WorkflowServiceImpl implements WorkflowServiceSpi {
    private IWorkflowManager workflowManager;

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @BizMutation
    @Override
    public WfStartResponseBean startWorkflow(@RequestBean WfStartRequestBean request,
                                             FieldSelectionBean selection, IServiceContext ctx) {
        IWorkflow wf = workflowManager.newWorkflow(request.getWfName(), request.getWfVersion());
        wf.start(request.getArgs(), ctx);

        WfStartResponseBean res = new WfStartResponseBean();
        res.setWfName(wf.getWfName());
        res.setWfVersion(wf.getWfVersion());
        res.setWfId(wf.getWfId());

        IWfActor manager = wf.getManagerActor();
        if (manager != null) {
            res.setManagerType(manager.getType());
            res.setManagerId(manager.getActorId());
            res.setManagerDeptId(manager.getDeptId());
            res.setManagerName(manager.getName());
        }
        return res;
    }

    @BizMutation
    @Override
    public void notifySubFlowEnd(@RequestBean WfSubFlowEndRequestBean request,
                                 FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        WfStepReference parentStep = new WfStepReference(request.getParentWfName(), request.getParentWfVersion(),
                request.getParentWfId(), request.getParentWfStepId());

        workflowManager.notifySubFlowEnd(wfRef, request.getStatus(), parentStep, request.getResults(), ctx.getEvalScope());
    }

    @BizMutation
    @Override
    public CompletionStage<Object> invokeActionAsync(
            @RequestBean WfActionRequestBean request, FieldSelectionBean selection, IServiceContext ctx) {
        IWorkflow wf = workflowManager.getWorkflow(request.getWfId());
        IWorkflowStep step = wf.getStepById(request.getStepId());
        Object result = step.invokeAction(request.getActionName(), request.getArgs(), ctx);
        return FutureHelper.toCompletionStage(result);
    }

    @BizMutation
    @Override
    public void killWorkflow(@RequestBean WfCommandRequestBean request,
                             FieldSelectionBean selection, IServiceContext ctx) {
        IWorkflow wf = workflowManager.getWorkflow(request.getWfId());
        wf.kill(request.getArgs(), ctx);
    }

    @BizMutation
    @Override
    public void suspendWorkflow(@RequestBean WfCommandRequestBean request,
                                FieldSelectionBean selection, IServiceContext ctx) {
        IWorkflow wf = workflowManager.getWorkflow(request.getWfId());
        wf.suspend(request.getArgs(), ctx);
    }

    @BizMutation
    @Override
    public void resumeWorkflow(@RequestBean WfCommandRequestBean request,
                               FieldSelectionBean selection, IServiceContext ctx) {
        IWorkflow wf = workflowManager.getWorkflow(request.getWfId());
        wf.resume(request.getArgs(), ctx);
    }

}
