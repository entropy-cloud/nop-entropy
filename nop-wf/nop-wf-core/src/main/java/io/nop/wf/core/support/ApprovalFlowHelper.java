package io.nop.wf.core.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;

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
}