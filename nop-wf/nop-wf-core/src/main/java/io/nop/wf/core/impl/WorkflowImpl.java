/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.IWorkflowVarSet;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.engine.IWorkflowEngine;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;

import java.util.*;

import static io.nop.wf.core.NopWfCoreErrors.*;

public class WorkflowImpl implements IWorkflowImplementor {
    private final IWorkflowEngine wfEngine;
    private final IWorkflowStore wfStore;
    private final IWorkflowModel wfModel;
    private final IWorkflowRecord wfRecord;

    private final Map<String, WorkflowStepImpl> steps = new HashMap<>();

    private IWorkflowVarSet globalVars;
    private IWorkflowVarSet outputVars;

    private Deque<Runnable> continuations = new ArrayDeque<>();

    /**
     * 是否正在执行过程中。如果是，则待做的工作放到continuations中等待后续执行
     */
    private boolean executing = false;

    public WorkflowImpl(IWorkflowEngine wfEngine,
                        IWorkflowStore wfStore,
                        IWorkflowModel wfModel,
                        IWorkflowRecord wfRecord) {
        this.wfEngine = wfEngine;
        this.wfStore = wfStore;
        this.wfModel = wfModel;
        this.wfRecord = wfRecord;
    }

    public String toString() {
        return "Workflow[wfId=" + getWfId() + ",wfName=" + getWfName()
                + ",wfVersion=" + getWfVersion() + ",wfStatus=" + getWfStatus() + "]";
    }

    @Override
    public void continueExecute(Runnable task) {
        doExecute(task);
    }

    @Override
    public IWfActor resolveActor(String actorType, String actorId, String actorDeptId) {
        return wfEngine.resolveActor(actorType, actorId, actorDeptId);
    }

    @Override
    public IWfActor resolveUser(String userId) {
        return wfEngine.resolveUser(userId);
    }

    @Override
    public IWorkflowEngine getEngine() {
        return wfEngine;
    }

    @Override
    public IWorkflowModel getModel() {
        return wfModel;
    }

    @Override
    public IWorkflowRecord getRecord() {
        return wfRecord;
    }

    @Override
    public IWorkflowStore getStore() {
        return wfStore;
    }

    @Override
    public String getWfId() {
        return wfRecord.getWfId();
    }

    @Override
    public IWorkflowStepImplementor getStepById(String stepId) {
        IWorkflowStepImplementor step = this.steps.get(stepId);
        if (step != null)
            return step;

        IWorkflowStepRecord stepRecord = wfStore.getStepRecordById(wfRecord, stepId);
        if (stepRecord == null)
            throw new NopException(ERR_WF_STEP_INSTANCE_NOT_EXISTS)
                    .param(ARG_WF_NAME, wfRecord.getWfName())
                    .param(ARG_WF_VERSION, wfRecord.getWfVersion())
                    .param(ARG_STEP_ID, stepId);

        return getStepByRecord(stepRecord);
    }

    @Override
    public IWorkflowStepImplementor getStepByRecord(IWorkflowStepRecord stepRecord) {
        if (stepRecord == null)
            return null;

        Guard.notEmpty(stepRecord.getStepId(), "step record no id");

        IWorkflowStepImplementor step = this.steps.get(stepRecord.getStepId());
        if (step != null)
            return step;

        IWorkflowStepModel stepModel = wfModel.requireStep(stepRecord.getStepName());

        WorkflowStepImpl stepImpl = new WorkflowStepImpl(this, stepModel, stepRecord);
        steps.put(stepRecord.getStepId(), stepImpl);
        return stepImpl;
    }


    @Override
    public IWorkflowStep getLatestStepByName(String stepName) {
        IWorkflowStepRecord stepRecord = wfStore.getLatestStepRecordByName(wfRecord, stepName);
        return getStepByRecord(stepRecord);
    }

    @Override
    public IWfActor getManagerActor() {
        String managerId = wfRecord.getManagerId();
        if (managerId == null)
            return null;
        return resolveActor(wfRecord.getManagerType(), wfRecord.getManagerId(), wfRecord.getManagerDeptId());
    }

    @Override
    public IWfActor getStarterActor() {
        String starerId = wfRecord.getStarterId();
        if (starerId == null)
            return null;
        return resolveUser(starerId);
    }

