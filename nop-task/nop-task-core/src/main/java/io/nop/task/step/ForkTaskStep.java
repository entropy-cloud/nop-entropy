/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ForkTaskStep extends AbstractTaskStep {
    private IEvalAction producer;
    private String varName;

    private String indexName;
    private IEnhancedTaskStep step;

    private ITaskStepResultAggregator aggregator;

    public IEvalAction getProducer() {
        return producer;
    }

    public void setProducer(IEvalAction producer) {
        this.producer = producer;
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
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

    @DataBean
    public static class ForkStateBean {
        private List<Object> items;

        public List<Object> getItems() {
            return items;
        }

        public void setItems(List<Object> items) {
            this.items = items;
        }
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        ForkStateBean stateBean = stepRt.getStateBean(ForkStateBean.class);
        if (stateBean == null) {
            stateBean = new ForkStateBean();
            List<Object> items = CollectionHelper.toList(producer.invoke(stepRt));
            stateBean.setItems(items);
            stepRt.setStateBean(stateBean);
        }

        List<Object> items = stateBean.getItems();

        List<CompletionStage<TaskStepResult>> promises = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            try {
                TaskStepResult result = step.executeWithParentRt(stepRt,
                        varName, items.get(i), indexName, i);
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
