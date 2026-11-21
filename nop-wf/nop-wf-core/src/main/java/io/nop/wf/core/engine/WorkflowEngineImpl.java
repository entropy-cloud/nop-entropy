/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.model.graph.dag.Dag;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.api.actor.WfActorBean;
import io.nop.wf.api.actor.WfActorCandidatesBean;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.WorkflowTransitionTarget;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.IWorkflowArgumentsModel;
import io.nop.wf.core.model.IWorkflowConditionalModel;
import io.nop.wf.core.model.WfActionModel;
import io.nop.wf.core.model.WfArgVarModel;
import io.nop.wf.core.model.WfAssignmentModel;
import io.nop.wf.core.model.WfEndModel;
import io.nop.wf.core.model.WfExecGroupType;
import io.nop.wf.core.model.WfJoinType;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfModelAuth;
import io.nop.wf.core.model.WfRetryModel;
import io.nop.wf.core.model.WfReturnVarModel;
import io.nop.wf.core.model.WfSplitType;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.model.WfStepType;
import io.nop.wf.core.model.WfSubFlowArgModel;
import io.nop.wf.core.model.WfSubFlowStartModel;
import io.nop.wf.core.model.WfTransitionModel;
import io.nop.wf.core.model.WfTransitionToModel;
import io.nop.wf.core.model.WfTransitionToStepModel;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_TYPE;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ARG_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_REJECT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_STATUS;
import static io.nop.wf.core.NopWfCoreErrors.ARG_TARGET_CASES;
import static io.nop.wf.core.NopWfCoreErrors.ARG_TARGET_STEPS;
import static io.nop.wf.core.NopWfCoreErrors.ARG_TO_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ACTION_CONDITIONS_NOT_PASSED;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ACTION_NOT_ALLOWED_WHEN_SIGNAL_NOT_READY;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ACTION_TRANSITION_NO_NEXT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ACTOR_NOT_EXISTS;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ALREADY_STARTED;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_EMPTY_ACTION_ARG;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MISSING_STEP_INSTANCE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_REMOVE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_START;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_SUSPEND;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NO_ACTOR_ASSIGNED_FOR_TRANSFER;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_REJECT_ACTION_IS_NOT_ALLOWED;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_START_WF_FAIL;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_STEP_NO_ASSIGNMENT;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TARGET_CASES_NOT_MATCH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSIT_TO_NO_TARGETS;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION_ARG;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED;
import static io.nop.wf.core.engine.ExecGroupSupport.isSameExecGroup;

/**
 * 工作流引擎的核心处理逻辑
 */
public class WorkflowEngineImpl extends WfActorAssignSupport implements IWorkflowEngine {
    static final Logger LOG = LoggerFactory.getLogger(WorkflowEngineImpl.class);


    protected WfRuntime newWfRuntime(IWorkflowImplementor wf, IServiceContext ctx) {
        return new WfRuntime(wf, ctx);
    }

    // ==================== plan 2514 MA-02（裁定①）wfInstanceId 条带锁 ====================
    // 锁体经 IWorkflowStore.withWfLock 暴露（core 定义契约 / dao 提供 64 槽实现），
    // 引擎绝不直接引用 DaoWorkflowStore 具体类。锁序声明见设计文档 §3.1：
    // 禁止在 withWfLock 段内调用会获取其他 wf 条带锁的引擎入口（跨 wf 互调经 runXpl
    // 无锁窗自然无环）。
    //
    // plan 2585 Phase 1 全局锁序不变式（子→父单向）：跨 wf 条带锁嵌套仅允许
    // 「持子锁 → 取父锁」方向（子流程终结通知 notifySubFlowEnd 的父侧段），禁止
    // 「持父锁 → 取子锁」反向嵌套。核对结论：startSubFlow 启动的新子流程 wfId 为 null，
    // withWfLock 对 null id 直通不取子锁（plan 2514 :232-233 注释同口径），故父侧迁移段/
    // join 段内启动子流程不构成父→子锁嵌套；子流程终结的 endSubFlow 通知已移出
    // doEndWorkflow 锁段（reserveEndWorkflowLocked 只做终态预留，通知在锁外触发）。
    // 单向序成立 → 无环 → 无死锁面。

    private <T> T withWfLock(IWorkflowImplementor wf, Supplier<T> action) {
        String wfId = wf == null ? null : wf.getWfId();
        if (wfId == null)
            return action.get();
        return wf.getStore().withWfLock(wfId, action);
    }

    /**
     * 锁内重读：按 store 刷新 wf 实例与步骤的已提交状态，消除每请求 WorkflowImpl
     * 快照互不可见缺陷（判定输入不再复用请求初载值）。
     */
    private void refreshSnapshotsUnderLock(IWorkflowStepImplementor step, boolean includeExecGroup) {
        IWorkflowImplementor wf = step.getWorkflow();
        refreshWfStatusFromStore(wf);
        refreshStepStatusFromStore(step);
        if (includeExecGroup) {
            for (IWorkflowStep member : step.getStepsInSameExecGroup(true, true)) {
                refreshStepStatusFromStore((IWorkflowStepImplementor) member);
            }
        }
    }

    private static void refreshWfStatusFromStore(IWorkflowImplementor wf) {
        if (wf.getWfId() == null)
            return;
        IWorkflowRecord fresh = wf.getStore().getWfRecord(null, null, wf.getWfId());
        if (fresh == null || fresh == wf.getRecord())
            return;
        Integer freshStatus = fresh.getStatus();
        if (freshStatus != null && !freshStatus.equals(wf.getRecord().getStatus()))
            wf.getRecord().transitToStatus(freshStatus);
    }

    private static void refreshStepStatusFromStore(IWorkflowStepImplementor step) {
        IWorkflowImplementor wf = step.getWorkflow();
        if (wf.getWfId() == null || step.getStepId() == null)
            return;
        IWorkflowStepRecord fresh = wf.getStore().getStepRecordById(wf.getRecord(), step.getStepId());
        if (fresh == null || fresh == step.getRecord())
            return;
        Integer freshStatus = fresh.getStatus();
        if (freshStatus != null && !freshStatus.equals(step.getRecord().getStatus()))
            step.getRecord().transitToStatus(freshStatus);
    }