    @Override
    public List<? extends IWorkflowStep> getStepsByName(String stepName) {
        Collection<? extends IWorkflowStepRecord> stepRecords = wfStore.getStepRecordsByName(wfRecord,
                stepName);
        return this.getStepsByRecords(stepRecords);
    }

    @Override
    public List<? extends IWorkflowStep> getActivatedSteps() {
        Collection<? extends IWorkflowStepRecord> stepRecords = wfStore.getStepRecords(wfRecord,
                false, IWorkflowStepRecord::isActivated);
        return getStepsByRecords(stepRecords);
    }

    @Override
    public List<? extends IWorkflowStep> getWaitingSteps() {
        Collection<? extends IWorkflowStepRecord> stepRecords = wfStore.getStepRecords(wfRecord,
                false, IWorkflowStepRecord::isWaiting);
        return getStepsByRecords(stepRecords);
    }

    @Override
    public List<? extends IWorkflowStep> getSteps(boolean includeHistory) {
        Collection<? extends IWorkflowStepRecord> stepRecords = wfStore.getStepRecords(wfRecord, includeHistory, null);
        return getStepsByRecords(stepRecords);
    }

    @Override
    public List<IWorkflowStepImplementor> getStepsByRecords(Collection<? extends IWorkflowStepRecord> records) {
        if (records == null || records.isEmpty())
            return Collections.emptyList();

        List<IWorkflowStepImplementor> steps = new ArrayList<>(records.size());
        for (IWorkflowStepRecord stepRecord : records) {
            if (stepRecord == null)
                throw new IllegalArgumentException("wf.err_null_step_record");
            IWorkflowStepImplementor step = this.getStepByRecord(stepRecord);
            steps.add(step);
        }
        Collections.sort(steps);
        return steps;
    }

    @Override
    public WorkflowTransitionTarget getJumpToTarget(String stepName, IServiceContext ctx) {
        return null;
    }

    @Override
    public String getBizObjName() {
        return wfRecord.getBizObjName();
    }

    @Override
    public String getBizEntityId() {
        return wfRecord.getBizObjId();
    }

    @Override
    public Object getBizEntity() {
        return wfStore.loadBizEntity(getBizObjName(), getBizEntityId());
    }

    @Override
    public IWorkflowVarSet getGlobalVars() {
        if (globalVars == null) {
            globalVars = wfStore.getGlobalVars(getRecord());
        }
        return globalVars;
    }

    @Override
    public IWorkflowVarSet getOutputVars() {
        if (outputVars == null) {
            outputVars = wfStore.getOutputVars(getRecord());
        }
        return outputVars;
    }

    private void doExecute(Runnable task) {
        continuations.add(task);
        if (!executing) {
            this.executing = true;
            try {
                do {
                    Runnable command = this.continuations.poll();
                    if (command == null)
                        break;

                    command.run();
                } while (true);
            } finally {
                this.executing = false;
            }
        }
    }

    @Override
    public void save(IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.save(this, ctx);
        });
    }

    @Override
    public boolean isAllowStart(IServiceContext ctx) {
        return wfEngine.isAllowStart(this, ctx);
    }

    @Override
    public void start(Map<String, Object> args, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.start(this, args, ctx);
        });
    }

    @Override
    public void suspend(Map<String, Object> args, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.suspend(this, args, ctx);
        });
    }

    @Override
    public void resume(Map<String, Object> args, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.resume(this, args, ctx);
        });
    }

    @Override
    public void remove(Map<String, Object> args, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.remove(this, args, ctx);
        });
    }

    @Override
    public void kill(Map<String, Object> args, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.kill(this, args, ctx);
        });
    }

    @Override
    public void turnSignalOn(Set<String> signals, IServiceContext ctx) {
        if (CollectionHelper.isEmpty(signals))
            return;

        doExecute(() -> {
            wfEngine.turnSignalOn(this, signals, ctx);
        });
    }

    @Override
    public void turnSignalOff(Set<String> signals, IServiceContext ctx) {
        doExecute(() -> {
            wfEngine.turnSignalOff(this, signals, ctx);
        });
    }

    @Override
    public boolean isSignalOn(String signal) {
        return getOnSignals().contains(signal);
    }

    @Override
    public boolean isAllSignalOn(Set<String> signals) {
        if (signals == null || signals.isEmpty())
            return true;

        Set<String> on = getOnSignals();
        return on.containsAll(signals);
    }

    @Override
    public Set<String> getOnSignals() {
        return wfStore.getOnSignals(getRecord());
    }
}