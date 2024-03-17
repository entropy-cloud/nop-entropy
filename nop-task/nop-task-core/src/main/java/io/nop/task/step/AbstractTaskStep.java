/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.validator.DefaultValidationErrorCollector;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepResult;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.task.TaskStepResult.SUSPEND;


public abstract class AbstractTaskStep implements ITaskStep {
    static final Logger LOG = LoggerFactory.getLogger(AbstractTaskStep.class);

    private TaskStepModel stepModel;

    public TaskStepModel getStepModel() {
        return stepModel;
    }

    public void setStepModel(TaskStepModel stepModel) {
        this.stepModel = stepModel;
    }

    public boolean isUseParentScope() {
        return stepModel.isUseParentScope();
    }

    public boolean isSaveState() {
        return stepModel.isSaveState();
    }

    @Override
    public SourceLocation getLocation() {
        return stepModel.getLocation();
    }

    @Override
    public String getStepName() {
        return stepModel.getId();
    }

    @Override
    public String getStepType() {
        return stepModel.getType();
    }

    public Set<String> getTagSet() {
        return stepModel.getTagSet();
    }

    public boolean isAllowStartIfComplete() {
        return stepModel.isAllowStartIfComplete();
    }

    public String getNextStepId() {
        return stepModel.getNext();
    }

    @Override
    public List<? extends ITaskOutputModel> getOutputs() {
        return stepModel.getOutputs();
    }

    @Override
    public List<? extends ITaskInputModel> getInputs() {
        return stepModel.getInputs();
    }

    public IEvalPredicate getWhen() {
        return stepModel.getWhen();
    }

    public boolean isInternal() {
        return stepModel.isInternal();
    }

    public String getExtType() {
        return stepModel.getExtType();
    }

    protected ITaskStepState loadState(int runId, ITaskStepState parentState, ITaskRuntime taskRt) {
        if (!isSaveState()) return null;

        ITaskStateStore store = taskRt.getStateStore();
        if (store == null) return null;

        ITaskStepState state = store.loadStepState(getStepName(), runId, taskRt);
        return state;
    }

    protected void saveState(ITaskStepState state, ITaskRuntime taskRt) {
        if (!isSaveState()) return;

        if (!state.needSave()) return;

        taskRt.saveState(state);
    }

    public TaskStepResult execute(int runId, ITaskStepState parentState, ICancelToken cancelToken, ITaskRuntime taskRt) {
        return null;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepState stepState, Set<String> outputNames,
                                  ICancelToken cancelToken, ITaskRuntime taskRt) {
        int runId = stepState.getRunId();
        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={}," + "loc={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), this.getStepName(), runId, getLocation());

        if (taskRt.isCancelled()) throw newError(ERR_TASK_CANCELLED, taskRt);

        ITaskStepState parentState = stepState.getParentState();

        IEvalScope parentScope = parentState != null ? parentState.getEvalScope() : taskRt.getEvalScope().duplicate();

        ITaskStepState state = null;
        try {
            state = loadState(runId, parentState, taskRt);

            if (state == null) {
                // 如果具有历史状态，则不会执行判断条件，而是执行continuation
                IEvalPredicate when = getWhen();
                if (when != null) {
                    // 如果不满足条件，则直接跳过执行
                    if (!when.passConditions(parentScope)) {
                        LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={}," + "stepId={},runId={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), this.getStepName(), runId);
                        return TaskStepResult.CONTINUE;
                    }
                }

                // 只有第一次执行时才会初始化input
                state = newStepState(runId, parentScope, parentState, taskRt);

                // 执行具体步骤内容之前先保存参数状态
                saveState(state, taskRt);
            } else {
                if (state.isSuccess()) {
                    LOG.info("nop.task.step.skip-completed-step:taskName={},taskInstanceId={}," + "stepId={},runId={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), getStepName(), runId);
                    if (state.exception() != null) throw NopException.adapt(state.exception());
                    return state.result();
                }

                if (!isAllowStartIfComplete())
                    throw newError(TaskErrors.ERR_TASK_STEP_NOT_RESTARTABLE, taskRt).param(TaskErrors.ARG_RUN_ID, runId);

                LOG.info("nop.task.step.restart-step:taskName={},taskInstanceId={},stepId={},runId={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), getStepName(), runId);
            }

            TaskStepResult result = doExecute(state, taskRt);
            if (result.isAsync()) {
                result = asyncComplete(result, state, parentScope, taskRt);
            } else if (result == SUSPEND) {
                onSuspend(result, state, taskRt);
            } else {
                result = onComplete(result.getReturnValue(), result.getNextStepName(), state, parentScope, taskRt);
            }
            return result;
        } catch (NopException e) {
            e.addXplStack(this);
            onFailure(e, state, taskRt);
            throw e;
        }
    }

    protected abstract TaskStepResult doExecute(ITaskStepState state, ITaskRuntime taskRt);

