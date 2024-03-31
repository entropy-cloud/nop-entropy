package io.nop.task.step;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;

public class TaskStepExecution implements ITaskStepExecution {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepExecution.class);

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
        private final boolean toTaskScope;

        public OutputConfig(SourceLocation location,
                            String exportName, String name, boolean toTaskScope) {
            this.exportName = Guard.notEmpty(exportName, "exportName");
            this.name = Guard.notEmpty(name, "name");
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
    private final ITaskStep step;
    private final String nextStepName;
    private final String nextStepNameOnError;

    /**
     * 是否忽略本步骤的执行结果。日志步骤、延伸步骤等如果在顺序流中执行，不会修改当前环境中的result变量
     */
    private final boolean ignoreResult;

    private final String errorName;
    private final boolean useParentScope;

    public TaskStepExecution(SourceLocation location, String stepName,
                             List<InputConfig> inputConfigs,
                             List<OutputConfig> outputConfigs, Set<String> outputVars,
                             IEvalPredicate when,
                             ITaskStep step, String nextStepName, String nextStepNameOnError,
                             boolean ignoreResult, String errorName,
                             boolean useParentScope
    ) {
        this.location = location;
        this.stepName = stepName;
        this.inputConfigs = inputConfigs;
        this.step = step;
        this.when = when;
        this.outputConfigs = outputConfigs;
        this.outputVars = outputVars;
        this.nextStepName = nextStepName;
        this.nextStepNameOnError = nextStepNameOnError;
        this.ignoreResult = ignoreResult;
        this.errorName = errorName;
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

    public boolean isIgnoreResult() {
        return ignoreResult;
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

                return TaskStepReturn.CONTINUE;
            }

            initInputs(stepRt, parentScope, taskRt);

            stepRt.saveState();
        }

        try {
            TaskStepReturn stepResult = step.execute(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            return stepResult.thenCompose((ret, err) -> {
                if (err != null) {
                    LOG.info("nop.task.step.run-fail:usedTime={},taskName={},taskInstanceId={},stepId={},runId={},nextStepNameOnError={},loc={}",
                            CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                            stepRt.getStepId(), stepRt.getRunId(), nextStepNameOnError, step.getLocation(), err);
                    if (nextStepNameOnError != null)
                        return buildErrorResult(stepRt, parentScope, err);
                    throw NopException.adapt(err);
                } else {
                    if (ret.isSuspend())
                        return ret;

                    Guard.checkState(!ret.isAsync());

                    if (!ignoreResult) {
                        parentScope.setLocalValue(TaskConstants.VAR_RESULT, ret.getOutput(TaskConstants.VAR_RESULT));
                    }

                    if (!outputConfigs.isEmpty()) {
                        initOutputs(ret, stepRt, parentScope);
                    }

                    // 如果ret中明确指定了nextStepName，则以指定的值为准
                    if (ret.getNextStepName() == null && nextStepName != null)
                        ret = TaskStepReturn.RETURN(nextStepName, ret.get());

                    LOG.debug("nop.task.step.run-ok:usedTime={},taskName={},taskInstanceId={},stepId={},runId={}," +
                                    "nextStepName={},outputs={},loc={}",
                            CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                            stepRt.getStepId(), stepRt.getRunId(), ret.getNextStepName(), ret.getOutputs(),
                            step.getLocation());
                    return ret;
                }
            });
        } catch (Exception e) {
            LOG.info("nop.task.step.run-fail:usedTime={},taskName={},taskInstanceId={},stepId={},runId={},nextStepNameOnError={},loc={}",
                    CoreMetrics.currentTimeMillis() - beginTime, taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                    stepRt.getStepId(), stepRt.getRunId(), nextStepNameOnError, step.getLocation(), e);

            if (nextStepNameOnError != null) {
                return buildErrorResult(stepRt, parentScope, e);
            }
            throw NopException.adapt(e);
        }
    }

    TaskStepReturn buildErrorResult(ITaskStepRuntime stepRt, IEvalScope parentScope, Throwable e) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e, false, false);
        Map<String, Object> data = Collections.singletonMap(TaskConstants.VAR_ERROR, errorBean);
        if (errorName != null) {
            parentScope.setLocalValue(errorName, errorBean);
        }
        return TaskStepReturn.of(nextStepNameOnError, data);
    }

    private void initInputs(ITaskStepRuntime stepRt, IEvalScope parentScope, ITaskRuntime taskRt) {
        inputConfigs.forEach(inputConfig -> {
            String name = inputConfig.getName();
            IEvalScope scope = inputConfig.isFromTaskScope() ? taskRt.getEvalScope() : parentScope;
            IEvalAction expr = inputConfig.getExpr();
            Object value = expr == null ? parentScope.getValue(name) : expr.invoke(scope);
            stepRt.setValue(name, value);
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
        });
    }
}
