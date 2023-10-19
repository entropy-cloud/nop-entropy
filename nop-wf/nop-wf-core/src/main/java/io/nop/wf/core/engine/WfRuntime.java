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
import io.nop.commons.concurrent.executor.ContinuationExecutor;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.WfConstants;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfActorBean;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.WfListenerModel;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.store.IWorkflowActionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.nop.wf.core.WfErrors.ARG_WF_ID;
import static io.nop.wf.core.WfErrors.ARG_WF_NAME;
import static io.nop.wf.core.WfErrors.ARG_WF_VERSION;

/**
 * 每次调用工作流对象上的action时所创建的运行时对象。在EL表达式中可以通过wfRt变量来访问该运行时对象
 */
public class WfRuntime implements IEvalContext, Executor {

    static final ThreadLocal<Map<IWorkflowImplementor, ContinuationExecutor.Continuation>> s_continuation
            = ThreadLocal.withInitial(HashMap::new);

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


    private List<IWfActor> currentActors;
    private IWorkflowStepImplementor currentStep;
    private IWorkflowActionRecord actionRecord;

    private Throwable exception;

    class WfContinuation extends ContinuationExecutor.Continuation {
        @Override
        protected void onLoopFinished() {
            s_continuation.get().remove(wf);
        }
    }

    public WfRuntime(IWorkflowImplementor wf, IServiceContext serviceContext) {
        this.serviceContext = serviceContext;
        this.wf = wf;
        this.scope = newEvalScope();
    }

    private IEvalScope newEvalScope() {
        IEvalScope scope = serviceContext.getEvalScope().newChildScope();
        scope.setLocalValue(null, WfConstants.VAR_WF, wf);
        scope.setLocalValue(null, WfConstants.VAR_WF_RT, this);
        return scope;
    }

    /**
     * 如果当前有同一工作流实例相关的任务在执行，则会放入执行队列，延迟执行
     *
     * @param command the runnable task
     */
    @Override
    public void execute(Runnable command) {
        Map<IWorkflowImplementor, ContinuationExecutor.Continuation> conts = s_continuation.get();
        ContinuationExecutor.Continuation cont = conts.get(wf);
        if (cont == null) {
            cont = new WfContinuation();
            conts.put(wf, cont);
        }
        cont.submit(command);
    }

    public Object getValue(String name) {
        return scope.getLocalValue(name);
    }

    public void setValue(String name, Object value) {
        scope.setLocalValue(name, value);
    }

    public void initArgs(Map<String, Object> args) {
        if (args != null) {
            scope.setLocalValues(args);

            this.targetSteps = ConvertHelper.toCsvSet(getValue(WfConstants.VAR_TARGET_STEPS));
            this.rejectSteps = ConvertHelper.toCsvSet(getValue(WfConstants.VAR_REJECT_STEPS));
        }
    }

    public List<IWfActor> getSelectedActors() {
        if (selectedActors == null) {
            selectedActors = resolveActors(getValue(WfConstants.VAR_SELECTED_ACTORS));
        }
        return selectedActors;
    }

    private List<IWfActor> resolveActors(Object value) {
        if (value == null)
            return Collections.emptyList();

        List<WfActorBean> actors = BeanTool.castBeanToType(value, JavaGenericTypeBuilder.buildListType(WfActorBean.class));
        List<IWfActor> ret = new ArrayList<>(actors.size());
        for (WfActorBean actorInfo : actors) {
            ret.add(wf.resolveActor(actorInfo.getType(), actorInfo.getActorId(), actorInfo.getDeptId()));
        }
        return ret;
    }

    public void setSelectedActors(List<IWfActor> selectedActors) {
        this.selectedActors = selectedActors;
    }

    public Map<String, List<IWfActor>> getSelectedStepActors() {
        if (selectedStepActors == null)
            selectedStepActors = resolveStepActors(getValue(WfConstants.VAR_SELECTED_STEP_ACTORS));
        return selectedStepActors;
    }

    private Map<String, List<IWfActor>> resolveStepActors(Object value) {
        if (value == null)
            return Collections.emptyMap();

        Map<String, Object> map = (Map<String, Object>) value;
        Map<String, List<IWfActor>> ret = CollectionHelper.newHashMap(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object v = entry.getValue();
            List<IWfActor> actors = resolveActors(v);
            ret.put(entry.getKey(), actors);
        }
        return ret;
    }

    public void setSelectedStepActors(Map<String, List<IWfActor>> selectedStepActors) {
        this.selectedStepActors = selectedStepActors;
    }

    public Set<String> getRejectSteps() {
        return rejectSteps;
    }

    public void setRejectSteps(Set<String> rejectSteps) {
        this.rejectSteps = rejectSteps;
    }

    public Set<String> getTargetSteps() {
        return targetSteps;
    }

    public void setTargetSteps(Set<String> targetSteps) {
        this.targetSteps = targetSteps;
    }

    public Set<String> getTargetCases() {
        return targetCases;
    }

    public void setTargetCases(Set<String> targetCases) {
        this.targetCases = targetCases;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public IServiceContext getServiceContext() {
        return serviceContext;
    }

    public IWorkflowImplementor getWf() {
        return wf;
    }

    public WfModel getWfModel() {
        return (WfModel) wf.getModel();
    }

    public NopException newError(ErrorCode errorCode) {
        NopException e = new NopException(errorCode).param(ARG_WF_NAME, wf.getWfName())
                .param(ARG_WF_VERSION, wf.getWfVersion()).param(ARG_WF_ID, wf.getWfId());
        return e;
    }

    public IWfActor getCaller() {
        String userId = serviceContext.getContext().getUserId();
        if (userId == null)
            userId = IWfActor.SYS_USER_ID;
        return wf.resolveUser(userId);
    }

    public List<IWfActor> getSelectedActors(String targetStep) {
        if (targetStep == null)
            return getSelectedActors();

        Map<String, List<IWfActor>> stepActors = getSelectedStepActors();
        List<IWfActor> actors = stepActors.get(targetStep);
        if (actors == null)
            actors = getSelectedActors();
        return actors;
    }

    public List<IWfActor> getCurrentActors() {
        return currentActors;
    }

    public void setCurrentActors(List<IWfActor> currentActors) {
        this.currentActors = currentActors;
    }

    public IWorkflowStepImplementor getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(IWorkflowStepImplementor currentStep) {
        this.currentStep = currentStep;
    }

    public void triggerEvent(String event) {
        WfModel wfModel = (WfModel) wf.getModel();
        List<WfListenerModel> listeners = wfModel.getListeners();
        if (listeners != null && listeners.isEmpty()) {
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

    public void saveWfRecord(int status) {
        wf.getRecord().transitToStatus(NopWfCoreConstants.WF_STATUS_CREATED);

        wf.getStore().saveWfRecord(wf.getRecord());

        triggerEvent(WfConstants.EVENT_AFTER_SAVE);
    }

    public IWorkflowActionRecord getActionRecord() {
        return actionRecord;
    }

    public void setActionRecord(IWorkflowActionRecord actionRecord) {
        this.actionRecord = actionRecord;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public void markEnd() {
        wf.getRecord().markEnd();
    }

    public boolean willEnd() {
        return wf.getRecord().willEnd();
    }
}