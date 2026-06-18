package io.nop.task.step;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepFlagOperation;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.metrics.ITaskFlowMetrics;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.task.TaskErrors.ARG_INPUT_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_ALREADY_FAILED;

public class TaskStepExecution implements ITaskStepExecution {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepExecution.class);

    public static class InputConfig {
        private final SourceLocation location;
        private final String name;
        private final IEvalAction expr;
        private final boolean fromTaskScope;
        private final boolean mandatory;
        private final boolean dump;

        public InputConfig(SourceLocation loc, String name, IEvalAction expr,
                           boolean fromTaskScope, boolean mandatory, boolean dump) {
            this.location = loc;
            this.name = name;
            this.expr = expr;
            this.fromTaskScope = fromTaskScope;
            this.mandatory = mandatory;
            this.dump = dump;
        }

        public boolean isDump() {
            return dump;
        }

        public boolean isMandatory() {
            return mandatory;
        }

        public SourceLocation getLocation() {
            return location;
        }

        public String getName() {
            return name;
        }

        public IEvalAction getExpr() {
            return expr;
        }

        public boolean isFromTaskScope() {
            return fromTaskScope;
        }
    }

    public static class OutputConfig {
        private final SourceLocation location;
        private final String exportName;
        private final String name;
        private final boolean toTaskScope;
        private final boolean dump;

        public OutputConfig(SourceLocation location,
                            String exportName, String name, boolean toTaskScope, boolean dump) {
            this.exportName = Guard.notEmpty(exportName, "exportName");
            this.name = Guard.notEmpty(name, "name");
            this.toTaskScope = toTaskScope;
            this.location = location;
            this.dump = dump;
        }

        public boolean isDump() {
            return dump;
        }

        public SourceLocation getLocation() {
            return location;
        }

        public String getExportName() {
            return exportName;
        }

        public String getName() {
            return name;
        }

        public boolean isToTaskScope() {
            return toTaskScope;
        }
    }

    private final SourceLocation location;

    private final String stepName;
    private final List<InputConfig> inputConfigs;

    private final List<OutputConfig> outputConfigs;

    private final Set<String> outputVars;

    private final ITaskStepFlagOperation flagOperation;
    private final IEvalPredicate when;
    private final ITaskStep step;
    private final String nextStepName;
    private final String nextStepNameOnError;

    private final boolean recordMetrics;

    private final String errorName;
    private final boolean useParentScope;

    public TaskStepExecution(SourceLocation location, String stepName,
                             List<InputConfig> inputConfigs,
                             List<OutputConfig> outputConfigs, Set<String> outputVars,
                             ITaskStepFlagOperation flagOperation, IEvalPredicate when,
                             ITaskStep step, String nextStepName, String nextStepNameOnError,
                             boolean recordMetrics, String errorName,
                             boolean useParentScope
    ) {
        this.location = location;
        this.stepName = stepName;
        this.inputConfigs = inputConfigs;
        this.step = step;
        this.flagOperation = flagOperation;
        this.when = when;
        this.outputConfigs = outputConfigs;
        this.outputVars = outputVars;
        this.nextStepName = nextStepName;
        this.nextStepNameOnError = nextStepNameOnError;
        this.recordMetrics = recordMetrics;
        this.errorName = errorName == null ? TaskConstants.VAR_ERROR : errorName;
        this.useParentScope = useParentScope;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Nonnull
    @Override
    public TaskStepReturn executeWithParentRt(ITaskStepRuntime parentRt) {
        IEvalScope parentScope = parentRt.getEvalScope();

        long beginTime = CoreMetrics.currentTimeMillis();

        ITaskRuntime taskRt = parentRt.getTaskRuntime();
        ITaskStepRuntime stepRt = parentRt.newStepRuntime(stepName, step.getStepType(),
                step.getPersistVars(), useParentScope, step.isConcurrent());

        stepRt.setOutputNames(outputVars);

        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepPath={},runId={},loc={}",
                taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                stepRt.getStepPath(), stepRt.getRunId(), step.getLocation());

        // plan 257: continuation-skip reader —— resume/re-execution 时检查 state.isDone()。
        // 终态 COMPLETED → 返回缓存 result（step body 不被调用）；终态 FAILED → 重抛 exception。
        // 这是 plans 252-256 状态机 write-side（succeed/COMPLETED + FAILED driver）的 read-side 消费方，
        // 使 ITaskStepState.isDone()/result() 首次被 production 消费。
        ITaskStepState stepState = stepRt.getState();
        if (stepState != null && stepState.isDone()) {
            if (stepState.isSuccess()) {
                TaskStepReturn cached = stepState.result();
                LOG.info("nop.task.step.continuation-skip:taskName={},taskInstanceId={},stepPath={},runId={},loc={}",
                        taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                        stepRt.getStepPath(), stepRt.getRunId(), step.getLocation());
                if (cached != null) {
                    parentScope.setLocalValue(TaskConstants.VAR_RESULT, cached.getResult());
                    if (cached.getNextStepName() == null && nextStepName != null)
                        cached = TaskStepReturn.RETURN(nextStepName, cached.get());
                    return cached;
                }
                return TaskStepReturn.CONTINUE;
            } else {
                // 终态失败（FAILED/EXPIRED/KILLED）→ 重抛 exception（非静默跳过，设计裁定 4）
                Throwable exp = stepState.exception();
                if (exp == null) {
                    exp = new NopException(ERR_TASK_STEP_ALREADY_FAILED)
                            .param(TaskErrors.ARG_TASK_NAME, taskRt.getTaskName())
                            .param(TaskErrors.ARG_STEP_PATH, stepRt.getStepPath());
                }
                if (exp instanceof NopException)
                    ((NopException) exp).addXplStack(stepRt.getStepPath() + '@' + this.getLocation());
                throw NopException.adapt(exp);
            }
        }

        TaskStepHelper.checkNotCancelled(parentRt);

        if (!stepRt.isRecoverMode()) {
            if (!allowExecute(parentRt)) {
                LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={},"
                                + "stepPath={},runId={},loc={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                        stepRt.getStepPath(), stepRt.getRunId(), step.getLocation());

                return TaskStepReturn.CONTINUE;
            }

            initInputs(stepRt, parentScope, taskRt);

            stepRt.saveState();
        }

        if (flagOperation != null) {
            stepRt.setTagSet(flagOperation.buildChildFlags(parentRt.getTagSet()));
        } else {
            stepRt.setTagSet(parentRt.getTagSet());
        }

        ITaskFlowMetrics metrics = parentRt.getTaskRuntime().getMetrics();
        Object meter = recordMetrics ? metrics.beginStep(stepRt.getStepPath(), step.getStepType()) : null;

        try {
            TaskStepReturn stepResult = step.execute(stepRt);
            if (stepResult.isSuspend()) {
                metrics.endStep(meter, false);
                return stepResult;
            }

            return stepResult.thenCompose((ret, err) -> {
                if (meter != null)
                    metrics.endStep(meter, err != null);

                if (err != null) {
                    LOG.info("nop.task.step.run-fail:usedTime={},taskName={},taskInstanceId={},stepPath={},runId={},nextStepNameOnError={},loc={}",
                            CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                            stepRt.getStepPath(), stepRt.getRunId(), nextStepNameOnError, step.getLocation(), err);

                    if (TaskStepHelper.isCancelledException(err))
                        throw NopException.adapt(err);

                    // plan 254: 终态失败 FAILED driver wiring（对称 plan 252/253 succeed-driver）。
                    // cancel-check 之后（cancelled != failed）、nextStepNameOnError/rethrow 之前，
                    // 使 step 终态失败后 stepStatus==FAILED + isDone + !isSuccess + exception() 非 null 可观测。
                    // fail() 仅保存 exception（plan 247 裁定，不变），setStepStatus(FAILED) 标记终态。
                    // retry-wrapped step 已在 TaskStepHelper.retry:178 由 fail() 保存 exception，harmless re-save。
                    stepRt.getState().fail(err, taskRt);
                    stepRt.getState().setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

                    if (nextStepNameOnError != null)
                        return buildErrorResult(stepRt, parentScope, err);
                    if (err instanceof NopException)
                        ((NopException) err).addXplStack(stepRt.getStepPath() + '@' + this.getLocation());

                    throw NopException.adapt(err);
                } else {
                    if (ret.isSuspend())
                        return ret;

                    Guard.checkState(!ret.isAsync());

                    parentScope.setLocalValue(TaskConstants.VAR_RESULT, ret.getResult());

                    if (!outputConfigs.isEmpty()) {
                        initOutputs(ret, stepRt, parentScope);
                    }

                    // 如果ret中明确指定了nextStepName，则以指定的值为准
                    if (ret.getNextStepName() == null && nextStepName != null)
                        ret = TaskStepReturn.RETURN(nextStepName, ret.get());

                    LOG.debug("nop.task.step.run-ok:usedTime={},taskName={},taskInstanceId={},stepPath={},runId={}," +
                                    "nextStepName={},outputs={},loc={}",
                            CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                            stepRt.getStepPath(), stepRt.getRunId(), ret.getNextStepName(), ret.getOutputs(),
                            step.getLocation());
                    stepRt.getState().succeed(ret.getResult(), ret.getNextStepName(), taskRt);
                    return ret;
                }
            });
        } catch (Exception e) {
            LOG.info("nop.task.step.run-fail:usedTime={},taskName={},taskInstanceId={},stepPath={},runId={},nextStepNameOnError={},loc={}",
                    CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                    stepRt.getStepPath(), stepRt.getRunId(), nextStepNameOnError, step.getLocation(), e);

            if (meter != null)
                metrics.endStep(meter, false);

            if (TaskStepHelper.isCancelledException(e))
                throw NopException.adapt(e);

            if (e instanceof NopException)
                ((NopException) e).addXplStack(stepRt.getStepPath() + '@' + this.getLocation());

            // plan 254: 终态失败 FAILED driver wiring（对称 plan 252/253 succeed-driver）。
            // cancel-check 之后（cancelled != failed）、nextStepNameOnError/rethrow 之前，
            // 使 step 终态失败后 stepStatus==FAILED + isDone + !isSuccess + exception() 非 null 可观测。
            // fail() 仅保存 exception（plan 247 裁定，不变），setStepStatus(FAILED) 标记终态。
            // retry-wrapped step 已在 TaskStepHelper.retry:178 由 fail() 保存 exception，harmless re-save。
            stepRt.getState().fail(e, taskRt);
            stepRt.getState().setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

            if (nextStepNameOnError != null) {
                return buildErrorResult(stepRt, parentScope, e);
            }
            throw NopException.adapt(e);
        }
    }

    boolean allowExecute(ITaskStepRuntime parentRt) {
        if (flagOperation != null && !flagOperation.checkMatchFlag(parentRt.getTagSet())) {
            return false;
        }
        if (when != null && !when.passConditions(parentRt))
            return false;
        return true;
    }

    TaskStepReturn buildErrorResult(ITaskStepRuntime stepRt, IEvalScope parentScope, Throwable e) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e, false, false);
        Map<String, Object> data = Collections.singletonMap(TaskConstants.VAR_ERROR, errorBean);
        parentScope.setLocalValue(errorName, errorBean);
        return TaskStepReturn.of(nextStepNameOnError, data);
    }

    private void initInputs(ITaskStepRuntime stepRt, IEvalScope parentScope, ITaskRuntime taskRt) {
        inputConfigs.forEach(inputConfig -> {
            String name = inputConfig.getName();
            IEvalScope scope = inputConfig.isFromTaskScope() ? taskRt.getEvalScope() : parentScope;
            IEvalAction expr = inputConfig.getExpr();
            Object value = expr == null ? parentScope.getValue(name) : expr.invoke(scope);
            if (inputConfig.isMandatory() && !StringHelper.isEmptyObject(value))
                throw new NopException(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY)
                        .param(ARG_STEP_PATH, stepRt.getStepPath())
                        .param(ARG_INPUT_NAME, name);
            stepRt.setValue(name, value);

            if (inputConfig.isDump()) {
                Object dumpValue = TaskStepHelper.getDumpValue(value);
                LOG.info("nop.task.step-input:step={},name={},value={}", getStepName(), name, dumpValue);
            }
        });
    }

    private void initOutputs(TaskStepReturn result, ITaskStepRuntime stepRt, IEvalScope parentScope) {
        outputConfigs.forEach(config -> {
            String exportName = config.getExportName();
            Object value = result.getOutput(config.getName());
            if (config.isToTaskScope()) {
                stepRt.getTaskRuntime().getEvalScope().setLocalValue(exportName, value);
            } else {
                parentScope.setLocalValue(exportName, value);
            }

            if (config.isDump()) {
                Object dumpValue = TaskStepHelper.getDumpValue(value);
                LOG.info("nop.task.step-output:name={},value={}", exportName, dumpValue);
            }
        });
    }
}
