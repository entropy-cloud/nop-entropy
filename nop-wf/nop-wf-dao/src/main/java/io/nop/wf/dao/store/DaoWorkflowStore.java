/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.LogLevel;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.sql.SQL;
import io.nop.core.utils.IVarSet;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.kv.KvTableVarSet;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.store.AbstractWorkflowStore;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.beans.WorkflowActionRecordBean;
import io.nop.wf.dao.entity.NopWfAction;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfLog;
import io.nop.wf.dao.entity.NopWfStepInstance;
import io.nop.wf.dao.entity.NopWfStepInstanceLink;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class DaoWorkflowStore extends AbstractWorkflowStore {
    private static final Logger LOG = LoggerFactory.getLogger(DaoWorkflowStore.class);

    private IDaoProvider daoProvider;

    private IOrmTemplate ormTemplate;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    // ==================== plan 2514 MA-02（裁定①）wfInstanceId 条带锁 ====================
    // plan 364 [05-02] DaoTaskStateStore.rowLock 范式投影（64 槽，有界内存；不同 wf 实例可能
    // 共享槽位被过度串行化，低频判定-迁移段可接受）。只覆盖单 JVM：多节点部署的跨进程残留
    // 由设计文档 §3.1 边界声明承接（DB 乐观锁兜底）。

    private static final int WF_LOCK_STRIPES = 64;
    private static final Object[] WF_LOCKS = newWfLocks();

    private static Object[] newWfLocks() {
        Object[] locks = new Object[WF_LOCK_STRIPES];
        for (int i = 0; i < WF_LOCK_STRIPES; i++)
            locks[i] = new Object();
        return locks;
    }

    @Override
    public <T> T withWfLock(String wfId, Supplier<T> action) {
        if (wfId == null)
            return action.get();
        Object lock = WF_LOCKS[(wfId.hashCode() & 0x7fffffff) % WF_LOCK_STRIPES];
        synchronized (lock) {
            return action.get();
        }
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
        stepRecord.setStepType(stepModel.getStepType().name());
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
        if (actionRecord instanceof NopWfAction) {
            actionDao().saveOrUpdateEntity((NopWfAction) actionRecord);
            return;
        }

        NopWfAction entity = actionDao().newEntity();
        entity.setSid(actionRecord.getSid());
        entity.setWfId(actionRecord.getWfId());
        entity.setStepId(actionRecord.getStepId());
        entity.setActionName(actionRecord.getActionName());
        entity.setDisplayName(actionRecord.getDisplayName());
        entity.setExecTime(actionRecord.getExecTime());
        entity.setCallerId(actionRecord.getCallerId());
        entity.setCallerName(actionRecord.getCallerName());
        entity.setOpinion(actionRecord.getOpinion());
        actionDao().saveOrUpdateEntity(entity);
    }

    @Override
    public List<? extends IWorkflowStepRecord> findActivatedStepsByOwner(String ownerId, Set<String> wfIds) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_ownerId, ownerId));
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
        if (wfIds != null && !wfIds.isEmpty()) {
            query.addFilter(FilterBeans.in(NopWfStepInstance.PROP_NAME_wfId, wfIds));
        }
        return stepDao().findAllByQuery(query);
    }

    @Override
    public List<? extends IWorkflowStepRecord> findDueActivatedSteps() {
        return stepDao().findAllByQuery(buildDueScanQuery(null, null));
    }

    @Override
    public List<? extends IWorkflowStepRecord> findRemindActivatedSteps() {
        return stepDao().findAllByQuery(buildRemindScanQuery(null, null));
    }

    /**
     * plan 2514 MA-05（裁定④）：due 扫描分批查询形状——limit(batchSize) + orderBy(dueTime asc)
     * + 游标谓词（dueTime &gt; cursor），消费侧按游标推进，消除全量驻留。C10-008 查询形状
     * 断言载体（包内可见供测试核验 limit/orderBy 存在）。
     */
    QueryBean buildDueScanQuery(Integer batchSize, Timestamp cursorDueTime) {
        return buildScanQuery(NopWfStepInstance.PROP_NAME_dueTime, batchSize, cursorDueTime);
    }

    /**
     * plan 2514 MA-05（裁定④）：remind 扫描分批查询形状，与 due 半区同构同修。
     */
    QueryBean buildRemindScanQuery(Integer batchSize, Timestamp cursorRemindTime) {
        return buildScanQuery(NopWfStepInstance.PROP_NAME_remindTime, batchSize, cursorRemindTime);
    }

    private QueryBean buildScanQuery(String timeProp, Integer batchSize, Timestamp cursorTime) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
        query.addFilter(FilterBeans.le(timeProp, CoreMetrics.currentTimestamp()));
        if (cursorTime != null) {
            // 分页游标：上批末条 time 为下批下界谓词（严格大于），无全量物化
            query.addFilter(FilterBeans.gt(timeProp, cursorTime));
        }
        if (batchSize != null) {
            query.setLimit(batchSize);
        }
        query.addOrderField(timeProp, false);
        return query;
    }

    @Override
    public List<? extends IWorkflowStepRecord> findDueActivatedSteps(int batchSize, Timestamp cursorDueTime) {
        if (batchSize <= 0)
            throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
        return stepDao().findAllByQuery(buildDueScanQuery(batchSize, cursorDueTime));
    }

    @Override
    public List<? extends IWorkflowStepRecord> findRemindActivatedSteps(int batchSize, Timestamp cursorRemindTime) {
        if (batchSize <= 0)
            throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
        return stepDao().findAllByQuery(buildRemindScanQuery(batchSize, cursorRemindTime));
    }

    // ==================== plan 2514 MA-23（裁定⑥）扫描抢占 fencing ====================
    // scanHolderId/scanLeaseTime 两列只经直发 UPDATE 语句读写（实体写路径列集约束，
    // 设计文档 §3.6），防止扫描会话陈旧实体全字段 save 覆写他节点抢占列。

    @Override
    public boolean tryClaimScanLease(IWorkflowStepRecord stepRecord, String holderId, Timestamp leaseUntil) {
        Timestamp now = CoreMetrics.currentTimestamp();
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_stepId, stepRecord.getStepId()));
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
        query.addFilter(FilterBeans.or(
                FilterBeans.isNull(NopWfStepInstance.PROP_NAME_scanLeaseTime),
                FilterBeans.lt(NopWfStepInstance.PROP_NAME_scanLeaseTime, now)));
        Map<String, Object> props = new HashMap<>();
        props.put(NopWfStepInstance.PROP_NAME_scanHolderId, holderId);
        props.put(NopWfStepInstance.PROP_NAME_scanLeaseTime, leaseUntil);
        // rowsAffected==1 赢单飞；==0 败者（调用方以 race skip INFO 形态显式跳过）
        return stepDao().updateByQuery(query, props) == 1;
    }

    @Override
    public void releaseScanLease(IWorkflowStepRecord stepRecord, String holderId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_stepId, stepRecord.getStepId()));
        query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_scanHolderId, holderId));
        Map<String, Object> props = new HashMap<>();
        props.put(NopWfStepInstance.PROP_NAME_scanHolderId, null);
        props.put(NopWfStepInstance.PROP_NAME_scanLeaseTime, null);
        stepDao().updateByQuery(query, props);
    }

    @Override
    public void incrementRemindCount(IWorkflowStepRecord stepRecord) {
        // DB 原子自增，消除双会话读-改-写丢失更新（裁定⑥条款③）
        SQL sql = SQL.begin()
                .update(NopWfStepInstance.class.getName())
                .sql(" set remindCount=COALESCE(remindCount,0)+1, remindTime=null")
                .where().eq(NopWfStepInstance.PROP_NAME_stepId, stepRecord.getStepId())
                .end();
        ormTemplate.executeUpdate(sql);
    }

    @Override
    public void postponeStepDueTime(IWorkflowStepRecord stepRecord, Timestamp newDueTime) {
        // 限定列集的直发 UPDATE（裁定④毒步骤退避 + 裁定⑥列集约束）
        SQL sql = SQL.begin()
                .update(NopWfStepInstance.class.getName())
                .sql(" set dueTime=").param(newDueTime)
                .where().eq(NopWfStepInstance.PROP_NAME_stepId, stepRecord.getStepId())
                .end();
        ormTemplate.executeUpdate(sql);
    }

    @Override
    public void saveTransferAction(IWorkflowStepRecord stepRecord, String fromOwnerId, String toOwnerId,
                                   String callerId, String callerName) {
        WorkflowActionRecordBean actionRecord = newManualActionRecord(stepRecord, "transfer", "transfer");
        actionRecord.setSid(StringHelper.generateUUID());
        actionRecord.setExecTime(CoreMetrics.currentTimestamp());
        actionRecord.setCallerId(callerId);
        actionRecord.setCallerName(callerName);
        actionRecord.setOpinion("ownerId: " + fromOwnerId + " -> " + toOwnerId);
        saveActionRecord(actionRecord);
    }

    @Override
    public List<? extends IWorkflowActionRecord> getActionRecords(IWorkflowStepRecord stepRecord) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfAction.PROP_NAME_stepId, stepRecord.getStepId()));
        return actionDao().findAllByQuery(query);
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
    public IVarSet getGlobalVars(IWorkflowRecord wfRecord) {
        NopWfInstance record = (NopWfInstance) wfRecord;
        return new KvTableVarSet(record.getGlobalVars());
    }

    @Override
    public IVarSet getOutputVars(IWorkflowRecord wfRecord) {
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
