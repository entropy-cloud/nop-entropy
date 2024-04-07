package io.nop.task.builder;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepLib;
import io.nop.task.impl.TaskStepLibImpl;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskStepModel;

import java.util.HashMap;
import java.util.Map;

public class TaskStepLibBuilder implements ITaskStepLibBuilder {
    @Override
    public ITaskStepLib buildTaskStepLib(TaskFlowModel taskFlowModel) {
        Map<String, ITaskStep> steps = new HashMap<>();
        TaskStepBuilder stepBuilder = new TaskStepBuilder();
        for (TaskStepModel stepModel : taskFlowModel.getSteps()) {
            steps.put(stepModel.getName(), stepBuilder.buildDecoratedStep(stepModel));
        }

        TaskStepLibImpl lib = new TaskStepLibImpl(taskFlowModel.getName(), taskFlowModel.getVersion(), steps);
        return lib;
    }
}
