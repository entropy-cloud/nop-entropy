/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.builder;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
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
import io.nop.task.step.ExecutorTaskStepWrapper;
import io.nop.task.step.RateLimitTaskStepWrapper;
import io.nop.task.step.RetryTaskStepWrapper;
import io.nop.task.step.RunOnContextTaskStepWrapper;
import io.nop.task.step.SyncTaskStepWrapper;
import io.nop.task.step.TaskStepExecution;
import io.nop.task.step.ThrottleTaskStepWrapper;
import io.nop.task.step.TimeoutTaskStepWrapper;
import io.nop.task.step.TryTaskStepWrapper;
import io.nop.task.step.ValidatorTaskStepWrapper;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.task.TaskConstants.BEAN_PREFIX_TASK_STEP_DECORATOR;

public class TaskStepEnhancer implements ITaskStepEnhancer {

    @Override
    public TaskStepExecution buildExecution(TaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        return enhancedTaskStep(stepModel, buildDecorated(stepModel, stepBuilder), stepBuilder);
    }

    @Override
    public ITaskStep buildDecorated(TaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        AbstractTaskStep step = stepBuilder.buildRawStep(stepModel);
        return wrap(stepModel, step);
    }

    private TaskStepExecution enhancedTaskStep(TaskStepModel stepModel, ITaskStep step, ITaskStepBuilder stepBuilder) {
        List<TaskStepExecution.InputConfig> inputs = new ArrayList<>(stepModel.getInputs().size());
        for (TaskInputModel inputModel : stepModel.getInputs()) {
            inputs.add(new TaskStepExecution.InputConfig(inputModel.getLocation(), inputModel.getName(),
                    inputModel.getValueExpr(), inputModel.isFromTaskScope(), inputModel.isMandatory()));
        }

        List<TaskStepExecution.OutputConfig> outputs = new ArrayList<>(stepModel.getOutputs().size() + 1);
        Set<String> outputVars = new HashSet<>();
        for (TaskOutputModel outputModel : stepModel.getOutputs()) {
            outputVars.add(outputModel.getName());
            String exportName = outputModel.getExportAs() == null ? outputModel.getName() : outputModel.getExportAs();
            outputs.add(new TaskStepExecution.OutputConfig(outputModel.getLocation(), exportName,
                    outputModel.getName(), outputModel.isToTaskScope()));
        }

        if (!StringHelper.isEmpty(stepModel.getReturnAs()) && !stepModel.hasOutput(stepModel.getReturnAs())) {
            outputs.add(new TaskStepExecution.OutputConfig(stepModel.getLocation(), stepModel.getReturnAs(),
                    stepModel.getReturnAs(), false));
        }

        return new TaskStepExecution(stepModel.getLocation(), stepModel.getName(), inputs, outputs, outputVars,
                stepModel.getFlags(), stepModel.getWhen(), step,
                stepModel.getNextOnError(), stepModel.getNextOnError(), stepModel.isIgnoreResult(),
                stepModel.isRecordMetrics(),
                stepModel.getErrorName(), Boolean.TRUE.equals(stepModel.getUseParentScope()));
    }

//    private IEvalAction buildValidator(ValidatorModel validatorModel) {
//        if (validatorModel == null)
//            return null;
//
//        return ctx -> {
//            ITaskStepRuntime stepRt = (ITaskStepRuntime) ctx;
//            BizValidatorHelper.runValidatorModelForValue(validatorModel, stepRt.getResult(), stepRt.getEvalScope(),
//                    stepRt.getTaskRuntime().getSvcCtx(), DefaultValidationErrorCollector.THROW_ERROR);
//            return null;
//        };
//    }

