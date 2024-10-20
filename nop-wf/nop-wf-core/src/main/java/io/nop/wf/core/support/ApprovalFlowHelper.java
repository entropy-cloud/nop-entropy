package io.nop.wf.core.support;

import io.nop.core.context.IServiceContext;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;

import java.util.List;
import java.util.Map;

public class ApprovalFlowHelper {
    public static void autoTransit(IWorkflow wf, IServiceContext ctx) {
        while (wf.runAutoTransitions(ctx)) ;
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

    public static void transferToUser(IWorkflowStep step, String nextUserId, IServiceContext ctx) {
        WfActorAndOwner actorAndOwner = new WfActorAndOwner();
        actorAndOwner.setActorId(nextUserId);
        actorAndOwner.setActorType(IWfActor.ACTOR_TYPE_USER);
        step.transferToActor(actorAndOwner, ctx);
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