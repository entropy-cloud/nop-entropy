/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.commons.util.CollectionHelper;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;

import static io.nop.task.TaskErrors.ARG_NEXT_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_NEXT_STEP;

/**
 * 顺序执行步骤，通过上下文中的RESULT变量保存返回值
 */
public class SequentialTaskStep extends AbstractTaskStep {
    private List<ITaskStepExecution> steps;
    private Map<String, Integer> stepIndex;

    public List<ITaskStepExecution> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStepExecution> steps) {
        this.steps = steps;

        this.stepIndex = CollectionHelper.newHashMap(steps.size());
        for (int i = 0, n = steps.size(); i < n; i++) {
            stepIndex.put(steps.get(i).getStepName(), i);
        }
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        int index = stepRt.getBodyStepIndex();

        do {
            if (index >= steps.size())
                return TaskStepResult.RETURN_RESULT(stepRt.getResult());

            ITaskStepExecution step = steps.get(index);

            TaskStepResult stepResult = step.executeWithParentRt(stepRt);
            if (stepResult.isSuspend())
                return stepResult;

            if (stepResult.isDone()) {
                if (stepResult.isEnd()) {
                    stepRt.setBodyStepIndex(steps.size());
                    return TaskStepResult.RETURN_RESULT_END(stepRt.getResult());
                } else if (stepResult.isExit()) {
                    stepRt.setBodyStepIndex(steps.size());
                    return TaskStepResult.RETURN_RESULT(stepRt.getResult());
                }

                index = getNextIndex(index, stepResult, stepRt);
                stepRt.setBodyStepIndex(index);
                stepRt.saveState();
            } else {
                int indexParam = index;
                return stepResult.thenApply(result -> {
                    if (stepResult.isEnd()) {
                        stepRt.setBodyStepIndex(steps.size());
                        return stepResult;
                    } else if (stepResult.isExit()) {
                        stepRt.setBodyStepIndex(steps.size());
                        return TaskStepResult.RETURN(stepResult.getOutputs());
                    } else {
                        stepRt.setBodyStepIndex(getNextIndex(indexParam, result, stepRt));
                        stepRt.saveState();
                        return execute(stepRt);
                    }
                });
            }
        } while (true);
    }

    int getNextIndex(int index, TaskStepResult result, ITaskStepRuntime stepRt) {
        if (result.getNextStepName() != null) {
            Integer next = stepIndex.get(result.getNextStepName());
            if (next == null)
                throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_UNKNOWN_NEXT_STEP)
                        .param(ARG_NEXT_STEP, result.getNextStepName());
            return next;
        }
        return index + 1;
    }
}
