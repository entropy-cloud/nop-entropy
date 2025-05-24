package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.AsyncJoinType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskStepReturn;
import io.nop.xlang.xdsl.action.IActionInputModel;

import java.util.List;
import java.util.concurrent.CompletionStage;

public abstract class AbstractForkTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private String stepName;
    private ITaskStep step;

    private String aggregateVarName;
    private IEvalAction aggregator;

    private boolean autoCancelUnfinished;

    private AsyncJoinType stepJoinType;

    public boolean isAutoCancelUnfinished() {
        return autoCancelUnfinished;
    }

    public void setAutoCancelUnfinished(boolean autoCancelUnfinished) {
        this.autoCancelUnfinished = autoCancelUnfinished;
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

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public ITaskStep getStep() {
        return step;
    }

    public void setStep(ITaskStep step) {
        this.step = step;
    }

    public AsyncJoinType getStepJoinType() {
        return stepJoinType;
    }

    public void setStepJoinType(AsyncJoinType stepJoinType) {
        this.stepJoinType = stepJoinType;
    }

    protected TaskStepReturn executeFork(ITaskStepRuntime parentRt, Object varValue, int index) {
        IEvalScope parentScope = parentRt.getEvalScope();
        ITaskStepRuntime stepRt = parentRt.newStepRuntime(stepName, step.getStepType(),
                null, true, step.isConcurrent());

        if (varName != null)
            stepRt.setValue(varName, varValue);
        if (indexName != null)
            stepRt.setValue(indexName, index);

        for (IActionInputModel inputModel : getInputs()) {
            stepRt.setValue(inputModel.getName(), parentScope.getLocalValue(inputModel.getName()));
        }

        TaskStepReturn stepResult = step.execute(stepRt);
        if (stepResult.isSuspend())
            return stepResult;

        return stepResult;
    }

    protected TaskStepReturn buildAggResult(CompletionStage<Void> promise,
                                            List<CompletionStage<TaskStepReturn>> promises,
                                            ITaskStepRuntime stepRt) {

        CompletionStage<?> aggPromise = promise.thenApply(v -> {
            MultiStepResultBean states = new MultiStepResultBean();
            int index = 0;
            for (CompletionStage<TaskStepReturn> future : promises) {
                index++;
                if (FutureHelper.isFutureDone(future)) {
                    StepResultBean result = StepResultBean.buildFrom(stepName, stepRt.getLocale(), future);
                    states.add(String.valueOf(index), result);
                }
            }

            if (aggregateVarName != null)
                stepRt.setValue(aggregateVarName, states);

            if (aggregator != null) {
                return aggregator.invoke(stepRt);
            }
            return states;
        });

        return TaskStepReturn.ASYNC(null, aggPromise);
    }
}
