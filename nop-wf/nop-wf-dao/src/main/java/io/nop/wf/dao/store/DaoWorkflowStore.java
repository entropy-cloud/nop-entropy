/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.LogLevel;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntitySet;
import io.nop.wf.core.IWorkflowVarSet;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.AbstractWorkflowStore;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.dao.entity.NopWfAction;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfLog;
import io.nop.wf.dao.entity.NopWfStepInstance;
import io.nop.wf.dao.entity.NopWfStepInstanceLink;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DaoWorkflowStore extends AbstractWorkflowStore {
    private static final Logger LOG = LoggerFactory.getLogger(DaoWorkflowStore.class);

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

    protected IEntityDao<NopWfAction> actionDao() {
        return daoProvider.daoFor(NopWfAction.class);
    }

    protected IEntityDao<NopWfLog> logDao() {
        return daoProvider.daoFor(NopWfLog.class);
    }

    @Override
    public Object loadBizEntity(String bizObjType, String bizEntityId) {
        if (StringHelper.isEmpty(bizObjType))
            return null;
        return daoProvider.dao(bizObjType).requireEntityById(bizEntityId);
    }
    @Override
    public IWorkflowRecord newWfRecord(IWorkflowModel wfModel) {
        NopWfInstance wfRecord = new NopWfInstance();
        wfRecord.setWfName(wfModel.getWfName());
        wfRecord.setWfVersion(wfModel.getWfVersion());
        String wfGroup = wfModel.getWfGroup();
        if (wfGroup == null)
            wfGroup = NopWfCoreConstants.DEFAULT_WF_GROUP;
        wfRecord.setWfGroup(wfGroup);
        wfRecord.setStatus(NopWfCoreConstants.WF_STATUS_CREATED);
        wfRecord.addTags(wfModel.getTagSet());
        wfRecord.setPriority(wfModel.getPriority());
        return wfRecord;
    }

    @Override
    public IWorkflowStepRecord newStepRecord(IWorkflowRecord wfRecord, IWorkflowStepModel stepModel) {
        NopWfStepInstance stepRecord = new NopWfStepInstance();
        stepRecord.setStepId(StringHelper.generateUUID());
        stepRecord.setStepName(stepModel.getName());
        stepRecord.setStepType(stepModel.getType().name());
        stepRecord.setDisplayName(stepModel.getDisplayName());
        if (StringHelper.isEmpty(stepRecord.getDisplayName()))
            stepRecord.setDisplayName(stepModel.getName());

        stepRecord.setWfInstance((NopWfInstance) wfRecord);
        stepRecord.setCreateTime(CoreMetrics.currentTimestamp());
        stepRecord.addTags(stepModel.getTagSet());
        stepRecord.setPriority(stepModel.getPriority());
        return stepRecord;
    }

    @Override
    public IWorkflowActionRecord newActionRecord(IWorkflowStepRecord stepRecord, IWorkflowActionModel actionModel) {
        NopWfAction actionRecord = new NopWfAction();
        actionRecord.setSid(StringHelper.generateUUID());
        actionRecord.setWfId(stepRecord.getWfId());
        actionRecord.setStepId(stepRecord.getStepId());
        actionRecord.setActionName(actionModel.getName());
        actionRecord.setDisplayName(actionModel.getDisplayName());
        if (StringHelper.isEmpty(actionRecord.getDisplayName()))
            actionRecord.setDisplayName(actionModel.getName());

        actionRecord.setWfStepInstance((NopWfStepInstance) stepRecord);
        actionRecord.setExecTime(CoreMetrics.currentTimestamp());
        return actionRecord;
    }

    @Override
    public void saveWfRecord(IWorkflowRecord wfRecord) {
        wfDao().saveOrUpdateEntity((NopWfInstance) wfRecord);
    }

    @Override
    public void removeWfRecord(IWorkflowRecord wfRecord) {
        wfDao().deleteEntity((NopWfInstance) wfRecord);
    }

    @Override
    public void saveStepRecord(IWorkflowStepRecord stepRecord) {
        NopWfStepInstance step = (NopWfStepInstance) stepRecord;
        IOrmEntitySet<NopWfStepInstance> steps = step.getWfInstance().getSteps();
        if (!steps.orm_proxy()) {
            steps.add(step);
        }
        stepDao().saveOrUpdateEntity(step);
    }

    @Override
    public void saveActionRecord(IWorkflowActionRecord actionRecord) {
        actionDao().saveOrUpdateEntity((NopWfAction) actionRecord);
    }

    @Override
    public void addNextStepRecord(IWorkflowStepRecord currentStep, String actionName, IWorkflowStepRecord nextStep) {
        NopWfStepInstance stepInstance = (NopWfStepInstance) currentStep;
        NopWfStepInstance nextInstance = (NopWfStepInstance) nextStep;
        NopWfStepInstanceLink link = stepInstance.addNextStepLink(nextStep.getStepId());
        link.setExecAction(actionName);

        nextInstance.getPrevLinks().add(link);
    }

    @Override
    public void addNextSpecialStep(IWorkflowStepRecord currentStep, String actionName, String specialStepId) {
        NopWfStepInstance stepInstance = (NopWfStepInstance) currentStep;
        stepInstance.addNextStepLink(specialStepId).setExecAction(actionName);
    }

    @Override
    public IWorkflowRecord getWfRecord(String wfName, Long wfVersion, String wfId) {
        return wfDao().getEntityById(wfId);
    }

    @Override
    public IWorkflowRecord reloadWfRecord(IWorkflowRecord wfRecord) {
        return wfDao().requireEntityById(wfRecord.getWfId());
    }

    @Override
    public IWorkflowVarSet getGlobalVars(IWorkflowRecord wfRecord) {
        NopWfInstance record = (NopWfInstance) wfRecord;
        return new KvTableVarSet(record.getGlobalVars());
    }

    @Override
    public IWorkflowVarSet getOutputVars(IWorkflowRecord wfRecord) {
        NopWfInstance record = (NopWfInstance) wfRecord;
        return new KvTableVarSet(record.getOutputs());
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void logError(IWorkflowRecord wfRecord, String stepId, String actionId,
                         String errorCode, Map<String, Object> params) {
        LOG.error("nop.error:errorCode={},params={}", errorCode, params);

        NopWfLog log = new NopWfLog();
        log.setWfId(wfRecord.getWfId());
        log.setStepId(stepId);
        log.setActionId(actionId);
        log.setLogLevel(LogLevel.ERROR.getLevel());
        log.setErrCode(errorCode);
        String locale = ContextProvider.currentLocale();
        log.setLogMsg(ErrorMessageManager.instance().getErrorDescription(locale, errorCode, params));

        logDao().saveEntityDirectly(log);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void logError(IWorkflowRecord wfRecord, String stepId, String actionId, Throwable e) {
        NopWfLog log = new NopWfLog();
        log.setWfId(wfRecord.getWfId());
        log.setStepId(stepId);
        log.setActionId(actionId);
        log.setLogLevel(LogLevel.ERROR.getLevel());
        String locale = ContextProvider.currentLocale();

        ErrorBean error = ErrorMessageManager.instance().buildErrorMessage(locale, e, false, false);
        log.setErrCode(error.getErrorCode());
        log.setLogMsg(error.getDescription());

        logDao().saveEntityDirectly(log);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void logMsg(IWorkflowRecord wfRecord, String stepId, String actionId, String msg) {
        NopWfLog log = new NopWfLog();
        log.setWfId(wfRecord.getWfId());
        log.setStepId(stepId);
        log.setActionId(actionId);
        log.setLogLevel(LogLevel.INFO.getLevel());
        log.setLogMsg(msg);

        logDao().saveEntityDirectly(log);
    }

    protected NopWfInstance getWfRecord(IWorkflowStepRecord stepRecord) {
        return ((NopWfStepInstance) stepRecord).getWfInstance();
    }

    protected Collection<? extends IWorkflowStepRecord> getSteps(IWorkflowRecord wfRecord) {
        return ((NopWfInstance) wfRecord).getSteps();
    }

    @Override
    public IWorkflowStepRecord getStepRecordById(IWorkflowRecord wfRecord, String stepId) {
        if (StringHelper.isEmpty(stepId) || stepId.startsWith(NopWfCoreConstants.SPECIAL_STEP_ID_PREFIX))
            return null;
        return stepDao().getEntityById(stepId);
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecords(IWorkflowStepRecord stepRecord) {
        List<IWorkflowStepRecord> ret = new ArrayList<>();
        NopWfStepInstance step = (NopWfStepInstance) stepRecord;

        step.getPrevLinks().forEach(link -> {
            ret.add(link.getWfStep());
        });

        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecords(IWorkflowStepRecord stepRecord) {
        List<IWorkflowStepRecord> ret = new ArrayList<>();
        NopWfStepInstance recordBean = (NopWfStepInstance) stepRecord;
        NopWfInstance wfRecord = getWfRecord(stepRecord);

        if (recordBean.getNextLinks() != null) {
            recordBean.getNextLinks()
                    .forEach(link -> {
                        IWorkflowStepRecord step = getStepRecordById(wfRecord, link.getNextStepId());
                        if (step != null)
                            ret.add(step);
                    });
        }
        return ret;
    }
}