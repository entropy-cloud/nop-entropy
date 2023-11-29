/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.IWorkflowActionModel;
import io.nop.wf.core.model.WfListenerModel;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.store.IWorkflowActionRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;

/**
 * 每次调用工作流对象上的action时所创建的运行时对象。在EL表达式中可以通过wfRt变量来访问该运行时对象
 */
public class WfRuntime implements IWfRuntime {

    private final IEvalScope scope;
    private final IServiceContext serviceContext;
    private final IWorkflowImplementor wf;

    private List<IWfActor> selectedActors;
    private Map<String, List<IWfActor>> selectedStepActors;

    /**
     * reject action使用
     */
    private Set<String> rejectSteps;

    /**
     * transition使用
     */
    private Set<String> targetSteps;

    private Set<String> targetCases;


    private List<WfActorWithWeight> currentActorAssignments;
    private IWorkflowStepImplementor currentStep;

    private IWorkflowStepImplementor prevStep;
    private IWorkflowActionRecord actionRecord;

    private IWorkflowStepImplementor actionStep;

    private IWfActor assigner;

    private Throwable exception;

    private Object bizEntity;

    public WfRuntime(IWorkflowImplementor wf, IServiceContext serviceContext) {
        this.serviceContext = serviceContext;
        this.wf = wf;
        this.scope = newEvalScope();
    }

    public static WfRuntime fromContext(IServiceContext ctx) {
        return (WfRuntime) ctx.getEvalScope().getValue(NopWfCoreConstants.VAR_WF_RT);
    }

    private IEvalScope newEvalScope() {
        IEvalScope scope = serviceContext.getEvalScope().newChildScope();
        scope.setLocalValue(null, NopWfCoreConstants.VAR_WF, wf);
        scope.setLocalValue(null, NopWfCoreConstants.VAR_WF_RT, this);
        return scope;
    }

    public IWfActor getAssigner() {
        return assigner;
    }

    public void setAssigner(IWfActor assigner) {
        this.assigner = assigner;
    }

    /**
     * 如果当前有同一工作流实例相关的任务在执行，则会放入执行队列，延迟执行
     *
     * @param command the runnable task
     */
    @Override
    public void delayExecute(Runnable command) {
        wf.delayExecute(command);
    }

    @Override
    public Object getValue(String name) {
        return scope.getLocalValue(name);
    }

    @Override
    public void setValue(String name, Object value) {
        scope.setLocalValue(name, value);
    }

    public IWorkflowStepImplementor getPrevStep() {
        return prevStep;
    }

    public void setPrevStep(IWorkflowStepImplementor prevStep) {
        this.prevStep = prevStep;
    }

    @Override
    public List<IWfActor> getSelectedActors() {
        return selectedActors;
    }

    @Override
    public void setSelectedActors(List<IWfActor> selectedActors) {
        this.selectedActors = selectedActors;
    }

    public Map<String, List<IWfActor>> getSelectedStepActors() {
        return selectedStepActors;
    }

    @Override
    public void setSelectedStepActors(Map<String, List<IWfActor>> selectedStepActors) {
        this.selectedStepActors = selectedStepActors;
    }

    @Override
    public Set<String> getRejectSteps() {
        return rejectSteps;
    }

    @Override
    public void setRejectSteps(Set<String> rejectSteps) {
        this.rejectSteps = rejectSteps;
    }

    @Override
    public Object getBizEntity() {
        if (bizEntity == null) {
            bizEntity = wf.getBizEntity();
        }
        return bizEntity;
    }

    @Override
    public Set<String> getTargetSteps() {
        return targetSteps;
    }

    @Override
    public void setTargetSteps(Set<String> targetSteps) {
        this.targetSteps = targetSteps;
    }

    @Override
    public Set<String> getTargetCases() {
        return targetCases;
    }

