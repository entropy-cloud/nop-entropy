package io.nop.task.impl;

import io.nop.api.core.util.Guard;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;

import java.util.List;
import java.util.Set;

public class TaskImpl implements ITask {
    private final String taskName;
    private final long taskVersion;
    private final ITaskStep mainStep;

    private final List<? extends ITaskInputModel> inputs;

    private final List<? extends ITaskOutputModel> outputs;

    public TaskImpl(String taskName, long taskVersion, ITaskStep mainStep,
                    List<? extends ITaskInputModel> inputs, List<? extends ITaskOutputModel> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        Guard.checkArgument(taskVersion > 0, "taskVersion");
        this.taskName = Guard.notEmpty(taskName, "taskName");
        this.taskVersion = taskVersion;
        this.mainStep = Guard.notNull(mainStep, "mainStep");
    }

    @Override
    public List<? extends ITaskInputModel> getInputs() {
        return inputs;
    }

    @Override
    public List<? extends ITaskOutputModel> getOutputs() {
        return outputs;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getTaskVersion() {
        return taskVersion;
    }

    @Override
    public TaskStepResult execute(ITaskRuntime taskRt, Set<String> outputNames) {
        ITaskStepRuntime stepRt = taskRt.newMainStepRuntime();
        stepRt.setOutputNames(outputNames);
        return mainStep.execute(stepRt);
    }
}