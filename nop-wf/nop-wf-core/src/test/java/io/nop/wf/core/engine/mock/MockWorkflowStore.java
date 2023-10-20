/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine.mock;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.IWorkflowVarSet;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.wf.core.store.beans.WorkflowActionRecordBean;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MockWorkflowStore implements IWorkflowStore {
    private Map<Pair<String, String>, Object> boMap = new HashMap<>();

    @Override
    public Object loadBizEntity(String bizObjType, String bizEntityId) {
        return boMap.get(Pair.of(bizObjType, bizEntityId));
    }

    @Override
    public void updateBizEntityState(String bizObjType, Object bizEntity, String bizEntityStateProp, String state) {
        BeanTool.setComplexProperty(bizEntity, bizEntityStateProp, state);
    }

    @Override
    public IWorkflowRecord newWfRecord(IWorkflowModel wfModel) {
        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        wfRecord.setWfName(wfModel.getWfName());
        wfRecord.setWfVersion(wfModel.getWfVersion());
        return wfRecord;
    }

    @Override
    public IWorkflowStepRecord newStepRecord(IWorkflowRecord wfRecord, IWorkflowStepModel stepModel) {
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        stepRecord.setStepId(StringHelper.generateUUID());
        stepRecord.setStepName(stepModel.getName());
        stepRecord.setWfRecord(wfRecord);
        stepRecord.setCreateTime(CoreMetrics.currentTimestamp());
        ((WorkflowRecordBean) wfRecord).addStep(stepRecord);
        return stepRecord;
    }

    @Override
    public IWorkflowActionRecord newActionRecord(IWorkflowStepRecord stepRecord, IWorkflowActionModel actionModel) {
        WorkflowActionRecordBean actionRecord = new WorkflowActionRecordBean();
        actionRecord.setSid(StringHelper.generateUUID());
        actionRecord.setActionName(actionModel.getName());
        actionRecord.setStepRecord(stepRecord);
        actionRecord.setExecTime(CoreMetrics.currentTimestamp());
        wfRecord.getActions().add(actionRecord);
        return actionRecord;
    }

    @Override
    public void saveWfRecord(IWorkflowRecord wfRecord) {

    }

    @Override
    public void removeWfRecord(IWorkflowRecord wfRecord) {

    }

    @Override
    public void saveStepRecord(IWorkflowStepRecord stepRecord) {

    }

    @Override
    public void saveActionRecord(IWorkflowActionRecord actionRecord) {

    }

    @Override
    public void addNextStepRecord(IWorkflowStepRecord currentStep, String actionId, IWorkflowStepRecord nextStep) {

    }

    @Override
    public void addNextSpecialStep(IWorkflowStepRecord currentStep, String actionId, String specialStepId) {

    }

    @Override
    public IWorkflowRecord getWfRecord(String wfName, String wfVersion, String wfId) {
        return null;
    }

    @Override
    public IWorkflowVarSet getGlobalVars(IWorkflowRecord wfRecord) {
        return null;
    }

    @Override
    public IWorkflowVarSet getOutputVars(IWorkflowRecord wfRecord) {
        return null;
    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String errorCode, Map<String, Object> params) {

    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String stepName, String actionName, Throwable e) {

    }

    @Override
    public IWorkflowStepRecord getStepRecordById(IWorkflowRecord wfRecord, String stepId) {
        return null;
    }

    @Override
    public IWorkflowStepRecord getLatestStepRecordByName(IWorkflowRecord wfRecord, String stepName) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecords(IWorkflowRecord wfRecord, boolean includeHistory) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecordsByName(IWorkflowRecord wfRecord, String stepName) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecords(IWorkflowStepRecord stepRecord) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecords(IWorkflowStepRecord stepRecord) {
        return null;
    }

    @Override
    public IWorkflowStepRecord getPrevStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName, IWfActor actor) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getJoinWaitStepRecords(IWorkflowStepRecord stepRecord, String joinKey, Set<String> stepNames) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames) {
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames) {
        return null;
    }

    @Override
    public boolean isAllStepsHistory(IWorkflowRecord wfRecord) {
        return false;
    }

    @Override
    public IWorkflowStepRecord getNextWaitingStepRecord(IWorkflowStepRecord stepRecord, String joinKey, String stepName, IWfActor actor) {
        return null;
    }

    @Override
    public Set<String> getOnSignals(IWorkflowRecord wfRecord) {
        return null;
    }

    @Override
    public void saveOnSignals(IWorkflowRecord wfRecord, Set<String> signals) {

    }
}
