/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.context.IServiceContext;
import io.nop.core.model.graph.dag.DagNode;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.engine.IWfRuntime;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.model.WfAssignmentActorModel;
import io.nop.wf.core.model.WfAssignmentModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.store.IWorkflowStepRecord;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_TYPE;
import static io.nop.wf.core.NopWfCoreErrors.ARG_CALLER_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_OWNER_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER;

public class WorkflowStepImpl implements IWorkflowStepImplementor {
    private final IWorkflowImplementor wf;
    private final IWorkflowStepModel model;
    private final IWorkflowStepRecord record;

    public WorkflowStepImpl(IWorkflowImplementor wf, IWorkflowStepModel model, IWorkflowStepRecord record) {
        this.wf = wf;
        this.model = model;
        this.record = record;
    }

    public String toString() {
        return "WorkflowStep[stepId=" + getStepId() + ",stepName=" + getStepName() + ",stepStatus=" + getStepStatus() + "]";
    }

    @Override
    public IWorkflowImplementor getWorkflow() {
        return wf;
    }

    @Override
    public IWorkflowStepModel getModel() {
        return model;
    }


    @Override
    public int compareTo(IWorkflowStep o) {
        int cmp = this.getRecord().getCreateTime().compareTo(o.getRecord().getCreateTime());
        if (cmp != 0)
            return cmp;

        cmp = getStepName().compareTo(o.getStepName());
        if (cmp != 0)
            return 0;

        return record.getStepId().compareTo(o.getRecord().getStepId());
    }

    @Override
    public IWorkflowStepRecord getRecord() {
        return record;
    }

    @Override
    public String getStepName() {
        return record.getStepName();
    }

    @Override
    public IWfActor getActor() {
        return wf.resolveActor(record.getActorType(), record.getActorId(), record.getActorDeptId());
    }

    @Override
    public IWfActor getOwner() {
        return wf.resolveUser(record.getOwnerId());
    }

    @Override
    public IWfActor getCaller() {
        return wf.resolveUser(record.getCallerId());
    }

    @Override
    public IWfActor getAssigner() {
        return wf.resolveUser(record.getAssignerId());
    }

    @Override
    public void changeActor(IWfActor actor, IServiceContext ctx) {
        wf.executeNow(() -> {
            wf.getEngine().changeActor(this, actor, ctx);
            return null;
        });
    }

    @Override
    public void changeOwnerId(String ownerId, IServiceContext ctx) {
        wf.executeNow(() -> {
            wf.getEngine().changeOwner(this, ownerId, ctx);
            return null;
        });
    }

    @Override
    public IWorkflowStep transferToActor(WfActorAndOwner actorAndOwner, IServiceContext ctx) {
        return wf.executeNow(() -> {
            IWorkflowStepImplementor step = wf.getEngine().transferToActor(this, actorAndOwner, ctx);
            return step;
        });
    }

