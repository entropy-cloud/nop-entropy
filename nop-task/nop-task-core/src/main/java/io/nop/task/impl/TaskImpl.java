package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
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

    private final ITaskBeanContainerFactory taskBeanContainerFactory;

    public TaskImpl(String taskName, long taskVersion, ITaskStep mainStep, boolean recordMetrics,
                    ITaskStepFlagOperation flagOperation, ITaskBeanContainerFactory taskBeanContainerFactory,
                    List<? extends ITaskInputModel> inputs, List<? extends ITaskOutputModel> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.flagOperation = flagOperation;
        this.taskName = Guard.notEmpty(taskName, "taskName");
        this.taskVersion = taskVersion;
        this.recordMetrics = recordMetrics;
        this.taskBeanContainerFactory = taskBeanContainerFactory;
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

    IBeanContainer createBeanContainer(ITaskRuntime taskRt) {
        if (taskBeanContainerFactory == null)
            return null;
        return taskBeanContainerFactory.createBeanContainer(taskRt);
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

        TaskStepReturn stepReturn;
        ITaskFlowMetrics metrics = recordMetrics ? taskRt.getMetrics() : null;
        Object meter = metrics == null ? null : metrics.beginTask();
        try {
            // 如果设置了任务专用的beanContainer
            IBeanContainer beanContainer = createBeanContainer(taskRt);
            if (beanContainer != null) {
                beanContainer.start();
                taskRt.addTaskCleanup(beanContainer::stop);
                taskRt.getEvalScope().setBeanProvider(beanContainer);
            }
            stepReturn = mainStep.execute(stepRt);
        } catch (Exception e) {
            taskRt.runCleanup();
            metrics.endTask(meter, false);
            throw NopException.adapt(e);
        }

        return stepReturn.thenCompose((ret, err) -> {
            taskRt.runCleanup();
            if (metrics != null)
                metrics.endTask(meter, err != null);
            if (err == null)
                return ret;
            throw NopException.adapt(err);
        });
    }
}