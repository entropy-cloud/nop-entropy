/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
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

import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.task.TaskConstants.SPECIAL_STEP_PREFIX;
import static io.nop.task.TaskStepResult.RESULT_SUSPEND;


public abstract class AbstractStep implements ITaskStep {
    static final Logger LOG = LoggerFactory.getLogger(AbstractStep.class);

    private SourceLocation location;
    private String id;
    private String stepType;
    private boolean saveState;
    private boolean shareState;

    private boolean shareScope;
    private boolean internal;
    private Set<String> tagSet;
    private String extType;

    private boolean allowStartIfComplete;

    private String nextStepId;

    private IEvalPredicate when;

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
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isShareScope() {
        return shareScope;
    }

    public void setShareScope(boolean shareScope) {
        this.shareScope = shareScope;
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

    protected ITaskStepState loadState(String runId, ITaskContext context) {
        if (!isSaveState())
            return null;

        ITaskStateStore store = context.getStateStore();
        if (store == null)
            return null;

        ITaskStepState state = store.loadStepState(getId(), runId, context);
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
    public TaskStepResult execute(String runId, Map<String, Object> params,
                                  ITaskStepState parentState, ITaskContext context) {
        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={},params={}," +
                        "loc={}",
                context.getTaskName(), context.getTaskInstanceId(),
                this.getId(), runId, params, getLocation());

        ITaskStepState state = null;
        try {
            if (isShareState()) {
                state = parentState;
            } else {
                state = loadState(runId, context);
            }
            if (state == null) {
                // 只有第一次执行时才会初始化input
                IEvalScope scope = buildScope(params, parentState, context);

                IEvalPredicate when = getWhen();
                if (when != null) {
                    // 如果不满足条件，则直接跳过执行
                    if (!when.passConditions(scope)) {
                        LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={}," +
                                        "stepId={},runId={}",
                                context.getTaskName(), context.getTaskInstanceId(), this.getId(), runId);
                        return TaskStepResult.RESULT_SUCCESS;
                    }
                }

                state = newStepState(runId, params, scope, parentState, context);

                // 执行具体步骤内容之前先保存参数状态
                saveState(state, context);
            } else if (!isShareState()) {
                if (state.isCompletedSuccessfully()) {
                    LOG.info("nop.task.step.skip-completed-step:taskName={},taskInstanceId={}," +
                                    "stepId={},runId={}",
                            context.getTaskName(), context.getTaskInstanceId(),
                            getId(), runId);
                    if (state.exception() != null)
                        throw NopException.adapt(state.exception());
                    return state.result();
                }

                if (!isAllowStartIfComplete())
                    throw newError(TaskErrors.ERR_TASK_STEP_NOT_RESTARTABLE, context).param(TaskErrors.ARG_RUN_ID, runId);

                LOG.info("nop.task.step.restart-step:taskName={},taskInstanceId={},stepId={},runId={}",
                        context.getTaskName(), context.getTaskInstanceId(),
                        getId(), runId);
            }

            TaskStepResult result = doExecute(state, context);
            if (result.isAsync()) {
                asyncComplete(result, state, context);
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

    protected ITaskStepState newStepState(String runId, Map<String, Object> params,
                                          IEvalScope scope, ITaskStepState parentState,
                                          ITaskContext context) {
        LOG.info("nop.io.nop.task.step.create:taskName={},taskInstanceId={},stepId={},runId={},params={}",
                context.getTaskName(), context.getTaskInstanceId(),
                getId(), runId, params);
        ITaskStepState state = context.getStateStore().newStepState(getStepType(), getId(), runId, context);
        state.evalScope(scope);

        if (parentState != null) {
            state.setParentRunId(parentState.getParentRunId());
            state.setParentStepId(parentState.getParentStepId());
        }
        state.setInternal(internal);
        state.setTagSet(tagSet);
        state.setExtType(extType);

        initStepState(state);
        return state;
    }

    protected void initStepState(ITaskStepState state) {

    }

    private IEvalScope buildScope(Map<String, Object> params,
                                  ITaskStepState parentState,
                                  ITaskContext context) {
        IEvalScope scope = parentState == null ? context.getEvalScope() : parentState.evalScope();

        if (!isShareScope()) {
            scope = scope.newChildScope();
        }

        if (params != null) {
            scope.setLocalValues(getLocation(), params);
        }

        for (ITaskInputModel input : getInputs()) {
            Object value = input.getValue().invoke(scope);
            scope.setLocalValue(input.getLocation(), input.getName(), value);
        }

        return scope;
    }

    protected void exportOutputs(IEvalScope scope, ITaskContext context) {
        List<? extends ITaskOutputModel> outputs = getOutputs();
        if (outputs.isEmpty())
            return;

        IEvalScope parentScope = isShareScope() ? scope : scope.getParentScope();
        for (ITaskOutputModel outputModel : outputs) {
            Object value = outputModel.getValue().invoke(scope);
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
                getId(), state.getRunId());
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
                getId(), state.getRunId(), nextStepId, result);
        state.succeed(result, nextStepId, context);
        exportOutputs(state.evalScope(), context);
        saveState(state, context);

        return TaskStepResult.of(nextStepId, result);
    }

    void onFailure(Throwable e, ITaskStepState state, ITaskContext context) {
        LOG.error("nop.io.nop.task.step.async-return-failure:taskName={},stepId={},runId={}",
                context.getTaskName(), getId(), state.getRunId(), e);
        state.fail(e, context);
        saveState(state, context);
    }

    void asyncComplete(TaskStepResult stepResult, ITaskStepState state, ITaskContext context) {
        LOG.info("nop.task.step.async-complete:taskName={},taskInstanceId={},stepId={},runId={}",
                context.getTaskName(), context.getTaskInstanceId(),
                getId(), state.getRunId());

        stepResult.getReturnPromise().whenComplete((ret, ex) -> {
            if (ex == null) {
                String nextStepId = getNextStepId();
                if (ret instanceof TaskStepResult) {
                    TaskStepResult result = (TaskStepResult) ret;
                    if (result.getNextStepId() != null)
                        nextStepId = result.getNextStepId();
                    ret = ((TaskStepResult) ret).getReturnValue();
                }
                onComplete(ret, nextStepId, state, context);
            } else {
                onFailure(ex, state, context);
            }
        });
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
            if (stepId.equals(step.getId()))
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
                .param(TaskErrors.ARG_STEP_ID, getId()).param(TaskErrors.ARG_STEP_TYPE, getStepType());
    }

    protected NopException newError(ErrorCode errorCode, ITaskContext context) {
        throw new NopException(errorCode).source(this).param(TaskErrors.ARG_TASK_NAME, context.getTaskName())
                .param(TaskErrors.ARG_STEP_ID, getId()).param(TaskErrors.ARG_STEP_TYPE, getStepType());
    }

    protected boolean isDefaultRunId(String runId) {
        return runId == null || runId.isEmpty();
    }

    protected String getPartitionRunId(String runId, int index) {
        if (index == 0)
            return runId;

        if (isDefaultRunId(runId))
            return '-' + String.valueOf(index);
        return runId + "-" + index;
    }

    protected String getLoopRunId(String runId, int index) {
        if (isDefaultRunId(runId))
            return ':' + String.valueOf(index);
        return runId + ":" + index;
    }

    protected String getAttemptRunId(String runId, int attempt) {
        if (attempt == 0)
            return runId;

        if (isDefaultRunId(runId))
            return '+' + String.valueOf(attempt);
        return runId + "+" + attempt;
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