    protected ITaskStepState newStepState(int runId, IEvalScope parentScope, ITaskStepState parentState, ITaskRuntime taskRt) {
        LOG.info("nop.io.nop.task.step.create:taskName={},taskInstanceId={},stepId={},runId={},", taskRt.getTaskName(), taskRt.getTaskInstanceId(), getStepName(), runId);

        ITaskStepState state = taskRt.getStateStore().newStepState(getStepType(), getStepName(), runId, taskRt);
        IEvalScope scope = state.getEvalScope();

        if (parentState != null) {
            state.setParentRunId(parentState.getParentRunId());
            state.setParentStepId(parentState.getParentStepId());
        }

        state.setInternal(isInternal());
        state.setTagSet(getTagSet());
        state.setExtType(getExtType());

        initInputs(parentScope, scope);

        validate(scope);

        initStepState(state, taskRt);
        return state;
    }

    protected void validate(IEvalScope scope) {
        if (stepModel.getValidator() != null) {
            new ModelBasedValidator(stepModel.getValidator()).validate(scope, DefaultValidationErrorCollector.THROW_ERROR);
        }
    }

    protected void initStepState(ITaskStepState state, ITaskRuntime context) {

    }

    private void initInputs(IEvalScope parentScope, IEvalScope scope) {
        for (ITaskInputModel input : getInputs()) {
            String name = input.getName();
            IEvalAction source = input.getSource();
            Object value = source == null ? parentScope.getValue(name) : source.invoke(parentScope);
            scope.setLocalValue(input.getLocation(), name, value);
        }
    }

    protected void exportOutputs(IEvalScope scope, IEvalScope parentScope, ITaskRuntime taskRt) {
        List<? extends ITaskOutputModel> outputs = getOutputs();
        if (outputs.isEmpty()) return;

        for (ITaskOutputModel outputModel : outputs) {
            Object value = outputModel.getSource().invoke(scope);
            IEvalScope targetScope = outputModel.isToTaskScope() ? taskRt.getEvalScope() : parentScope;
            if (targetScope == null) targetScope = taskRt.getEvalScope();

            targetScope.setLocalValue(outputModel.getLocation(), outputModel.getName(), value);
        }
    }

    void onSuspend(TaskStepResult stepResult, ITaskStepState state, ITaskRuntime context) {
        LOG.info("nop.io.nop.task.step.suspend-step:taskName={},taskInstanceId={},stepId={},runId={}", context.getTaskName(), context.getTaskInstanceId(), getStepName(), state.getRunId());
        // suspend表示本步骤尚未结束, 等待条件具备后继续执行
        saveState(state, context);
    }

    TaskStepResult onComplete(Object result, String nextStepId, ITaskStepState state, IEvalScope parentScope, ITaskRuntime taskRt) {
        if (nextStepId == null) {
            nextStepId = this.getNextStepId();
        }

        LOG.info("nop.task.step.async-return-success:taskName={},taskInstanceId={},stepId={},runId={},nextStepId={}," + "returnValue={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), getStepName(), state.getRunId(), nextStepId, result);
        state.succeed(result, nextStepId, taskRt);
        exportOutputs(state.getEvalScope(), parentScope, taskRt);
        saveState(state, taskRt);

        return TaskStepResult.of(nextStepId, result);
    }

    void onFailure(Throwable e, ITaskStepState state, ITaskRuntime context) {
        LOG.error("nop.io.nop.task.step.async-return-failure:taskName={},stepId={},runId={}", context.getTaskName(), getStepName(), state.getRunId(), e);
        state.fail(e, context);
        saveState(state, context);
    }

    TaskStepResult asyncComplete(TaskStepResult stepResult, ITaskStepState state, IEvalScope parentScope, ITaskRuntime taskRt) {
        LOG.info("nop.task.step.async-complete:taskName={},taskInstanceId={},stepId={},runId={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(), getStepName(), state.getRunId());

        CompletionStage<TaskStepResult> future = ContextProvider.thenOnContext(stepResult.getReturnPromise()).thenApply(ret -> {
            String nextStepId = getNextStepId();
            if (ret instanceof TaskStepResult) {
                TaskStepResult result = (TaskStepResult) ret;
                if (result.getNextStepName() != null) nextStepId = result.getNextStepName();
                ret = result.getReturnValue();
            }
            return onComplete(ret, nextStepId, state, parentScope, taskRt);
        }).exceptionally(err -> {
            onFailure(err, state, taskRt);
            throw NopException.adapt(err);
        });
        return TaskStepResult.of(null, future);
    }

    protected TaskStepResult toStepResult(Object ret) {
        return TaskStepHelper.toStepResult(ret, getNextStepId());
    }

    protected NopException newError(ErrorCode errorCode, ITaskRuntime taskRt) {
        return TaskStepHelper.newError(this, errorCode, taskRt);
    }
}