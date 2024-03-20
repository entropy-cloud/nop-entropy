/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 多个子步骤同时执行。执行结果汇总为MultiStepResultBean。如果定义了aggregator，则通过RESULT变量访问到返回结果集，它的执行结果作为最终结果
 */
public class ParallelTaskStep extends AbstractTaskStep {
    private List<IEnhancedTaskStep> steps;
    private ITaskStepResultAggregator aggregator;
    public void setAggregator(ITaskStepResultAggregator aggregator) {
        this.aggregator = aggregator;
    }

    public void setSteps(List<IEnhancedTaskStep> steps) {
        this.steps = steps;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        List<CompletionStage<StepResultBean>> promises = new ArrayList<>();

        for (int i = 0, n = steps.size(); i < n; i++) {
            IEnhancedTaskStep step = steps.get(i);
            try {
                TaskStepResult stepResult = step.executeWithParentRt(stepRt);
                promises.add(stepResult.getReturnPromise().thenApply(v -> {
                    StepResultBean result = new StepResultBean();
                    result.setStepName(step.getStepName());
                    result.setNextStepName(v.getNextStepName());
                    result.setReturnValues(v.getReturnValues());
                    return result;
                }).exceptionally(err -> {
                    StepResultBean result = new StepResultBean();
                    result.setStepName(step.getStepName());
                    result.setError(ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), err));
                    return result;
                }));
            } catch (Exception e) {
                StepResultBean result = new StepResultBean();
                result.setStepName(step.getStepName());
                result.setError(ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e));
                promises.add(FutureHelper.success(result));
            }
        }

        CompletionStage<?> promise = FutureHelper.waitAll(promises);

        promise = promise.thenApply(v -> {
            MultiStepResultBean states = new MultiStepResultBean();
            for (CompletionStage<StepResultBean> future : promises) {
                StepResultBean result = FutureHelper.syncGet(future);
                states.add(result.getStepName(), result);
            }

            if (aggregator != null) {
                return aggregator.aggregate(states, stepRt);
            }
            return states;
        });

        return TaskStepResult.ASYNC(null, promise);
    }
}
