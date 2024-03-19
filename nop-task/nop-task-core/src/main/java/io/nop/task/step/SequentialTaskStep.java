/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.ICancelToken;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.task.TaskStepResult.SUSPEND;

public class SequentialTaskStep extends AbstractTaskStep {
    private List<ITaskStep> steps;

    public List<ITaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStep> steps) {
        this.steps = steps;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepState state, Set<String> outputNames, ICancelToken cancelToken, ITaskRuntime taskRt) {
        Integer index = (Integer) state.getStateBean();
        if (index == null)
            index = 0;

        do {
            TaskStepResult stepResult = state.result();
            if (index >= steps.size() || stepResult.isEnd())
                return stepResult;

            if (stepResult.isExit()) {
                return TaskStepResult.of(null, stepResult.getReturnValue());
            }

            ITaskStep step = steps.get(index);

            stepResult = null;//step.execute(state.getRunId(), state, null, taskRt);
            if (stepResult.isAsync()) {
                int indexParam = index;
                CompletionStage<Object> promise = stepResult.getReturnPromise().thenApply(ret -> {
                    onStepSuccess(ret, indexParam, state, taskRt);
                    return null;// doExecute(state, taskRt);
                });
                return TaskStepResult.of(null, promise);
            }

            // 在saveState之后判断suspend。刚进入doExecute时不能判断suspend, 因为有可能是从休眠中恢复
            if (stepResult == SUSPEND)
                return stepResult;

            onStepSuccess(stepResult.getReturnValue(), index, state, taskRt);
            index++;
        } while (true);
    }

    void onStepSuccess(Object ret, int index, ITaskStepState state, ITaskRuntime context) {
        state.setStateBean(index + 1);
        state.setResultValue(ret);
        //saveState(state, context);
    }
}
