/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepResult;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.task.TaskConstants.SPECIAL_STEP_PREFIX;
import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.task.TaskStepResult.RESULT_SUSPEND;


public abstract class AbstractTaskStep implements ITaskStep {
    static final Logger LOG = LoggerFactory.getLogger(AbstractTaskStep.class);

    private SourceLocation location;
    private String stepId;
    private String stepType;
    private boolean saveState;

    /**
     * 与父步骤共享状态对象
     */
    private boolean shareState;
    private boolean internal;
    private Set<String> tagSet;
    private String extType;

    private boolean allowStartIfComplete;

    private String nextStepId;

    private IEvalPredicate when;

    private IEvalAction validator;

    public IEvalAction getValidator() {
        return validator;
    }

    public void setValidator(IEvalAction validator) {
        this.validator = validator;
    }

    public IEvalPredicate getWhen() {
        return when;
    }

    public void setWhen(IEvalPredicate when) {
        this.when = when;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public boolean isSaveState() {
        return saveState;
    }

    public void setSaveState(boolean saveState) {
        this.saveState = saveState;
    }

    public boolean isShareState() {
        return shareState;
    }

    public void setShareState(boolean shareState) {
        this.shareState = shareState;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        this.tagSet = tagSet;
    }

    public String getExtType() {
        return extType;
    }

    public void setExtType(String extType) {
        this.extType = extType;
    }

    public boolean isAllowStartIfComplete() {
        return allowStartIfComplete;
    }

    public void setAllowStartIfComplete(boolean allowStartIfComplete) {
        this.allowStartIfComplete = allowStartIfComplete;
    }

    public String getNextStepId() {
        return nextStepId;
    }

    protected List<? extends ITaskOutputModel> getOutputs() {
        return Collections.emptyList();
    }

    protected List<? extends ITaskInputModel> getInputs() {
        return Collections.emptyList();
    }

    public void setNextStepId(String nextStepId) {
        this.nextStepId = nextStepId;
    }

    protected ITaskStepState loadState(int runId, ITaskStepState parentState, ITaskContext context) {
        if (isShareState())
            return parentState;

        if (!isSaveState())
            return null;

        // 初次调用的时候runId <= 0
        if (runId <= 0)
            return null;

        ITaskStateStore store = context.getStateStore();
        if (store == null)
            return null;

        ITaskStepState state = store.loadStepState(getStepId(), runId, context);
        return state;
    }

    protected void saveState(ITaskStepState state, ITaskContext context) {
        if (!isSaveState())
            return;

        if (!state.needSave())
            return;

        context.saveState(state);
    }

    @Override
    public TaskStepResult execute(int runId,
                                  ITaskStepState parentState, ITaskContext context) {
        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={}," +
                        "loc={}",
                context.getTaskName(), context.getTaskInstanceId(),
                this.getStepId(), runId, getLocation());

        if (context.isCancelled())
            throw new NopException(ERR_TASK_CANCELLED);

        IEvalScope parentScope = parentState == null ? parentState.getEvalScope() : context.getEvalScope().duplicate();

        ITaskStepState state = null;
        try {
            state = loadState(runId, parentState, context);

            if (state == null) {
                // 如果具有历史状态，则不会执行判断条件，而是执行continuation
                IEvalPredicate when = getWhen();
                if (when != null) {
                    // 如果不满足条件，则直接跳过执行
                    if (!when.passConditions(parentScope)) {
                        LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={}," +
                                        "stepId={},runId={}",
                                context.getTaskName(), context.getTaskInstanceId(), this.getStepId(), runId);
                        return TaskStepResult.RESULT_SUCCESS;
                    }
                }

                // 只有第一次执行时才会初始化input
                state = newStepState(runId, parentScope, parentState, context);

                // 执行具体步骤内容之前先保存参数状态
                saveState(state, context);
            } else if (!isShareState()) {
                if (state.isCompletedSuccessfully()) {
                    LOG.info("nop.task.step.skip-completed-step:taskName={},taskInstanceId={}," +
                                    "stepId={},runId={}",
                            context.getTaskName(), context.getTaskInstanceId(),
                            getStepId(), runId);
                    if (state.exception() != null)
                        throw NopException.adapt(state.exception());
                    return state.result();
                }

                if (!isAllowStartIfComplete())
                    throw newError(TaskErrors.ERR_TASK_STEP_NOT_RESTARTABLE, context).param(TaskErrors.ARG_RUN_ID, runId);

                LOG.info("nop.task.step.restart-step:taskName={},taskInstanceId={},stepId={},runId={}",
                        context.getTaskName(), context.getTaskInstanceId(),
                        getStepId(), runId);
            }

            TaskStepResult result = doExecute(state, context);
            if (result.isAsync()) {
                result = asyncComplete(result, state, context);
            } else if (result == RESULT_SUSPEND) {
                onSuspend(result, state, context);
            } else {
                result = onComplete(result.getReturnValue(), result.getNextStepId(), state, context);
            }
            return result;
        } catch (NopException e) {
            if (!isInternal())
                e.addXplStack(this);
            onFailure(e, state, context);
            throw e;
        }
    }

    protected abstract TaskStepResult doExecute(ITaskStepState state, ITaskContext context);

    protected ITaskStepState newStepState(int runId,
                                          IEvalScope parentScope, ITaskStepState parentState,
                                          ITaskContext context) {
        LOG.info("nop.io.nop.task.step.create:taskName={},taskInstanceId={},stepId={},runId={},",
                context.getTaskName(), context.getTaskInstanceId(),
                getStepId(), runId);
        ITaskStepState state = context.getStateStore().newStepState(getStepType(), getStepId(), runId, context);
        IEvalScope scope = state.getEvalScope();

        if (parentState != null) {
            state.setParentRunId(parentState.getParentRunId());
            state.setParentStepId(parentState.getParentStepId());
        }
        state.setInternal(internal);
        state.setTagSet(tagSet);
        state.setExtType(extType);

        initInputs(parentScope, scope);

        if (validator != null) {
            validator.invoke(scope);
        }

        initStepState(state, context);
        return state;
    }

    protected void initStepState(ITaskStepState state, ITaskContext context) {

    }

    private void initInputs(IEvalScope parentScope, IEvalScope scope) {
        for (ITaskInputModel input : getInputs()) {
            String name = input.getName();
            IEvalAction source = input.getSource();
            Object value = source == null ? parentScope.getValue(name) : source.invoke(parentScope);
            scope.setLocalValue(input.getLocation(), name, value);
        }
    }

    protected void exportOutputs(IEvalScope scope, ITaskContext context) {
        List<? extends ITaskOutputModel> outputs = getOutputs();
        if (outputs.isEmpty())
            return;

        IEvalScope parentScope = scope.getParentScope();
        for (ITaskOutputModel outputModel : outputs) {
            Object value = outputModel.getSource().invoke(scope);
            if (outputModel.isForAttr()) {
                context.setAttribute(outputModel.getName(), value);
            } else {
                parentScope.setLocalValue(outputModel.getLocation(), outputModel.getName(), value);
            }
        }
    }

    void onSuspend(TaskStepResult stepResult, ITaskStepState state, ITaskContext context) {
        LOG.info("nop.io.nop.task.step.suspend-step:taskName={},taskInstanceId={},stepId={},runId={}",
                context.getTaskName(), context.getTaskInstanceId(),
                getStepId(), state.getRunId());
        // suspend表示本步骤尚未结束, 等待条件具备后继续执行
        saveState(state, context);
    }

    TaskStepResult onComplete(Object result, String nextStepId, ITaskStepState state, ITaskContext context) {
        if (nextStepId == null) {
            nextStepId = this.getNextStepId();
        }

        LOG.info("nop.task.step.async-return-success:taskName={},taskInstanceId={},stepId={},runId={},nextStepId={}," +
                        "returnValue={}",
                context.getTaskName(), context.getTaskInstanceId(),
                getStepId(), state.getRunId(), nextStepId, result);
        state.succeed(result, nextStepId, context);
        exportOutputs(state.getEvalScope(), context);
        saveState(state, context);

        return TaskStepResult.of(nextStepId, result);
    }

    void onFailure(Throwable e, ITaskStepState state, ITaskContext context) {
        LOG.error("nop.io.nop.task.step.async-return-failure:taskName={},stepId={},runId={}",
                context.getTaskName(), getStepId(), state.getRunId(), e);
        state.fail(e, context);
        saveState(state, context);
    }

    TaskStepResult asyncComplete(TaskStepResult stepResult, ITaskStepState state, ITaskContext context) {
        LOG.info("nop.task.step.async-complete:taskName={},taskInstanceId={},stepId={},runId={}",
                context.getTaskName(), context.getTaskInstanceId(),
                getStepId(), state.getRunId());

        CompletionStage<TaskStepResult> future = ContextProvider.thenOnContext(stepResult.getReturnPromise()).thenApply(ret -> {
            String nextStepId = getNextStepId();
            if (ret instanceof TaskStepResult) {
                TaskStepResult result = (TaskStepResult) ret;
                if (result.getNextStepId() != null)
                    nextStepId = result.getNextStepId();
                ret = result.getReturnValue();
            }
            return onComplete(ret, nextStepId, state, context);
        }).exceptionally(err -> {
            onFailure(err, state, context);
            throw NopException.adapt(err);
        });
        return TaskStepResult.of(null, future);
    }

    protected TaskStepResult toStepResult(Object ret) {
        if (ret instanceof TaskStepResult)
            return ((TaskStepResult) ret);
        return TaskStepResult.of(getNextStepId(), ret);
    }

    protected int getStepIndex(List<ITaskStep> steps, String stepId, int startIndex) {
        for (int i = startIndex; i < steps.size(); i++) {
            ITaskStep step = steps.get(i);
            // 所有step都必然存在id
            if (stepId.equals(step.getStepId()))
                return i;
        }
        return -1;
    }

    protected boolean isSpecialStep(String stepId) {
        return stepId.charAt(0) == SPECIAL_STEP_PREFIX;
    }

    protected NopException newError(ErrorCode errorCode, ITaskContext context, Throwable e) {
        if (e == null)
            return newError(errorCode, context);
        throw new NopException(errorCode, e).source(this).param(TaskErrors.ARG_TASK_NAME, context.getTaskName())
                .param(TaskErrors.ARG_STEP_ID, getStepId()).param(TaskErrors.ARG_STEP_TYPE, getStepType());
    }

    protected NopException newError(ErrorCode errorCode, ITaskContext context) {
        throw new NopException(errorCode).source(this).param(TaskErrors.ARG_TASK_NAME, context.getTaskName())
                .param(TaskErrors.ARG_STEP_ID, getStepId()).param(TaskErrors.ARG_STEP_TYPE, getStepType());
    }

    protected String getInternalStepId(String stepId, String internalName) {
        return stepId + '@' + internalName;
    }

    protected long getLong(Map<String, Object> vars, String name, long defaultValue) {
        Long value = ConvertHelper.toLong(vars.get(name), err -> new NopException(err).param(ARG_VAR_NAME, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    protected int getInt(Map<String, Object> vars, String name, int defaultValue) {
        Integer value = ConvertHelper.toInt(vars.get(name), err -> new NopException(err).param(ARG_VAR_NAME, name));
        if (value == null)
            value = defaultValue;
        return value;
    }
}