    private ITaskStep wrap(TaskStepModel stepModel, ITaskStep step) {
        step = decorateStep(stepModel, step);

        step = new TryTaskStepWrapper(step, stepModel.getCatch(), stepModel.getFinally(), stepModel.getCatchInternalException());

        step = addOutput(stepModel, step);

        if (stepModel.getRetry() != null) {
            step = new RetryTaskStepWrapper(step, buildRetryPolicy(stepModel.getRetry()));
        }

        if (!StringHelper.isEmpty(stepModel.getExecutor())) {
            step = new ExecutorTaskStepWrapper(step, stepModel.getExecutor());
        }

        // 在线程池上执行之后重新投递到context上
        if (stepModel.isRunOnContext()) {
            step = new RunOnContextTaskStepWrapper(step);
        }

        // timeout控制整个retry过程的时长
        if (stepModel.getTimeout() > 0) {
            step = new TimeoutTaskStepWrapper(step, stepModel.getTimeout());
        }

        if (stepModel.getThrottle() != null) {
            TaskThrottleModel throttleModel = stepModel.getThrottle();
            step = new ThrottleTaskStepWrapper(step, throttleModel.isGlobal(),
                    throttleModel.getMaxConcurrency(), throttleModel.getMaxWait(),
                    throttleModel.getKeyExpr());
        }

        if (stepModel.getRateLimit() != null && stepModel.getRateLimit().getRequestPerSecond() > 0) {
            TaskRateLimitModel rateLimitModel = stepModel.getRateLimit();
            step = new RateLimitTaskStepWrapper(step, rateLimitModel.getRequestPerSecond(), rateLimitModel.isGlobal(),
                    rateLimitModel.getMaxWait(), rateLimitModel.getKeyExpr());
        }

        if (stepModel.getValidator() != null || stepModel.getOnReload() != null || stepModel.getOnEnter() != null) {
            step = new ValidatorTaskStepWrapper(step, stepModel.getOnEnter(), stepModel.getValidator(), stepModel.getOnReload());
        }

        if (stepModel.isSync())
            step = new SyncTaskStepWrapper(step);

        if (stepModel.isAllowFailure())
            step = new TryTaskStepWrapper(step, new LogEvalFunction(stepModel.getName(), stepModel.getLocation()),
                    null, false);
        return step;
    }

    private ITaskStep addOutput(TaskStepModel stepModel, ITaskStep step) {
        if (stepModel.getOutputs().isEmpty())
            return step;

        Map<String, IEvalAction> outputExprs = new LinkedHashMap<>();
        stepModel.getOutputs().forEach(output -> {
            outputExprs.put(output.getName(), output.getValueExpr());
        });

        return new BuildOutputTaskStepWrapper(step, outputExprs);
    }

    private ITaskStep decorateStep(TaskStepModel stepModel, ITaskStep step) {
        if (stepModel.getDecorators().isEmpty())
            return step;

        List<TaskDecoratorModel> decorators = stepModel.getDecorators();
        for (TaskDecoratorModel decoratorModel : decorators) {
            step = decorate(decoratorModel, stepModel, step);
        }
        return step;
    }

    private ITaskStep decorate(TaskDecoratorModel decoratorModel, TaskStepModel stepModel, ITaskStep step) {
        if (!StringHelper.isEmpty(decoratorModel.getBean())) {
            ITaskStepDecorator decorator = (ITaskStepDecorator) BeanContainer.instance().getBean(decoratorModel.getBean());
            return decorator.decorate(step, decoratorModel, stepModel);
        }

        if (decoratorModel.getSource() != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(TaskConstants.VAR_DECORATOR_MODEL, decoratorModel);
            scope.setLocalValue(TaskConstants.VAR_STEP, step);
            return (ITaskStep) decoratorModel.getSource().invoke(scope);
        }

        String bean = BEAN_PREFIX_TASK_STEP_DECORATOR + decoratorModel.getName();
        ITaskStepDecorator decorator = (ITaskStepDecorator) BeanContainer.instance().getBean(bean);
        return decorator.decorate(step, decoratorModel, stepModel);
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
                return ConvertHelper.toTruthy(retryModel.getExceptionFilter().call1(null, e, scope));
            });
        }
        return policy;
    }
}
