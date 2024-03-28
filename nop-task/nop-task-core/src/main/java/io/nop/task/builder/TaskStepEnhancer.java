/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.builder;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.validator.DefaultValidationErrorCollector;
import io.nop.core.model.validator.ValidatorModel;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskInputModel;
import io.nop.task.model.TaskOutputModel;
import io.nop.task.model.TaskRateLimitModel;
import io.nop.task.model.TaskRetryModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskThrottleModel;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.BuildOutputTaskStepWrapper;
import io.nop.task.step.EnhancedTaskStep;
import io.nop.task.step.ExecutorTaskStepWrapper;
import io.nop.task.step.RateLimitTaskStepWrapper;
import io.nop.task.step.RetryTaskStepWrapper;
import io.nop.task.step.RunOnContextTaskStepWrapper;
import io.nop.task.step.ThrottleTaskStepWrapper;
import io.nop.task.step.TimeoutTaskStepWrapper;
import io.nop.task.step.TryTaskStepWrapper;
import io.nop.task.step.ValidatorTaskStepWrapper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.filter.BizValidatorHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskStepEnhancer implements ITaskStepEnhancer {

    @Override
    public EnhancedTaskStep buildEnhanced(TaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        return enhancedTaskStep(stepModel, buildDecorated(stepModel, stepBuilder), stepBuilder);
    }

    @Override
    public ITaskStep buildDecorated(TaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        AbstractTaskStep step = stepBuilder.buildRawStep(stepModel);
        return wrap(stepModel, step);
    }

    private EnhancedTaskStep enhancedTaskStep(TaskStepModel stepModel, ITaskStep step, ITaskStepBuilder stepBuilder) {
        List<EnhancedTaskStep.InputConfig> inputs = new ArrayList<>(stepModel.getInputs().size());
        for (TaskInputModel inputModel : stepModel.getInputs()) {
            inputs.add(new EnhancedTaskStep.InputConfig(inputModel.getLocation(), inputModel.getName(),
                    inputModel.getSource(), inputModel.isFromTaskScope()));
        }

        List<EnhancedTaskStep.OutputConfig> outputs = new ArrayList<>(stepModel.getOutputs().size());
        Set<String> outputVars = new HashSet<>();
        for (TaskOutputModel outputModel : stepModel.getOutputs()) {
            outputVars.add(outputModel.getName());
            String exportName = outputModel.getExportAs() == null ? outputModel.getName() : outputModel.getExportAs();
            outputs.add(new EnhancedTaskStep.OutputConfig(outputModel.getLocation(), exportName,
                    outputModel.getName(), outputModel.isToTaskScope()));
        }

        return new EnhancedTaskStep(stepModel.getLocation(), stepModel.getName(), inputs, outputs, outputVars,
                stepModel.getWhen(), step,
                stepModel.getNextOnError(), stepModel.getNextOnError(), stepModel.isIgnoreResult(),
                stepModel.getErrorName(), stepModel.isUseParentScope());
    }

    private IEvalAction notNull(IEvalAction action) {
        return action == null ? IEvalAction.NULL_ACTION : action;
    }

    private IEvalAction buildValidator(ValidatorModel validatorModel) {
        if (validatorModel == null)
            return null;

        return ctx -> {
            ITaskStepRuntime stepRt = (ITaskStepRuntime) ctx;
            BizValidatorHelper.runValidatorModelForValue(validatorModel, stepRt.getResult(), stepRt.getEvalScope(),
                    stepRt.getTaskRuntime().getSvcCtx(), DefaultValidationErrorCollector.THROW_ERROR);
            return null;
        };
    }

    private ITaskStep wrap(TaskStepModel stepModel, ITaskStep step) {
        step = decorateStep(stepModel, step);

        if (stepModel.getCatch() != null || stepModel.getFinally() != null) {
            step = new TryTaskStepWrapper(step, stepModel.getCatch(), stepModel.getFinally());
        }

        step = addOutput(stepModel, step);

        if (!StringHelper.isEmpty(stepModel.getExecutor())) {
            step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
        }

        // 在线程池上执行之后重新投递到context上
        if (stepModel.isRunOnContext()) {
            step = new RunOnContextTaskStepWrapper(step);
        }

        if (stepModel.getRetry() != null) {
            step = new RetryTaskStepWrapper(step, buildRetryPolicy(stepModel.getRetry()));
        }

        // timeout控制整个retry过程的时长
        if (stepModel.getTimeout() > 0) {
            step = new TimeoutTaskStepWrapper(step, stepModel.getTimeout());
        }

        if (stepModel.getThrottle() != null) {
            TaskThrottleModel throttleModel = stepModel.getThrottle();
            step = new ThrottleTaskStepWrapper(step, throttleModel.getMaxConcurrent(), throttleModel.getMaxWait(),
                    throttleModel.getKeyExpr());
        }

        if (stepModel.getRateLimit() != null && stepModel.getRateLimit().getRequestPerSecond() > 0) {
            TaskRateLimitModel rateLimitModel = stepModel.getRateLimit();
            step = new RateLimitTaskStepWrapper(step, rateLimitModel.getRequestPerSecond(),
                    rateLimitModel.getMaxWait(), rateLimitModel.getKeyExpr());
        }

        if (stepModel.getValidator() != null || stepModel.getOnReload() != null) {
            step = new ValidatorTaskStepWrapper(step, buildValidator(stepModel.getValidator()), stepModel.getOnReload());
        }
        return step;
    }

    private ITaskStep addOutput(TaskStepModel stepModel, ITaskStep step) {
        if (stepModel.getOutputs().isEmpty())
            return step;

        Map<String, IEvalAction> outputExprs = new LinkedHashMap<>();
        stepModel.getOutputs().forEach(output -> {
            outputExprs.put(output.getName(), output.getSource());
        });

        return new BuildOutputTaskStepWrapper(step, outputExprs);
    }

    private ITaskStep decorateStep(TaskStepModel stepModel, ITaskStep step) {
        if (stepModel.getDecorators().isEmpty())
            return step;

        List<TaskDecoratorModel> decorators = stepModel.getDecorators();
        for (TaskDecoratorModel decoratorModel : decorators) {
            step = decorate(decoratorModel, step);
        }
        return step;
    }

    private ITaskStep decorate(TaskDecoratorModel decoratorModel, ITaskStep step) {
        if (!StringHelper.isEmpty(decoratorModel.getBean())) {
            ITaskStepDecorator decorator = (ITaskStepDecorator) BeanContainer.instance().getBean(decoratorModel.getBean());
            return decorator.decorate(step, decoratorModel);
        }

        if (decoratorModel.getSource() != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(TaskConstants.VAR_DECORATOR_MODEL, decoratorModel);
            scope.setLocalValue(TaskConstants.VAR_STEP, step);
            return (ITaskStep) decoratorModel.getSource().invoke(scope);
        }

        return step;
    }

    private IRetryPolicy<ITaskStepRuntime> buildRetryPolicy(TaskRetryModel retryModel) {
        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setRetryDelay(retryModel.getRetryDelay());
        policy.setMaxRetryDelay(retryModel.getMaxRetryDelay());
        policy.setMaxRetryCount(retryModel.getMaxRetryCount());
        policy.setExponentialDelay(retryModel.isExponentialDelay());
        if (retryModel.getExceptionFilter() != null) {
            policy.setExceptionFilter((e, ctx) -> {
                IEvalScope scope = ctx.getEvalScope();
                scope.setLocalValue(TaskConstants.VAR_EXCEPTION, e);
                return retryModel.getExceptionFilter().passConditions(ctx);
            });
        }
        return policy;
    }
}
