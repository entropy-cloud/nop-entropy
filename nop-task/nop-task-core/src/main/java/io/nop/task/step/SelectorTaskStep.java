package io.nop.task.step;

import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.List;

public class SelectorTaskStep extends AbstractTaskStep {
    private List<ITaskStepExecution> steps;

    public List<ITaskStepExecution> getSteps() {
        return steps;
    }

    public void setSteps(List<ITaskStepExecution> steps) {
        this.steps = steps;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        int index = stepRt.getBodyStepIndex();

        do {
            if (index >= steps.size())
                return TaskStepReturn.RETURN_RESULT(stepRt.getResult());

            ITaskStepExecution step = steps.get(index);

            stepRt.setValue(TaskConstants.VAR_RESULT, null);

            TaskStepReturn stepResult;
            try {
                stepResult = step.executeWithParentRt(stepRt);
            } catch (Exception e) {
                index++;
                stepRt.setBodyStepIndex(index);
                stepRt.saveState();
                continue;
            }

            if (stepResult.isSuspend())
                return stepResult;

            if (stepResult.isDone()) {
                if (stepResult.isEnd()) {
                    stepRt.setBodyStepIndex(steps.size());
                    return TaskStepReturn.RETURN_RESULT_END(stepRt.getResult());
                }

                if (hasResult(stepResult))
                    return stepResult;

                index++;
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
                        return TaskStepReturn.RETURN(stepResult.getOutputs());
                    } else {
                        stepRt.setBodyStepIndex(indexParam + 1);
                        stepRt.saveState();
                        return execute(stepRt);
                    }
                });
            }
        } while (true);
    }

    private boolean hasResult(TaskStepReturn stepReturn) {
        if (stepReturn.getOutputs().isEmpty())
            return false;
        if (stepReturn.getOutputs().size() == 1) {
            if (stepReturn.getOutputs().containsKey(TaskConstants.VAR_RESULT)) {
                return stepReturn.getOutput(TaskConstants.VAR_RESULT) != null;
            }
        }
        return true;
    }
}