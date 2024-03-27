package io.nop.task.impl;

import io.nop.api.core.util.Guard;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;

public class TaskImpl implements ITask {
    private final String taskName;
    private final long taskVersion;
    private final ITaskStep mainStep;

    public TaskImpl(String taskName, long taskVersion, ITaskStep mainStep) {
        Guard.checkArgument(taskVersion > 0, "taskVersion");
        this.taskName = Guard.notEmpty(taskName, "taskName");
        this.taskVersion = taskVersion;
        this.mainStep = Guard.notNull(mainStep, "mainStep");
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
    public TaskStepResult execute(ITaskRuntime taskRt) {
        ITaskStepRuntime stepRt = taskRt.newMainStepRuntime();
        return mainStep.execute(stepRt);
    }
}