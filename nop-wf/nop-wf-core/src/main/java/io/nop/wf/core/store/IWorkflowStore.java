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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    /**
     * plan 2514 MA-05（裁定④）：到期步骤分批查询（R02 分批范式）。返回按 dueTime 升序的至多
     * batchSize 条 ACTIVATED 到期步骤；cursorDueTime 非空时只返回 dueTime 严格大于该值的
     * 步骤（分页游标推进）。缺省实现保留全量语义，由支持分批的 store 覆盖。
     */
    default List<? extends IWorkflowStepRecord> findDueActivatedSteps(int batchSize, Timestamp cursorDueTime) {
        return findDueActivatedSteps();
    }

    /**
     * plan 2514 MA-05（裁定④）：提醒步骤分批查询，与 {@link #findDueActivatedSteps(int, Timestamp)}
     * 同构（按 remindTime 升序 + 游标推进）。
     */
    default List<? extends IWorkflowStepRecord> findRemindActivatedSteps(int batchSize, Timestamp cursorRemindTime) {
        return findRemindActivatedSteps();
    }

    /**
     * plan 2514 MA-02（裁定①）：wfInstanceId 级条带锁。在锁段内串行化同一工作流实例的
     * 判定-迁移操作，消除每请求 WorkflowImpl 快照互不可见导致的组迁移丢失/终结滞留/join
     * 双实例。缺省实现不加锁（无锁实现的 store 下退化为既有乐观锁语义），锁体由具体 store
     * 提供（DaoWorkflowStore 64 槽条带锁，DaoTaskStateStore.rowLock 同构范式）。
     * 禁止在 action 回调内再获取其他 wf 实例的 withWfLock（跨实例互调构成 ABBA 死锁面）。
     */
    default <T> T withWfLock(String wfId, Supplier<T> action) {
        return action.get();
    }

    /**
     * plan 2514 MA-23（裁定⑥）：扫描抢占（条件 UPDATE 语义）。仅当步骤仍处于 ACTIVATED
     * 且无未到期抢占租约时写入 holderId/租约并返回 true；否则返回 false（他节点/他线程已
     * 抢占）。缺省实现直通成功（单节点无 DB fencing 时保持既有语义），由 DaoWorkflowStore
     * 以条件 UPDATE 覆盖。
     */
    default boolean tryClaimScanLease(IWorkflowStepRecord stepRecord, String holderId, Timestamp leaseUntil) {
        return true;
    }

    /**
     * plan 2514 MA-23（裁定⑥）：释放本持有者的扫描抢占（租约清理）。缺省实现为空（无
     * fencing 列的 store 无需清理）。
     */
    default void releaseScanLease(IWorkflowStepRecord stepRecord, String holderId) {
    }

    /**
     * plan 2514 MA-23（裁定⑥）：remindCount 原子自增并清空 remindTime。缺省实现为进程内
     * 读-改-写（经 saveStepRecord），由 DaoWorkflowStore 以 DB 原子自增
     * （COALESCE(remindCount,0)+1）覆盖，消除多会话丢失更新。
     */
    default void incrementRemindCount(IWorkflowStepRecord stepRecord) {
        Integer count = stepRecord.getRemindCount();
        stepRecord.setRemindCount(count == null ? 1 : count + 1);
        stepRecord.setRemindTime(null);
        saveStepRecord(stepRecord);
    }

    /**
     * plan 2514 MA-05（裁定④）：毒步骤退避——due action 执行失败后按指数退避推进该步骤的
     * dueTime，使其退出本轮扫描集且重入频率指数衰减（dueTime 兼作下次重试时间，MA-06 接缝
     * 字段语义由本批定）。缺省实现经实体保存，DaoWorkflowStore 以限定列集的直发 UPDATE 覆盖
     * （裁定⑥实体写路径列集约束：避免陈旧实体全字段 save 覆写他节点抢占列）。
     */
    default void postponeStepDueTime(IWorkflowStepRecord stepRecord, Timestamp newDueTime) {
        stepRecord.setDueTime(newDueTime);
        saveStepRecord(stepRecord);
    }

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
