/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.service.mock;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.wf.core.IWorkflowVarSet;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.AbstractWorkflowStore;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.beans.MapVarSet;
import io.nop.wf.core.store.beans.WorkflowActionRecordBean;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockWorkflowStore extends AbstractWorkflowStore {
    static final Logger LOG = LoggerFactory.getLogger(MockWorkflowStore.class);
    private final Map<Pair<String, String>, Object> boMap = new ConcurrentHashMap<>();
    private final Map<String, WorkflowRecordBean> workflowBeans = new ConcurrentHashMap<>();

    private final Map<String, WorkflowStepRecordBean> stepBeans = new ConcurrentHashMap<>();

    @Override
    public Object loadBizEntity(String bizObjType, String bizEntityId) {
        return boMap.get(Pair.of(bizObjType, bizEntityId));
    }

    @Override
    public void updateBizEntityState(String bizObjType, Object bizEntity, String bizEntityStateProp, String state) {
        BeanTool.setComplexProperty(bizEntity, bizEntityStateProp, state);
    }

    @Override
    protected IWorkflowRecord getWfRecord(IWorkflowStepRecord stepRecord) {
        return workflowBeans.get(stepRecord.getWfId());
    }

    @Override
    protected Collection<? extends IWorkflowStepRecord> getSteps(IWorkflowRecord wfRecord) {
        return ((WorkflowRecordBean) wfRecord).getSteps();
    }

    @Override
    public IWorkflowRecord newWfRecord(IWorkflowModel wfModel) {
        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        wfRecord.setWfName(wfModel.getWfName());
        wfRecord.setWfVersion(wfModel.getWfVersion());
        wfRecord.setStatus(NopWfCoreConstants.WF_STATUS_CREATED);
        return wfRecord;
    }

    @Override
    public IWorkflowStepRecord newStepRecord(IWorkflowRecord wfRecord, IWorkflowStepModel stepModel) {
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        stepRecord.setStepId(StringHelper.generateUUID());
        stepRecord.setStepName(stepModel.getName());
        stepRecord.setDisplayName(stepModel.getDisplayName());
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
        actionRecord.setDisplayName(actionModel.getDisplayName());
        actionRecord.setStepRecord(stepRecord);
        actionRecord.setExecTime(CoreMetrics.currentTimestamp());
        ((WorkflowStepRecordBean) stepRecord).addAction(actionRecord);
        return actionRecord;
    }

    @Override
    public void saveWfRecord(IWorkflowRecord wfRecord) {
        WorkflowRecordBean record = (WorkflowRecordBean) wfRecord;
        if (wfRecord.getWfId() == null)
            wfRecord.setWfId(StringHelper.generateUUID());
        workflowBeans.put(wfRecord.getWfId(), record);
    }

    @Override
    public void removeWfRecord(IWorkflowRecord wfRecord) {
        workflowBeans.remove(wfRecord.getWfId());
    }

    @Override
    public void saveStepRecord(IWorkflowStepRecord stepRecord) {
        WorkflowRecordBean wfRecord = workflowBeans.get(stepRecord.getWfId());
        if (wfRecord.getStep(stepRecord.getStepId()) == null) {
            wfRecord.addStep((WorkflowStepRecordBean) stepRecord);
        }
        stepBeans.put(stepRecord.getStepId(), (WorkflowStepRecordBean) stepRecord);
    }

    @Override
    public void saveActionRecord(IWorkflowActionRecord actionRecord) {
        WorkflowRecordBean wfRecord = workflowBeans.get(actionRecord.getWfId());
        WorkflowStepRecordBean stepRecord = wfRecord.getStep(actionRecord.getStepId());
        if (stepRecord.getAction(actionRecord.getSid()) == null) {
            stepRecord.addAction((WorkflowActionRecordBean) actionRecord);
        }
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
        return workflowBeans.get(wfId);
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
    public void logMsg(IWorkflowRecord wfRecord, String stepId, String actionId, String msg) {

    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String stepId, String actionId,
                         String errorCode, Map<String, Object> params) {
        LOG.error("nop.error:errorCode={},params={}", errorCode, params);
    }

    @Override
    public void logError(IWorkflowRecord wfRecord, String stepId, String actionId, Throwable e) {

    }

    @Override
    public IWorkflowStepRecord getStepRecordById(IWorkflowRecord wfRecord, String stepId) {
        return ((WorkflowRecordBean) wfRecord).getStep(stepId);
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecords(IWorkflowStepRecord stepRecord) {
        List<WorkflowStepRecordBean> ret = new ArrayList<>();
        WorkflowRecordBean wfBean = workflowBeans.get(stepRecord.getWfId());
        wfBean.getSteps().forEach(step -> {
            if (step.getNextStepLinks() != null) {
                step.getNextStepLinks().forEach(link -> {
                    if (link.getNextStepId().equals(stepRecord.getStepId())) {
                        ret.add(step);
                    }
                });
            }
        });
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecords(IWorkflowStepRecord stepRecord) {
        List<WorkflowStepRecordBean> ret = new ArrayList<>();
        WorkflowStepRecordBean recordBean = (WorkflowStepRecordBean) stepRecord;
        if (recordBean.getNextStepLinks() != null) {
            recordBean.getNextStepLinks()
                    .forEach(link -> {
                        WorkflowStepRecordBean step = stepBeans.get(link.getNextStepId());
                        if (step != null)
                            ret.add(step);
                    });
        }
        return ret;
    }


}
