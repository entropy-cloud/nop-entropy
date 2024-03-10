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
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.RetryTaskStep;
import io.nop.task.step.TryTaskStep;
import io.nop.task.step.WrapTaskStep;

public class TaskStepBuilder {
    public ITaskStep buildStep(TaskStepModel stepModel) {
        return null;
    }

    private ITaskStep buildSequential(TaskStepsModel stepModel) {
        return null;
    }

    private ITaskStep decorateStep(TaskStepModel stepModel, ITaskStep body) {
        ITaskStep step = body;

        if (stepModel.getCatch() != null || stepModel.getFinally() != null) {
            ITaskStep catchStep = null;//buildSequential(stepModel.getCatch());
            ITaskStep finallyStep = null;//buildSequential(stepModel.getFinally());

            TryTaskStep tryStep = new TryTaskStep();
            tryStep.setLocation(stepModel.getLocation());
            tryStep.setStepType(TaskConstants.STEP_TYPE_TRY);
            tryStep.setStepId(stepModel.getId() + TaskConstants.POSTFIX_TRY);
            tryStep.setStepType(TaskConstants.STEP_TYPE_TRY);
            tryStep.setBody(body);
            tryStep.setCatchAction(catchStep);
            tryStep.setFinallyAction(finallyStep);
            tryStep.setInternal(true);
            step = tryStep;
        }

        if (stepModel.getRetry() != null) {
            IRetryPolicy<IEvalContext> retryPolicy = buildRetryPolicy(stepModel.getRetry());
            RetryTaskStep retryStep = new RetryTaskStep();
            retryStep.setLocation(stepModel.getLocation());
            retryStep.setStepType(TaskConstants.STEP_TYPE_RETRY);
            retryStep.setBody(step);
            retryStep.setRetryPolicy(retryPolicy);
            retryStep.setInternal(true);
            retryStep.setStepId(stepModel.getId() + TaskConstants.POSTFIX_RETRY);
            step = retryStep;
        }

        return wrap(stepModel, step);
    }

    private ITaskStep wrap(TaskStepModel stepModel, ITaskStep step) {
        AbstractTaskStep wrap;
        if (stepModel.getId().equals(step.getStepId()) && step instanceof AbstractTaskStep) {
            wrap = (AbstractTaskStep) step;
        } else {
            wrap = new WrapTaskStep(step);
        }
        wrap.setWhen(stepModel.getWhen());
        wrap.setStepId(stepModel.getId());
        wrap.setLocation(stepModel.getLocation());
        wrap.setStepType(stepModel.getType());
        wrap.setTagSet(stepModel.getTagSet());
        wrap.setAllowStartIfComplete(stepModel.isAllowStartIfComplete());
        wrap.setExtType(stepModel.getExtType());
        wrap.setNextStepId(stepModel.getNext());
        wrap.setInternal(stepModel.isInternal());
        wrap.setSaveState(stepModel.isSaveState());
        return wrap;
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
