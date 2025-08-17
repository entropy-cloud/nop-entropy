package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.model.ChooseTaskStepModel;
import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.model.IfTaskStepModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskStepsModel;

import java.util.function.Consumer;

import static io.nop.task.TaskErrors.ARG_NEXT_STEP;
import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ARG_WAIT_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_NEXT_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_WAIT_STEP;

public class TaskFlowAnalyzer {

    public void analyze(TaskFlowModel flowModel) {
        forEachStep(flowModel, TaskStepModel::normalize);
        forEachStep(flowModel, stepModel -> {

            checkStepRef(stepModel);

            if (Boolean.TRUE.equals(flowModel.getDefaultUseParentScope()))
                forceUseParentScope(stepModel);

            if (stepModel instanceof IGraphTaskStepModel) {
                IGraphTaskStepModel graphModel = (IGraphTaskStepModel) stepModel;
                if (graphModel.isGraphMode())
                    new GraphStepAnalyzer().analyze(graphModel);
            }
        });
    }

    void forceUseParentScope(TaskStepModel stepModel) {
        if (stepModel.getUseParentScope() == null)
            stepModel.setUseParentScope(true);
    }

    private void checkStepRef(TaskStepModel stepModel) {
        if (stepModel instanceof TaskStepsModel) {
            TaskStepsModel stepsModel = (TaskStepsModel) stepModel;
            for (TaskStepModel subStep : stepsModel.getSteps()) {
                if (subStep.getNext() != null) {
                    if (!stepsModel.hasStep(subStep.getNext())) {
                        throw new NopException(ERR_TASK_UNKNOWN_NEXT_STEP)
                                .source(stepModel)
                                .param(ARG_STEP_NAME, stepModel.getName())
                                .param(ARG_NEXT_STEP, subStep.getNext());
                    }
                }

                if (subStep.getNextOnError() != null) {
                    if (!stepsModel.hasStep(subStep.getNext())) {
                        throw new NopException(ERR_TASK_UNKNOWN_NEXT_STEP)
                                .source(subStep)
                                .param(ARG_STEP_NAME, stepModel.getName())
                                .param(ARG_NEXT_STEP, subStep.getNextOnError());
                    }
                }

                if (subStep.getWaitSteps() != null) {
                    for (String waitStep : subStep.getWaitSteps()) {
                        if (!stepsModel.hasStep(waitStep)) {
                            throw new NopException(ERR_TASK_UNKNOWN_WAIT_STEP)
                                    .source(subStep)
                                    .param(ARG_STEP_NAME, stepModel.getName())
                                    .param(ARG_WAIT_STEP, waitStep);
                        }
                    }
                }

                if (subStep.getWaitErrorSteps() != null) {
                    for (String waitStep : subStep.getWaitErrorSteps()) {
                        if (!stepsModel.hasStep(waitStep)) {
                            throw new NopException(ERR_TASK_UNKNOWN_WAIT_STEP)
                                    .source(subStep)
                                    .param(ARG_STEP_NAME, stepModel.getName())
                                    .param(ARG_WAIT_STEP, waitStep);
                        }
                    }
                }
            }
        }
    }

    public static void forEachStep(TaskStepModel stepModel, Consumer<TaskStepModel> action) {
        action.accept(stepModel);
        if (stepModel instanceof TaskStepsModel) {
            TaskStepsModel steps = (TaskStepsModel) stepModel;
            steps.getSteps().forEach(subStep -> {
                forEachStep(subStep, action);
            });
        } else if (stepModel instanceof IfTaskStepModel) {
            IfTaskStepModel ifModel = (IfTaskStepModel) stepModel;
            if (ifModel.getThen() != null) {
                action.accept(ifModel.getThen());
            }
            if (ifModel.getElse() != null) {
                action.accept(ifModel.getElse());
            }
        } else if (stepModel instanceof ChooseTaskStepModel) {
            ChooseTaskStepModel chooseModel = (ChooseTaskStepModel) stepModel;
            if (chooseModel.getCases() != null) {
                chooseModel.getCases().forEach(subStep -> {
                    forEachStep(subStep, action);
                });
            }
            if (chooseModel.getOtherwise() != null) {
                forEachStep(chooseModel.getOtherwise(), action);
            }
        }
    }
}