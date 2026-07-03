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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.api.beans.WfCommandRequestBean;
import io.nop.wf.api.beans.WfSignalRequestBean;
import io.nop.wf.api.beans.WfStartRequestBean;
import io.nop.wf.api.beans.WfStartResponseBean;
import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
import io.nop.wf.api.beans.WfTransferActorsRequestBean;
import io.nop.wf.api.beans.WfTransferFailedItemBean;
import io.nop.wf.api.beans.WfTransferResultBean;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.engine.IWorkflowExecutor;
import io.nop.wf.core.service.WorkflowServiceSpi;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;


@BizModel("WorkflowService")
public class WorkflowServiceImpl implements WorkflowServiceSpi {

    private IWorkflowExecutor workflowExecutor;
    private IWorkflowStore workflowStore;

    @Inject
    public void setWorkflowExecutor(IWorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
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
            return res;
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
            return null;
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
            return result;
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
            return null;
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
            return null;
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
            return null;
        });
    }

    @BizMutation
    @Override
    public CompletionStage<Void> signalWfAsync(@RequestBean WfSignalRequestBean request,
                                               FieldSelectionBean selection, IServiceContext ctx) {
        WfReference wfRef = new WfReference(request.getWfName(), request.getWfVersion(), request.getWfId());
        checkMandatory(wfRef, true);
        Guard.notEmpty(request.getSignals(), "signals");

        return workflowExecutor.execute(wfRef, ctx, wf -> {
            if (wf.isEnded()) {
                throw new NopException(NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS)
                        .param(NopWfCoreErrors.ARG_WF_NAME, wf.getWfName())
                        .param(NopWfCoreErrors.ARG_STEP_NAME, "signal")
                        .param(NopWfCoreErrors.ARG_ACTION_NAME, "signalWf")
                        .param(NopWfCoreErrors.ARG_STEP_STATUS, wf.getWfStatus());
            }
            if (request.getOn()) {
                wf.turnSignalOn(request.getSignals(), ctx);
            } else {
                wf.turnSignalOff(request.getSignals(), ctx);
            }
            return null;
        });
    }

    @BizMutation
    @Override
    public CompletionStage<WfTransferResultBean> transferActorsAsync(@RequestBean WfTransferActorsRequestBean request,
                                                                     FieldSelectionBean selection, IServiceContext ctx) {
        Guard.notEmpty(request.getFromUserId(), "fromUserId");
        Guard.notEmpty(request.getToUserId(), "toUserId");

        List<WfTransferFailedItemBean> failedItems = new ArrayList<>();
        int successCount = 0;

        List<? extends IWorkflowStepRecord> stepRecords = workflowStore.findActivatedStepsByOwner(
                request.getFromUserId(), request.getWfIds());

        for (IWorkflowStepRecord stepRecord : stepRecords) {
            WfReference wfRef = new WfReference("_", null, stepRecord.getWfId());
            try {
                FutureHelper.syncGet(workflowExecutor.execute(wfRef, ctx, wf -> {
                    IWorkflowStep step = wf.getStepById(stepRecord.getStepId());
                    String fromOwnerId = step.getRecord().getOwnerId();
                    step.changeOwnerId(request.getToUserId(), ctx);
                    workflowStore.saveTransferAction(step.getRecord(), fromOwnerId,
                            request.getToUserId(), ctx.getUserId(), getCallerName(step, ctx));
                    return null;
                }));
                successCount++;
            } catch (Throwable e) {
                failedItems.add(buildFailedItem(stepRecord, e));
            }
        }

        WfTransferResultBean result = new WfTransferResultBean();
        result.setSuccessCount(successCount);
        result.setFailedItems(failedItems);
        return FutureHelper.success(result);
    }

    private void checkMandatory(WfReference wfRef, boolean requireId) {
        Guard.notEmpty(wfRef.getWfName(), "wfName");
        if (requireId)
            Guard.notEmpty(wfRef.getWfId(), "wfId");
    }

    private WfTransferFailedItemBean buildFailedItem(IWorkflowStepRecord stepRecord, Throwable e) {
        WfTransferFailedItemBean item = new WfTransferFailedItemBean();
        item.setWfId(stepRecord.getWfId());
        item.setStepId(stepRecord.getStepId());
        item.setReason(e.getMessage());
        return item;
    }

    private String getCallerName(IWorkflowStep step, IServiceContext ctx) {
        IWfActor caller = step.getWorkflow().resolveUser(ctx.getUserId());
        if (caller == null) {
            return ctx.getUserId();
        }
        return caller.getActorName();
    }
}
