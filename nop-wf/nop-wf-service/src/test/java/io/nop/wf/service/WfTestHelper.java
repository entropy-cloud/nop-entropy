package io.nop.wf.service;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.dao.entity.NopWfDynEntity;

import java.util.List;

public class WfTestHelper {
    public static void start(IWorkflow wf, String starterId) {
        IServiceContext ctx = new ServiceContextImpl();
        ctx.getContext().setUserId(starterId);
        wf.start(null, ctx);
        wf.runAutoTransitions(ctx);
    }

    public static void start(IWorkflow wf, String starterId, NopWfDynEntity entity) {
        IServiceContext ctx = new ServiceContextImpl();
        IEvalScope scope = ctx.getEvalScope();
        scope.setLocalValue(NopWfCoreConstants.PARAM_BIZ_OBJ_NAME, StringHelper.simpleClassName(entity.get_entityName()));
        scope.setLocalValue(NopWfCoreConstants.PARAM_BIZ_OBJ_ID, entity.orm_idString());

        ctx.getContext().setUserId(starterId);
        wf.start(null, ctx);
        wf.runAutoTransitions(ctx);
    }

    public static void invoke(IWorkflow wf, String userId, String action) {
        IServiceContext ctx = new ServiceContextImpl();
        ctx.getContext().setUserId(userId);

        List<? extends IWorkflowStep> activeSteps = wf.getActivatedSteps();
        Guard.notEmpty(activeSteps, "activeSteps");

        for (IWorkflowStep step : activeSteps) {
            if (step.getActor().containsUser(userId)) {
                step.invokeAction(action, null, ctx);
                wf.runAutoTransitions(ctx);
                return;
            }
        }

        throw new IllegalStateException("nop.wf.no-active-step-for-user:" + userId);
    }
}
