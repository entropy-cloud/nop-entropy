/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ForkNTaskStep extends AbstractTaskStep {
    private IEvalAction countExpr;
    private String indexName;
    private IEnhancedTaskStep step;

    private ITaskStepResultAggregator aggregator;

    public IEvalAction getCountExpr() {
        return countExpr;
    }

    public void setCountExpr(IEvalAction countExpr) {
        this.countExpr = countExpr;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public IEnhancedTaskStep getStep() {
        return step;
    }

    public void setStep(IEnhancedTaskStep step) {
        this.step = step;
    }

    public ITaskStepResultAggregator getAggregator() {
        return aggregator;
    }

    public void setAggregator(ITaskStepResultAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        Integer count = (Integer) stepRt.getStateBean();
        if (count == null) {
            count = TaskStepHelper.castInt(countExpr.invoke(stepRt), getLocation(), stepRt);
            stepRt.setStateBean(count);
        }

        List<CompletionStage<TaskStepResult>> promises = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                TaskStepResult result = step.executeWithParentRt(stepRt, null, null, indexName, i);
                promises.add(result.getReturnPromise());
            } catch (Exception e) {
                promises.add(FutureHelper.reject(e));
            }
        }

        CompletionStage<?> promise = FutureHelper.waitAll(promises);

        promise = promise.thenApply(v -> {
            MultiStepResultBean states = new MultiStepResultBean();
            int index = 0;
            for (CompletionStage<TaskStepResult> future : promises) {
                StepResultBean result = StepResultBean.buildFrom(step.getStepName(), stepRt.getLocale(), future);
                states.add(result.getStepName() + '-' + (index++), result);
            }

            if (aggregator != null) {
                return aggregator.aggregate(states, stepRt);
            }
            return states;
        });

        return TaskStepResult.ASYNC(null, promise);
    }
}
