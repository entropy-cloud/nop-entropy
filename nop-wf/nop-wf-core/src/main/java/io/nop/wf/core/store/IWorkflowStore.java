/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.store;

import io.nop.core.utils.IVarSet;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.beans.WorkflowActionRecordBean;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IWorkflowStore {

    Object loadBizEntity(String bizObjType, String bizEntityId);

    void updateBizEntityState(IWorkflowRecord wfRecord, String bizEntityStateProp, String state);

    void bindBizEntityFlowId(IWorkflowRecord wfRecord, String bizFlowIdProp);

    IWorkflowRecord newWfRecord(IWorkflowModel model);

    /**
     * 返回的stepRecord的status和stepId, stepDefId等字段都已经被正确初始化
     *
     * @param wfRecord
     * @param stepModel
     * @return
     */
    IWorkflowStepRecord newStepRecord(IWorkflowRecord wfRecord, IWorkflowStepModel stepModel);

    IWorkflowActionRecord newActionRecord(IWorkflowStepRecord stepRecord, IWorkflowActionModel actionModel);

    void saveWfRecord(IWorkflowRecord wfRecord);

    void removeWfRecord(IWorkflowRecord wfRecord);

    void saveStepRecord(IWorkflowStepRecord stepRecord);

    void saveActionRecord(IWorkflowActionRecord actionRecord);

    void addNextStepRecord(IWorkflowStepRecord currentStep, String actionId, IWorkflowStepRecord nextStep);

    void addNextSpecialStep(IWorkflowStepRecord currentStep, String actionId, String specialStepId);

    IWorkflowRecord getWfRecord(String wfName, Long wfVersion, String wfId);

    IWorkflowRecord reloadWfRecord(IWorkflowRecord wfRecord);

    IVarSet getGlobalVars(IWorkflowRecord wfRecord);

    IVarSet getOutputVars(IWorkflowRecord wfRecord);

    void logMsg(IWorkflowRecord wfRecord, String stepId, String actionId, String msg);

    void logError(IWorkflowRecord wfRecord, String stepId, String actionId, String errorCode, Map<String, Object> params);

    void logError(IWorkflowRecord wfRecord, String stepId, String actionId, Throwable e);

    IWorkflowStepRecord getStepRecordById(IWorkflowRecord wfRecord, String stepId);

    IWorkflowStepRecord getLatestStepRecordByName(IWorkflowRecord wfRecord, String stepName);

    Collection<? extends IWorkflowStepRecord> getStepRecords(IWorkflowRecord wfRecord, boolean includeHistory,
                                                             Predicate<? super IWorkflowStepRecord> filter);

    Collection<? extends IWorkflowStepRecord> getStepRecordsByName(IWorkflowRecord wfRecord, String stepName,
                                                                   boolean includeHistory);

    Collection<? extends IWorkflowStepRecord> getPrevStepRecords(IWorkflowStepRecord stepRecord);

    Collection<? extends IWorkflowStepRecord> getNextStepRecords(IWorkflowStepRecord stepRecord);

    IWorkflowStepRecord getPrevStepRecordByName(IWorkflowStepRecord stepRecord, String stepName);

    IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName);

    IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName, IWfActor actor);

    Collection<? extends IWorkflowStepRecord> getJoinWaitStepRecords(IWorkflowStepRecord stepRecord,
                                                                     Function<IWorkflowStepRecord, String> joinGroupGetter,
                                                                     Set<String> stepNames);

    Collection<? extends IWorkflowStepRecord> getPrevStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames);

    Collection<? extends IWorkflowStepRecord> getNextStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames);

    boolean isAllStepsHistory(IWorkflowRecord wfRecord);

    IWorkflowStepRecord getNextJoinStepRecord(IWorkflowStepRecord stepRecord, String joinGroup, String stepName, IWfActor actor);

    Set<String> getOnSignals(IWorkflowRecord wfRecord);

    boolean isAllSignalOn(IWorkflowRecord wfRecord, Set<String> signals);

    boolean isSignalOn(IWorkflowRecord wfRecord, String signal);

    void addSignals(IWorkflowRecord wfRecord, Set<String> signals);

    boolean removeSignals(IWorkflowRecord wfRecord, Set<String> signals);

    List<? extends IWorkflowStepRecord> findActivatedStepsByOwner(String ownerId, Set<String> wfIds);

    List<? extends IWorkflowStepRecord> findDueActivatedSteps();

    List<? extends IWorkflowStepRecord> findRemindActivatedSteps();

    void saveTransferAction(IWorkflowStepRecord stepRecord, String fromOwnerId, String toOwnerId, String callerId,
                            String callerName);

    List<? extends IWorkflowActionRecord> getActionRecords(IWorkflowStepRecord stepRecord);

    default List<? extends IWorkflowActionRecord> getActionRecords(IWorkflowRecord wfRecord,
                                                                   Predicate<? super IWorkflowActionRecord> filter) {
        java.util.List<IWorkflowActionRecord> ret = new java.util.ArrayList<>();
        for (IWorkflowStepRecord stepRecord : getStepRecords(wfRecord, true, null)) {
            for (IWorkflowActionRecord actionRecord : getActionRecords(stepRecord)) {
                if (filter == null || filter.test(actionRecord)) {
                    ret.add(actionRecord);
                }
            }
        }
        return ret;
    }

    default WorkflowActionRecordBean newManualActionRecord(IWorkflowStepRecord stepRecord, String actionName,
                                                           String displayName) {
        WorkflowActionRecordBean actionRecord = new WorkflowActionRecordBean();
        actionRecord.setStepRecord(stepRecord);
        actionRecord.setActionName(actionName);
        actionRecord.setDisplayName(displayName);
        return actionRecord;
    }
}
