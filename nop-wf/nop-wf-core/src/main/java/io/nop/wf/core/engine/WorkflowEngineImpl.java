/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.model.graph.dag.Dag;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.api.actor.IWfActor;
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
import io.nop.wf.core.model.WfJoinType;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfModelAuth;
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

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_TYPE;
import static io.nop.wf.core.NopWfCoreErrors.ARG_ARG_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_REJECT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_TARGET_CASES;
import static io.nop.wf.core.NopWfCoreErrors.ARG_TARGET_STEPS;
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
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_REJECT_ACTION_IS_NOT_ALLOWED;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_START_WF_FAIL;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_STEP_NO_ASSIGNMENT;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TARGET_CASES_NOT_MATCH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION_ARG;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED;

/**
 * 工作流引擎的核心处理逻辑
 */
public class WorkflowEngineImpl extends WfActorAssignSupport implements IWorkflowEngine {
    static final Logger LOG = LoggerFactory.getLogger(WorkflowEngineImpl.class);


    protected WfRuntime newWfRuntime(IWorkflowImplementor wf, IServiceContext ctx) {
        return new WfRuntime(wf, ctx);
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
        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowRecord record = wf.getRecord();
        if (record.getStatus() > NopWfCoreConstants.WF_STATUS_CREATED) {
            throw wfRt.newError(ERR_WF_ALREADY_STARTED);
        }

        WfModel wfModel = (WfModel) wf.getModel();

        args = removeStdStartParam(args, record);
        initArgs(wfModel.getStart(), args, NopWfCoreConstants.SYS_ACTION_START, wfRt);

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

    private Map<String, Object> removeStdStartParam(Map<String, Object> args, IWorkflowRecord wfRecord) {
        if (args == null || args.isEmpty())
            return args;

        args = new LinkedHashMap<>(args);
        String title = (String) args.remove(NopWfCoreConstants.PARAM_TITLE);
        if (title != null)
            wfRecord.setTitle(title);

        String bizObjName = (String) args.remove(NopWfCoreConstants.PARAM_BIZ_OBJ_NAME);
        wfRecord.setBizObjName(bizObjName);

        String bizEntityId = (String) args.remove(NopWfCoreConstants.PARAM_BIZ_OBJ_ID);
        wfRecord.setBizObjId(bizEntityId);

        String bizKey = (String) args.remove(NopWfCoreConstants.PARAM_BIZ_KEY);
        wfRecord.setBizKey(bizKey);
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
        // 对每一个actor生成一个步骤实例
        for (WfActorWithWeight actor : actors) {
            newStepForActor(stepGroup, (double) execOrder, currentStep, stepModel, fromAction, actor, null, wfRt);
            execOrder++;
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

    void newStepForActor(String stepGroup, Double execOrder,
                         IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                         WfActorWithWeight actorWithWeight, IWfActor owner, WfRuntime wfRt) {
        IWfActor actor = actorWithWeight.getActor();

        IWorkflowImplementor wf = wfRt.getWf();
        if (stepModel.getJoinType() != null) {
            String joinGroup = getJoinGroup(stepModel, currentStep, wfRt);

            // join步骤会自动查找已经存在的步骤实例
            IWorkflowStepRecord stepRecord = wf.getStore().getNextJoinStepRecord(currentStep.getRecord(),
                    joinGroup, stepModel.getName(), actor);
            if (stepRecord != null) {
                IWorkflowStepImplementor step = wf.getStepByRecord(stepRecord);

                stepRecord.setStepGroup(stepGroup);
                stepRecord.setExecOrder(execOrder);
                stepRecord.setActorModelId(actorWithWeight.getActorModelId());

                if (currentStep != null) {
                    wf.getStore().addNextStepRecord(currentStep.getRecord(), fromAction, step.getRecord());
                }

                // 处于等待状态的join步骤，新增加上游步骤之后需要检查是否可以转入激活状态
                if (step.isWaiting()) {
                    wfRt.delayExecute(() -> checkWaitingJoinStep(step, wfRt));
                }
                return;
            }
        }

        if (owner == null)
            owner = getOwner(stepModel.getAssignment(), actor, wfRt);

        IWorkflowStepRecord stepRecord = wf.getStore().newStepRecord(wf.getRecord(), stepModel);
        stepRecord.setStepGroup(stepGroup);
        stepRecord.setExecOrder(execOrder);
        stepRecord.setActorModelId(actorWithWeight.getActorModelId());

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

        // add step link after saving step
        if (currentStep != null) {
            wf.getStore().addNextStepRecord(currentStep.getRecord(), fromAction, step.getRecord());
        }
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
        IWorkflowRecord wfRecord = wfRt.getWf().getRecord();
        wfRecord.setStartTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wfRecord.setStarter(caller);
        wfRecord.setLastOperator(caller);
        wfRecord.setLastOperateTime(wfRecord.getStartTime());

        // record的title不能为空
        setDefaultTitle(wfRecord, wfRt);

        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_START);
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
            return true;
        }
        return false;
    }

    private Object runXpl(IEvalAction action, WfRuntime wfRt) {
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
                    SimpleSchemaValidator.INSTANCE.validate(schema, null, name, value, IValidationErrorCollector.THROW_ERROR);
                }
                IGenericType type = schema.getType();
                if (type != null) {
                    value = ConvertHelper.convertTo(type.getRawClass(), value, NopException::new);
                }

                wfRt.setValue(name, value);
            }
        }

        for (WfArgVarModel argModel : argsModel.getArgs()) {
            String name = argModel.getName();
            if (argModel.isMandatory()) {
                Object value = args.get(name);
                if (StringHelper.isEmptyObject(value))
                    throw wfRt.newError(ERR_WF_EMPTY_ACTION_ARG)
                            .param(ARG_ACTION_NAME, actionName).param(ARG_ARG_NAME, name);
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
            wfRt.getEvalScope().setLocalValues(args);
        }
    }

    @Override
    public void remove(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
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
        if (wf.isEnded())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        initArgs(wfRt, args);
        checkManageAuth(wfRt);

//        IWfActor caller = wfRt.getCaller();
//        wf.getRecord().setLastOperator(caller);
//        wf.getRecord().setLastOperateTime(CoreMetrics.currentTimestamp());

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_KILL);

        killSteps(wfRt);

        wfRt.markEnd();
        wfRt.delayExecute(() -> {
            doEndWorkflow(NopWfCoreConstants.WF_STATUS_KILLED, wfRt);
        });

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_KILL);
    }

    @Override
    public void turnSignalOn(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx) {
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

        WfStepModel stepModel = (WfStepModel) step.getModel();
        if (stepModel.getTransition() != null) {

            runXpl(stepModel.getSource(), wfRt);

            boolean hasTrans = this.doTransition(step, step.getRecord().getFromAction(),
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
    }

    @Override
    public void changeActor(IWorkflowStepImplementor step, IWfActor actor, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        step.getRecord().setActor(actor);
        saveStepRecord(step);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public void changeOwner(IWorkflowStepImplementor step, String ownerId, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        IWfActor owner = StringHelper.isEmpty(ownerId) ? null : resolveUser(ownerId);
        step.getRecord().setOwner(owner);
        saveStepRecord(step);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public void triggerStepEvent(IWorkflowStepImplementor step, String eventName, IServiceContext ctx) {

    }

    @Override
    public void killStep(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
        if (step.isHistory())
            return;

        WfRuntime wfRt = newWfRuntime(step, ctx);

        _killStep(step, wfRt);

        wfRt.delayExecute(() -> {
            checkEnd(wfRt);
        });
    }

    void _killStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        LOG.info("nop.wf.kill-step:step={}", step);

        IWfActor caller = wfRt.getCaller();
        if (caller != null) {
            step.getRecord().setCaller(caller);
        }

        this.doExitStep(step, NopWfCoreConstants.WF_STEP_STATUS_KILLED, wfRt);
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_KILL_STEP);
    }

    @Override
    public boolean triggerTransition(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);
        checkWaitingStep(step, wfRt);
        return runStepAutoTransition(step, wfRt);
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

        step.getRecord().setSubWfResultStatus(status);

        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.VAR_SUB_WF_RESULTS, results);

        // 触发工作流引擎检查step的状态检查，再根据状态触发auto action实现变迁
        step.triggerTransition(args, ctx);
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
            } catch (NopException e) {
                // 权限校验失败
                continue;
            }
            if (checkAllowedAction(actModel, step, wfRt) == null) {
                ret.add(actModel);
            }
        }
        return ret;
    }

    @Override
    public Object invokeAction(IWorkflowStepImplementor step, String actionName, Map<String, Object> args, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        WfModel wfModel = wfRt.getWfModel();

        WfActionModel actionModel = requireActionModel(wfRt, actionName);

        checkActionAuth(wfModel, wfRt);

        checkAllowedAction(actionModel, step, wfRt);

        initArgs(actionModel, args, actionName, wfRt);

        return doInvokeAction(actionModel, step, wfRt);
    }

    private Object doInvokeAction(WfActionModel actionModel, IWorkflowStepImplementor step, WfRuntime wfRt) {
        step.getRecord().setLastAction(actionModel.getName());

        IWorkflowActionRecord actionRecord = null;
        if (actionModel.isSaveActionRecord()) {
            actionRecord = step.getStore().newActionRecord(step.getRecord(), actionModel);
            wfRt.setActionRecord(actionRecord);
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_ACTION);


        Object result = null;
        try {
            // 只要action不是local=true,就必然会迁移为历史步骤。reject和withdraw相当于是内置实现的action的source段，因此在transition之前执行
            if (actionModel.isForReject()) {
                doReject(step, actionModel, wfRt);
            } else if (actionModel.isForWithdraw()) {
                doWithdraw(step, actionModel, wfRt);
            }
            result = runXpl(actionModel.getSource(), wfRt);

            if (actionModel.isSaveActionRecord())
                step.getStore().saveActionRecord(actionRecord);

            if (actionModel.getTransition() != null) {
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
                }
            }

        } catch (Exception e) {
            handleError(e, actionModel.getName(), (WfStepModel) step.getModel(), wfRt);
        }

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_ACTION);
        return result;
    }

    private void doReject(IWorkflowStepImplementor step, WfActionModel actionModel, WfRuntime wfRt) {
        String actionName = actionModel.getName();
        WfStepModel stepModel = (WfStepModel) step.getModel();
        Set<String> rejectSteps = wfRt.getRejectSteps();

        Dag dag = wfRt.getWfModel().getDag();

        IWorkflowImplementor wf = step.getWorkflow();
        if (rejectSteps != null && !rejectSteps.isEmpty()) {
            for (String rejectStepName : rejectSteps) {
                IWorkflowStepImplementor rejectStep = wf.getStepById(rejectStepName);
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

        if (stepModel.getJoinType() == WfJoinType.and) {
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
            saveStepRecord(step);
        }
    }

    private void doRejectStep(IWorkflowStepImplementor currentStep, IWorkflowStepImplementor rejectStep,
                              String actionName, WfRuntime wfRt) {
        if (rejectStep.isHistory()) {
            String stepGroup = rejectStep.getRecord().getStepGroup();
            Double execOrder = rejectStep.getRecord().getExecOrder();
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
            String stepGroup = step.getRecord().getStepGroup();
            Double execOrder = step.getRecord().getExecOrder();

            this.newStepForActor(stepGroup, execOrder, step, stepModel, actionModel.getName(),
                    new WfActorWithWeight(step.getActor(), step.getRecord().getActorModelId(),
                            step.getRecord().getVoteWeight()),
                    step.getOwner(), wfRt);
        }
    }

    private void doExitStep(IWorkflowStepImplementor step, int status, WfRuntime wfRt) {
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
            Object bo = step.getWorkflow().getBizEntity();
            if (bo != null) {
                step.getStore().updateBizEntityState(step.getWorkflow().getBizObjName(), bo,
                        wfModel.getBizEntityStateProp(), bizEntityState);
            }
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
                if (targetSteps != null) {
                    for (String targetStep : targetSteps) {
                        transitionToStep(currentStep, targetStep, actionName, toM, wfRt);
                    }
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
        // 如果工作流实例上的状态位没有结束，但是所有流程步骤都已经结束，则自动将整个工作流结束
        if (!wf.isEnded() && wf.isStarted()) {
            IWorkflowRecord wfRecord = wf.getRecord();
            boolean bEnd = wfRt.willEnd();
            if (bEnd) {
                LOG.info("wf.force_end:wfRecord={}", wfRecord);
                killSteps(wfRt);
            } else if (wf.getStore().isAllStepsHistory(wfRecord)) {
                bEnd = true;
                LOG.info("wf.auto_end_since_all_steps_finished:wfRecord={}", wfRecord);
            }
            if (bEnd) {
                this.doEndWorkflow(NopWfCoreConstants.WF_STATUS_COMPLETED, wfRt);
            }
        }
    }

    void doEndWorkflow(int status, WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        if (wf.isEnded())
            return;

        LOG.debug("wf.end-workflow:status={},wf={}", status, wf.getRecord());
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_END);
        IWorkflowRecord wfRecord = wf.getRecord();
        wfRecord.setEndTime(CoreMetrics.currentTimestamp());
        wfRecord.setLastOperateTime(CoreMetrics.currentTimestamp());
        wfRecord.setLastOperator(wfRt.getCaller());

        WfModel wfModel = (WfModel) wf.getModel();
        WfEndModel endModel = wfModel.getEnd();

        if (endModel != null) {
            runXpl(endModel.getSource(), wfRt);
        }
        wfRt.saveWfRecord(status);

        this.endSubFlow(wf, status, wfRt);

        wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_END);
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
        wf.getStore().logError(wf.getRecord(), stepName, actionName, e);
    }

    private void handleError(Exception e, String actionName, WfStepModel stepModel,
                             WfRuntime wfRt) {
        this.logError(wfRt.getWf(), stepModel.getName(), actionName, e);

        wfRt.setException(e);

        if (stepModel.getOnError() != null) {
            if (ConvertHelper.toBoolean(runXpl(stepModel.getOnError(), wfRt)))
                return;
        }

        WfModel wfModel = wfRt.getWfModel();
        if (wfModel.getOnError() != null) {
            if (ConvertHelper.toBoolean(runXpl(wfModel.getOnError(), wfRt)))
                return;
        }

        if (wfRt.getException() != null)
            throw NopException.adapt(wfRt.getException());
    }

    private void saveStepRecord(IWorkflowStepImplementor step) {
        step.getStore().saveStepRecord(step.getRecord());
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

        if (actionModel.isCommon() && actionModel.getWhenSteps() != null && !actionModel.getWhenSteps().isEmpty()) {
            if (!actionModel.getWhenSteps().contains(step.getStepName()))
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
        if (step.getWorkflow().isSuspended())
            return false;

        // 如果流程已结束, 一般情况下是不允许执行action的
        if (step.getWorkflow().isEnded()) {
            if (!actionModel.isForFlowEnded())
                return false;
        }

        // 分成三种情况：等待/活动/历史
        int status = step.getRecord().getStatus();
        if (status == NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED) {
            return actionModel.isForActivated();
        } else if (status == NopWfCoreConstants.WF_STEP_STATUS_WAITING) {
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

                        target.setStepType(targetStepModel.getType().name());
                        WfAssignmentModel assignment = targetStepModel.getAssignment();
                        if (assignment != null) {
                            WfActorCandidatesBean candidates = getActorCandidates(assignment, wfRt);
                            target.setActorCandidates(candidates);
                            target.setIgnoreNoAssign(assignment.isIgnoreNoAssign());
                        }
                        target.setStepSpecialType(targetStepModel.getSpecialType());
                        target.setStepType(targetStepModel.getType().toString());
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
        WfRuntime wfRt = newWfRuntime(step, ctx);
        initArgs(wfRt, args);

        WfStepModel stepModel = wfRt.getWfModel().getStep(stepName);
        if (stepModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, stepName);

        WfAssignmentModel assignment = stepModel.getAssignment();
        List<WfActorWithWeight> actors = getActors(assignment, stepName, wfRt);
        this.newSteps(step, stepModel, null, actors, wfRt);
    }

    @Override
    public List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWorkflowStepImplementor step, IWfRuntime wfRt) {
        if (step.getModel().getJoinType() == WfJoinType.and) {
            WfStepModel stepModel = (WfStepModel) step.getModel();
            Set<String> waitSteps = stepModel.getWaitStepNames();
            Collection<? extends IWorkflowStepRecord> stepRecords = step.getStore().getJoinWaitStepRecords(
                    step.getRecord(), stepRecord -> {
                        IWorkflowStepImplementor waitStep = step.getWorkflow().getStepByRecord(stepRecord);
                        return getJoinGroup((WfStepModel) waitStep.getModel(), waitStep, (WfRuntime) wfRt);
                    }, waitSteps);
            return step.getWorkflow().getStepsByRecords(stepRecords);
        } else {
            return Collections.emptyList();
        }
    }
}