    @Override
    public void setTargetCases(Set<String> targetCases) {
        this.targetCases = targetCases;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public IServiceContext getSvcCtx() {
        return serviceContext;
    }

    @Override
    public IWorkflowImplementor getWf() {
        return wf;
    }

    @Override
    public WfModel getWfModel() {
        return (WfModel) wf.getModel();
    }

    @Override
    public NopException newError(ErrorCode errorCode) {
        NopException e = new NopException(errorCode).param(ARG_WF_NAME, wf.getWfName())
                .param(ARG_WF_VERSION, wf.getWfVersion()).param(ARG_WF_ID, wf.getWfId());
        return e;
    }

    @Override
    public IWfActor getCaller() {
        String userId = serviceContext.getContext().getUserId();
        if (userId == null)
            userId = IWfActor.SYS_USER_ID;
        return wf.resolveUser(userId);
    }

    @Override
    public IWfActor getSysUser() {
        String userId = IWfActor.SYS_USER_ID;
        return wf.resolveUser(userId);
    }

    @Override
    public IWfActor getManagerActor() {
        return wf.getManagerActor();
    }

    @Override
    public List<IWfActor> getSelectedActors(String targetStep) {
        if (targetStep == null)
            return getSelectedActors();

        Map<String, List<IWfActor>> stepActors = getSelectedStepActors();
        if (stepActors == null)
            return getSelectedActors();

        List<IWfActor> actors = stepActors.get(targetStep);
        if (actors == null)
            actors = getSelectedActors();
        return actors;
    }

    @Override
    public List<WfActorWithWeight> getCurrentActorAssignments() {
        return currentActorAssignments;
    }

    @Override
    public void setCurrentActorAssignments(List<WfActorWithWeight> currentActorAssignments) {
        this.currentActorAssignments = currentActorAssignments;
    }

    @Override
    public IWorkflowStepImplementor getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(IWorkflowStepImplementor currentStep) {
        this.currentStep = currentStep;
    }

    @Override
    public void triggerEvent(String event) {
        WfModel wfModel = (WfModel) wf.getModel();
        List<WfListenerModel> listeners = wfModel.getListeners();
        if (listeners != null && !listeners.isEmpty()) {
            for (WfListenerModel listener : listeners) {
                if (listener.matchPattern(event)) {
                    IEvalAction source = listener.getSource();
                    if (source != null) {
                        source.invoke(scope);
                    }
                }
            }
        }
    }

    @Override
    public void saveWfRecord(int status) {
        wf.getRecord().transitToStatus(status);

        wf.getStore().saveWfRecord(wf.getRecord());

        triggerEvent(NopWfCoreConstants.EVENT_AFTER_SAVE);
    }

    @Override
    public IWorkflowActionModel getActionModel() {
        if (actionRecord == null)
            return null;

        String actionName = actionRecord.getActionName();
        return wf.getModel().getAction(actionName);
    }

    @Override
    public IWorkflowActionRecord getActionRecord() {
        return actionRecord;
    }

    public void setActionRecord(IWorkflowActionRecord actionRecord) {
        this.actionRecord = actionRecord;
    }

    @Override
    public IWorkflowStepImplementor getActionStep() {
        return actionStep;
    }

    public void setActionStep(IWorkflowStepImplementor actionStep) {
        this.actionStep = actionStep;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public void markEnd() {
        wf.getRecord().markEnd();
    }

    public boolean willEnd() {
        return wf.getRecord().willEnd();
    }

    @Override
    public void logMsg(String msg) {
        String stepId = currentStep == null ? null : currentStep.getStepId();
        String actionId = actionRecord == null ? null : actionRecord.getSid();
        wf.getStore().logMsg(wf.getRecord(), stepId, actionId, msg);
    }

    @Override
    public void logError(String errorCode, Map<String, Object> params) {
        String stepId = currentStep == null ? null : currentStep.getStepId();
        String actionId = actionRecord == null ? null : actionRecord.getSid();
        wf.getStore().logError(wf.getRecord(), stepId, actionId, errorCode, params);
    }

    @Override
    public void logError(Throwable exp) {
        String stepId = currentStep == null ? null : currentStep.getStepId();
        String actionId = actionRecord == null ? null : actionRecord.getSid();
        wf.getStore().logError(wf.getRecord(), stepId, actionId, exp);
    }
}