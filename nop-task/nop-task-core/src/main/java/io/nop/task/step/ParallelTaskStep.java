/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.AsyncJoinType;
import io.nop.commons.util.AsyncHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 多个子步骤同时执行。执行结果汇总为MultiStepResultBean。如果定义了aggregator，则通过RESULT变量访问到返回结果集，它的执行结果作为最终结果
 */
public class ParallelTaskStep extends AbstractTaskStep {
    private List<ITaskStepExecution> steps;

    private AsyncJoinType stepJoinType;

    private IEvalFunction aggregator;

    private boolean autoCancelUnfinished;

    public boolean isAutoCancelUnfinished() {
        return autoCancelUnfinished;
    }

    public void setAutoCancelUnfinished(boolean autoCancelUnfinished) {
        this.autoCancelUnfinished = autoCancelUnfinished;
    }

    public void setAggregator(IEvalFunction aggregator) {
        this.aggregator = aggregator;
    }

    public void setSteps(List<ITaskStepExecution> steps) {
        this.steps = steps;
    }

    public void setStepJoinType(AsyncJoinType stepJoinType) {
        this.stepJoinType = stepJoinType;
    }


    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>();

        Supplier<CompletionStage<Void>> action = () -> {
            for (int i = 0, n = steps.size(); i < n; i++) {
                ITaskStepExecution step = steps.get(i);
                try {
                    TaskStepReturn stepResult = step.executeWithParentRt(stepRt);
                    promises.add(stepResult.getReturnPromise());
                } catch (Exception e) {
                    promises.add(FutureHelper.reject(e));
                }
            }

            return AsyncHelper.waitAsync(promises, stepJoinType);
        };

        CompletionStage<Void> promise = TaskStepHelper.withCancellable(action, stepRt, autoCancelUnfinished);

        CompletionStage<?> aggPromise = promise.thenApply(v -> {
            MultiStepResultBean states = new MultiStepResultBean();
            int index = 0;
            for (CompletionStage<TaskStepReturn> future : promises) {
                String stepName = steps.get(index++).getStepName();
                if (FutureHelper.isFutureDone(future)) {
                    StepResultBean result = StepResultBean.buildFrom(stepName, stepRt.getLocale(), future);
                    states.add(result.getStepName(), result);
                }
            }

            if (aggregator != null) {
                return aggregator.call1(null, states, stepRt.getEvalScope());
            }
            return states;
        });

        return TaskStepReturn.ASYNC(null, aggPromise);
    }
}
