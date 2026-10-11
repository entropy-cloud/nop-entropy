package io.nop.wf.core.support;

import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.model.IWorkflowModel;

import java.util.List;
import java.util.Map;

public class ApprovalFlowHelper {

    /**
     * 安全契约：本类的 jump/cancelSteps/transferToUser 直接调用步骤管理操作（transitTo/exitStep/transferToActor），
     * 引擎层不做操作者鉴权（见 IWorkflowStep 管理类操作契约），调用方必须确保已对当前用户完成鉴权，
     * 不得将未鉴权的用户输入直接透传进来。
     */
    public static void autoTransit(IWorkflow wf, IServiceContext ctx) {
        int maxLoops = 10_000;
        int loops = 0;
        while (wf.runAutoTransitions(ctx)) {
            if (++loops >= maxLoops)
                throw new NopException(io.nop.wf.core.NopWfCoreErrors.ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT)
                        .param("loops", loops).param("wfId", wf.getWfId());
        }
    }

    public static void start(IWorkflow wf, Map<String, Object> args, IServiceContext ctx) {
        wf.start(args, ctx);
        wf.getLatestStartStep().invokeAction(NopWfCoreConstants.ACTION_COMPLETE, null, ctx);
        autoTransit(wf, ctx);
    }

    public static void jump(IWorkflowStep step, String targetStepName, IServiceContext ctx) {
        List<? extends IWorkflowStep> activeSteps = step.getWorkflow().getActivatedSteps();
        step.transitTo(targetStepName, null, ctx);
        cancelSteps(activeSteps, ctx);
        autoTransit(step.getWorkflow(), ctx);
    }

    public static void cancelSteps(List<? extends IWorkflowStep> steps, IServiceContext ctx) {
        for (IWorkflowStep s : steps) {
            s.exitStep(NopWfCoreConstants.WF_STEP_STATUS_CANCELLED, null, ctx);
        }
    }

    public static void transferToUser(IWorkflowStep step, String nextUserId, boolean exitCurrentStep, IServiceContext ctx) {
        WfActorAndOwner actorAndOwner = new WfActorAndOwner();
        actorAndOwner.setActorId(nextUserId);
        actorAndOwner.setActorType(IWfActor.ACTOR_TYPE_USER);
        step.transferToActor(actorAndOwner, exitCurrentStep, ctx);
        autoTransit(step.getWorkflow(), ctx);
    }

    public static void agree(IWorkflowStep step, Map<String, Object> args, IServiceContext ctx) {
        step.invokeAction(NopWfCoreConstants.ACTION_AGREE, args, ctx);
        autoTransit(step.getWorkflow(), ctx);
    }

    public static void disagree(IWorkflowStep step, Map<String, Object> args, IServiceContext ctx) {
        step.invokeAction(NopWfCoreConstants.ACTION_DISAGREE, args, ctx);
        autoTransit(step.getWorkflow(), ctx);
    }

    /**
     * P1-3.1-001（plan 2379）：审批双事实源 gate——实体绑定的审批流活跃（未结束）期间，
     * 除引擎回调（{@code svcCtx} 携带 {@link NopWfCoreConstants#ATTR_WF_ENGINE_CALLBACK} 标记）
     * 外的直调一律抛 {@link NopWfCoreErrors#ERR_WF_APPROVAL_FLOW_ACTIVE_LOCK}。
     * 受派人校验由引擎步骤动作 allowCallByUser 承担（活跃期唯一合法落点=引擎回调）。
     *
     * @param workflowManager     流程管理器（脚本侧 inject('nopWorkflowManager')）
     * @param wfName              实体 objMeta 的 wf:wfName 标签（无绑定 = 无 gate）
     * @param entity              已加载实体（经 flowIdProp 读取绑定 flowId）
     * @param svcCtx              调用上下文（引擎回调窗口携带 ATTR_WF_ENGINE_CALLBACK 标记）
     * @param allowEngineCallback approve/reject=true（引擎回调放行）；withdraw/reverseApprove
     *                            与 submitForApproval 重提交守卫=false（活跃期无条件拒绝）
     */
    public static void checkApprovalFlowLocked(IWorkflowManager workflowManager, String wfName, Object entity,
                                               IServiceContext svcCtx, boolean allowEngineCallback) {
        if (wfName == null || entity == null)
            return;

        if (allowEngineCallback && hasEngineCallbackMarker(svcCtx))
            return;

        IWorkflowModel workflowModel = workflowManager.getWorkflowModel(wfName, null);
        if (workflowModel == null)
            return;

        Object flowIdPropObj = BeanTool.getProperty(workflowModel, "bizEntityFlowIdProp");
        if (!(flowIdPropObj instanceof CharSequence) || StringHelper.isEmpty((CharSequence) flowIdPropObj))
            return;

        Object flowId = BeanTool.getProperty(entity, flowIdPropObj.toString());
        if (flowId == null || StringHelper.isEmpty(flowId.toString()))
            return;

        IWorkflow boundWf;
        try {
            boundWf = workflowManager.getWorkflow(flowId.toString());
        } catch (NopException e) {
            // 绑定的流程实例已被清理（历史遗留 flowId）：视为非活跃，直调窗口保持
            return;
        }
        if (boundWf == null || boundWf.isEnded())
            return;

        throw new NopException(NopWfCoreErrors.ERR_WF_APPROVAL_FLOW_ACTIVE_LOCK)
                .param(NopWfCoreErrors.ARG_WF_ID, flowId.toString())
                .param("bizObjName", wfName)
                .param("bizObjId", BeanTool.getProperty(entity, "id"));
    }

    private static boolean hasEngineCallbackMarker(IServiceContext svcCtx) {
        return svcCtx != null && svcCtx.getContext() != null
                && Boolean.TRUE.equals(svcCtx.getContext().getAttribute(NopWfCoreConstants.ATTR_WF_ENGINE_CALLBACK));
    }
}