    protected WfRuntime newWfRuntime(IWorkflowStepImplementor step, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step.getWorkflow(), ctx);
        wfRt.setCurrentStep(step);
        wfRt.setActionStep(step);
        return wfRt;
    }

    @Override
    public void save(IWorkflowImplementor wf, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(wf, ctx);
        initCreateStatus(wfRt);
    }

    private void initCreateStatus(WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        IWorkflowRecord wfRecord = wf.getRecord();

        setDefaultTitle(wfRecord, wfRt);
        wfRecord.setLastOperateTime(CoreMetrics.currentTimestamp());

        if (wfRecord.getStatus() == null || wfRecord.getStatus() <= NopWfCoreConstants.WF_STATUS_CREATED) {
            wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_CREATED);
        }

        wfRecord.setStartTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wfRecord.setStarter(caller);
        wfRecord.setLastOperator(caller);
        wfRecord.setLastOperateTime(wfRecord.getStartTime());
    }

    @Override
    public boolean isAllowStart(IWorkflowImplementor wf, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowRecord record = wf.getRecord();
        if (record.getStatus() > NopWfCoreConstants.WF_STATUS_CREATED) {
            return false;
        }

        WfModel wfModel = (WfModel) wf.getModel();

        // 判断是否允许执行
        if (!passConditions(wfModel.getStart(), wfRt))
            return false;

        return true;
    }

    @Override
    public void start(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.start:wfName={},wfVersion={},args={}", wf.getWfName(), wf.getWfVersion(), args);

        // MA-02（裁定① saveWfRecord 6 调用点覆盖，:148/:470 写点）：start 入口段纳入 wf 条带锁。
        // 新建实例尚无 wfId（withWfLock 对 null id 直通），持久化后由锁保护并发驱动。
        withWfLock(wf, () -> {
            doStart(wf, args, ctx);
            return null;
        });
    }

    private void doStart(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowRecord record = wf.getRecord();
        if (record.getStatus() > NopWfCoreConstants.WF_STATUS_CREATED) {
            throw wfRt.newError(ERR_WF_ALREADY_STARTED);
        }

        WfModel wfModel = (WfModel) wf.getModel();

        args = removeStdStartParam(args, record);
        initArgs(wfModel.getStart(), args, NopWfCoreConstants.SYS_ACTION_START, wfRt);

        saveWfParams(args, wfModel, record);

        // 判断是否允许执行
        if (!passConditions(wfModel.getStart(), wfRt))
            throw wfRt.newError(ERR_WF_NOT_ALLOW_START);

        List<WfModelAuth> auths = wfModel.getAuths();
        if (auths != null) {
            for (WfModelAuth auth : auths) {
                if (auth.isAllowManage()) {
                    IWfActor actor = resolveActor(auth.getActorType(), auth.getActorId(), auth.getDeptId());
                    if (actor == null)
                        throw new NopException(ERR_WF_ACTOR_NOT_EXISTS)
                                .param(ARG_WF_NAME, wf.getWfName())
                                .param(ARG_WF_VERSION, wf.getWfVersion())
                                .param(ARG_ACTOR_TYPE, auth.getActorType())
                                .param(ARG_ACTOR_ID, auth.getActorId());
                    record.setManager(actor);
                    break;
                }
            }
        }

        initCreateStatus(wfRt);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_START);

        // 执行source
        runXpl(wfModel.getStart().getSource(), wfRt);

        List<WfActorWithWeight> actors = getActors(wfModel.getStartStep().getAssignment(), null, wfRt);

        // 迁移到起始步骤
        if (!newSteps(null, wfModel.getStartStep(), NopWfCoreConstants.SYS_ACTION_START, actors, wfRt))
            throw wfRt.newError(ERR_WF_START_WF_FAIL);

        saveStarted(wfRt);
    }

    private void saveWfParams(Map<String, Object> args, WfModel wfModel, IWorkflowRecord record) {
        if (args == null || args.isEmpty())
            return;

        Map<String, Object> params = new LinkedHashMap<>();
        for (WfArgVarModel argModel : wfModel.getStart().getArgs()) {
            if (args.containsKey(argModel.getName())) {
                params.put(argModel.getName(), args.get(argModel.getName()));
            }
        }
        record.setParams(params);
    }

    private Map<String, Object> removeStdStartParam(Map<String, Object> args, IWorkflowRecord wfRecord) {
        if (args == null || args.isEmpty())
            return args;

        args = new LinkedHashMap<>(args);
        // 安全类型转换（客户端可能传非字符串值导致ClassCastException），且仅在非空时写回，
        // 避免参数缺省时把调用方在record上已设置的值覆盖为null
        String title = ConvertHelper.toString(args.remove(NopWfCoreConstants.PARAM_TITLE));
        if (!StringHelper.isEmpty(title))
            wfRecord.setTitle(title);

        String bizObjName = ConvertHelper.toString(args.remove(NopWfCoreConstants.PARAM_BIZ_OBJ_NAME));
        if (!StringHelper.isEmpty(bizObjName))
            wfRecord.setBizObjName(bizObjName);

        String bizEntityId = ConvertHelper.toString(args.remove(NopWfCoreConstants.PARAM_BIZ_OBJ_ID));
        if (!StringHelper.isEmpty(bizEntityId)) {
            //VarCollector.instance().collectVar("NopWfInstance@bizEntityId",bizEntityId);
            wfRecord.setBizObjId(bizEntityId);
        }

        String bizKey = ConvertHelper.toString(args.remove(NopWfCoreConstants.PARAM_BIZ_KEY));
        if (!StringHelper.isEmpty(bizKey)) {
            //VarCollector.instance().collectVar("NopWfInstance@bizKey",bizKey);
            wfRecord.setBizKey(bizKey);
        }
        return args;
    }


    boolean newSteps(IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                     List<WfActorWithWeight> actors, WfRuntime wfRt) {
        if (actors == null)
            actors = Collections.emptyList();

        WfAssignmentModel assignment = stepModel.getAssignment();

        if (actors.isEmpty()) {
            // 如果没有指定assignment，则缺省使用manager或者系统用户
            if (assignment == null || (assignment.getActors().isEmpty() && !assignment.isIgnoreNoAssign())) {
                IWfActor manager = wfRt.getWf().getManagerActor();
                if (manager == null) {
                    manager = wfRt.getSysUser();
                }
                actors = Collections.singletonList(new WfActorWithWeight(manager, null, 1));
            }
        }

        if (actors.isEmpty()) {
            handleNoAssign(stepModel, wfRt);
            return false;
        }

        String stepGroup = StringHelper.generateUUID();
        int execOrder = 0;
        List<IWorkflowStepImplementor> createdSteps = new ArrayList<>();
        // 对每一个actor生成一个步骤实例
        for (WfActorWithWeight actor : actors) {
            IWorkflowStepImplementor newStep = newStepForActor(stepGroup, execOrder, currentStep, stepModel, fromAction, actor, null, wfRt);
            createdSteps.add(newStep);
            execOrder += 1000;
        }

        // SEQ_GROUP 顺序控制：仅 execOrder 最小的步骤保持 ACTIVATED，其余设为 WAITING
        if (stepModel.getExecGroupType() == WfExecGroupType.SEQ_GROUP && createdSteps.size() > 1) {
            for (int i = 1; i < createdSteps.size(); i++) {
                IWorkflowStepImplementor s = createdSteps.get(i);
                if (s.isActivated()) {
                    s.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
                    saveStepRecord(s);
                }
            }
        }

        return true;
    }

    private void handleNoAssign(WfStepModel stepModel, WfRuntime wfRt) {
        WfAssignmentModel assignment = stepModel.getAssignment();
        if (assignment != null && assignment.isIgnoreNoAssign()) {
            LOG.info("nop.wf.ignore-new-step-since-no-actor:wfName={},wfId={},stepName={}",
                    wfRt.getWf().getWfName(), wfRt.getWf().getWfId(), stepModel.getName());
            return;
        }
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_ON_NO_ASSIGN);
        throw wfRt.newError(ERR_WF_STEP_NO_ASSIGNMENT).param(ARG_STEP_NAME, stepModel.getName());
    }

    protected IWorkflowStepImplementor newStepForActor(String stepGroup, Integer execOrder,
                                                       IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                                                       WfActorWithWeight actorWithWeight, IWfActor owner, WfRuntime wfRt) {
        IWfActor actor = actorWithWeight.getActor();

        IWorkflowImplementor wf = wfRt.getWf();
        final String joinGroup = stepModel.getJoinType() == null ? null : getJoinGroup(stepModel, currentStep, wfRt);
        if (stepModel.getJoinType() != null) {
            // MA-02（裁定① join 双实例站点，and+or 两型）：锁内原子查建——同一 wfInstanceId
            // 条带锁内「查已存在 join 实例 → 复用或新建」串行化，双到达只产单实例。
            // 零 DDL 前提下不采 DB 唯一约束（设计文档 §3.1 join 行）。锁可重入：
            // 自迁移段（已持锁）调用时直接嵌套进入。
            if (currentStep != null) {
                return withWfLock(wf, () -> {
                    // join步骤会自动查找已经存在的步骤实例。
                    // 起始步骤本身为join步骤时无上游实例可复用（currentStep为null），直接新建实例
                    IWorkflowStepRecord stepRecord = wf.getStore().getNextJoinStepRecord(currentStep.getRecord(),
                            joinGroup, stepModel.getName(), actor);
                    if (stepRecord != null) {
                        IWorkflowStepImplementor step = wf.getStepByRecord(stepRecord);

                        stepRecord.setExecGroup(stepGroup);
                        stepRecord.setExecOrder(execOrder);
                        stepRecord.setActorModelId(actorWithWeight.getActorModelId());

                        if (!isSameExecGroup(currentStep, step)) {
                            wf.getStore().addNextStepRecord(currentStep.getRecord(), fromAction, step.getRecord());
                        }

                        // 处于等待状态的join步骤，新增加上游步骤之后需要检查是否可以转入激活状态
                        if (step.isWaiting()) {
                            wfRt.delayExecute(() -> checkWaitingJoinStep(step, wfRt));
                        }

                        LOG.info("nop.wf.enter-join-step-for-actor:wfName={},wfId={},stepName={},actor={}", wf.getWfName(), wf.getWfId(), stepModel.getName(), actor);
                        return step;
                    }

                    return newStepForActorLocked(stepGroup, execOrder, currentStep, stepModel, fromAction,
                            actorWithWeight, owner, joinGroup, wfRt);
                });
            }
        }

        return newStepForActorLocked(stepGroup, execOrder, currentStep, stepModel, fromAction,
                actorWithWeight, owner, joinGroup, wfRt);
    }

    /**
     * 步骤实例创建体（原 newStepForActor 查建后续段）。join 步骤经 withWfLock 锁内调用，
     * 非 join 步骤直接调用。
     */
    private IWorkflowStepImplementor newStepForActorLocked(String stepGroup, Integer execOrder,
                                                           IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                                                           WfActorWithWeight actorWithWeight, IWfActor owner, String joinGroup, WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        IWfActor actor = actorWithWeight.getActor();

        if (owner == null)
            owner = getOwner(stepModel.getAssignment(), actor, wfRt);

        IWorkflowStepRecord stepRecord = wf.getStore().newStepRecord(wf.getRecord(), stepModel);
        stepRecord.setExecGroup(stepGroup);
        stepRecord.setExecOrder(execOrder);
        // 持久化join-group-expr的计算值：getNextJoinStepRecord/getJoinWaitStepRecords依赖记录上的
        // joinGroup做合并匹配，此前从不写入导致配置了join-group-expr的join步骤无法复用，
        // 每条入边都新建实例，join后的步骤被执行N次
        stepRecord.setJoinGroup(joinGroup);
        stepRecord.setActorModelId(actorWithWeight.getActorModelId());
        stepRecord.setAssigner(wfRt.getAssigner());

        IWorkflowStepImplementor step = wf.getStepByRecord(stepRecord);
        stepRecord.setActor(actor);
        stepRecord.setOwner(owner);
        stepRecord.setVoteWeight(actorWithWeight.getVoteWeight());

        stepRecord.setFromAction(fromAction);

        runXpl(stepModel.getOnEnter(), wfRt);

        if (stepModel.getDueTimeExpr() != null && stepModel.getDueAction() != null) {
            Timestamp dueTime = ConvertHelper.toTimestamp(stepModel.getDueTimeExpr().invoke(wfRt));
            if (dueTime != null) {
                stepRecord.setDueTime(dueTime);
            }
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_ENTER_STEP);

        // 子流程步骤和需要等待合并的步骤新建时处于waiting状态，其他情况都是activated状态
        if (step.isFlowType()) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
            startSubFlow(stepModel.getStart(), step, wfRt);
        } else if (stepModel.getJoinType() == WfJoinType.and) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
        } else if (!wf.isAllSignalOn(stepModel.getWaitSignals())) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
        } else if (stepModel.isInitAsWaiting()) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
        } else {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        }

        saveStepRecord(step);

        // add step link after saving step。  同一个execGroup仅在addActor, transferToActor等调用时出现，同一个执行分组不需要建立连接
        if (currentStep != null && !isSameExecGroup(currentStep, step)) {
            wf.getStore().addNextStepRecord(currentStep.getRecord(), fromAction, step.getRecord());
        }

        // 处于等待状态的join步骤，新增加上游步骤之后需要检查是否可以转入激活状态
        if (stepModel.getJoinType() != null && step.isWaiting()) {
            wfRt.delayExecute(() -> checkWaitingJoinStep(step, wfRt));
        }

        LOG.info("nop.wf.new-step-for-actor:wfName={},wfId={},stepName={},actor={}",
                wf.getWfName(), wf.getWfId(), stepModel.getName(), actor);

        return step;
    }

    private String getJoinGroup(WfStepModel stepModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        if (stepModel.getJoinGroupExpr() != null) {
            IWorkflowStepImplementor oldStep = wfRt.getCurrentStep();
            try {
                wfRt.setCurrentStep(step);
                return ConvertHelper.toString(stepModel.getJoinGroupExpr().invoke(wfRt));
            } finally {
                wfRt.setCurrentStep(oldStep);
            }
        }
        return null;
    }

    private void startSubFlow(WfSubFlowStartModel startFlowModel, IWorkflowStepImplementor parentStep, WfRuntime wfRt) {

        Map<String, Object> vars = getStartArgs(startFlowModel.getArgs(), wfRt);

        WfReference wfRef = wfRt.getWf().getCoordinator().startSubFlow(startFlowModel.getWfName(), startFlowModel.getWfVersion(),
                parentStep.getStepReference(),
                vars, wfRt.getSvcCtx());

        parentStep.getRecord().setSubWfRef(wfRef);
    }

    private Map<String, Object> getStartArgs(List<WfSubFlowArgModel> argsModel, WfRuntime wfRt) {
        if (argsModel == null || argsModel.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> ret = new LinkedHashMap<>();
        for (WfSubFlowArgModel argModel : argsModel) {
            Object value = null;
            if (argModel.getSource() != null) {
                value = argModel.getSource().invoke(wfRt);
            }
            ret.put(argModel.getName(), value);
        }
        return ret;
    }

    private void saveStarted(WfRuntime wfRt) {
        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_START);

        WfModel wfModel = wfRt.getWfModel();
        if (!StringHelper.isEmpty(wfModel.getBizEntityFlowIdProp())) {
            IWorkflowRecord wfRecord = wfRt.getWf().getRecord();
            wfRt.getWf().getStore().bindBizEntityFlowId(wfRecord, wfModel.getBizEntityFlowIdProp());
        }
    }

    void setDefaultTitle(IWorkflowRecord wfRecord, WfRuntime wfRt) {
        String title = wfRecord.getTitle();
        if (StringHelper.isEmpty(title)) {
            title = wfRt.getWfModel().getDisplayName();
            if (StringHelper.isEmpty(title))
                title = wfRecord.getWfName();
            wfRecord.setTitle(title);
        }
    }

    private boolean checkWaitingJoinStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        if (!step.isWaiting())
            return false;

        LOG.info("nop.wf.check-waiting-join-step:wfName={},stepName={},stepId={}", step.getWfName(),
                step.getStepName(), step.getStepId());

        if (!wf.isAllSignalOn(step.getModel().getWaitSignals()))
            return false;

        // 如果是and-join, 则当它所等待的所有步骤都结束时它才进入activated状态。
        boolean allWaitFinished = true;
        for (IWorkflowStepImplementor waitStep : step.getJoinWaitSteps(wfRt)) {
            if (!waitStep.isHistory()) {
                allWaitFinished = false;
                break;
            }
        }

        if (allWaitFinished) {
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
            wfRt.triggerEvent(NopWfCoreConstants.EVENT_ACTIVATE_STEP);

            LOG.info("nop.wf.waiting-join-step-transit-to-activated:wfName={},stepName={},stepId={}", step.getWfName(),
                    step.getStepName(), step.getStepId());
            return true;
        }
        return false;
    }

    private Object runXpl(IEvalAction action, IWfRuntime wfRt) {
        if (action == null)
            return null;
        return action.invoke(wfRt);
    }

    private void initArgs(IWorkflowArgumentsModel argsModel, Map<String, Object> args,
                          String actionName, WfRuntime wfRt) {
        if (args == null) {
            args = Collections.emptyMap();
        }

        IWorkflow wf = wfRt.getWf();

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            // 为简化配置，假设总是允许几个内置的selectedActors参数
            if (name.equals(NopWfCoreConstants.VAR_SELECTED_ACTORS)) {
                wfRt.setSelectedActors(resolveActors(wf, entry.getValue()));
                continue;
            } else if (name.equals(NopWfCoreConstants.VAR_SELECTED_STEP_ACTORS)) {
                wfRt.setSelectedStepActors(resolveStepActors(wf, entry.getValue()));
                continue;
            } else if (name.equals(NopWfCoreConstants.VAR_REJECT_STEPS)) {
                wfRt.setRejectSteps(ConvertHelper.toCsvSet(entry.getValue()));
                continue;
            } else if (name.equals(NopWfCoreConstants.VAR_TARGET_STEPS)) {
                wfRt.setTargetSteps(ConvertHelper.toCsvSet(entry.getValue()));
                continue;
            }

            WfArgVarModel argModel = argsModel.getArg(name);
            if (argModel == null) {
                throw wfRt.newError(ERR_WF_UNKNOWN_ACTION_ARG)
                        .param(ARG_WF_NAME, wfRt.getWfModel().getWfName())
                        .param(ARG_ACTION_NAME, actionName).param(ARG_ARG_NAME, name);
            }

            Object value = entry.getValue();

            ISchema schema = argModel.getSchema();
            if (schema != null && value != null) {
                if (schema.isSimpleSchema()) {
                    SimpleSchemaValidator.INSTANCE.validate(schema, null, wf.getBizObjName(), name, value, wfRt.getEvalScope(),
                            IValidationErrorCollector.THROW_ERROR);
                }
                IGenericType type = schema.getType();
                if (type != null) {
                    value = ConvertHelper.convertTo(type.getRawClass(), value, NopException::new);
                }
            }
            // arg可无schema（xdef中为可选），无schema/空值的参数同样写入求值作用域：
            // 参数已通过已知名/mandatory/persist检查却不生效属契约漂移
            wfRt.setValue(name, value);
        }

        for (WfArgVarModel argModel : argsModel.getArgs()) {
            String name = argModel.getName();
            if (argModel.isMandatory()) {
                Object value = args.get(name);
                if (StringHelper.isEmptyObject(value))
                    throw wfRt.newError(ERR_WF_EMPTY_ACTION_ARG)
                            .param(ARG_ACTION_NAME, actionName).param(ARG_ARG_NAME, name);
            }
            if (argModel.isPersist()) {
                wf.getGlobalVars().setVar(name, args.get(name));
            }
        }
    }

    private Map<String, List<IWfActor>> resolveStepActors(IWorkflow wf, Object value) {
        if (value == null)
            return Collections.emptyMap();

        Map<String, Object> map = (Map<String, Object>) value;
        Map<String, List<IWfActor>> ret = CollectionHelper.newHashMap(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object v = entry.getValue();
            List<IWfActor> actors = resolveActors(wf, v);
            ret.put(entry.getKey(), actors);
        }
        return ret;
    }

    private List<IWfActor> resolveActors(IWorkflow wf, Object value) {
        if (value == null)
            return Collections.emptyList();

        List<Object> list = CollectionHelper.toList(value);

        List<WfActorBean> actors = new ArrayList<>(list.size());
        for (Object obj : list) {
            if (obj instanceof WfActorBean) {
                actors.add((WfActorBean) obj);
            } else {
                actors.add(BeanTool.castBeanToType(obj, WfActorBean.class));
            }
        }
        List<IWfActor> ret = new ArrayList<>(actors.size());
        for (WfActorBean actorInfo : actors) {
            IWfActor actor = resolveActor(actorInfo.getActorType(), actorInfo.getActorId(), actorInfo.getDeptId());
            if (actor == null)
                throw new NopException(ERR_WF_ACTOR_NOT_EXISTS)
                        .param(ARG_WF_NAME, wf.getWfName())
                        .param(ARG_WF_VERSION, wf.getWfVersion())
                        .param(ARG_ACTOR_TYPE, actorInfo.getActorType())
                        .param(ARG_ACTOR_ID, actorInfo.getActorId());
            ret.add(actor);
        }
        return ret;
    }

    private boolean passConditions(IWorkflowConditionalModel condition, IEvalContext scope) {
        if (condition.getWhen() == null)
            return true;
        return condition.getWhen().passConditions(scope);
    }


    @Override
    public void suspend(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.suspend:wfName={},wfId={}", wf.getWfName(), wf.getWfId());

        // MA-02（裁定① saveWfRecord 6 调用点覆盖，:659 写点）：入口段纳入 wf 条带锁
        withWfLock(wf, () -> {
            doSuspend(wf, args, ctx);
            return null;
        });
    }

    private void doSuspend(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        if (wf.isSuspended())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        if (wf.isEnded())
            throw wfRt.newError(ERR_WF_NOT_ALLOW_SUSPEND);

        initArgs(wfRt, args);
        checkManageAuth(wfRt);

        wf.getRecord().setLastOperateTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wf.getRecord().setLastOperator(caller);

        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_SUSPENDED);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_SUSPEND);
    }

    void checkManageAuth(WfRuntime wfRt) {
        runXpl(wfRt.getWfModel().getCheckManageAuth(), wfRt);
    }

    @Override
    public void resume(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.resume:wfName={},wfId={}", wf.getWfName(), wf.getWfId());

        // MA-02（裁定① saveWfRecord 6 调用点覆盖，:682 写点）：入口段纳入 wf 条带锁
        withWfLock(wf, () -> {
            doResume(wf, args, ctx);
            return null;
        });
    }

    private void doResume(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        if (!wf.isSuspended())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        initArgs(wfRt, args);
        checkManageAuth(wfRt);

        wf.getRecord().setLastOperateTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wf.getRecord().setLastOperator(caller);

        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_RESUME);
    }

    private void initArgs(WfRuntime wfRt, Map<String, Object> args) {
        if (args != null) {
            // wf/wfRt/wfVars是WfRuntime构造的内置变量：客户端args可覆盖它们污染求值上下文
            // （checkManageAuth/listener/source XPL读到攻击者控制的对象），过滤保留名
            args.forEach((name, value) -> {
                if (NopWfCoreConstants.VAR_WF.equals(name)
                        || NopWfCoreConstants.VAR_WF_RT.equals(name)
                        || NopWfCoreConstants.VAR_WF_VARS.equals(name))
                    return;
                wfRt.getEvalScope().setLocalValue(name, value);
            });
        }
    }

    @Override
    public void remove(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.remove:wfName={},wfId={}", wf.getWfName(), wf.getWfId());

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        if (!wf.isEnded() && wf.isStarted())
            throw wfRt.newError(ERR_WF_NOT_ALLOW_REMOVE);

        initArgs(wfRt, args);
        checkManageAuth(wfRt);

        wf.getStore().removeWfRecord(wf.getRecord());
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_REMOVE);
    }

    @Override
    public void kill(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.kill:wfName={},wfId={}", wf.getWfName(), wf.getWfId());

        WfRuntime wfRt = newWfRuntime(wf, ctx);

        // MA-02（裁定①）：killSteps 驱动段纳入 wf 条带锁。
        // plan 2585 Phase 1：终结段（doEndWorkflow）移出锁外——锁内只做 kill + markEnd，
        // end 脚本与子流程通知在锁释放后执行，避免子→父通知在锁内嵌套取父锁。
        boolean killed = withWfLock(wf, () -> doKill(wf, wfRt, args, ctx));

        if (killed) {
            doEndWorkflow(NopWfCoreConstants.WF_STATUS_KILLED, wfRt);
            wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_KILL);
        }
    }

    private boolean doKill(IWorkflowImplementor wf, WfRuntime wfRt, Map<String, Object> args, IServiceContext ctx) {
        if (wf.isEnded())
            return false;

        initArgs(wfRt, args);
        checkManageAuth(wfRt);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_KILL);

        killSteps(wfRt);

        wfRt.markEnd();
        return true;
    }

    @Override
    public void turnSignalOn(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx) {
        LOG.info("nop.wf.turn-signals-on:wfName={},wfId={},signals={}", wf.getWfName(), wf.getWfId(), signals);

        if (signals == null || signals.isEmpty())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowStore wfStore = wf.getStore();

        wfStore.addSignals(wf.getRecord(), signals);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_SIGNAL_ON);

        wf.delayExecute(() -> {
            checkWaitingSteps(wf, wfRt);
        });
    }

    // signal变化后检查是否有步骤需要被激活
    protected void checkWaitingSteps(IWorkflowImplementor wf, WfRuntime wfRt) {
        for (IWorkflowStepImplementor step : wf.getWaitingSteps()) {
            checkWaitingStep(step, wfRt);
        }
    }

    protected boolean checkWaitingStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        IWorkflowImplementor wf = step.getWorkflow();
        if (step.getModel().getJoinType() != null) {
            return checkWaitingJoinStep(step, wfRt);
        } else {
            if (!step.isWaiting())
                return false;

            // SEQ_GROUP成员只允许由ExecGroupSupport.activateNextSeqStep按execOrder顺序激活，
            // 转办等待(moveStepToWaiting)的步骤已记录finishTime且由新步骤的完成驱动，
            // 二者都不允许被与本步骤无关的signal事件提前激活
            if (step.getExecGroupType() == WfExecGroupType.SEQ_GROUP)
                return false;
            if (step.getRecord().getFinishTime() != null)
                return false;

            if (!wf.isAllSignalOn(step.getModel().getWaitSignals()))
                return false;

            if (step.isFlowType()) {
                // 子流程尚未结束
                if (step.getRecord().getSubWfResultStatus() == null)
                    return false;
            }

            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
            wfRt.triggerEvent(NopWfCoreConstants.EVENT_ACTIVATE_STEP);
            return true;
        }
    }

    @Override
    public void turnSignalOff(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx) {
        LOG.info("nop.wf.turn-signals-off:wfName={},wfId={},signals={}", wf.getWfName(), wf.getWfId(), signals);

        if (signals == null || signals.isEmpty())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowStore wfStore = wf.getStore();

        if (wfStore.removeSignals(wf.getRecord(), signals)) {
            wfRt.triggerEvent(NopWfCoreConstants.EVENT_SIGNAL_OFF);
        }
    }

    @Override
    public boolean runAutoTransitions(IWorkflowImplementor wf, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(wf, ctx);

        boolean ret = false;
        for (IWorkflowStepImplementor step : wf.getActivatedSteps()) {
            boolean hasTrans = runStepAutoTransition(step, wfRt);
            if (hasTrans)
                ret = true;
        }


        return ret;
    }

    private boolean runStepAutoTransition(IWorkflowStepImplementor step, WfRuntime wfRt) {
        if (!step.isActivated())
            return false;

        wfRt.setCurrentStep(step);
        WfStepModel stepModel = (WfStepModel) step.getModel();
        try {
            if (step.getRecord().getStatus() < NopWfCoreConstants.WF_STEP_STATUS_EXECUTED) {
                // EXECUTED状态表示source已执行完毕、仅等待迁移条件满足，不允许重复执行source。
                // 此前条件为<=EXECUTED，迁移受阻的EXECUTED步骤会在每轮自动迁移时重复执行source
                runSourceWithRetry(step, stepModel, wfRt);
                step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_EXECUTED);
                saveStepRecord(step);
            }

            if (stepModel.getTransition() != null) {
                Set<String> onStates = stepModel.getTransition().getOnAppStates();
                if (onStates != null && !onStates.isEmpty()) {
                    if (!onStates.contains(step.getRecord().getAppState())) {
                        LOG.info("nop.wf.step-not-allow-transition-when-state-not-ready:stepName={},state={},onStates={},wfName={},wfId={}",
                                step.getModel().getName(), step.getRecord().getAppState(), onStates, wfRt.getWf().getWfName(), wfRt.getWf().getWfId());
                        return false;
                    }
                }
                boolean hasTrans = this.doTransition(step, NopWfCoreConstants.INTERNAL_ACTION_TRANSIT,
                        stepModel.getTransition(), wfRt);
                if (hasTrans) {
                    if (!step.isHistory()) {
                        int status = NopWfCoreConstants.WF_STEP_STATUS_COMPLETED;
                        doExitStep(step, status, wfRt);
                    }
                }
                return hasTrans;
            }
            return false;
        } catch (Exception e) {
            this.handleError(e, NopWfCoreConstants.INTERNAL_ACTION_TRANSIT, stepModel, wfRt);
            return false;
        }
    }

    protected Object runSource(WfStepModel stepModel, IWfRuntime wfRt) {
        return FutureHelper.tryResolve(runXpl(stepModel.getSource(), wfRt));
    }

    /**
     * 按wf.xdef的<retry>规格执行步骤source：maxRetryCount（含首次共执行maxRetryCount+1次）、
     * retryDelay/maxRetryDelay/exponentialDelay（毫秒指数退避，maxRetryDelay=0不封顶）、
     * exception-filter（谓词返回false表示异常不可恢复，立即终止重试）。
     * 每次尝试（含重试）对该步骤记录递增execCount。重试耗尽后抛原始异常。
     */
    private Object runSourceWithRetry(IWorkflowStepImplementor step, WfStepModel stepModel, WfRuntime wfRt) {
        // execCount语义为source执行计数：无source配置的步骤不计数
        if (stepModel.getSource() == null)
            return null;
        WfRetryModel retry = stepModel.getRetry();
        int maxRetryCount = retry == null ? 0 : retry.getMaxRetryCount();
        int attempt = 0;
        while (true) {
            step.getRecord().incExecCount();
            try {
                return runSource(stepModel, wfRt);
            } catch (Exception e) {
                if (attempt >= maxRetryCount || !isRetryableException(e, retry, wfRt))
                    throw e;
                long delay = computeRetryDelay(retry, attempt);
                if (delay > 0) {
                    // W-12（CRA 8.48 R4-08，MA-22 裁定 F）：活动事务上下文中延迟等待降级不重试——
                    // Thread.sleep 内联于请求事务（连接/行锁全程持有、不可取消）为更劣形态；
                    // 显式 WARN 留痕后抛原业务异常（定时器异步退避否决——2497 §七 timer 只触发
                    // 约束以「不新增定时器面」守约落款；事务检测经 IWorkflowStore 接口投影，
                    // txnGroup=null 时由 DaoWorkflowStore 解析 wf 实体 querySpace）
                    if (wfRt.getWf().getStore().hasOpenedTransaction(null)) {
                        LOG.warn("nop.wf.retry-degraded-in-transaction:wfName={},wfId={},stepName={},maxRetryCount={}; "
                                        + "step retry delay skipped inside an open transaction "
                                        + "(retry semantics require a non-transactional context)",
                                step.getWfName(), step.getWfId(), stepModel.getName(), maxRetryCount);
                        throw e;
                    }
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        // 中断放弃重试，传播原始业务异常
                        throw e;
                    }
                }
                attempt++;
                LOG.info("nop.wf.retry-step-source:wfName={},wfId={},stepName={},attempt={},maxRetryCount={}",
                        step.getWfName(), step.getWfId(), step.getStepName(), attempt, maxRetryCount);
            }
        }
    }

    private boolean isRetryableException(Exception e, WfRetryModel retry, WfRuntime wfRt) {
        if (retry == null)
            return false;
        IEvalPredicate filter = retry.getExceptionFilter();
        if (filter == null)
            return true;
        // 按xdef契约，异常以$exception变量暴露给exception-filter
        wfRt.getEvalScope().setLocalValue(null, "$exception", e);
        try {
            return filter.passConditions(wfRt);
        } finally {
            wfRt.getEvalScope().removeLocalValue("$exception");
        }
    }

    private long computeRetryDelay(WfRetryModel retry, int attempt) {
        long delay = retry.getRetryDelay();
        if (delay <= 0)
            return 0;
        if (retry.isExponentialDelay()) {
            delay = delay * (1L << Math.min(attempt, 30));
        }
        // W-12（CRA 8.48 R4-08，MA-22 裁定 F）：封顶统一对固定 delay 与 exponential 两分支生效
        //（对齐 task 侧 RetryPolicy 两分支均封顶先例——修复前固定 delay 路径不应用 maxRetryDelay）；
        // maxRetryDelay<=0 维持不封顶（未配置语义，存量长退避行为零变更——E5 显式声明）
        int maxRetryDelay = retry.getMaxRetryDelay();
        if (maxRetryDelay > 0)
            delay = Math.min(delay, maxRetryDelay);
        return delay;
    }

    @Override
    public void changeActor(IWorkflowStepImplementor step, IWfActor actor, IServiceContext ctx) {
        LOG.info("nop.wf.change-actor:wfName={},wfId={},stepName={},stepId={},actor={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(), actor);

        WfRuntime wfRt = newWfRuntime(step, ctx);
        step.getRecord().setActor(actor);
        saveStepRecord(step);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public void changeOwner(IWorkflowStepImplementor step, String ownerId, IServiceContext ctx) {
        LOG.info("nop.wf.change-owner:wfName={},wfId={},stepName={},stepId={},ownerId={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(), ownerId);

        WfRuntime wfRt = newWfRuntime(step, ctx);
        // 非空 ownerId 必须解析到真实用户：不允许指向不存在的用户时静默清空 owner 使任务失去归属
        IWfActor owner = StringHelper.isEmpty(ownerId) ? null : requireUser(ownerId, wfRt);
        step.getRecord().setOwner(owner);
        saveStepRecord(step);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public IWorkflowStepImplementor transferToActor(IWorkflowStepImplementor step, WfActorAndOwner actorAndOwner,
                                                    boolean exitCurrentStep, IServiceContext ctx) {
        LOG.info("nop.wf.transfer-to-actor:wfName={},wfId={},stepName={},stepId={},actorType={},actorId={},ownerId={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(),
                actorAndOwner.getActorType(), actorAndOwner.getActorId(), actorAndOwner.getOwnerId());

        WfRuntime wfRt = newWfRuntime(step, ctx);
        IWfActor owner = StringHelper.isEmpty(actorAndOwner.getOwnerId()) ?
                null : requireUser(actorAndOwner.getOwnerId(), wfRt);

        wfRt.setAssigner(wfRt.getCaller());

        IWfActor actor = null;
        if (!StringHelper.isEmpty(actorAndOwner.getActorType())) {
            actor = resolveDynamicActor(actorAndOwner, wfRt);
            if (actor == null)
                throw wfRt.newError(ERR_WF_ACTOR_NOT_EXISTS)
                        .param(ARG_ACTOR_TYPE, actorAndOwner.getActorType())
                        .param(ARG_ACTOR_ID, actorAndOwner.getActorId());
        }

        if (actor == null && owner == null)
            throw wfRt.newError(ERR_WF_NO_ACTOR_ASSIGNED_FOR_TRANSFER);

        if (actor == null)
            actor = step.getActor();

        WfActorWithWeight actorWithWeight = new WfActorWithWeight(actor,
                step.getRecord().getActorModelId(), step.getRecord().getVoteWeight());

        // 新建步骤
        IWorkflowStepImplementor nextStep = newStepForActor(step.getRecord().getExecGroup(),
                step.getRecord().getExecOrder(), step, (WfStepModel) step.getModel(),
                NopWfCoreConstants.INTERNAL_ACTION_TRANSFER_TO_ACTOR, actorWithWeight, owner, wfRt);

        IWfActor caller = wfRt.getCaller();
        if (caller != null) {
            step.getRecord().setCaller(caller);
            step.getRecord().setAssigner(caller);
        }

        if (exitCurrentStep) {
            nextStep.getRecord().setNextStepId(step.getRecord().getNextStepId());
            this.doExitStep(step, NopWfCoreConstants.WF_STEP_STATUS_TRANSFERRED, wfRt);
        } else {
            nextStep.getRecord().setNextStepId(step.getStepId());
            this.moveStepToWaiting(step, wfRt);
        }
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_TRANSFER_TO_ACTOR);

        return nextStep;
    }

    private void moveStepToWaiting(IWorkflowStepImplementor step, WfRuntime wfRt) {
        if (!step.isHistory()) {
            WfStepModel stepModel = (WfStepModel) step.getModel();
            step.getRecord().setFinishTime(CoreMetrics.currentTimestamp());
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);

            IWorkflowStepImplementor currentStep = wfRt.getCurrentStep();
            try {
                wfRt.setCurrentStep(step);
                saveStepRecord(step);

                wfRt.triggerEvent(NopWfCoreConstants.EVENT_CHANGE_STATUS);
            } finally {
                wfRt.setCurrentStep(currentStep);
            }
        }
    }

    @Override
    public IWorkflowStepImplementor addActor(IWorkflowStepImplementor step, WfActorAndOwner actorAndOwner,
                                             IServiceContext ctx) {
        LOG.info("nop.wf.add-actor:wfName={},wfId={},stepName={},stepId={},actorType={},actorId={},ownerId={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(),
                actorAndOwner.getActorType(), actorAndOwner.getActorId(), actorAndOwner.getOwnerId());

        WfRuntime wfRt = newWfRuntime(step, ctx);
        IWfActor owner = StringHelper.isEmpty(actorAndOwner.getOwnerId()) ?
                null : requireUser(actorAndOwner.getOwnerId(), wfRt);

        wfRt.setAssigner(wfRt.getCaller());

        IWfActor actor = null;
        if (!StringHelper.isEmpty(actorAndOwner.getActorType())) {
            actor = resolveDynamicActor(actorAndOwner, wfRt);
            if (actor == null)
                throw wfRt.newError(ERR_WF_ACTOR_NOT_EXISTS)
                        .param(ARG_ACTOR_TYPE, actorAndOwner.getActorType())
                        .param(ARG_ACTOR_ID, actorAndOwner.getActorId());
        }

        if (actor == null && owner == null)
            throw wfRt.newError(ERR_WF_NO_ACTOR_ASSIGNED_FOR_TRANSFER);

        if (actor == null)
            actor = step.getActor();

        WfActorWithWeight actorWithWeight = new WfActorWithWeight(actor,
                step.getRecord().getActorModelId(), step.getRecord().getVoteWeight());

        // 新建步骤
        IWorkflowStepImplementor nextStep = newStepForActor(step.getRecord().getExecGroup(),
                step.getRecord().getExecOrder(), step, (WfStepModel) step.getModel(),
                NopWfCoreConstants.INTERNAL_ADD_ACTOR, actorWithWeight, owner, wfRt);

        IWfActor caller = wfRt.getCaller();
        if (caller != null) {
            step.getRecord().setCaller(caller);
            step.getRecord().setAssigner(caller);
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_ADD_ACTOR);

        return nextStep;
    }


    @Override
    public void triggerStepEvent(IWorkflowStepImplementor step, String eventName, IServiceContext ctx) {

    }

//    @Override
//    public void killStep(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
//        LOG.info("nop.wf.kill-step:wfName={},wfId={},stepName={},stepId={}",
//                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId());
//
//        if (step.isHistory())
//            return;
//
//        WfRuntime wfRt = newWfRuntime(step, ctx);
//
//        _killStep(step, wfRt);
//
//        wfRt.delayExecute(() -> {
//            checkEnd(wfRt);
//        });
//    }

    void _killStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        this.doExitStep(step, NopWfCoreConstants.WF_STEP_STATUS_KILLED, wfRt);
        // wfRt.triggerEvent(NopWfCoreConstants.EVENT_KILL_STEP);
    }

    @Override
    public boolean triggerTransition(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.trigger-transition:wfName={},wfId={},stepName={}",
                step.getWorkflow().getWfName(), step.getWorkflow().getWfId(), step.getStepName());

        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);
        return runStepAutoTransition(step, wfRt);
    }

    @Override
    public boolean triggerWaiting(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.trigger-waiting:wfName={},wfId={},stepName={}",
                step.getWorkflow().getWfName(), step.getWorkflow().getWfId(), step.getStepName());

        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);
        return checkWaitingStep(step, wfRt);
    }

    @Override
    public void notifySubFlowEnd(IWorkflowStepImplementor step, int status, Map<String, Object> results,
                                 IServiceContext ctx) {
        IWorkflowImplementor wf = step.getWorkflow();

        if (!step.isFlowType())
            throw new NopException(ERR_WF_MISSING_STEP_INSTANCE).param(ARG_WF_NAME, wf.getWfName())
                    .param(ARG_WF_VERSION, wf.getWfVersion()).param(ARG_WF_ID, wf.getWfId())
                    .param(ARG_STEP_NAME, step.getStepName())
                    .param(ARG_STEP_ID, step.getStepId());

        if (step.isHistory())
            return;

        // MA-02（裁定① 子流回滚重放站点）：父流程侧通知段纳入父 wf 条带锁 + 锁内重读父步骤
        // 已提交状态——兄弟子流结束串行化后只有一次有效迁移，输家不再以乐观锁异常丢整个事务，
        // 另一侧见状态已迁移走幂等短路（INFO 留痕）。
        withWfLock(wf, () -> {
            IWorkflowStepRecord freshRecord = wf.getStore().getStepRecordById(wf.getRecord(), step.getStepId());
            if (freshRecord == null || freshRecord.isHistory()) {
                LOG.info("nop.wf.skip-subflow-end-since-parent-step-migrated:wfId={},stepId={}",
                        wf.getWfId(), step.getStepId());
                return null;
            }

            step.getRecord().setSubWfResultStatus(status);

            Map<String, Object> args = new HashMap<>();
            args.put(NopWfCoreConstants.VAR_SUB_WF_RESULTS, results);

            // 触发工作流引擎检查step的状态检查，再根据状态触发auto action实现变迁
            step.triggerWaiting(args, ctx);
            step.triggerTransition(args, ctx);
            return null;
        });
    }

    @Override
    public boolean allowCallByUser(IWorkflowStepImplementor step, IServiceContext ctx) {
        // P1-3.1-002（plan 2379 Phase 1）：系统动作触发标记放行——dueAction 超时触发的主体是
        // 系统（WfTaskScanner 固定虚拟身份），步骤动作名来自流程定义而非用户输入。标记仅
        // 服务端内部组件可设（RPC 入口 context 由框架构建，用户不可伪造）。
        // 标记读取与 WfTaskScanner 的设置侧同层（内层 IContext——与 userId 同一存放位置）
        if (ctx.getContext() != null
                && Boolean.TRUE.equals(ctx.getContext().getAttribute(NopWfCoreConstants.ATTR_WF_SYSTEM_ACTION_TRIGGER)))
            return true;

        IWfActor owner = step.getOwner();
        String userId = ctx.getUserId();

        if (owner != null) {
            if (owner.getActorId().equals(userId))
                return true;

            return canBeDelegatedBy(step, userId);
        }

        IWfActor actor = step.getActor();
        // actor 指向的用户/部门/角色可能已被删除（resolver 返回 null），此时无人可调用该步骤
        if (actor == null)
            return false;

        if (IWfActor.ACTOR_TYPE_USER.equals(actor.getActorType())) {
            if (actor.getActorId().equals(userId))
                return true;

            return canBeDelegatedBy(step, userId);
        }
        return actor.containsUser(userId);
    }

    @Override
    public List<? extends IWorkflowActionModel> getAllowedActions(IWorkflowStepImplementor step, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        List<WfActionModel> ret = new ArrayList<>();
        WfStepModel stepModel = (WfStepModel) step.getModel();
        WfModel wfModel = (WfModel) step.getWorkflow().getModel();
        for (IWorkflowActionModel actionModel : stepModel.getActions()) {
            WfActionModel actModel = (WfActionModel) actionModel;
            try {
                checkActionAuth(wfModel, wfRt);
                if (checkAllowedAction(actModel, step, wfRt) == null) {
                    ret.add(actModel);
                }
            } catch (NopException e) {
                // 权限校验失败
                continue;
            } catch (Exception e) {
                // 鉴权XPL抛出非NopException时跳过该action（列表接口不应因单个action的配置问题整体失败）
                LOG.debug("nop.wf.get-allowed-actions-fail:wfName={},stepName={},actionName={}",
                        step.getWfName(), step.getStepName(), actModel.getName(), e);
            }
        }
        return ret;
    }

    @Override
    public Object invokeAction(IWorkflowStepImplementor step, String actionName, Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.invoke-action:wfName={},wfId={},actionName={},args={}", step.getWfName(), step.getWfId(), actionName, args);

        WfRuntime wfRt = newWfRuntime(step, ctx);
        WfModel wfModel = wfRt.getWfModel();

        WfActionModel actionModel = requireActionModel(wfRt, actionName);

        ErrorCode errorCode = checkAllowedAction(actionModel, step, wfRt);
        if (errorCode != null)
            throw wfRt.newError(errorCode).param(ARG_ACTION_NAME, actionModel.getName());

        checkActionAuth(wfModel, wfRt);

        initArgs(actionModel, args, actionName, wfRt);

        return doInvokeAction(actionModel, step, wfRt);
    }

    private Object doInvokeAction(WfActionModel actionModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        IWfActor caller = wfRt.getCaller();
        if (!step.getRecord().isHistory()) {
            step.getRecord().setLastAction(actionModel.getName());
            step.getRecord().setCaller(caller);
        }

        IWorkflowActionRecord actionRecord = null;
        if (actionModel.isSaveActionRecord()) {
            actionRecord = step.getStore().newActionRecord(step.getRecord(), actionModel);
            wfRt.setActionRecord(actionRecord);
            actionRecord.setCaller(caller);
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_ACTION);

        Object result = null;
        try {
            // MA-02（裁定① action 站点锁段收窄）：runXpl 前锁内双检——入 action 链先经 wf
            // 条带锁校验步骤仍处于允许操作的状态（锁内重读已提交状态）。
            withWfLock(step.getWorkflow(), () -> {
                refreshSnapshotsUnderLock(step, false);
                requireStepStatusForAction(step, actionModel, wfRt);
                return null;
            });

            // 只要action不是local=true,就必然会迁移为历史步骤。reject和withdraw相当于是内置实现的action的source段，因此在transition之前执行
            if (actionModel.isForReject()) {
                doReject(step, actionModel, wfRt);
            } else if (actionModel.isForWithdraw()) {
                doWithdraw(step, actionModel, wfRt);
            }
            // runXpl 本体不持锁：用户 source 可长执行，持锁将封锁同 wf 全部操作且跨 wf 互调
            // 构成 ABBA 死锁面（裁定① action 行）；状态面由迁移段重取锁终检保证恰好一次迁移
            result = runXpl(actionModel.getSource(), wfRt);


            if (actionModel.isSaveActionRecord())
                step.getStore().saveActionRecord(actionRecord);

            // MA-02（裁定① 组迁移站点）：迁移段重取 wf 条带锁 + 锁内终检 + 锁内重读同组兄弟
            // 步骤已提交状态，使判定-迁移原子化——双线程并发 action 同组时迁移恰好一次。
            // 第二到达者的 source 已执行一次但迁移被终检拒绝，ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 兜底复用。
            withWfLock(step.getWorkflow(), () -> {
                refreshSnapshotsUnderLock(step, true);
                requireStepStatusForAction(step, actionModel, wfRt);

                doActionMigration(actionModel, step, wfRt);
                return null;
            });

        } catch (Exception e) {
            handleError(e, actionModel.getName(), (WfStepModel) step.getModel(), wfRt);
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_ACTION);

        wfRt.delayExecute(() -> {
            checkEnd(wfRt);
        });
        return result;
    }

    /**
     * MA-02（裁定① 组迁移站点）：action 迁移段——exec group 完成判定 + skipExecGroupMembers
     * + doTransition + doExitStep + SEQ_GROUP 顺序推进。仅应在 wf 条带锁段内调用
     * （见 doInvokeAction 迁移段），判定读快照与迁移写库之间不再有跨请求竞态窗。
     */
    /**
     * MA-02（裁定① action 行双检）：锁内状态位守卫——只复检步骤状态/wf 挂起/终结维度
     * （isForStatus 同判），不重跑 action 权限与条件判定（reject/withdraw 在 runXpl 前已
     * 改变 next-steps 拓扑，完整复检会误拒）。状态已迁移走即拒绝：
     * ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 现役兜底复用。
     */
    private void requireStepStatusForAction(IWorkflowStepImplementor step, WfActionModel actionModel,
                                            WfRuntime wfRt) {
        if (!isForStatus(step, actionModel)) {
            throw wfRt.newError(ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS)
                    .param(ARG_STEP_NAME, step.getStepName())
                    .param(ARG_STEP_ID, step.getStepId())
                    .param(ARG_ACTION_NAME, actionModel.getName())
                    .param(ARG_STEP_STATUS, step.getRecord().getStatus());
        }
    }

    private void doActionMigration(WfActionModel actionModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        // exec group 完成检查：仅在普通 action（非 reject/withdraw）且步骤显式声明了 execGroupType 时执行。
        // 注意：所有步骤都有 execGroup UUID（newSteps 自动生成），但只有 execGroupType != null 才是真正的执行分组。
        // 应在 doTransition 之前判断：组未完成则跳过下游创建，但步骤仍正常 exit（用户 action 已执行）
        boolean shouldTransition = true;
        WfExecGroupType execGroupType = step.getExecGroupType();
        if (execGroupType != null && !actionModel.isForReject() && !actionModel.isForWithdraw()) {
            boolean groupComplete = isExecGroupComplete(step, wfRt);
            if (!groupComplete) {
                shouldTransition = false;
            } else if (execGroupType == WfExecGroupType.OR_GROUP
                    || execGroupType == WfExecGroupType.VOTE_GROUP) {
                ExecGroupSupport.skipExecGroupMembers(this, step, wfRt);
            }
            // and-group / seq-group: fall through to normal transition
        }

        if (shouldTransition && actionModel.getTransition() != null) {
            WfTransitionModel transModel = actionModel.getTransition();

            boolean hasTrans = this.doTransition(step, actionModel.getName(), transModel, wfRt);

            if (!hasTrans && !actionModel.isLocal() && !actionModel.isForReject()
                    && !actionModel.isForWithdraw()) {
                throw wfRt.newError(ERR_WF_ACTION_TRANSITION_NO_NEXT_STEP)
                        .param(ARG_STEP_NAME, step.getStepName())
                        .param(ARG_STEP_ID, step.getStepId())
                        .param(ARG_ACTION_NAME, actionModel.getName());
            }
        }

        // 在触发迁移之后离开本步骤
        if (!actionModel.isLocal()) {
            if (!step.isHistory()) {
                int status = NopWfCoreConstants.WF_STEP_STATUS_COMPLETED;
                if (actionModel.isForReject()) {
                    status = NopWfCoreConstants.WF_STEP_STATUS_REJECTED;
                }
                doExitStep(step, status, wfRt);

                // SEQ_GROUP 顺序推进：仅正常完成时激活下一个顺序步骤（不含 reject/withdraw/kill）
                if (status == NopWfCoreConstants.WF_STEP_STATUS_COMPLETED
                        && step.getExecGroupType() == WfExecGroupType.SEQ_GROUP) {
                    ExecGroupSupport.activateNextSeqStep(this, step, wfRt);
                }
            }
        }
    }

    private boolean isExecGroupComplete(IWorkflowStepImplementor step, WfRuntime wfRt) {
        WfStepModel stepModel = (WfStepModel) step.getModel();
        if (stepModel.getCheckExecGroupComplete() != null)
            return stepModel.getCheckExecGroupComplete().passConditions(wfRt);
        return ExecGroupSupport.shouldExecGroupComplete(step);
    }

    private boolean isExecGroupReject(IWorkflowStepImplementor step, WfRuntime wfRt) {
        WfStepModel stepModel = (WfStepModel) step.getModel();
        if (stepModel.getCheckExecGroupReject() != null)
            return stepModel.getCheckExecGroupReject().passConditions(wfRt);
        return ExecGroupSupport.shouldExecGroupReject(step);
    }

    private void doReject(IWorkflowStepImplementor step, WfActionModel actionModel, WfRuntime wfRt) {
        String actionName = actionModel.getName();
        WfStepModel stepModel = (WfStepModel) step.getModel();
        Set<String> rejectSteps = wfRt.getRejectSteps();

        Dag dag = wfRt.getWfModel().getDag();

        IWorkflowImplementor wf = step.getWorkflow();
        if (rejectSteps != null && !rejectSteps.isEmpty()) {
            for (String rejectStepName : rejectSteps) {
                // rejectSteps按步骤名（模型名）指定驳回目标，取该步骤名最近一次的实例。
                // 此前用getStepById按【步骤实例ID】查找、又用同一字符串按【模型名】做祖先校验，
                // 两种口径不可能同时满足，显式驳回目标在任何输入下都必然抛错
                IWorkflowStepImplementor rejectStep = wf.getLatestStepByName(rejectStepName);
                if (rejectStep == null)
                    throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, rejectStepName);

                if (!dag.hasAncestor(stepModel.getName(), rejectStepName))
                    throw wfRt.newError(ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP)
                            .param(ARG_REJECT_STEP, rejectStepName);
                doRejectStep(step, rejectStep, actionName, wfRt);
            }
        } else {
            List<? extends IWorkflowStepImplementor> prevSteps = step.getPrevNormalStepsInTree();
            for (IWorkflowStepImplementor prevStep : prevSteps) {
                doRejectStep(step, prevStep, actionName, wfRt);
            }
        }

        // reject 分组检查：当前步骤 reject 后，若步骤显式声明了 execGroupType 且整个组也应拒绝
        // （如 vote-group 无法达到 passPercent），则跳过组内其余未完成成员
        if (step.getExecGroupType() != null) {
            if (isExecGroupReject(step, wfRt)) {
                ExecGroupSupport.skipExecGroupMembers(this, step, wfRt);
            }
        }

        if (stepModel.getJoinType() == WfJoinType.and) {
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
            saveStepRecord(step);
        }
    }

    private void doRejectStep(IWorkflowStepImplementor currentStep, IWorkflowStepImplementor rejectStep,
                              String actionName, WfRuntime wfRt) {
        if (rejectStep.isHistory()) {
            String stepGroup = rejectStep.getRecord().getExecGroup();
            Integer execOrder = rejectStep.getRecord().getExecOrder();
            this.newStepForActor(stepGroup, execOrder, currentStep, (WfStepModel) rejectStep.getModel(), actionName,
                    new WfActorWithWeight(rejectStep.getActor(), rejectStep.getRecord().getActorModelId(),
                            rejectStep.getRecord().getVoteWeight()), rejectStep.getOwner(), wfRt);
        }
    }

    private void doWithdraw(IWorkflowStepImplementor step, WfActionModel actionModel, WfRuntime wfRt) {
        WfStepModel stepModel = (WfStepModel) step.getModel();
        for (IWorkflowStepImplementor nextStep : step.getNextSteps()) {
            if (nextStep.isHistory()) {
                throw wfRt.newError(ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED);
            } else {
                this.doExitStep(nextStep, NopWfCoreConstants.WF_STEP_STATUS_WITHDRAWN, wfRt);
            }
        }

        if (step.isHistory()) {
            String stepGroup = step.getRecord().getExecGroup();
            Integer execOrder = step.getRecord().getExecOrder();

            this.newStepForActor(stepGroup, execOrder, step, stepModel, actionModel.getName(),
                    new WfActorWithWeight(step.getActor(), step.getRecord().getActorModelId(),
                            step.getRecord().getVoteWeight()),
                    step.getOwner(), wfRt);
        }
    }

    protected void doExitStep(IWorkflowStepImplementor step, int status, WfRuntime wfRt) {
        if (!step.isHistory()) {
            WfStepModel stepModel = (WfStepModel) step.getModel();
            step.getRecord().setFinishTime(CoreMetrics.currentTimestamp());
            step.getRecord().transitToStatus(status);

            IWorkflowStepImplementor currentStep = wfRt.getCurrentStep();
            try {
                wfRt.setCurrentStep(step);
                runXpl(stepModel.getOnExit(), wfRt);
                saveStepRecord(step);

                wfRt.triggerEvent(NopWfCoreConstants.EVENT_EXIT_STEP);
            } finally {
                wfRt.setCurrentStep(currentStep);
            }
        }
    }

    private void enterWaiting(IWorkflowStepImplementor step) {
        if (!step.isHistory()) {
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
            saveStepRecord(step);
        }
    }

    private boolean doTransition(IWorkflowStepImplementor step, String actionName,
                                 WfTransitionModel transModel, WfRuntime wfRt) {
        changeWfAppState(step, transModel.getWfAppState());
        changeStepAppState(step, transModel.getAppState());
        changeBizEntityState(step, transModel.getBizEntityState());

        boolean hasTrans = false;

        IWorkflowImplementor wf = step.getWorkflow();

        Set<String> targetSteps = wfRt.getTargetSteps();
        Set<String> targetCases = wfRt.getTargetCases();

        for (WfTransitionToModel toM : transModel.getTransitionTos()) {
            if (wfRt.willEnd() || wf.isEnded())
                break;

            // 如果明确限制了允许的目标步骤集合
            if (toM instanceof WfTransitionToStepModel) {
                WfTransitionToStepModel toStep = (WfTransitionToStepModel) toM;
                if (targetSteps != null && !targetSteps.isEmpty()) {
                    if (!targetSteps.contains(toStep.getStepName())) {
                        continue;
                    }
                }

                if (toM.getCaseValue() != null) {
                    if (targetCases == null || !targetCases.contains(toM.getCaseValue())) {
                        LOG.info("nop.wf.ignore-transition-to-with-case-value:to={},caseValue={},cases={}", toM,
                                toM.getCaseValue(), targetCases);
                        continue;
                    }
                }
            }

            if (!passConditions(toM, wfRt)) {
                LOG.debug("nop.wf.ignore-transition-to-when-condition-check-fail:to={}", toM);
                continue;
            }


            hasTrans = true;
            transitionTo(step, actionName, targetSteps, toM, wfRt);

            // 如果splitType=or, 则只执行第一个迁移分支
            if (transModel.getSplitType() == WfSplitType.or) {
                break;
            }
        }

        if (!hasTrans) {
            if (targetSteps != null && !targetSteps.isEmpty())
                throw wfRt.newError(ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH)
                        .param(ARG_TARGET_STEPS, targetSteps);

            if (targetCases != null && !targetCases.isEmpty())
                throw wfRt.newError(ERR_WF_TRANSITION_TARGET_CASES_NOT_MATCH)
                        .param(ARG_TARGET_CASES, targetCases);
        }
        return hasTrans;
    }

    void changeWfAppState(IWorkflowStepImplementor step, String wfAppState) {
        if (wfAppState != null)
            step.getWorkflow().getRecord().setAppState(wfAppState);
    }

    void changeStepAppState(IWorkflowStepImplementor step, String stepAppState) {
        if (stepAppState != null)
            step.getRecord().setAppState(stepAppState);
    }

    void changeBizEntityState(IWorkflowStepImplementor step, String bizEntityState) {
        WfModel wfModel = (WfModel) step.getWorkflow().getModel();
        if (bizEntityState != null && wfModel.getBizEntityStateProp() != null) {
            step.getStore().updateBizEntityState(step.getWorkflow().getRecord(),
                    wfModel.getBizEntityStateProp(), bizEntityState);
        }
    }

    void transitionTo(IWorkflowStepImplementor currentStep, String actionName, Set<String> targetSteps,
                      WfTransitionToModel toM, WfRuntime wfRt) {
        IWorkflowStore wfStore = currentStep.getStore();

        switch (toM.getType()) {
            case TO_EMPTY: {
                LOG.debug("nop.wf.transition-to-empty:step={},action={}", currentStep, actionName);
                runXpl(toM.getBeforeTransition(), wfRt);
                wfStore.addNextSpecialStep(currentStep.getRecord(), actionName, NopWfCoreConstants.STEP_ID_EMPTY);
                runXpl(toM.getAfterTransition(), wfRt);
                return;
            }
            case TO_END: {
                LOG.debug("nop.wf.transition-to-end:step={},action={}", currentStep, actionName);
                runXpl(toM.getBeforeTransition(), wfRt);
                wfStore.addNextSpecialStep(currentStep.getRecord(), actionName, NopWfCoreConstants.STEP_ID_END);
                wfRt.markEnd();// 延迟结束工作流实例
                runXpl(toM.getAfterTransition(), wfRt);
                wfRt.delayExecute(() -> {
                    checkEnd(wfRt);
                });
                return;
            }
            case TO_ASSIGNED: {
                LOG.debug("nop.wf.transition-to-assigned:step={},actionName={},targetSteps={}", currentStep, actionName,
                        targetSteps);
                // to-assigned 迁移的目标步骤必须由调用方显式指定：缺失/为空时快速失败，
                // 避免当前步骤被静默完成后流程因无后继步骤而意外整体结束
                if (targetSteps == null || targetSteps.isEmpty())
                    throw wfRt.newError(ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH)
                            .param(ARG_TARGET_STEPS, targetSteps);
                for (String targetStep : targetSteps) {
                    transitionToStep(currentStep, targetStep, actionName, toM, wfRt);
                }
                break;
            }
            case TO_STEP:
                String stepName = toM.getStepName();
                LOG.debug("nop.wf.transition-to-step:step={},actionName={},stepName={}", currentStep, actionName, stepName);
                transitionToStep(currentStep, stepName, actionName, toM, wfRt);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    void transitionToStep(IWorkflowStepImplementor currentStep, String targetStep, String actionName,
                          WfTransitionToModel toM, WfRuntime wfRt) {
        WfModel wfModel = (WfModel) currentStep.getWorkflow().getModel();
        runXpl(toM.getBeforeTransition(), wfRt);

        WfStepModel stepModel = wfModel.getStep(targetStep);
        if (stepModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, targetStep);

        wfRt.setPrevStep(currentStep);
        List<WfActorWithWeight> actors = getActors(stepModel.getAssignment(), targetStep, wfRt);
        this.newSteps(currentStep, stepModel, actionName, actors, wfRt);

        runXpl(toM.getAfterTransition(), wfRt);
    }

    void checkEnd(WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        // MA-02（裁定① 流程终结站点）：willEnd/isAllStepsHistory 判定段纳入 wf 条带锁，
        // 双实例并发 checkEnd 时终结恰好一次（wf.isEnded() 复检在锁内）。
        // plan 2585 Phase 1：终结本体（doEndWorkflow）移出判定锁段，在锁释放后执行。
        boolean bEnd = withWfLock(wf, () -> doCheckEnd(wfRt, wf));
        if (bEnd) {
            this.doEndWorkflow(NopWfCoreConstants.WF_STATUS_COMPLETED, wfRt);
        }
    }

    /**
     * 锁内终结判定段。返回 true 表示本调用决定终结该流程（调用方随后在锁外执行
     * {@link #doEndWorkflow}）；返回 false 表示流程已终结或未满足终结条件。
     */
    private boolean doCheckEnd(WfRuntime wfRt, IWorkflowImplementor wf) {
        // 如果工作流实例上的状态位没有结束，但是所有流程步骤都已经结束，则自动将整个工作流结束
        if (!wf.isEnded() && wf.isStarted()) {
            // 锁内重读已提交实例状态：另一请求可能已在本请求快照固化后完成终结
            refreshWfStatusFromStore(wf);
            if (wf.isEnded())
                return false;

            IWorkflowRecord wfRecord = wf.getRecord();
            boolean bEnd = wfRt.willEnd();
            if (bEnd) {
                LOG.info("nop.wf.force-end:wfName={},wfRecord={}", wfRecord.getWfName(), wfRecord);
                killSteps(wfRt);
            } else if (wf.getStore().isAllStepsHistory(wfRecord)) {
                bEnd = true;
                LOG.info("nop.wf.auto-end-since-all-steps-finished:wfName={},wfRecord={}", wfRecord.getWfName(), wfRecord);
            }
            return bEnd;
        }
        return false;
    }

    /**
     * plan 2585 Phase 1（end 脚本出锁 + endSubFlow 出锁）三段式终结：
     * <ol>
     * <li>锁内预留终态（{@link #reserveEndWorkflowLocked}）：锁内复检 isEnded 防双跑，
     * 先写 ended 状态落盘（预留终态），此后任何并发终结到达都看到 isEnded 短路；</li>
     * <li>锁外执行用户 end 脚本与子流程结束通知（endSubFlow 同步触发父侧
     * notifySubFlowEnd，取父锁时不再持本 wf 条带锁——子→父单向锁序）；</li>
     * <li>锁外触发 AFTER_END 事件收尾。</li>
     * </ol>
     * 取舍：end 脚本在终态预留之后执行，脚本抛异常时流程已终结而脚本副作用丢失——
     * 以防双跑为优先（plan Phase 1 裁定）。
     */
    void doEndWorkflow(int status, WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        AtomicBoolean reserved = new AtomicBoolean(false);
        WfEndModel endModel = withWfLock(wf, () -> reserveEndWorkflowLocked(status, wfRt, wf, reserved));
        if (!reserved.get())
            return;

        // 锁外：用户 end 脚本可长执行，持锁将封锁同 wf 全部操作（裁定① 不变式投影）
        if (endModel != null) {
            runXpl(endModel.getSource(), wfRt);
        }

        // 锁外：子流程结束通知父流程（notifySubFlowEnd 内取父 wf 条带锁）
        this.endSubFlow(wf, status, wfRt);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_END);
    }

    /**
     * 锁内终态预留段（MA-02 裁定① saveWfRecord 6 调用点覆盖，终结写点；plan 2585
     * Phase 1 改名自 doEndWorkflowLocked）。仅在预留成功时置位 reserved；end 脚本与
     * 子流程通知均不在本段执行（见 {@link #doEndWorkflow}）。
     */
    private WfEndModel reserveEndWorkflowLocked(int status, WfRuntime wfRt, IWorkflowImplementor wf,
                                                AtomicBoolean reserved) {
        if (wf.isEnded())
            return null;

        refreshWfStatusFromStore(wf);
        if (wf.isEnded())
            return null;

        LOG.info("nop.wf.end-workflow:wfName={},status={},wf={}", wf.getWfName(), status, wf.getRecord());
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_END);
        IWorkflowRecord wfRecord = wf.getRecord();
        wfRecord.setEndTime(CoreMetrics.currentTimestamp());
        wfRecord.setLastOperateTime(CoreMetrics.currentTimestamp());
        wfRecord.setLastOperator(wfRt.getCaller());

        WfModel wfModel = (WfModel) wf.getModel();
        WfEndModel endModel = wfModel.getEnd();

        // 预留终态：先落盘 ended 状态防双跑，用户 end 脚本移至锁外执行
        wfRt.saveWfRecord(status);
        reserved.set(true);

        return endModel;
    }

    void endSubFlow(IWorkflowImplementor wf, int status, WfRuntime wfRt) {
        WfModel wfModel = (WfModel) wf.getModel();
        Map<String, Object> results = null;
        if (wfModel.getEnd() != null) {
            results = getVars(wfModel.getEnd().getOutputs(), wfRt);
        }
        WfStepReference parentStepRef = getParentStepRef(wf);
        if (parentStepRef != null) {
            // 子流程结束，通知父流程
            wf.getCoordinator().endSubFlow(wf.getWfReference(), status, parentStepRef, results, wfRt.getSvcCtx());
        }
    }

    Map<String, Object> getVars(List<WfReturnVarModel> varModels, WfRuntime wfRt) {
        if (varModels == null)
            return null;

        Map<String, Object> ret = new HashMap<>(varModels.size());
        for (WfReturnVarModel varModel : varModels) {
            String name = varModel.getName();
            Object value = runXpl(varModel.getSource(), wfRt);
            ret.put(name, value);
        }
        return ret;
    }

    WfStepReference getParentStepRef(IWorkflowImplementor wf) {
        IWorkflowRecord wfRecord = wf.getRecord();
        String parentStepId = wfRecord.getParentStepId();
        if (parentStepId == null)
            return null;

        return new WfStepReference(wfRecord.getParentWfName(), wfRecord.getParentWfVersion(), wfRecord.getParentWfId(),
                wfRecord.getParentStepId());
    }

    void killSteps(WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        IWorkflowStore wfStore = wf.getStore();
        for (IWorkflowStepRecord record : wfStore.getStepRecords(wf.getRecord(), false, null)) {
            IWorkflowStepImplementor step = wf.getStepByRecord(record);
            WfRuntime stepRt = newWfRuntime(step.getWorkflow(), wfRt.getSvcCtx());
            _killStep(step, stepRt);
        }
    }

    @Override
    public void logError(IWorkflowImplementor wf, String stepName, String actionName, Throwable e) {
        LOG.error("nop.wf.error:wfName={},wfId={},stepName={},actionName={}",
                wf.getWfName(), wf.getWfId(), stepName, actionName, e);

        wf.getStore().logError(wf.getRecord(), stepName, actionName, e);
    }

    private void handleError(Throwable e, String actionName, WfStepModel stepModel,
                             WfRuntime wfRt) {
        this.logError(wfRt.getWf(), stepModel.getName(), actionName, e);

        wfRt.setException(e);

        // onError XPL自身抛异常时只记日志，不得掩盖原始异常（此前会直接传播onError的异常）
        if (stepModel.getOnError() != null) {
            if (runOnErrorSafe(stepModel.getOnError(), wfRt, stepModel.getName(), actionName))
                return;
        }

        WfModel wfModel = wfRt.getWfModel();
        if (wfModel.getOnError() != null) {
            if (runOnErrorSafe(wfModel.getOnError(), wfRt, stepModel.getName(), actionName))
                return;
        }

        if (wfRt.getException() != null)
            throw NopException.adapt(wfRt.getException());
    }

    private boolean runOnErrorSafe(IEvalAction onError, WfRuntime wfRt, String stepName, String actionName) {
        try {
            return ConvertHelper.toBoolean(runXpl(onError, wfRt));
        } catch (Exception e2) {
            LOG.error("nop.wf.on-error-xpl-fail:wfName={},wfId={},stepName={},actionName={}",
                    wfRt.getWf().getWfName(), wfRt.getWf().getWfId(), stepName, actionName, e2);
            return false;
        }
    }

    private void saveStepRecord(IWorkflowStepImplementor step) {
        step.getStore().saveStepRecord(step.getRecord());
    }

    /**
     * 将步骤从 WAITING 状态激活为 ACTIVATED 并保存记录。供 ExecGroupSupport.activateNextSeqStep 调用。
     */
    void activateSeqStep(IWorkflowStepImplementor step) {
        step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        saveStepRecord(step);
    }

    private WfActionModel requireActionModel(WfRuntime wfRt, String actionName) {
        WfActionModel actionModel = wfRt.getWfModel().getAction(actionName);
        if (actionModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_ACTION).param(ARG_ACTION_NAME, actionName);
        return actionModel;
    }

    private void checkActionAuth(WfModel wfModel, WfRuntime wfRt) {
        runXpl(wfModel.getCheckActionAuth(), wfRt);
    }

    private ErrorCode checkAllowedAction(WfActionModel actionModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        ErrorCode errorCode = _checkAllowedAction(actionModel, step, wfRt);
        if (errorCode != null) {
            LOG.debug("{}:wfName={},stepName={},actionName={}", errorCode, step.getWfName(),
                    step.getStepName(), actionModel.getName());
        }
        return errorCode;
    }

    private ErrorCode _checkAllowedAction(WfActionModel actionModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        if (!isForStatus(step, actionModel)) {
            return ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS;
        }

        if (actionModel.isCommon()) {
            if (actionModel.getWhenSteps() != null && !actionModel.getWhenSteps().isEmpty()) {
                if (!actionModel.getWhenSteps().contains(step.getStepName()))
                    return ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP;
            }
        } else {
            if (step.getModel().getAction(actionModel.getName()) == null)
                return ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP;
        }

        if (actionModel.isForWithdraw()) {
            if (!canWithdraw(step)) {
                return ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED;
            }
        }

        if (actionModel.isForReject()) {
            if (!step.getModel().isAllowReject()) {
                return ERR_WF_REJECT_ACTION_IS_NOT_ALLOWED;
            }
        }

        if (!passConditions(actionModel, wfRt)) {
            return ERR_WF_ACTION_CONDITIONS_NOT_PASSED;
        }

        if (!step.getWorkflow().isAllSignalOn(actionModel.getWaitSignals())) {
            return ERR_WF_ACTION_NOT_ALLOWED_WHEN_SIGNAL_NOT_READY;
        }

        return null;
    }

    private boolean isForStatus(IWorkflowStepImplementor step, WfActionModel actionModel) {
        // 如果流程被挂起，则暂停所有action的执行
        if (step.getWorkflow().isSuspended()) {
            LOG.info("nop.wf.workflow-is-suspended:wfName={},wfId={}", step.getWfName(), step.getWfId());
            return false;
        }

        // 如果流程已结束, 一般情况下是不允许执行action的
        if (step.getWorkflow().isEnded()) {
            if (!actionModel.isForFlowEnded())
                return false;
        }

        // 分成三种情况：等待/活动/历史
        if (step.isActivated()) {
            return actionModel.isForActivated();
        } else if (step.isWaiting()) {
            return actionModel.isForWaiting();
        } else if (step.isHistory()) {
            return actionModel.isForHistory() || actionModel.isForWithdraw();
        }
        // 所有其他状况都不允许
        return false;
    }

    private boolean canWithdraw(IWorkflowStepImplementor step) {
        if (!step.isHistory())
            return false;

        if (!step.getModel().isAllowWithdraw())
            return false;

        for (IWorkflowStep nextStep : step.getNextSteps()) {
            if (nextStep.isHistory()) {
                if (nextStep.getRecord().getStatus() == NopWfCoreConstants.WF_STEP_STATUS_REJECTED)
                    continue;

                LOG.debug("wf.next-step-is-history-so-not-allow-withdraw:nextStep={},step={}",
                        nextStep.getStepName(), step.getStepName());
                return false;
            }
        }
        return true;
    }

    @Override
    public List<WorkflowTransitionTarget> getTransitionTargetsForAction(
            IWorkflowStepImplementor step, String actionName, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        WfModel wfModel = wfRt.getWfModel();
        WfActionModel actionModel = wfModel.getAction(actionName);
        if (actionModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_ACTION).param(ARG_ACTION_NAME, actionName);

        List<WorkflowTransitionTarget> targets = new ArrayList<>();
        if (actionModel.getTransition() != null) {
            for (WfTransitionToModel toM : actionModel.getTransition().getTransitionTos()) {
                if (!passConditions(toM, wfRt))
                    continue;

                WorkflowTransitionTarget target = new WorkflowTransitionTarget();
                target.setAppState(actionModel.getTransition().getAppState());
                target.setStepType(WfStepType.step.name());

                targets.add(target);

                switch (toM.getType()) {
                    case TO_EMPTY: {
                        target.setStepName(NopWfCoreConstants.STEP_ID_EMPTY);
                        target.setStepDisplayName(NopWfCoreConstants.STEP_ID_EMPTY);
                        break;
                    }
                    case TO_END: {
                        target.setStepName(NopWfCoreConstants.STEP_ID_END);
                        target.setStepDisplayName(NopWfCoreConstants.STEP_ID_END);
                        break;
                    }
                    case TO_ASSIGNED: {
                        target.setStepName(NopWfCoreConstants.STEP_ID_ASSIGNED);
                        target.setStepDisplayName(NopWfCoreConstants.STEP_ID_ASSIGNED);
                        break;
                    }
                    case TO_STEP: {
                        WfStepModel targetStepModel = wfModel.getStep(toM.getStepName());
                        if (targetStepModel == null)
                            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, toM.getStepName());

                        target.setStepType(targetStepModel.getStepType().name());
                        WfAssignmentModel assignment = targetStepModel.getAssignment();
                        if (assignment != null) {
                            WfActorCandidatesBean candidates = getActorCandidates(assignment, wfRt);
                            target.setActorCandidates(candidates);
                            target.setIgnoreNoAssign(assignment.isIgnoreNoAssign());
                        }
                        target.setStepSpecialType(targetStepModel.getSpecialType());
                        target.setStepType(targetStepModel.getStepType().toString());
                        target.setStepName(targetStepModel.getName());
                        target.setStepDisplayName(targetStepModel.getDisplayName());
                        if (target.getAppState() == null)
                            target.setAppState(targetStepModel.getAppState());
                        break;
                    }
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }
        return targets;
    }

    @Override
    public void transitTo(IWorkflowStepImplementor step, String stepName,
                          Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.transit-to:wfName={},wfId={},stepName={},stepId={},toStepName={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(), stepName);

        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);

        wfRt.setAssigner(wfRt.getCaller());

        WfStepModel stepModel = wfRt.getWfModel().getStep(stepName);
        if (stepModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, stepName);

        WfAssignmentModel assignment = stepModel.getAssignment();
        List<WfActorWithWeight> actors = getActors(assignment, stepName, wfRt);
        if (!this.newSteps(step, stepModel, NopWfCoreConstants.INTERNAL_ACTION_TRANSIT_TO, actors, wfRt))
            throw wfRt.newError(ERR_WF_TRANSIT_TO_NO_TARGETS)
                    .param(ARG_TO_STEP_NAME, step.getStepName());
    }

    @Override
    public void exitStep(IWorkflowStepImplementor step, int status,
                         Map<String, Object> args, IServiceContext ctx) {
        LOG.info("nop.wf.exit-step:wfName={},wfId={},stepName={},stepId={},status={}",
                step.getWfName(), step.getWfId(), step.getStepName(), step.getStepId(), status);

        if (step.isHistory())
            return;

        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);

        this.doExitStep(step, status, wfRt);

        wfRt.delayExecute(() -> {
            checkEnd(wfRt);
        });
    }

    @Override
    public List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWorkflowStepImplementor step, IWfRuntime wfRt) {
        if (step.getModel().getJoinType() == WfJoinType.and) {
            WfStepModel stepModel = (WfStepModel) step.getModel();
            Set<String> waitSteps = stepModel.getWaitStepNames();
            // join-group-expr只能定义在join步骤上（见wf.xdef），分组匹配时必须用join自身的模型表达式
            // 在每个上游等待步骤的上下文中求值，与join记录上持久化的joinGroup比较。
            // 此前误读waitStep.getModel()的表达式（普通步骤恒为null），带分组的join等待集永远为空，
            // join步骤会被立即激活
            final WfStepModel joinModel = stepModel;
            Collection<? extends IWorkflowStepRecord> stepRecords = step.getStore().getJoinWaitStepRecords(
                    step.getRecord(), stepRecord -> {
                        IWorkflowStepImplementor waitStep = step.getWorkflow().getStepByRecord(stepRecord);
                        return getJoinGroup(joinModel, waitStep, (WfRuntime) wfRt);
                    }, waitSteps);
            return step.getWorkflow().getStepsByRecords(stepRecords);
        } else {
            return Collections.emptyList();
        }
    }
}
