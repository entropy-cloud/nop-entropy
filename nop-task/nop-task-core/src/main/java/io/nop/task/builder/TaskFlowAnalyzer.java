package io.nop.task.builder;

import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskStepsModel;

import java.util.function.Consumer;

public class TaskFlowAnalyzer {

    public void analyze(TaskFlowModel flowModel) {
        forEachStep(flowModel, TaskStepModel::normalize);
        forEachStep(flowModel, stepModel -> {
            if (stepModel instanceof IGraphTaskStepModel) {
                IGraphTaskStepModel graphModel = (IGraphTaskStepModel) stepModel;
                if (graphModel.isGraphMode())
                    new GraphStepAnalyzer().analyze(graphModel);
            }
        });
    }

    public static void forEachStep(TaskStepModel stepModel, Consumer<TaskStepModel> action) {
        action.accept(stepModel);
        if (stepModel instanceof TaskStepsModel) {
            TaskStepsModel steps = (TaskStepsModel) stepModel;
            steps.getSteps().forEach(subStep -> {
                forEachStep(subStep, action);
            });
        }
    }
}