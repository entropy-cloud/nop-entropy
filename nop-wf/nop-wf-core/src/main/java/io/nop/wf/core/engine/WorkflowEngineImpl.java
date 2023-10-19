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
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorCandidatesBean;
import io.nop.wf.core.IWorkflowCoordinator;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.WfConstants;
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
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.core.WfErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.WfErrors.ARG_ARG_NAME;
import static io.nop.wf.core.WfErrors.ARG_REJECT_STEP;
import static io.nop.wf.core.WfErrors.ARG_STEP_ID;
import static io.nop.wf.core.WfErrors.ARG_STEP_NAME;
import static io.nop.wf.core.WfErrors.ARG_TARGET_CASES;
import static io.nop.wf.core.WfErrors.ARG_TARGET_STEPS;
import static io.nop.wf.core.WfErrors.ERR_WF_ACTION_CONDITIONS_NOT_PASSED;
import static io.nop.wf.core.WfErrors.ERR_WF_ACTION_NOT_ALLOWED_WHEN_SIGNAL_NOT_READY;
import static io.nop.wf.core.WfErrors.ERR_WF_ACTION_TRANSITION_NO_NEXT_STEP;
import static io.nop.wf.core.WfErrors.ERR_WF_ALREADY_STARTED;
import static io.nop.wf.core.WfErrors.ERR_WF_EMPTY_ACTION_ARG;
import static io.nop.wf.core.WfErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS;
import static io.nop.wf.core.WfErrors.ERR_WF_NOT_ALLOW_REMOVE;
import static io.nop.wf.core.WfErrors.ERR_WF_NOT_ALLOW_START;
import static io.nop.wf.core.WfErrors.ERR_WF_NOT_ALLOW_SUSPEND;
import static io.nop.wf.core.WfErrors.ERR_WF_REJECT_ACTION_IS_NOT_ALLOWED;
import static io.nop.wf.core.WfErrors.ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP;
import static io.nop.wf.core.WfErrors.ERR_WF_START_WF_FAIL;
import static io.nop.wf.core.WfErrors.ERR_WF_STEP_NO_ASSIGNMENT;
import static io.nop.wf.core.WfErrors.ERR_WF_TRANSITION_TARGET_CASES_NOT_MATCH;
import static io.nop.wf.core.WfErrors.ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH;
import static io.nop.wf.core.WfErrors.ERR_WF_UNKNOWN_ACTION;
import static io.nop.wf.core.WfErrors.ERR_WF_UNKNOWN_ACTION_ARG;
import static io.nop.wf.core.WfErrors.ERR_WF_UNKNOWN_STEP;
import static io.nop.wf.core.WfErrors.ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED;

/**
 * 工作流引擎的核心处理逻辑
 */
public class WorkflowEngineImpl extends WfActorAssignSupport implements IWorkflowEngine {
    static final Logger LOG = LoggerFactory.getLogger(WorkflowEngineImpl.class);

    private IWorkflowCoordinator workflowCoordinator;

    @Inject
    public void setWorkflowCoordinator(IWorkflowCoordinator workflowCoordinator) {
        this.workflowCoordinator = workflowCoordinator;
    }

    protected WfRuntime newWfRuntime(IWorkflowImplementor wf, IServiceContext ctx) {
        return new WfRuntime(wf, ctx);
    }

