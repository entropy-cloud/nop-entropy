package io.nop.task.step;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;

public class TaskStepCall {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepCall.class);

    public static class InputConfig {
        private final String name;
        private final IEvalAction expr;
        private final boolean fromTaskScope;

        public InputConfig(String name, IEvalAction expr,
                           boolean fromTaskScope) {
            this.name = name;
            this.expr = expr;
            this.fromTaskScope = fromTaskScope;
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
        private final String exportName;
        private final String name;
        private final boolean toTaskScope;

        public OutputConfig(String exportName, String name, boolean toTaskScope) {
            this.exportName = exportName;
            this.name = name;
            this.toTaskScope = toTaskScope;
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

    private final List<InputConfig> inputConfigs;

    private final List<OutputConfig> outputConfigs;

    private final Set<String> outputVars;

    private final IEvalPredicate when;
    private final IEvalAction validator;
    private final IEvalAction onReload;
    private final ITaskStep step;

    public TaskStepCall(List<InputConfig> inputConfigs,
                        List<OutputConfig> outputConfigs,
                        IEvalPredicate when, IEvalAction validator, IEvalAction onReload,
                        ITaskStep step) {
        this.inputConfigs = inputConfigs;
        this.step = step;
        this.when = when;
        this.validator = validator;
        this.onReload = onReload;
        this.outputConfigs = outputConfigs;
        this.outputVars = outputConfigs.stream().map(OutputConfig::getName).collect(Collectors.toSet());
    }

    public TaskStepResult execute(ITaskStepState parentState, ICancelToken cancelToken,
                                  ITaskRuntime taskRt) {
        IEvalScope parentScope = parentState == null ? taskRt.getEvalScope() : parentState.getEvalScope();

        String stepName = step.getStepName();
        ITaskStepState stepState = taskRt.newStepState(parentState, stepName);
        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={},loc={}",
                taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                stepState.getStepId(), stepState.getRunId(), step.getLocation());

        if (cancelToken != null && cancelToken.isCancelled()) {
            throw TaskStepHelper.newError(step, ERR_TASK_CANCELLED, taskRt);
        }

        if (!stepState.load()) {
            if (when != null && !when.passConditions(parentScope)) {
                LOG.info("nop.task.step.skip-when-condition-not-satisfied:taskName={},taskInstanceId={},"
                                + "stepId={},runId={},loc={}", taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                        stepState.getStepId(), stepState.getRunId(), step.getLocation());

                return TaskStepResult.CONTINUE;
            }

            initInputs(stepState, parentScope, taskRt);

            stepState.save();
        } else {
            if (onReload != null)
                onReload.invoke(stepState.getEvalScope());
        }

        TaskStepResult stepResult = step.execute(stepState, outputVars, cancelToken, taskRt);
        if (!outputConfigs.isEmpty()) {
            stepResult = stepResult.thenApply(ret -> {
                initOutputs(stepState, parentScope, taskRt);
                return ret;
            });
        }

        return stepResult;
    }

    private void initInputs(ITaskStepState stepState, IEvalScope parentScope, ITaskRuntime taskRt) {
        inputConfigs.forEach(inputConfig -> {
            IEvalScope scope = inputConfig.isFromTaskScope() ? taskRt.getEvalScope() : parentScope;
            stepState.setInput(inputConfig.getName(), inputConfig.getExpr().invoke(scope));
        });

        if (validator != null)
            validator.invoke(stepState.getEvalScope());
    }

    private void initOutputs(ITaskStepState stepState, IEvalScope parentScope, ITaskRuntime taskRt) {
        IEvalScope stepScope = stepState.getEvalScope();

        outputConfigs.forEach(config -> {
            Object value = stepScope.getValue(config.getName());
            if (config.isToTaskScope()) {
                taskRt.getEvalScope().setLocalValue(config.getExportName(), value);
            } else {
                parentScope.setLocalValue(config.getExportName(), value);
            }
        });
    }
}
