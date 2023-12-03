package io.nop.wf.service;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.DaoProvider;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.dao.entity.NopWfDynEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WfTestHelper {
    public static void start(IWorkflow wf, String starterId) {
        IServiceContext ctx = new ServiceContextImpl();
        ctx.getContext().setUserId(starterId);
        wf.start(null, ctx);
        wf.runAutoTransitions(ctx);
    }

    public static void start(IWorkflow wf, String starterId, NopWfDynEntity entity) {
        DaoProvider.instance().dao(entity.get_entityName()).saveOrUpdateEntity(entity);

        IServiceContext ctx = new ServiceContextImpl();
        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.PARAM_BIZ_OBJ_NAME, StringHelper.simpleClassName(entity.get_entityName()));
        args.put(NopWfCoreConstants.PARAM_BIZ_OBJ_ID, entity.orm_idString());

        ctx.getContext().setUserId(starterId);
        wf.start(args, ctx);
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
                wf.runAutoTransitions(ctx);
                return;
            }
        }

        throw new IllegalStateException("nop.wf.no-active-step-for-user:" + userId);
    }
}
