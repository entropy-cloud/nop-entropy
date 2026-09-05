/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowCoordinator;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.store.IWorkflowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_WF_INSTANCE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_STEP_INSTANCE_NOT_EXISTS;

public class WorkflowCoordinatorImpl implements IWorkflowCoordinator {
    static final Logger LOG = LoggerFactory.getLogger(WorkflowCoordinatorImpl.class);

    private final IWorkflowManager wfManager;

    public WorkflowCoordinatorImpl(IWorkflowManager wfManager) {
        this.wfManager = wfManager;
    }

    @Override
    public WfReference startSubFlow(String wfName, Long wfVersion, WfStepReference parentStep,
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
    public void endSubFlow(WfReference wfRef, int status, WfStepReference parentStep, Map<String, Object> results,
                           IServiceContext ctx) {
        // 父流程已被删除/父步骤实例不存在时只记警告并跳过通知：
        // 子流程自身的结束事务不应因父流程先行清理而整体失败
        try {
            IWorkflow parentWf = wfManager.getWorkflow(parentStep.getWfId());
            IWorkflowStep step = parentWf.getStepById(parentStep.getStepId());
            step.notifySubFlowEnd(status, results, ctx);
        } catch (NopException e) {
            if (ERR_WF_MISSING_WF_INSTANCE.getErrorCode().equals(e.getErrorCode())
                    || ERR_WF_STEP_INSTANCE_NOT_EXISTS.getErrorCode().equals(e.getErrorCode())) {
                LOG.warn("nop.wf.skip-notify-subflow-end-since-parent-missing:parentWfId={},parentStepId={},wfRef={}",
                        parentStep.getWfId(), parentStep.getStepId(), wfRef, e);
                return;
            }
            throw e;
        }
    }
}
