package io.nop.task.step;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_MANDATORY_OUTPUT_IS_EMPTY;

public class EnhancedTaskStep implements IEnhancedTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(EnhancedTaskStep.class);

    public static class InputConfig {
        private final SourceLocation location;
        private final String name;
        private final IEvalAction expr;
        private final boolean fromTaskScope;

        public InputConfig(SourceLocation loc, String name, IEvalAction expr,
                           boolean fromTaskScope) {
            this.location = loc;
            this.name = name;
            this.expr = expr;
            this.fromTaskScope = fromTaskScope;
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

        private final boolean mandatory;
        private final boolean toTaskScope;

        public OutputConfig(SourceLocation location,
                            String exportName, String name, boolean mandatory, boolean toTaskScope) {
            this.exportName = exportName;
            this.name = name;
            this.mandatory = mandatory;
            this.toTaskScope = toTaskScope;
            this.location = location;
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

        public boolean isMandatory() {
            return mandatory;
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
    private final IEvalPredicate when;
    private final IEvalAction validator;
    private final IEvalAction onReload;
    private final ITaskStep step;
    private final String nextStepName;
    private final String nextStepNameOnError;

    /**
     * 是否忽略本步骤的执行结果。日志步骤、延伸步骤等如果在顺序流中执行，不会修改当前环境中的result变量
     */
    private final boolean ignoreResult;

    private final String errorName;

    public EnhancedTaskStep(SourceLocation location, String stepName,
                            List<InputConfig> inputConfigs,
                            List<OutputConfig> outputConfigs,
                            IEvalPredicate when, IEvalAction validator, IEvalAction onReload,
                            ITaskStep step, String nextStepName, String nextStepNameOnError,
                            boolean ignoreResult, String errorName
    ) {
        this.location = location;
        this.stepName = stepName;
        this.inputConfigs = inputConfigs;
        this.step = step;
        this.when = when;
        this.validator = validator;
        this.onReload = onReload;
        this.outputConfigs = outputConfigs;
        this.outputVars = outputConfigs.stream().map(OutputConfig::getName).collect(Collectors.toSet());
        this.nextStepName = nextStepName;
        this.nextStepNameOnError = nextStepNameOnError;
        this.ignoreResult = ignoreResult;
        this.errorName = errorName;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    public boolean isIgnoreResult() {
        return ignoreResult;
    }

    @Nonnull
    @Override
    public TaskStepResult executeWithParentRt(ITaskStepRuntime parentRt,
                                              String varName, Object varValue,
                                              String indexName, int index) {
        IEvalScope parentScope = parentRt.getEvalScope();

        ITaskRuntime taskRt = parentRt.getTaskRuntime();
        ITaskStepRuntime stepRt = parentRt.newStepRuntime(stepName, step.getPersistVars());
        stepRt.setOutputNames(outputVars);
        if (varName != null)
            stepRt.setValue(varName, varValue);
        if (indexName != null)
            stepRt.setValue(indexName, index);

        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={},loc={}",
                taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                stepRt.getStepId(), stepRt.getRunId(), step.getLocation());

        if (parentRt.isCancelled()) {
            throw TaskStepHelper.newError(location, stepRt, ERR_TASK_CANCELLED);
        }

        if (!stepRt.isRecoverMode()) {
            if (when != null && !when.passConditions(parentScope)) {
                LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={},"
                                + "stepId={},runId={},loc={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                        stepRt.getStepId(), stepRt.getRunId(), step.getLocation());

                return TaskStepResult.CONTINUE;
            }

            initInputs(stepRt, parentScope, taskRt);

            stepRt.saveState();
        } else {
            if (onReload != null)
                onReload.invoke(stepRt);
        }

        try {
            TaskStepResult stepResult = step.execute(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            return stepResult.thenCompose((ret, err) -> {
                if (err != null) {
                    if (nextStepNameOnError != null)
                        return buildErrorResult(stepRt, parentScope, err);
                    throw NopException.adapt(err);
                } else {
                    if (ret.isSuspend())
                        return ret;

                    Guard.checkState(!ret.isAsync());

                    if (!ignoreResult) {
                        parentScope.setLocalValue(TaskConstants.VAR_RESULT, ret.getValue(TaskConstants.VAR_RESULT));
                    }

                    if (!outputConfigs.isEmpty()) {
                        initOutputs(ret, stepRt, parentScope);
                    }

                    // 如果ret中明确指定了nextStepName，则以指定的值为准
                    if (ret.getNextStepName() == null && nextStepName != null)
                        return TaskStepResult.RETURN(nextStepName, ret.get());
                    return ret;
                }
            });
        } catch (Exception e) {
            if (nextStepNameOnError != null) {
                return buildErrorResult(stepRt, parentScope, e);
            }
            throw NopException.adapt(e);
        }
    }

    TaskStepResult buildErrorResult(ITaskStepRuntime stepRt, IEvalScope parentScope, Throwable e) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e, false, false);
        Map<String, Object> data = Collections.singletonMap(TaskConstants.VAR_ERROR, errorBean);
        if (errorName != null) {
            parentScope.setLocalValue(errorName, errorBean);
        }
        return TaskStepResult.of(nextStepNameOnError, data);
    }

    private void initInputs(ITaskStepRuntime stepRt, IEvalScope parentScope, ITaskRuntime taskRt) {
        inputConfigs.forEach(inputConfig -> {
            IEvalScope scope = inputConfig.isFromTaskScope() ? taskRt.getEvalScope() : parentScope;
            stepRt.getState().setInput(inputConfig.getName(), inputConfig.getExpr().invoke(scope));
        });

        if (validator != null)
            validator.invoke(stepRt);
    }

    private void initOutputs(TaskStepResult result, ITaskStepRuntime stepRt, IEvalScope parentScope) {
        outputConfigs.forEach(config -> {
            Object value = result.getValue(config.getName());
            if (config.isMandatory() && StringHelper.isEmptyObject(value))
                throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_STEP_MANDATORY_OUTPUT_IS_EMPTY);

            if (config.getExportName() != null) {
                if (config.isToTaskScope()) {
                    stepRt.getTaskRuntime().getEvalScope().setLocalValue(config.getExportName(), value);
                } else {
                    parentScope.setLocalValue(config.getExportName(), value);
                }
            }
        });
    }
}
