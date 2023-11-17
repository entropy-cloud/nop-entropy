package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowCoordinator;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.store.IWorkflowRecord;

import java.util.HashMap;
import java.util.Map;

import static io.nop.wf.core.NopWfCoreErrors.ARG_PARENT_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_PARENT_WF;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_STEP_INSTANCE;

public class WorkflowCoordinatorImpl implements IWorkflowCoordinator {

    private final IWorkflowManager wfManager;

    public WorkflowCoordinatorImpl(IWorkflowManager wfManager) {
        this.wfManager = wfManager;
    }

    @Override
    public WfReference startSubflow(String wfName, Long wfVersion, WfStepReference parentStep,
                                    Map<String, Object> args, IServiceContext ctx) {
        if (args == null)
            args = new HashMap<>();

        IWorkflow wf = wfManager.newWorkflow(wfName, wfVersion);
        IWorkflowRecord record = wf.getRecord();
        record.setParentStepId(parentStep.getStepId());
        record.setParentWfName(parentStep.getWfName());
        record.setParentWfVersion(parentStep.getWfVersion());
        record.setParentWfId(parentStep.getWfId());

        wf.start(args, ctx);

        return wf.getWfReference();
    }

    @Override
    public void endSubflow(WfReference wfRef, int status, WfStepReference parentStep, Map<String, Object> results,
                           IServiceContext ctx) {
        IWorkflow parentWf = wfManager.getWorkflow(parentStep.getWfId());
        if (parentWf == null)
            throw new NopException(ERR_WF_MISSING_PARENT_WF)
                    .param(ARG_WF_NAME, parentStep.getWfName())
                    .param(ARG_WF_VERSION, parentStep.getWfId())
                    .param(ARG_WF_ID, parentStep.getWfId()).param(ARG_PARENT_STEP_ID, parentStep.getStepId());

        IWorkflowStep step = parentWf.getStepById(parentStep.getStepId());
        if (!step.isFlowType())
            throw new NopException(ERR_WF_MISSING_STEP_INSTANCE).param(ARG_WF_NAME, parentStep.getWfName())
                    .param(ARG_WF_VERSION, parentStep.getWfVersion()).param(ARG_WF_ID, parentStep.getWfId())
                    .param(ARG_STEP_ID, parentStep.getStepId());

        step.getRecord().setSubWfResultStatus(status);

        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.VAR_SUB_WF_RESULTS, results);

        // 触发工作流引擎检查step的状态检查，再根据状态触发auto action实现变迁
        step.triggerChange(args, ctx);
    }
}
