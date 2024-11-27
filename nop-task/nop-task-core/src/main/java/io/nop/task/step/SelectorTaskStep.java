package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SelectorTaskStep extends AbstractTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(SelectorTaskStep.class);

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
                stepResult = step.executeWithParentRt(stepRt).syncIfDone();
            } catch (Exception e) {
                if (TaskStepHelper.isCancelledException(e))
                    throw e;

                LOG.debug("nop.task.selector-ignore-exception:stepPath={},runId={},subStep={},loc={}",
                        stepRt.getStepPath(), stepRt.getRunId(), step.getStepName(), step.getLocation(), e);
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
                } else if (stepResult.isExit()) {
                    stepRt.setBodyStepIndex(steps.size());
                    return TaskStepReturn.RETURN(stepResult.getOutputs());
                }

                index++;
                stepRt.setBodyStepIndex(index);

                if (stepResult.isResultTruthy())
                    return stepResult;

                stepRt.saveState();
            } else {
                int indexParam = index;
                return stepResult.thenCompose((result, err) -> {
                    if (err != null) {
                        if (TaskStepHelper.isCancelledException(err))
                            throw NopException.adapt(err);
                        LOG.debug("nop.task.selector-ignore-exception:stepPath={},runId={},subStep={},loc={}",
                                stepRt.getStepPath(), stepRt.getRunId(), step.getStepName(), step.getLocation(), err);

                        stepRt.setBodyStepIndex(indexParam + 1);
                        stepRt.saveState();
                        return execute(stepRt);

                    } else if (result.isEnd()) {
                        stepRt.setBodyStepIndex(steps.size());
                        return result;
                    } else if (result.isExit()) {
                        stepRt.setBodyStepIndex(steps.size());
                        return TaskStepReturn.RETURN(result.getOutputs());
                    } else {
                        stepRt.setBodyStepIndex(indexParam + 1);
                        if (result.isResultTruthy())
                            return result;

                        stepRt.saveState();
                        return execute(stepRt);
                    }
                });
            }
        } while (true);
    }
}