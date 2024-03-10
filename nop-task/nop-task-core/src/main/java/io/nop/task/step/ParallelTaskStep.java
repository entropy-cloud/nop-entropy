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
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.AsyncStepResult;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ParallelTaskStep extends AbstractTaskStep {
    private List<ITaskStep> steps;

    private String aggregateVarName;
    private IEvalAction aggregator;

    public String getAggregateVarName() {
        return aggregateVarName;
    }

    public void setAggregateVarName(String aggregateVarName) {
        this.aggregateVarName = aggregateVarName;
    }

    public IEvalAction getAggregator() {
        return aggregator;
    }

    public void setAggregator(IEvalAction aggregator) {
        this.aggregator = aggregator;
    }

    public List<ITaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStep> steps) {
        this.steps = steps;
    }

    @Override
    protected void initStepState(ITaskStepState state, ITaskContext context) {

    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        ParallelStateBean states = new ParallelStateBean();

        List<CompletionStage<?>> promises = new ArrayList<>();

        for (int i = 0, n = steps.size(); i < n; i++) {
            ITaskStep step = steps.get(i);
            try {
                TaskStepResult stepResult = step.execute(state.getRunId(), state, context);
                if (stepResult.isAsync()) {
                    promises.add(stepResult.getReturnPromise().thenApply(v -> {
                        TaskStepResult r = TaskStepResult.of(null, v);
                        AsyncStepResult result = new AsyncStepResult();
                        result.setRunId(state.getRunId());
                        result.setNextStepId(r.getNextStepId());
                        result.setReturnValue(r.getReturnValue());
                        states.add(result);
                        return null;
                    }));
                } else {
                    AsyncStepResult result = new AsyncStepResult();
                    result.setRunId(state.getRunId());
                    result.setNextStepId(stepResult.getNextStepId());
                    result.setReturnValue(stepResult.getReturnValue());
                    states.add(result);
                }
            } catch (Exception e) {
                AsyncStepResult result = new AsyncStepResult();
                result.setRunId(state.getRunId());
                result.setNextStepId(step.getStepId());
                result.setError(ErrorMessageManager.instance().buildErrorMessage(null, e));
                states.add(result);
            }
        }

        CompletionStage<?> promise = FutureHelper.waitAll(promises);
        if (aggregator != null) {
            promise = promise.thenApply(v -> {
                IEvalScope scope = state.getEvalScope();
                String varName = aggregateVarName;
                if (varName == null)
                    varName = TaskConstants.VAR_RESULTS;
                scope.setLocalValue(varName, states);
                return toStepResult(aggregator.invoke(scope));
            });
        }
        return toStepResult(promise);
    }
}
