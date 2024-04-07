package io.nop.task.builder;

import io.nop.task.ITask;
import io.nop.task.ITaskStep;
import io.nop.task.impl.TaskImpl;
import io.nop.task.model.TaskFlowModel;

public class TaskFlowBuilder implements ITaskFlowBuilder {

    @Override
    public ITask buildTask(TaskFlowModel taskFlowModel) {
        TaskStepBuilder stepBuilder = new TaskStepBuilder();
        ITaskStep mainStep = stepBuilder.buildMainStep(taskFlowModel);
        TaskImpl task = new TaskImpl(taskFlowModel.getName(), taskFlowModel.getVersion(), mainStep,
                taskFlowModel.isRecordMetrics(), taskFlowModel.getFlags(),
                taskFlowModel.getInputs(), taskFlowModel.getOutputs());
        return task;
    }
}
