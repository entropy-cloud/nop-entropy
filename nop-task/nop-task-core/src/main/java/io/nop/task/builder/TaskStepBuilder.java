/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.builder;

import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskStep;
import io.nop.task.TaskConstants;
import io.nop.task.model.TaskRetryModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskStepsModel;

public class TaskStepBuilder {
    public ITaskStep buildStep(TaskStepModel stepModel) {
        return null;
    }

    private ITaskStep buildSequential(TaskStepsModel stepModel) {
        return null;
    }

    private ITaskStep decorateStep(TaskStepModel stepModel, ITaskStep body) {
        ITaskStep step = body;


        return wrap(stepModel, step);
    }

    private ITaskStep wrap(TaskStepModel stepModel, ITaskStep step) {
//        AbstractTaskStep wrap;
//        if (stepModel.getId().equals(step.getStepName()) && step instanceof AbstractTaskStep) {
//            wrap = (AbstractTaskStep) step;
//        } else {
//            wrap = new WrapTaskStep(step);
//        }
//
//        return wrap;
        return null;
    }

    private IRetryPolicy<IEvalContext> buildRetryPolicy(TaskRetryModel retryModel) {
        RetryPolicy<IEvalContext> policy = new RetryPolicy<>();
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
