package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepFlagOperation;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.metrics.ITaskFlowMetrics;
import io.nop.xlang.xdsl.action.IActionInputModel;
import io.nop.xlang.xdsl.action.IActionOutputModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.nop.task.TaskErrors.ARG_INPUT_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ARG_TASK_INSTANCE_ID;
import static io.nop.task.TaskErrors.ARG_TASK_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_FAILED;
import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;

public class TaskImpl implements ITask {
    private static final Logger LOG = LoggerFactory.getLogger(TaskImpl.class);
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
        ITaskState taskState = taskRt.getTaskState();

        // plan 259 设计裁定 3: resume 短路 —— resume 路径（recoverMode=true）下，task 已终态则不重跑 mainStep。
        // COMPLETED → 返回缓存 result（mainStep 不重跑）；FAILED → 重抛缓存 exception（非静默跳过，#24）。
        // 短路判定基于从 DB load 的 taskState（snapshot），非 in-memory 引用。
        // in-progress（非终态）task 不短路，正常走 mainStep 重跑（内层 leaf-skip 由 plans 252-258 处理）。
        if (taskRt.isRecoverMode() && taskState.isTerminal()) {
            if (taskState.isSuccess()) {
                Object cached = taskState.getResultValue();
                LOG.info("nop.task.resume-skip:taskName={},taskInstanceId={},taskStatus=COMPLETED,cachedResult={}",
                        taskRt.getTaskName(), taskRt.getTaskInstanceId(), cached);
                return cached == null ? TaskStepReturn.CONTINUE : TaskStepReturn.RETURN_RESULT(cached);
            } else {
                Throwable exp = taskState.exception();
                if (exp == null) {
                    exp = new NopException(ERR_TASK_ALREADY_FAILED)
                            .param(ARG_TASK_NAME, taskRt.getTaskName())
                            .param(ARG_TASK_INSTANCE_ID, taskRt.getTaskInstanceId());
                }
                LOG.info("nop.task.resume-skip:taskName={},taskInstanceId={},taskStatus=FAILED",
                        taskRt.getTaskName(), taskRt.getTaskInstanceId(), exp);
                throw NopException.adapt(exp);
            }
        }

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
            // plan 259 设计裁定 1: task 终态 FAILED driver（sync exception）—— mainStep 抛错 → task 进入 FAILED + 捕获 exception。
            driveTaskFailed(taskRt, taskState, e);
            throw NopException.adapt(e);
        }

        return stepReturn.thenCompose((ret, err) -> {
            taskRt.runCleanup();
            if (metrics != null)
                metrics.endTask(meter, err != null);
            if (err == null) {
                // plan 259 设计裁定 1: task 终态 COMPLETED driver —— mainStep 成功 → task 进入 COMPLETED + 捕获 result。
                driveTaskCompleted(taskRt, taskState, ret);
                return ret;
            }
            // plan 259 设计裁定 1: task 终态 FAILED driver（async exception）—— mainStep async 抛错 → task 进入 FAILED + 捕获 exception。
            driveTaskFailed(taskRt, taskState, err);
            throw NopException.adapt(err);
        });
    }

    /**
     * plan 259 设计裁定 1/2: task 终态 COMPLETED driver + saveTaskState 接线。
     * mainStep 成功 → setTaskStatus(COMPLETED) + 捕获 result（修 result() no-op 为真实捕获），
     * 出口调 saveTaskState 使 DB-backed task instance 反映终态（闭合「saveTaskState 从未被调用」gap）。
     */
    private void driveTaskCompleted(ITaskRuntime taskRt, ITaskState taskState, TaskStepReturn ret) {
        taskState.result(ret);
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
        taskRt.saveTaskState();
    }

    /**
     * plan 259 设计裁定 1/2: task 终态 FAILED driver + saveTaskState 接线。
     * mainStep 抛错 → setTaskStatus(FAILED) + 捕获 exception，
     * 出口调 saveTaskState 使 DB-backed task instance 反映终态（幂等 upsert，设计裁定 2）。
     */
    private void driveTaskFailed(ITaskRuntime taskRt, ITaskState taskState, Throwable err) {
        taskState.exception(err);
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_FAILED);
        taskRt.saveTaskState();
    }

    void checkInputs(ITaskRuntime taskRt) {
        for (IActionInputModel input : inputs) {
            String name = input.getName();
            Object value = taskRt.getInput(name);
            if (value == null)
                value = input.getDefaultValue();

            if (input.getType() != null) {
                value = input.getType().getStdDataType().convert(value,
                        err -> new NopException(err).param(ARG_TASK_NAME, taskRt.getTaskName())
                                .param(ARG_INPUT_NAME, input.getName()));
            }

            // 重新设置一下，确保在当前上下文中总是存在此变量。读取时可能是从父scope中读取到的变量
            taskRt.setInput(name, value);

            if (input.isMandatory() && StringHelper.isEmptyObject(value)) {
                throw new NopException(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY)
                        .source(mainStep)
                        .param(ARG_TASK_NAME, taskRt.getTaskName())
                        .param(ARG_STEP_PATH, mainStep.getStepType())
                        .param(ARG_INPUT_NAME, input.getName());
            }
        }
    }
}