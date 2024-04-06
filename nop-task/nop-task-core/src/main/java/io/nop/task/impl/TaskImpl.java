package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepFlagOperation;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.metrics.ITaskFlowMetrics;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;

import java.util.List;
import java.util.Set;

public class TaskImpl implements ITask {
    private final String taskName;
    private final long taskVersion;
    private final ITaskStep mainStep;

    private final boolean recordMetrics;

    private final ITaskStepFlagOperation flagOperation;
    private final List<? extends ITaskInputModel> inputs;

    private final List<? extends ITaskOutputModel> outputs;

    public TaskImpl(String taskName, long taskVersion, ITaskStep mainStep, boolean recordMetrics,
                    ITaskStepFlagOperation flagOperation,
                    List<? extends ITaskInputModel> inputs, List<? extends ITaskOutputModel> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.flagOperation = flagOperation;
        Guard.checkArgument(taskVersion > 0, "taskVersion");
        this.taskName = Guard.notEmpty(taskName, "taskName");
        this.taskVersion = taskVersion;
        this.recordMetrics = recordMetrics;
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
    public TaskStepReturn execute(ITaskRuntime taskRt, Set<String> outputNames) {
        ITaskStepRuntime stepRt = taskRt.newMainStepRuntime();
        if (flagOperation == null) {
            stepRt.setEnabledFlags(taskRt.getEnabledFlags());
        } else {
            stepRt.setEnabledFlags(flagOperation.buildChildFlags(taskRt.getEnabledFlags()));
        }

        stepRt.setOutputNames(outputNames);
        if (!recordMetrics)
            return mainStep.execute(stepRt);

        ITaskFlowMetrics metrics = taskRt.getMetrics();
        Object meter = metrics.beginTask();
        try {
            return mainStep.execute(stepRt).thenCompose((ret, err) -> {
                metrics.endTask(meter, err != null);
                if (err == null)
                    return ret;
                throw NopException.adapt(err);
            });
        } catch (Exception e) {
            metrics.endTask(meter, false);
            throw NopException.adapt(e);
        }
    }
}