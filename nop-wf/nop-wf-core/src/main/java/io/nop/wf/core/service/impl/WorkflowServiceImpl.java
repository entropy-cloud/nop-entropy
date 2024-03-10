/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.service.impl;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.api.beans.WfCommandRequestBean;
import io.nop.wf.api.beans.WfStartRequestBean;
import io.nop.wf.api.beans.WfStartResponseBean;
import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.IWorkflowExecutor;
import io.nop.wf.core.service.WorkflowServiceSpi;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;


@BizModel("WorkflowService")
public class WorkflowServiceImpl implements WorkflowServiceSpi {

    private IWorkflowExecutor workflowExecutor;

    @Inject
    public void setWorkflowExecutor(IWorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    @BizMutation
    @Override
    public CompletionStage<WfStartResponseBean> startWorkflowAsync(@RequestBean WfStartRequestBean request,
                                                                   FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), null);
        checkMandatory(wfRef, false);

        return workflowExecutor.execute(wfRef, ctx, wf -> {
            wf.start(request.getWfParams(), ctx);

            WfStartResponseBean res = new WfStartResponseBean();
            res.setWfName(wf.getWfName());
            res.setWfVersion(wf.getWfVersion());
            res.setWfId(wf.getWfId());

            IWfActor manager = wf.getManagerActor();
            if (manager != null) {
                res.setManagerType(manager.getActorType());
                res.setManagerId(manager.getActorId());
                res.setManagerDeptId(manager.getDeptId());
                res.setManagerName(manager.getActorName());
            }
            return FutureHelper.toCompletionStage(res);
        });
    }

    @BizMutation
    @Override
    public CompletionStage<Void> notifySubFlowEndAsync(@RequestBean WfSubFlowEndRequestBean request,
                                                       FieldSelectionBean selection, IServiceContext ctx) {
        WfReference parentWfRef = new WfReference(request.getParentWfName(), request.getParentWfVersion(),
                request.getParentWfId());

        checkMandatory(parentWfRef, true);

        return workflowExecutor.execute(parentWfRef, ctx, parentWf -> {
            IWorkflowStep parentStep = parentWf.getStepById(request.getParentWfStepId());
            parentStep.notifySubFlowEnd(request.getStatus(), request.getResults(), ctx);
            return FutureHelper.success(null);
        });

    }

    @BizMutation
    @Override
    public CompletionStage<Object> invokeActionAsync(
            @RequestBean WfActionRequestBean request, FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        checkMandatory(wfRef, true);

        return workflowExecutor.execute(wfRef, ctx, wf -> {
            IWorkflowStep step = wf.getStepById(request.getStepId());
            Object result = step.invokeAction(request.getActionName(), request.getArgs(), ctx);
            return FutureHelper.toCompletionStage(result);
        });
    }

    @BizMutation
    @Override
    public CompletionStage<Void> killWorkflowAsync(@RequestBean WfCommandRequestBean request,
                                                   FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        checkMandatory(wfRef, false);
        return workflowExecutor.execute(wfRef, ctx, wf -> {
            wf.kill(request.getArgs(), ctx);
            return FutureHelper.success(null);
        });
    }

    @BizMutation
    @Override
    public CompletionStage<Void> suspendWorkflowAsync(@RequestBean WfCommandRequestBean request,
                                                      FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        checkMandatory(wfRef, true);
        return workflowExecutor.execute(wfRef, ctx, wf -> {
            wf.suspend(request.getArgs(), ctx);
            return FutureHelper.success(null);
        });
    }

    @BizMutation
    @Override
    public CompletionStage<Void> resumeWorkflowAsync(@RequestBean WfCommandRequestBean request,
                                                     FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        checkMandatory(wfRef, true);
        return workflowExecutor.execute(wfRef, ctx, wf -> {
            wf.resume(request.getArgs(), ctx);
            return FutureHelper.success(null);
        });
    }

    private void checkMandatory(WfReference wfRef, boolean requireId) {
        Guard.notEmpty(wfRef.getWfName(), "wfName");
        if (requireId)
            Guard.notEmpty(wfRef.getWfId(), "wfId");
    }
}