    protected WfRuntime newWfRuntime(IWorkflowStepImplementor step, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step.getWorkflow(), ctx);
        wfRt.setCurrentStep(step);
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
        if (wfRecord.getStatus() <= NopWfCoreConstants.WF_STATUS_CREATED) {
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

        initArgs(wfModel.getStart(), args, WfConstants.SYS_ACTION_START, wfRt);

        // 判断是否允许执行
        if (!passConditions(wfModel.getStart(), wfRt))
            throw wfRt.newError(ERR_WF_NOT_ALLOW_START);


        // 如果参数名与record的属性名相同，则直接更新record。例如可以设置title、bizObjName、bizObjId等属性
        if (wfModel.getStart().getArgs() != null) {
            IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(record.getClass());

            for (WfArgVarModel argModel : wfModel.getStart().getArgs()) {
                IBeanPropertyModel propModel = beanModel.getPropertyModel(argModel.getName());
                if (propModel != null) {
                    propModel.setPropertyValue(record, wfRt.getValue(argModel.getName()));
                }
            }
        }

        WfAssignmentModel manager = wfModel.getManagerAssignment();
        if (manager != null) {
            // 只取assignment配置的第一个actor
            List<IWfActor> actors = this.getAssignmentActors(manager, wfRt);
            if (actors != null && actors.size() >= 1) {
                IWfActor actor = actors.get(0);
                wf.getRecord().setManager(actor);
            }
        }

        initCreateStatus(wfRt);

        wfRt.triggerEvent(WfConstants.EVENT_BEFORE_START);

        // 执行source
        runXpl(wfModel.getStart().getSource(), wfRt);

        List<IWfActor> actors = getActors(wfModel.getStartStep().getAssignment(), null, wfRt);

        // 迁移到起始步骤
        if (!newSteps(null, wfModel.getStartStep(), WfConstants.SYS_ACTION_START, actors, wfRt))
            throw wfRt.newError(ERR_WF_START_WF_FAIL);

        saveStarted(wfRt);
    }


    boolean newSteps(IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                     List<IWfActor> actors, WfRuntime wfRt) {
        if (actors == null)
            actors = Collections.emptyList();

        WfAssignmentModel assignment = stepModel.getAssignment();

        if (actors.isEmpty()) {
            if (assignment == null || !assignment.isIgnoreNoAssign()) {
                IWfActor manager = wfRt.getWf().getManagerActor();
                if (manager == null) {
                    manager = wfRt.getCaller();
                }
                actors = Collections.singletonList(manager);
            }
        }

        if (actors.isEmpty()) {
            handleNoAssign(stepModel, wfRt);
            return false;
        }

        // 对每一个actor生成一个步骤实例
        for (IWfActor actor : actors) {
            newStepForActor(currentStep, stepModel, fromAction, actor, null, wfRt);
        }

        return true;
    }

    private void handleNoAssign(WfStepModel stepModel, WfRuntime wfRt) {
        WfAssignmentModel assignment = stepModel.getAssignment();
        if (assignment != null && assignment.isIgnoreNoAssign())
            return;
        wfRt.triggerEvent(WfConstants.EVENT_ON_NO_ASSIGN);
        throw wfRt.newError(ERR_WF_STEP_NO_ASSIGNMENT).param(ARG_STEP_NAME, stepModel.getName());
    }


