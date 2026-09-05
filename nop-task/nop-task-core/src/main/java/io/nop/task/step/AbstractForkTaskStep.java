package io.nop.task.step;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.AsyncJoinType;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.xlang.xdsl.action.IActionInputModel;

import java.util.List;
import java.util.concurrent.CompletionStage;

public abstract class AbstractForkTaskStep extends AbstractTaskStep {
    private String varName;
    private String indexName;

    private String stepName;
    private ITaskStep step;

    private IEvalFunction aggregator;

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

    public IEvalFunction getAggregator() {
        return aggregator;
    }

    public void setAggregator(IEvalFunction aggregator) {
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
        // 挂起分支经 getReturnPromise()（已完成的 SUSPEND promise）参与聚合，
        // 由 buildAggResult 统一检测并向上传播（plan 349 Phase 1）
        return stepResult;
    }

    protected TaskStepReturn buildAggResult(CompletionStage<Void> promise,
                                            List<CompletionStage<TaskStepReturn>> promises,
                                            ITaskStepRuntime stepRt) {

        CompletionStage<?> aggPromise = promise.thenApply(v -> {
            // 挂起传播（plan 349 Phase 1）：任一已完成分支为 SUSPEND 时向上传播挂起返回值，
            // 聚合结果无意义。默认 join=ALL 下挂起分支的 promise 立即可见；
            // 提前返回型 join（ANY_*）中未完成分支的挂起不在本检测范围（最小语义，见 plan 记录）
            for (CompletionStage<TaskStepReturn> future : promises) {
                Object branch = valueOfDone(future);
                if (branch instanceof TaskStepReturn && ((TaskStepReturn) branch).isSuspend())
                    return branch;
            }

            MultiStepResultBean states = new MultiStepResultBean();
            int index = 0;
            for (CompletionStage<TaskStepReturn> future : promises) {
                index++;
                if (FutureHelper.isFutureDone(future)) {
                    StepResultBean result = StepResultBean.buildFrom(stepName, stepRt.getLocale(), future);
                    states.add(String.valueOf(index), result);
                } else {
                    StepResultBean result = new StepResultBean();
                    result.setError(new ErrorBean(TaskErrors.ERR_TASK_CANCELLED.getErrorCode()));
                    states.add(String.valueOf(index), result);
                }
            }

            if (aggregator != null) {
                return aggregator.call1(null, states, stepRt.getEvalScope());
            }

            return states;
        });

        return TaskStepReturn.ASYNC(null, aggPromise);
    }

    private Object valueOfDone(CompletionStage<TaskStepReturn> future) {
        if (!FutureHelper.isFutureDone(future))
            return null;
        try {
            return future.toCompletableFuture().getNow(null);
        } catch (Exception e) {
            // 异常完成的分支由下方 StepResultBean.buildFrom 记录错误占位
            return null;
        }
    }
}
