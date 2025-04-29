package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepFlagOperation;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.metrics.ITaskFlowMetrics;
import io.nop.xlang.xdsl.action.IActionInputModel;
import io.nop.xlang.xdsl.action.IActionOutputModel;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.nop.task.TaskErrors.ARG_INPUT_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ARG_TASK_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;

public class TaskImpl implements ITask {
    private final String taskName;
    private final long taskVersion;
    private final ITaskStep mainStep;

    private final boolean recordMetrics;

    private final ITaskStepFlagOperation flagOperation;
    private final List<? extends IActionInputModel> inputs;

    private final List<? extends IActionOutputModel> outputs;

    private final ITaskBeanContainerFactory taskBeanContainerFactory;

    public TaskImpl(String taskName, long taskVersion, ITaskStep mainStep, boolean recordMetrics,
                    ITaskStepFlagOperation flagOperation, ITaskBeanContainerFactory taskBeanContainerFactory,
                    List<? extends IActionInputModel> inputs, List<? extends IActionOutputModel> outputs) {
        this.inputs = inputs == null ? Collections.emptyList() : inputs;
        this.outputs = outputs == null ? Collections.emptyList() : outputs;
        this.flagOperation = flagOperation;
        this.taskName = Guard.notEmpty(taskName, "taskName");
        this.taskVersion = taskVersion;
        this.recordMetrics = recordMetrics;
        this.taskBeanContainerFactory = taskBeanContainerFactory;
        this.mainStep = Guard.notNull(mainStep, "mainStep");
    }

    @Override
    public List<? extends IActionInputModel> getInputs() {
        return inputs;
    }

    @Override
    public List<? extends IActionOutputModel> getOutputs() {
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
            stepRt.setTagSet(taskRt.getTagSet());
        } else {
            stepRt.setTagSet(flagOperation.buildChildFlags(taskRt.getTagSet()));
        }

        stepRt.setOutputNames(outputNames);

        TaskStepReturn stepReturn;
        ITaskFlowMetrics metrics = recordMetrics ? taskRt.getMetrics() : null;
        Object meter = metrics == null ? null : metrics.beginTask();
        try {
            checkInputs(taskRt);

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

    void checkInputs(ITaskRuntime taskRt) {
        for (IActionInputModel input : inputs) {
            String name = input.getName();
            Object value = taskRt.getInput(name);
            if (value == null) {
                // value为null可能是没有设置input，这里强制设置一下，确保scope中的input变量一定存在
                taskRt.setInput(name, null);
            } else if (input.getType() != null) {
                Object castedValue = input.getType().getStdDataType().convert(value,
                        err -> new NopException(err).param(ARG_TASK_NAME, taskRt.getTaskName())
                                .param(ARG_INPUT_NAME, input.getName()));
                if (castedValue != value)
                    taskRt.setInput(name, castedValue);
            }

            if (input.isMandatory() && StringHelper.isEmptyObject(value)) {
                throw new NopException(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY)
                        .param(ARG_TASK_NAME, taskRt.getTaskName())
                        .param(ARG_STEP_PATH, mainStep.getStepType())
                        .param(ARG_INPUT_NAME, input.getName());
            }
        }
    }
}