    void newStepForActor(IWorkflowStepImplementor currentStep, WfStepModel stepModel, String fromAction,
                         IWfActor actor, IWfActor owner, WfRuntime wfRt) {

        IWorkflowImplementor wf = wfRt.getWf();
        if (stepModel.getJoinType() != null) {
            // join步骤会自动查找已经存在的步骤实例
            IWorkflowStepRecord stepRecord = wf.getStore().getNextWaitingStepRecord(currentStep.getRecord(),
                    stepModel.getJoinTargetStep(), stepModel.getName(), actor);
            if (stepRecord != null) {
                IWorkflowStepImplementor step = wf.getStepByRecord(stepRecord);

                if (currentStep != null) {
                    wf.getStore().addNextStepRecord(currentStep.getRecord(), fromAction, step.getRecord());
                }

                // 处于等待状态的join步骤，新增加上游步骤之后需要检查是否可以转入激活状态
                if (step.isWaiting() && stepModel.getJoinType() == WfJoinType.and) {
                    wfRt.execute(() -> checkJoinStep(step, wfRt));
                }
                return;
            }
        }

        if (owner == null)
            owner = getOwner(stepModel.getAssignment(), actor, wfRt);

        IWorkflowStepRecord stepRecord = wf.getStore().newStepRecord(wf.getRecord(), stepModel);
        if (stepModel.getJoinTargetStep() != null && stepModel.getJoinValueExpr() != null) {
            Object value = runXpl(stepModel.getJoinValueExpr(), wfRt);
            stepRecord.setJoinValue(stepModel.getJoinTargetStep(), StringHelper.toString(value, ""));
        }

        IWorkflowStepImplementor step = wf.getStepByRecord(stepRecord);
        stepRecord.setActor(actor);
        stepRecord.setOwner(owner);

        stepRecord.setFromAction(fromAction);

        runXpl(stepModel.getOnEnter(), wfRt);

        if (stepModel.getDueTimeExpr() != null && stepModel.getDueAction() != null) {
            Timestamp dueTime = ConvertHelper.toTimestamp(stepModel.getDueTimeExpr().invoke(wfRt));
            if (dueTime != null) {
                stepRecord.setDueTime(dueTime);
            }
        }

        wfRt.triggerEvent(WfConstants.EVENT_ENTER_STEP);

        // 子流程步骤和需要等待合并的步骤新建时处于waiting状态，其他情况都是activated状态
        if (step.isFlowType()) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
            startSubflow(stepModel.getStart(), step, wfRt);
        } else if (stepModel.getJoinType() == WfJoinType.and) {
            stepRecord.transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_WAITING);
        } else if (!wf.isAllSignalOn(stepModel.getWaitSignals())) {
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

    private void startSubflow(WfSubFlowStartModel startFlowModel, IWorkflowStepImplementor parentStep, WfRuntime wfRt) {

        Map<String, Object> vars = getStartArgs(startFlowModel.getArgs(), wfRt);

        WfReference wfRef = this.workflowCoordinator.startSubflow(startFlowModel.getWfName(), startFlowModel.getWfVersion(),
                parentStep.getStepReference(),
                vars, wfRt.getEvalScope());

        parentStep.getRecord().setSubWfRef(wfRef);
    }

    private Map<String, Object> getStartArgs(List<WfSubFlowArgModel> argsModel, WfRuntime wfRt) {
        if (argsModel == null || argsModel.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> ret = new HashMap<>();
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
        wfRecord.setStarter(wfRt.getCaller());
        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);

        wfRt.triggerEvent(WfConstants.EVENT_AFTER_START);
    }

    private void checkJoinStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        IWorkflowImplementor wf = wfRt.getWf();
        if (!wf.isAllSignalOn(step.getModel().getWaitSignals()))
            return;

        // 如果是and-join, 则当它所等待的所有步骤都结束时它才进入activated状态。
        boolean allWaitFinished = true;
        for (IWorkflowStepImplementor waitStep : step.getJoinWaitSteps()) {
            if (!waitStep.isHistory()) {
                allWaitFinished = false;
                break;
            }
        }
        if (allWaitFinished) {
            step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
            wfRt.triggerEvent(WfConstants.EVENT_ACTIVATE_STEP);
        }
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

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            WfArgVarModel argModel = argsModel.getArg(name);
            if (argModel == null) {
                // 为简化配置，假设总是允许selectedActors这个参数
                if (name.equals(WfConstants.VAR_SELECTED_ACTORS) || name.equals(WfConstants.VAR_SELECTED_STEP_ACTORS))
                    continue;

                throw wfRt.newError(ERR_WF_UNKNOWN_ACTION_ARG)
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
                    entry.setValue(value);
                }
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

        wfRt.initArgs(args);
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

        wfRt.initArgs(args);
        checkManageAuth(wfRt);

        wf.getRecord().setSuspendTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wf.getRecord().setSuspendCaller(caller);

        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_SUSPENDED);
        wfRt.triggerEvent(WfConstants.EVENT_SUSPEND);
    }

    void checkManageAuth(WfRuntime wfRt) {
        runXpl(wfRt.getWfModel().getCheckManageAuth(), wfRt);
    }

    @Override
    public void resume(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        if (!wf.isSuspended())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        wfRt.initArgs(args);
        checkManageAuth(wfRt);

        wf.getRecord().setResumeTime(CoreMetrics.currentTimestamp());
        IWfActor caller = wfRt.getCaller();
        wf.getRecord().setResumeCaller(caller);

        wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);
        wfRt.triggerEvent(WfConstants.EVENT_RESUME);
    }

    @Override
    public void remove(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(wf, ctx);
        if (!wf.isEnded() && wf.isStarted())
            throw wfRt.newError(ERR_WF_NOT_ALLOW_REMOVE);

        wfRt.initArgs(args);
        checkManageAuth(wfRt);

        wf.getStore().removeWfRecord(wf.getRecord());
        wfRt.triggerEvent(WfConstants.EVENT_REMOVE);
    }

    @Override
    public void kill(IWorkflowImplementor wf, Map<String, Object> args, IServiceContext ctx) {
        if (wf.isEnded())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        wfRt.initArgs(args);
        checkManageAuth(wfRt);

        IWfActor caller = wfRt.getCaller();
        wf.getRecord().setCanceller(caller);

        wfRt.triggerEvent(WfConstants.EVENT_BEFORE_KILL);

        killSteps(wfRt);

        wfRt.markEnd();
        wfRt.execute(() -> {
            doEndWorkflow(NopWfCoreConstants.WF_STATUS_KILLED, wfRt);
        });

        wfRt.triggerEvent(WfConstants.EVENT_AFTER_KILL);
    }

    @Override
    public void turnSignalOn(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx) {
        if (signals == null || signals.isEmpty())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowStore wfStore = wf.getStore();
        Set<String> onSignals = wfStore.getOnSignals(wf.getRecord());
        onSignals.addAll(signals);
        wfStore.saveOnSignals(wf.getRecord(), onSignals);

        wfRt.triggerEvent(WfConstants.EVENT_SIGNAL_ON);
    }

    @Override
    public void turnSignalOff(IWorkflowImplementor wf, Set<String> signals, IServiceContext ctx) {
        if (signals == null || signals.isEmpty())
            return;

        WfRuntime wfRt = newWfRuntime(wf, ctx);
        IWorkflowStore wfStore = wf.getStore();
        Set<String> onSignals = wfStore.getOnSignals(wf.getRecord());
        onSignals.removeAll(signals);
        wfStore.saveOnSignals(wf.getRecord(), onSignals);

        wfRt.triggerEvent(WfConstants.EVENT_SIGNAL_OFF);
    }

    @Override
    public void changeActor(IWorkflowStepImplementor step, IWfActor actor, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        step.getRecord().setActor(actor);
        saveStepRecord(step);
        wfRt.triggerEvent(WfConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public void changeOwner(IWorkflowStepImplementor step, String ownerId, IServiceContext ctx) {
        WfRuntime wfRt = newWfRuntime(step, ctx);
        IWfActor owner = StringHelper.isEmpty(ownerId) ? null : resolveUser(ownerId);
        step.getRecord().setOwner(owner);
        saveStepRecord(step);
        wfRt.triggerEvent(WfConstants.EVENT_CHANGE_ACTOR);
    }

    @Override
    public void triggerStepEvent(IWorkflowStepImplementor step, String eventName, IServiceContext ctx) {

    }

    @Override
    public void killStep(IWorkflowStepImplementor step, Map<String, Object> args, IServiceContext ctx) {
        if (step.isHistory())
            return;

        WfRuntime rt = newWfRuntime(step, ctx);

        _killStep(step, rt);

        runContinuation(rt);
    }

    void _killStep(IWorkflowStepImplementor step, WfRuntime wfRt) {
        LOG.info("wf.kill_step:step={}", step);

        IWfActor caller = wfRt.getCaller();
        if (caller != null) {
            step.getRecord().setCaller(caller);
        }

        this.doExitStep(step, NopWfCoreConstants.WF_STEP_STATUS_KILLED, wfRt);
        wfRt.triggerEvent(WfConstants.EVENT_KILL_STEP);
    }

    void runContinuation(WfRuntime wfRt) {
        wfRt.execute(() -> {
            checkEnd(wfRt);
        });
    }

    @Override
    public void triggerChange(IWorkflowStepImplementor step, IServiceContext ctx) {

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

        wfRt.triggerEvent(WfConstants.EVENT_BEFORE_ACTION);


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

            if (!actionModel.isLocal()) {
                if (!step.isHistory() && !step.isWaiting()) {
                    int status = NopWfCoreConstants.WF_STEP_STATUS_COMPLETED;
                    if (actionModel.isForReject()) {
                        status = NopWfCoreConstants.WF_STEP_STATUS_REJECTED;
                    }
                    doExitStep(step, status, wfRt);
                }
            }

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
        } catch (Exception e) {
            handleError(e, actionModel.getName(), (WfStepModel) step.getModel(), wfRt);
        }

        wfRt.triggerEvent(WfConstants.EVENT_AFTER_ACTION);
        return result;
    }

    private void doReject(IWorkflowStepImplementor step, WfActionModel actionModel, WfRuntime wfRt) {
        String actionName = actionModel.getName();
        WfStepModel stepModel = (WfStepModel) step.getModel();
        Set<String> rejectSteps = wfRt.getRejectSteps();

        IWorkflowImplementor wf = step.getWorkflow();
        if (rejectSteps != null && !rejectSteps.isEmpty()) {
            for (String rejectStepName : rejectSteps) {
                IWorkflowStepImplementor rejectStep = wf.getStepById(rejectStepName);
                if (rejectStep == null)
                    throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, rejectStepName);

                if (!stepModel.hasAncestorStep(step.getStepName()))
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
            this.newStepForActor(currentStep, (WfStepModel) rejectStep.getModel(), actionName,
                    rejectStep.getActor(), rejectStep.getOwner(), wfRt);
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
            this.newStepForActor(step, stepModel, actionModel.getName(), step.getActor(), step.getOwner(), wfRt);
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

                wfRt.triggerEvent(WfConstants.EVENT_EXIT_STEP);
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
                LOG.debug("wf.transition_to_empty:step={},action={}", currentStep, actionName);
                runXpl(toM.getBeforeTransition(), wfRt);
                wfStore.addNextSpecialStep(currentStep.getRecord(), actionName, WfConstants.STEP_ID_EMPTY);
                runXpl(toM.getAfterTransition(), wfRt);
                return;
            }
            case TO_END: {
                LOG.debug("wf.transition_to_end:step={},action={}", currentStep, actionName);
                runXpl(toM.getBeforeTransition(), wfRt);
                wfStore.addNextSpecialStep(currentStep.getRecord(), actionName, WfConstants.STEP_ID_END);
                wfRt.markEnd();// 延迟结束工作流实例
                runXpl(toM.getAfterTransition(), wfRt);
                return;
            }
            case TO_ASSIGNED: {
                LOG.debug("wf.transition_to_assigned:step={},actionName={},targetSteps={}", currentStep, actionName,
                        targetSteps);
                if (targetSteps != null) {
                    for (String targetStep : targetSteps) {
                        transitionToStep(currentStep, targetStep, toM, wfRt);
                    }
                }
                break;
            }
            case TO_STEP:
                String stepName = toM.getStepName();
                LOG.debug("wf.transition_to_step:step={},actionName={},stepName={}", currentStep, actionName, stepName);
                transitionToStep(currentStep, actionName, toM, wfRt);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    void transitionToStep(IWorkflowStepImplementor currentStep, String actionName,
                          WfTransitionToModel toM, WfRuntime wfRt) {
        WfModel wfModel = (WfModel) currentStep.getWorkflow().getModel();
        runXpl(toM.getBeforeTransition(), wfRt);

        String targetStep = toM.getStepName();
        WfStepModel stepModel = wfModel.getStep(targetStep);
        if (stepModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, targetStep);

        List<IWfActor> actors = getActors(stepModel.getAssignment(), targetStep, wfRt);
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
        wfRt.triggerEvent(WfConstants.EVENT_BEFORE_END);
        IWorkflowRecord wfRecord = wf.getRecord();
        wfRecord.setEndTime(CoreMetrics.currentTimestamp());
        wfRt.saveWfRecord(status);

        WfModel wfModel = (WfModel) wf.getModel();
        WfEndModel endModel = wfModel.getEnd();

        if (endModel != null) {
            runXpl(endModel.getSource(), wfRt);
        }

        this.endSubflow(wf, status, wfRt);

        wfRt.triggerEvent(WfConstants.EVENT_AFTER_END);
    }

    void endSubflow(IWorkflowImplementor wf, int status, WfRuntime wfRt) {
        WfModel wfModel = (WfModel) wf.getModel();
        Map<String, Object> results = null;
        if (wfModel.getEnd() != null) {
            results = getVars(wfModel.getEnd().getOutputs(), wfRt);
        }
        WfStepReference parentStepRef = getParentStepRef(wf);
        if (parentStepRef != null) {
            // 子流程结束，通知父流程
            workflowCoordinator.endSubflow(wf.getWfReference(), status, parentStepRef, results, wfRt.getEvalScope());
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
        for (IWorkflowStepRecord record : wfStore.getStepRecords(wf.getRecord(), false)) {
            IWorkflowStepImplementor step = wf.getStepByRecord(record);
            WfRuntime stepRt = newWfRuntime(step.getWorkflow(), wfRt.getServiceContext());
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
            if (!actionModel.isForEnded())
                return false;
        }

        // 分成三种情况：等待/活动/历史
        int status = step.getRecord().getStatus();
        if (status == NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED) {
            return actionModel.isForActivated();
        } else if (status == NopWfCoreConstants.WF_STEP_STATUS_WAITING) {
            return actionModel.isForWaiting();
        } else if (step.isHistory()) {
            return actionModel.isForHistory();
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
                        target.setStepName(WfConstants.STEP_ID_EMPTY);
                        target.setStepDisplayName(WfConstants.STEP_ID_EMPTY);
                        break;
                    }
                    case TO_END: {
                        target.setStepName(WfConstants.STEP_ID_END);
                        target.setStepDisplayName(WfConstants.STEP_ID_END);
                        break;
                    }
                    case TO_ASSIGNED: {
                        target.setStepName(WfConstants.STEP_ID_ASSIGNED);
                        target.setStepDisplayName(WfConstants.STEP_ID_ASSIGNED);
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
        wfRt.initArgs(args);

        WfStepModel stepModel = wfRt.getWfModel().getStep(stepName);
        if (stepModel == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, stepName);

        WfAssignmentModel assignment = stepModel.getAssignment();
        List<IWfActor> actors = getActors(assignment, stepName, wfRt);
        this.newSteps(step, stepModel, null, actors, wfRt);
    }

    @Override
    public List<? extends IWorkflowStepImplementor> getJoinWaitSteps(IWorkflowStepImplementor step) {
        if (step.getModel().getJoinType() == WfJoinType.and) {
            WfStepModel stepModel = (WfStepModel) step.getModel();
            String joinKey = stepModel.getName();
            Set<String> waitSteps = stepModel.getWaitStepNames();
            Collection<? extends IWorkflowStepRecord> stepRecords = step.getStore().getJoinWaitStepRecords(step.getRecord(),
                    joinKey, waitSteps);
            return step.getWorkflow().getStepsByRecords(stepRecords);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void notifySubFlowEnd(@Nonnull WfReference wfRef, int status, @Nonnull WfStepReference parentStep, Map<String, Object> results, @Nonnull IEvalScope scope) {
        workflowCoordinator.endSubflow(wfRef, status, parentStep, results, scope);
    }
}