    @Override
    public WfAssignmentActorModel getActorModel(String actorModelId) {
        WfAssignmentModel assignment = ((WfStepModel) model).getAssignment();
        if (assignment == null)
            return null;
        return assignment.getActor(actorModelId);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStepImplementor> getPrevSteps() {
        return wf.getStepsByRecords(wf.getStore().getPrevStepRecords(record));
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStepImplementor> getNextSteps() {
        return wf.getStepsByRecords(wf.getStore().getNextStepRecords(record));
    }

    @Override
    public void markRead(IServiceContext ctx) {
        if (!Boolean.TRUE.equals(record.getIsRead())) {
            wf.executeNow(() -> {
                record.setIsRead(true);
                record.setReadTime(CoreMetrics.currentTimestamp());
                wf.getStore().saveStepRecord(record);
                wf.getEngine().triggerStepEvent(this, NopWfCoreConstants.EVENT_MARK_READ, ctx);
                return null;
            });
        }
    }
//
//    @Override
//    public void killStep(Map<String, Object> args, IServiceContext ctx) {
//        wf.executeNow(() -> {
//            wf.getEngine().killStep(this, args, ctx);
//            return null;
//        });
//    }

    @Override
    public boolean triggerTransition(Map<String, Object> args, IServiceContext ctx) {
        return wf.executeNow(() -> {
            return wf.getEngine().triggerTransition(this, args, ctx);
        });
    }

    @Override
    public boolean triggerWaiting(Map<String, Object> args, IServiceContext ctx) {
        return wf.executeNow(() -> {
            return wf.getEngine().triggerWaiting(this, args, ctx);
        });
    }

    @Override
    public void notifySubFlowEnd(int status, Map<String, Object> results, IServiceContext ctx) {
        wf.executeNow(() -> {
            wf.getEngine().notifySubFlowEnd(this, status, results, ctx);
            return null;
        });
    }

    @Override
    public boolean allowCallByUser(IServiceContext ctx) {
        return wf.getEngine().allowCallByUser(this, ctx);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowActionModel> getAllowedActions(IServiceContext ctx) {
        return wf.getEngine().getAllowedActions(this, ctx);
    }

    @Override
    public Object invokeAction(String actionName, Map<String, Object> args, IServiceContext ctx) {
        if (!allowCallByUser(ctx))
            throw new NopException(ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER)
                    .param(ARG_WF_NAME, getWfName())
                    .param(ARG_WF_VERSION, getWfVersion())
                    .param(ARG_WF_ID, getWfId())
                    .param(ARG_STEP_NAME, getStepName())
                    .param(ARG_STEP_ID, getStepId())
                    .param(ARG_OWNER_ID, getRecord().getOwnerId())
                    .param(ARG_CALLER_ID, ctx.getUserId())
                    .param(ARG_ACTOR_TYPE, getRecord().getActorType())
                    .param(ARG_ACTOR_ID, getRecord().getActorId());

        return wf.executeNow(() -> {
            return wf.getEngine().invokeAction(this, actionName, args, ctx);
        });
    }

    @Nonnull
    @Override
    public List<WorkflowTransitionTarget> getTransitionTargetsForAction(String actionName, IServiceContext ctx) {
        return wf.getEngine().getTransitionTargetsForAction(this, actionName, ctx);
    }

    @Override
    public void transitTo(String stepName, Map<String, Object> args, IServiceContext ctx) {
        wf.executeNow(() -> {
            wf.getEngine().transitTo(this, stepName, args, ctx);
            return null;
        });
    }

    @Override
    public void exitStep(int status, Map<String, Object> args, IServiceContext ctx) {
        wf.executeNow(() -> {
            wf.getEngine().exitStep(this, status, args, ctx);
            return null;
        });
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWfRuntime wfRt) {
        return wf.getEngine().getJoinWaitSteps(this, wfRt);
    }

    @Override
    public IWorkflowStep getPrevStepByName(String stepName) {
        IWorkflowStepRecord prev = wf.getStore().getPrevStepRecordByName(record, stepName);
        return wf.getStepByRecord(prev);
    }

    @Override
    public IWorkflowStep getNextStepByName(String stepName) {
        IWorkflowStepRecord next = wf.getStore().getNextStepRecordByName(record, stepName);
        return wf.getStepByRecord(next);
    }

    @Override
    public IWorkflowStep getNextStepByName(String stepName, IWfActor actor) {
        IWorkflowStepRecord next = wf.getStore().getNextStepRecordByName(record, stepName, actor);
        return wf.getStepByRecord(next);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStepImplementor> getPrevNormalStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getPrevStepRecordsByName(record,
                getDagNode().getPrevNormalNodeNames());
        return wf.getStepsByRecords(records);
    }

    private DagNode getDagNode() {
        return wf.getModel().getDag().getNode(model.getName());
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getNextNormalStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getNextStepRecordsByName(record,
                getDagNode().getNextNormalNodeNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getPrevStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getPrevStepRecordsByName(record,
                getDagNode().getPrevNodeNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getNextStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getNextStepRecordsByName(record,
                getDagNode().getNextNodeNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getStepsInSameExecGroup(boolean includeHistory, boolean includeSelf) {
        List<? extends IWorkflowStep> ret = wf.getStepsByName(model.getName(), includeHistory);
        String stepGroup = record.getExecGroup();
        ret.removeIf(step -> {
            if (!includeSelf) {
                if (step == WorkflowStepImpl.this)
                    return true;
            }
            return Objects.equals(step.getRecord().getExecGroup(), stepGroup);
        });
        return ret;
    }

    @Override
    public IWorkflowStepImplementor getExecGroupFirstStep() {
        List<? extends IWorkflowStep> ret = wf.getStepsByName(model.getName(), true);
        String stepGroup = record.getExecGroup();
        for (IWorkflowStep step : ret) {
            if (Objects.equals(step.getRecord().getExecGroup(), stepGroup) && step.getRecord().getExecOrder() == 0) {
                return (IWorkflowStepImplementor) step;
            }
        }
        return this;
    }
}