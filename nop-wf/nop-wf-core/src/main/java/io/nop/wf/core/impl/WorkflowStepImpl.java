/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.impl;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.context.IServiceContext;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.WfConstants;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.IWorkflowStepRecord;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class WorkflowStepImpl implements IWorkflowStepImplementor {
    private final IWorkflowImplementor wf;
    private final IWorkflowStepModel model;
    private final IWorkflowStepRecord record;

    public WorkflowStepImpl(IWorkflowImplementor wf, IWorkflowStepModel model, IWorkflowStepRecord record) {
        this.wf = wf;
        this.model = model;
        this.record = record;
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
        wf.getEngine().changeActor(this, actor, ctx);
    }

    @Override
    public void changeOwnerId(String ownerId, IServiceContext ctx) {
        wf.getEngine().changeOwner(this, ownerId, ctx);
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
            record.setIsRead(true);
            record.setReadTime(CoreMetrics.currentTimestamp());
            wf.getStore().saveStepRecord(record);
            wf.getEngine().triggerStepEvent(this, WfConstants.EVENT_MARK_READ, ctx);
        }
    }

    @Override
    public void kill(Map<String, Object> args, IServiceContext ctx) {
        wf.getEngine().killStep(this, args, ctx);
    }

    @Override
    public void triggerChange(IServiceContext ctx) {
        wf.getEngine().triggerChange(this, ctx);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowActionModel> getAllowedActions(IServiceContext ctx) {
        return wf.getEngine().getAllowedActions(this, ctx);
    }

    @Override
    public Object invokeAction(String actionName, Map<String, Object> args, IServiceContext ctx) {
        return wf.getEngine().invokeAction(this, actionName, args, ctx);
    }

    @Nonnull
    @Override
    public List<WorkflowTransitionTarget> getTransitionTargetsForAction(String actionName, IServiceContext ctx) {
        return wf.getEngine().getTransitionTargetsForAction(this, actionName, ctx);
    }

    @Override
    public void transitTo(String stepName, Map<String, Object> args, IServiceContext ctx) {
        wf.getEngine().transitTo(this, stepName, args, ctx);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStepImplementor> getJoinWaitSteps() {
        return wf.getEngine().getJoinWaitSteps(this);
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
                model.getPrevNormalStepNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getNextNormalStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getNextStepRecordsByName(record,
                model.getNextNormalStepNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getPrevStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getPrevStepRecordsByName(record,
                model.getPrevStepNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getNextStepsInTree() {
        Collection<? extends IWorkflowStepRecord> records = wf.getStore().getNextStepRecordsByName(record,
                model.getNextStepNames());
        return wf.getStepsByRecords(records);
    }

    @Nonnull
    @Override
    public List<? extends IWorkflowStep> getOtherStepsWithSameName() {
        List<? extends IWorkflowStep> ret = wf.getStepsByName(model.getName());
        ret.remove(this);
        return ret;
    }
}