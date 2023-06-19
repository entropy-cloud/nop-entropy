/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.task.TaskStepResult.RESULT_SUSPEND;

public class SequentialStep extends AbstractStep {
    private List<ITaskStep> steps;

    public List<ITaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStep> steps) {
        this.steps = steps;
    }

    public boolean isShareState() {
        return false;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        Integer index = (Integer) state.getStateBean();
        if (index == null)
            index = 0;

        do {
            TaskStepResult stepResult = state.result();
            if (index >= steps.size() || stepResult.isEnd())
                return stepResult;

            if (stepResult.isExit()) {
                return TaskStepResult.of(getNextStepId(), stepResult.getReturnValue());
            }

            ITaskStep step = steps.get(index);

            stepResult = step.execute(state.getRunId(), state, context);
            if (stepResult.isAsync()) {
                int indexParam = index;
                CompletionStage<Object> promise = stepResult.getReturnPromise().thenApply(ret -> {
                    onStepSuccess(ret, indexParam, state, context);
                    return doExecute(state, context);
                });
                return TaskStepResult.of(null, promise);
            }

            // 在saveState之后判断suspend。刚进入doExecute时不能判断suspend, 因为有可能是从休眠中恢复
            if (stepResult == RESULT_SUSPEND)
                return stepResult;

            onStepSuccess(stepResult.getReturnValue(), index, state, context);
            index++;
        } while (true);
    }

    void onStepSuccess(Object ret, int index, ITaskStepState state, ITaskContext context) {
        state.setStateBean(index + 1);
        state.setResultValue(ret);
        saveState(state, context);
    }
}
