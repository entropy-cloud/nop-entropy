/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.store;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.IWorkflowVarSet;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.wf.core.store.beans.MapVarSet;
import io.nop.wf.core.store.beans.WorkflowActionRecordBean;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import io.nop.wf.dao.entity.NopWfAction;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfStepInstance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DaoWorkflowStore implements IWorkflowStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoWorkflowStore.class);

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopWfInstance> wfDao() {
        return daoProvider.daoFor(NopWfInstance.class);
    }

    protected IEntityDao<NopWfStepInstance> stepDao() {
        return daoProvider.daoFor(NopWfStepInstance.class);
    }

    private IEntityDao<NopWfAction> actionDao() {
        return daoProvider.daoFor(NopWfAction.class);
    }

    @Override
    public Object loadBizEntity(String bizObjType, String bizEntityId) {
        return daoProvider.dao(bizObjType).getEntityById(bizEntityId);
    }

    @Override
    public void updateBizEntityState(String bizObjType, Object bizEntity, String bizEntityStateProp, String state) {
        BeanTool.setComplexProperty(bizEntity, bizEntityStateProp, state);
    }

    @Override
    public IWorkflowRecord newWfRecord(IWorkflowModel wfModel) {
        NopWfInstance wfRecord = new NopWfInstance();
        wfRecord.setWfName(wfModel.getWfName());
        wfRecord.setWfVersion(wfModel.getWfVersion());
        wfRecord.setStatus(NopWfCoreConstants.WF_STATUS_CREATED);
        return wfRecord;
    }

    @Override
    public IWorkflowStepRecord newStepRecord(IWorkflowRecord wfRecord, IWorkflowStepModel stepModel) {
        NopWfStepInstance stepRecord = new NopWfStepInstance();
        stepRecord.setStepId(StringHelper.generateUUID());
        stepRecord.setStepName(stepModel.getName());
        stepRecord.setWfInstance((NopWfInstance) wfRecord);
        stepRecord.setCreateTime(CoreMetrics.currentTimestamp());
       // ((NopWfInstance) wfRecord).addStep(stepRecord);
        return stepRecord;
    }

    @Override
    public IWorkflowActionRecord newActionRecord(IWorkflowStepRecord stepRecord, IWorkflowActionModel actionModel) {
        NopWfAction actionRecord = new NopWfAction();
        actionRecord.setSid(StringHelper.generateUUID());
        actionRecord.setActionName(actionModel.getName());
        actionRecord.setWfStepInstance((NopWfStepInstance) stepRecord);
        actionRecord.setExecTime(CoreMetrics.currentTimestamp());
        return actionRecord;
    }

    @Override
    public void saveWfRecord(IWorkflowRecord wfRecord) {
        wfDao().saveEntity((NopWfInstance) wfRecord);
    }

    @Override
    public void removeWfRecord(IWorkflowRecord wfRecord) {
        wfDao().deleteEntity((NopWfInstance) wfRecord);
    }

    @Override
    public void saveStepRecord(IWorkflowStepRecord stepRecord) {
//        WorkflowRecordBean wfRecord = workflowBeans.get(stepRecord.getWfId());
//        if (wfRecord.getStep(stepRecord.getStepId()) == null) {
//            wfRecord.addStep((WorkflowStepRecordBean) stepRecord);
//        }
//        stepBeans.put(stepRecord.getStepId(), (WorkflowStepRecordBean) stepRecord);
    }

    @Override
    public void saveActionRecord(IWorkflowActionRecord actionRecord) {
//        WorkflowRecordBean wfRecord = workflowBeans.get(actionRecord.getWfId());
//        WorkflowStepRecordBean stepRecord = wfRecord.getStep(actionRecord.getStepId());
//        if (stepRecord.getAction(actionRecord.getSid()) == null) {
//            stepRecord.addAction((WorkflowActionRecordBean) actionRecord);
//        }
    }

    @Override
    public void addNextStepRecord(IWorkflowStepRecord currentStep, String actionId, IWorkflowStepRecord nextStep) {
        WorkflowStepRecordBean stepRecord = (WorkflowStepRecordBean) currentStep;
        if (nextStep.getStepId() == null)
            ((WorkflowStepRecordBean) nextStep).setStepId(StringHelper.generateUUID());
        stepRecord.addNextStepLink(nextStep.getStepId()).setExecAction(actionId);
    }

    @Override
    public void addNextSpecialStep(IWorkflowStepRecord currentStep, String actionId, String specialStepId) {
        WorkflowStepRecordBean stepRecord = (WorkflowStepRecordBean) currentStep;
        stepRecord.addNextStepLink(specialStepId).setExecAction(actionId);

    }

    @Override
    public IWorkflowRecord getWfRecord(String wfName, String wfVersion, String wfId) {
       // return workflowBeans.get(wfId);
        return null;
    }

    @Override
    public IWorkflowVarSet getGlobalVars(IWorkflowRecord wfRecord) {
        WorkflowRecordBean record = (WorkflowRecordBean) wfRecord;
        if (record.getGlobalVars() == null)
            record.setGlobalVars(new LinkedHashMap<>());
        return new MapVarSet(record.getGlobalVars());
    }

    @Override
    public IWorkflowVarSet getOutputVars(IWorkflowRecord wfRecord) {
        WorkflowRecordBean record = (WorkflowRecordBean) wfRecord;
        if (record.getOutputVars() == null)
            record.setOutputVars(new LinkedHashMap<>());
        return new MapVarSet(record.getOutputVars());
    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String errorCode, Map<String, Object> params) {
        LOG.error("nop.error:errorCode={},params={}", errorCode, params);
    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String stepName, String actionName, Throwable e) {

    }

    @Override
    public IWorkflowStepRecord getStepRecordById(IWorkflowRecord wfRecord, String stepId) {
        return ((WorkflowRecordBean) wfRecord).getStep(stepId);
    }

    @Override
    public IWorkflowStepRecord getLatestStepRecordByName(IWorkflowRecord wfRecord, String stepName) {
        return ((WorkflowRecordBean) wfRecord).getSteps().stream().filter(step -> step.getStepName().equals(stepName))
                .sorted(Comparator.comparing(WorkflowStepRecordBean::getCreateTime).reversed())
                .findFirst().orElse(null);
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecords(IWorkflowRecord wfRecord, boolean includeHistory,
                                                                    Predicate<? super IWorkflowStepRecord> filter) {
        if (includeHistory && filter == null) {
            return ((WorkflowRecordBean) wfRecord).getSteps();
        }
        return ((WorkflowRecordBean) wfRecord).getSteps().stream().filter(step -> {
            if (!includeHistory && step.isHistory())
                return false;
            if (filter != null)
                return filter.test(step);
            return true;
        }).collect(Collectors.toList());
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecordsByName(IWorkflowRecord wfRecord, String stepName,
                                                                          boolean includeHistory) {
        return getStepRecords(wfRecord, includeHistory, step -> step.getStepName().equals(stepName));
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecords(IWorkflowStepRecord stepRecord) {
        List<WorkflowStepRecordBean> ret = new ArrayList<>();
//        WorkflowRecordBean wfBean = workflowBeans.get(stepRecord.getWfId());
//        wfBean.getSteps().forEach(step -> {
//            if (step.getNextStepLinks() != null) {
//                step.getNextStepLinks().forEach(link -> {
//                    if (link.getNextStepId().equals(stepRecord.getStepId())) {
//                        ret.add(step);
//                    }
//                });
//            }
//        });
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecords(IWorkflowStepRecord stepRecord) {
        List<WorkflowStepRecordBean> ret = new ArrayList<>();
        WorkflowStepRecordBean recordBean = (WorkflowStepRecordBean) stepRecord;
        if (recordBean.getNextStepLinks() != null) {
            recordBean.getNextStepLinks()
                    .forEach(link -> {
//                        WorkflowStepRecordBean step = stepBeans.get(link.getNextStepId());
//                        if (step != null)
//                            ret.add(step);
                    });
        }
        return ret;
    }

    @Override
    public IWorkflowStepRecord getPrevStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getPrevStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName, IWfActor actor) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName)
                        && actor.isActor(k.getActorType(), k.getActorId(), k.getActorDeptId()));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getJoinWaitStepRecords(
            IWorkflowStepRecord stepRecord, Function<IWorkflowStepRecord, String> joinGroupGetter,
            Set<String> stepNames) {

        List<IWorkflowStepRecord> ret = new ArrayList<>();

//        WorkflowRecordBean wfBean = workflowBeans.get(stepRecord.getWfId());
//        String joinGroup = stepRecord.getJoinGroup();
//
//        for (WorkflowStepRecordBean step : wfBean.getSteps()) {
//            if (step == stepRecord)
//                continue;
//
//            if (step.getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND)
//                continue;
//
//            if (!stepNames.contains(step.getStepName()))
//                continue;
//
//            String stepJoinGroup = joinGroupGetter.apply(step);
//            if (Objects.equals(joinGroup, stepJoinGroup)) {
//                ret.add(step);
//            }
//        }
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames) {
        final Set<IWorkflowStepRecord> ret = new LinkedHashSet<>();

        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getPrevStepRecords,
                stepRecord, false, step -> {
                    if (stepNames.contains(step.getStepName())) {
                        ret.add(step);
                        // 不再继续查找这一分支
                        return false;
                    }
                    return true;
                });

        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecordsByName(IWorkflowStepRecord stepRecord, Collection<String> stepNames) {
        final Set<IWorkflowStepRecord> ret = new LinkedHashSet<>();

        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, step -> {
                    if (stepNames.contains(step.getStepName())) {
                        ret.add(step);
                        // 不再继续查找这一分支
                        return false;
                    }
                    return true;
                });

        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    @Override
    public boolean isAllStepsHistory(IWorkflowRecord wfRecord) {
        WorkflowRecordBean record = (WorkflowRecordBean) wfRecord;
        return record.getSteps().stream().allMatch(WorkflowStepRecordBean::isHistory);
    }

    @Override
    public IWorkflowStepRecord getNextJoinStepRecord(IWorkflowStepRecord stepRecord, String joinGroup,
                                                     String stepName, IWfActor actor) {
        WorkflowStepRecordBean wfStep = (WorkflowStepRecordBean) stepRecord;
//        WorkflowRecordBean wfRecord = this.workflowBeans.get(wfStep.getWfId());
//
//        for (IWorkflowStepRecord prev : wfRecord.getSteps()) {
//            if (prev.getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND)
//                continue;
//
//            if (prev.getStepName().equals(stepName)) {
//                if (Objects.equals(joinGroup, prev.getJoinGroup()))
//                    return prev;
//            }
//        }
        return null;
    }

    @Override
    public boolean isAllSignalOn(IWorkflowRecord wfRecord, Set<String> signals) {
        return ((WorkflowRecordBean) wfRecord).isAllSignalOn(signals);
    }

    @Override
    public void addSignals(IWorkflowRecord wfRecord, Set<String> signals) {
        ((WorkflowRecordBean) wfRecord).addSignals(signals);
    }

    @Override
    public boolean removeSignals(IWorkflowRecord wfRecord, Set<String> signals) {
        return ((WorkflowRecordBean) wfRecord).removeSignals(signals);
    }

    @Override
    public Set<String> getOnSignals(IWorkflowRecord wfRecord) {
        return ((WorkflowRecordBean) wfRecord).getOnSignals();
    }

    @Override
    public boolean isSignalOn(IWorkflowRecord wfRecord, String signal) {
        return ((WorkflowRecordBean) wfRecord).isSignalOn(signal);
    }
}
