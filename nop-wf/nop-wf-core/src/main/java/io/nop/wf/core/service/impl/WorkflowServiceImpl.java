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
import io.nop.wf.api.actor.IWfActorResolver;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.api.beans.WfCommandRequestBean;
import io.nop.wf.api.beans.WfSignalRequestBean;
import io.nop.wf.api.beans.WfStartRequestBean;
import io.nop.wf.api.beans.WfStartResponseBean;
import io.nop.wf.api.beans.WfSubFlowEndRequestBean;
import io.nop.wf.api.beans.WfTransferActorsRequestBean;
import io.nop.wf.api.beans.WfTransferFailedItemBean;
import io.nop.wf.api.beans.WfTransferResultBean;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
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
    private IWfActorResolver wfActorResolver;
    private IWorkflowManager workflowManager;

    @Inject
    public void setWorkflowExecutor(IWorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @Inject
    public void setWfActorResolver(IWfActorResolver wfActorResolver) {
        this.wfActorResolver = wfActorResolver;
    }

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
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
            // 来源校验：步骤必须确实挂有子流程、且该子流程已结束并处于请求声称的状态——
            // 否则任意登录用户可伪造"子流程已结束"推进父流程（跳过实际子流程审批）
            String subWfId = parentStep.getRecord().getSubWfId();
            if (subWfId == null)
                throw new NopException(NopWfCoreErrors.ERR_WF_STEP_NO_SUB_WF)
                        .param(NopWfCoreErrors.ARG_WF_NAME, parentWf.getWfName())
                        .param(NopWfCoreErrors.ARG_WF_ID, parentWf.getWfId())
                        .param(NopWfCoreErrors.ARG_STEP_ID, request.getParentWfStepId());
            IWorkflow subWf = workflowManager.getWorkflow(subWfId);
            if (subWf == null || !subWf.isEnded()
                    || !java.util.Objects.equals(subWf.getRecord().getStatus(), request.getStatus()))
                throw new NopException(NopWfCoreErrors.ERR_WF_SUB_WF_NOT_ENDED)
                        .param(NopWfCoreErrors.ARG_WF_NAME, parentWf.getWfName())
                        .param(NopWfCoreErrors.ARG_WF_ID, parentWf.getWfId())
                        .param(NopWfCoreErrors.ARG_STEP_ID, request.getParentWfStepId())
                        .param("subWfId", subWfId);
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
            checkManageAuthByDefault(wf, ctx);
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
            checkManageAuthByDefault(wf, ctx);
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
            checkManageAuthByDefault(wf, ctx);
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
            checkManageAuthByDefault(wf, ctx);
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
        checkToUserExists(request.getToUserId());

        boolean transferBySelf = request.getFromUserId().equals(ctx.getUserId());

        List<WfTransferFailedItemBean> failedItems = new ArrayList<>();
        int successCount = 0;

        List<? extends IWorkflowStepRecord> stepRecords = workflowStore.findActivatedStepsByOwner(
                request.getFromUserId(), request.getWfIds());

        for (IWorkflowStepRecord stepRecord : stepRecords) {
            WfReference wfRef = new WfReference("_", null, stepRecord.getWfId());
            try {
                FutureHelper.syncGet(workflowExecutor.execute(wfRef, ctx, wf -> {
                    if (!transferBySelf)
                        checkTransferActorsAuth(wf, request.getFromUserId(), ctx);
                    IWorkflowStep step = wf.getStepById(stepRecord.getStepId());
                    String fromOwnerId = step.getRecord().getOwnerId();
                    step.changeOwnerId(request.getToUserId(), ctx);
                    workflowStore.saveTransferAction(step.getRecord(), fromOwnerId,
                            request.getToUserId(), ctx.getUserId(), getCallerName(step, ctx));
                    return null;
                }));
                successCount++;
            } catch (NopException e) {
                // 鉴权失败属于请求级别的拒绝，直接抛出，不记录为单项失败
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_TRANSFER_ACTORS_BY_USER.getErrorCode().equals(e.getErrorCode()))
                    throw e;
                failedItems.add(buildFailedItem(stepRecord, e));
            } catch (Throwable e) {
                failedItems.add(buildFailedItem(stepRecord, e));
            }
        }

        WfTransferResultBean result = new WfTransferResultBean();
        result.setSuccessCount(successCount);
        result.setFailedItems(failedItems);
        return FutureHelper.success(result);
    }

    private void checkToUserExists(String toUserId) {
        if (wfActorResolver.resolveUser(toUserId) == null)
            throw new NopException(NopWfCoreErrors.ERR_WF_USER_NOT_EXISTS)
                    .param(NopWfCoreErrors.ARG_USER_ID, toUserId);
    }

    /**
     * 管理类操作（kill/suspend/resume/signal）的缺省鉴权：模型配置了checkManageAuth XPL时
     * 交由引擎执行模型逻辑（自定义权限），否则要求调用者为流程manager或发起人——
     * 否则缺省不设防（任意登录用户可kill审批流）。
     */
    private void checkManageAuthByDefault(IWorkflow wf, IServiceContext ctx) {
        if (wf.getModel() instanceof io.nop.wf.core.model.WfModel
                && ((io.nop.wf.core.model.WfModel) wf.getModel()).getCheckManageAuth() != null)
            return; // 模型自定义检查由引擎checkManageAuth执行

        String userId = ctx.getUserId();
        IWfActor manager = wf.getManagerActor();
        if (manager != null && manager.containsUser(userId))
            return;
        if (userId != null && userId.equals(wf.getRecord().getCreatedBy()))
            return; // 发起人
        if (ctx.getUserContext() != null && ctx.getUserContext().getRoles() != null
                && (ctx.getUserContext().getRoles().contains("nop-admin")
                || ctx.getUserContext().getRoles().contains("admin")))
            return;
        throw new NopException(NopWfCoreErrors.ERR_WF_NOT_ALLOW_MANAGE_BY_USER)
                .param(NopWfCoreErrors.ARG_WF_NAME, wf.getWfName())
                .param(NopWfCoreErrors.ARG_WF_ID, wf.getWfId())
                .param(NopWfCoreErrors.ARG_CALLER_ID, userId);
    }

    private void checkTransferActorsAuth(IWorkflow wf, String fromUserId, IServiceContext ctx) {
        IWfActor manager = wf.getManagerActor();
        if (manager == null || !manager.containsUser(ctx.getUserId()))
            throw new NopException(NopWfCoreErrors.ERR_WF_NOT_ALLOW_TRANSFER_ACTORS_BY_USER)
                    .param(NopWfCoreErrors.ARG_WF_NAME, wf.getWfName())
                    .param(NopWfCoreErrors.ARG_WF_ID, wf.getWfId())
                    .param(NopWfCoreErrors.ARG_FROM_USER_ID, fromUserId)
                    .param(NopWfCoreErrors.ARG_CALLER_ID, ctx.getUserId());
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
