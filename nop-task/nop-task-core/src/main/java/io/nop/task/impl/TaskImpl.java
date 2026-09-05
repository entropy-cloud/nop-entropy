package io.nop.task.impl;

import io.nop.api.core.exceptions.ErrorCode;
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
import io.nop.task.utils.TaskStepHelper;
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
import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_KILLED;
import static io.nop.task.TaskErrors.ERR_TASK_ALREADY_TIMEOUT;
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
                // plan 260 设计裁定 3: resume 短路区分 FAILED / KILLED / TIMEOUT（非一律合成 ERR_TASK_ALREADY_FAILED）。
                // KILLED/TIMEOUT 终态在本计划前不可达，本计划使它们可达后必须同步修正误报。
                // 终态判定基于 DB load 的 snapshot，mainStep 不重跑（非静默跳过）。
                Integer status = taskState.getTaskStatus();
                Throwable exp = taskState.exception();
                if (exp == null) {
                    exp = synthesizeResumeException(status, taskRt);
                }
                LOG.info("nop.task.resume-skip:taskName={},taskInstanceId={},taskStatus={}",
                        taskRt.getTaskName(), taskRt.getTaskInstanceId(), status, exp);
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
            // recordMetrics=false 时 metrics 为 null（与成功路径的 null 守卫一致）。
            // 缺守卫会把真实失败吞成 NopException(NPE)，破坏 honest-failure 语义
            if (metrics != null)
                metrics.endTask(meter, false);
            // plan 260 设计裁定 2: task 终态 driver 出口区分 cancellation（KILLED/TIMEOUT）与普通失败（FAILED）。
            driveTaskTerminal(taskRt, taskState, e);
            throw NopException.adapt(e);
        }

        return stepReturn.thenCompose((ret, err) -> {
            if (err == null && ret != null && ret.isSuspend()) {
                // SUSPEND 不是终态（check2 P0-2 / plan 349 Phase 1）：任务进入 SUSPENDED 等待恢复，
                // 不能驱动为 COMPLETED——否则 resume 被 isTerminal 短路，挂起恢复语义端到端断裂。
                // 不 runCleanup（task 级 bean 容器保留给进程内 resume）；task meter 不关闭
                // （任务未终态，TaskFlowMetricsImpl 暂无 suspend 维度，挂起任务 meter 遗留量级有限，可接受）。
                taskState.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);
                taskRt.saveTaskState();
                return ret;
            }
            taskRt.runCleanup();
            if (metrics != null)
                metrics.endTask(meter, err != null);
            if (err == null) {
                // plan 259 设计裁定 1: task 终态 COMPLETED driver —— mainStep 成功 → task 进入 COMPLETED + 捕获 result。
                driveTaskCompleted(taskRt, taskState, ret);
                return ret;
            }
            // plan 260 设计裁定 2: async 出口同样区分 cancellation（KILLED/TIMEOUT）与普通失败（FAILED）。
            driveTaskTerminal(taskRt, taskState, err);
            throw NopException.adapt(err);
        });
    }

    /**
     * plan 259 设计裁定 1/2: task 终态 COMPLETED driver + saveTaskState 接线。
     * mainStep 成功 → setTaskStatus(COMPLETED) + 捕获 result（修 result() no-op 为真实捕获），
     * 出口调 saveTaskState 使 DB-backed task instance 反映终态（闭合「saveTaskState 从未被调用」gap）。
     */
    private void driveTaskCompleted(ITaskRuntime taskRt, ITaskState taskState, TaskStepReturn ret) {
        if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_COMPLETED))
            return;
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
        if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_FAILED))
            return;
        taskState.exception(err);
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_FAILED);
        taskRt.saveTaskState();
    }

    /**
     * plan 260 设计裁定 2: task 终态 driver 出口分发——区分 cancellation（KILLED/TIMEOUT）与普通失败（FAILED）。
     * <p>reason 来源限定为 Phase-1 step driver 编码进 exception 的 reason（{@link TaskStepHelper#getCancelReason}），
     * 非 {@code taskRt.getCancelReason()}（step-timeout 时它未被 cancel，恒为 null）/ 非 step token（可能被复位）。
     * {@code CANCEL_REASON_TIMEOUT} → {@link #driveTaskTimeout}（TIMEOUT(50)），kill/其它 → {@link #driveTaskKilled}（KILLED(70)），
     * 非 cancellation → 维持 {@link #driveTaskFailed}（FAILED(60)）。状态码已按 plan 349 与 ORM 字典对齐。
     */
    private void driveTaskTerminal(ITaskRuntime taskRt, ITaskState taskState, Throwable err) {
        if (TaskStepHelper.isCancelledException(err)) {
            String reason = TaskStepHelper.getCancelReason(err);
            if (TaskStepHelper.isTimeoutReason(reason)) {
                driveTaskTimeout(taskRt, taskState, err);
            } else {
                driveTaskKilled(taskRt, taskState, err);
            }
        } else {
            driveTaskFailed(taskRt, taskState, err);
        }
    }

    /**
     * plan 260 设计裁定 2: task 终态 KILLED driver（对称 plan 259 driveTaskFailed）。
     * kill-cancel 的 cancellation → setTaskStatus(KILLED(70)) + 捕获 exception + saveTaskState。
     */
    private void driveTaskKilled(ITaskRuntime taskRt, ITaskState taskState, Throwable err) {
        if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_KILLED))
            return;
        taskState.exception(err);
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_KILLED);
        taskRt.saveTaskState();
    }

    /**
     * plan 260 设计裁定 2: task 终态 TIMEOUT driver（对称 plan 259 driveTaskFailed）。
     * step-timeout 上浮的 cancellation → setTaskStatus(TIMEOUT(50)) + 捕获 exception + saveTaskState。
     */
    private void driveTaskTimeout(ITaskRuntime taskRt, ITaskState taskState, Throwable err) {
        if (skipTerminalOverwrite(taskState, TaskConstants.TASK_STATUS_TIMEOUT))
            return;
        taskState.exception(err);
        taskState.setTaskStatus(TaskConstants.TASK_STATUS_TIMEOUT);
        taskRt.saveTaskState();
    }

    /**
     * 终态守卫（plan 349 Phase 2）：task 已进入终态时不得被后到的 driver 覆写
     * （first-terminal-wins）。竞态场景：cancel 路径先写 KILLED，异步完成的主流程
     * thenCompose 稍后成功返回即用 COMPLETED 覆盖——修复后保留先到终态并告警。
     */
    private boolean skipTerminalOverwrite(ITaskState taskState, int targetStatus) {
        if (!taskState.isTerminal())
            return false;
        Integer current = taskState.getTaskStatus();
        if (current != null && current == targetStatus)
            return false; // 同一终态的重复驱动：幂等放行
        LOG.warn("nop.task.terminal-state-overwrite-blocked:taskInstanceId={},keepStatus={},ignoredStatus={}",
                taskState.getTaskInstanceId(), current, targetStatus);
        return true;
    }

    /**
     * plan 260 设计裁定 3: resume 短路区分非 COMPLETED 终态的合成 exception。
     * FAILED → ERR_TASK_ALREADY_FAILED / KILLED → ERR_TASK_ALREADY_KILLED / TIMEOUT → ERR_TASK_ALREADY_TIMEOUT
     * （非一律 ERR_TASK_ALREADY_FAILED，#24 非静默）。
     */
    private NopException synthesizeResumeException(Integer status, ITaskRuntime taskRt) {
        int s = status == null ? TaskConstants.TASK_STATUS_FAILED : status;
        ErrorCode code;
        if (s == TaskConstants.TASK_STATUS_KILLED) {
            code = ERR_TASK_ALREADY_KILLED;
        } else if (s == TaskConstants.TASK_STATUS_TIMEOUT) {
            code = ERR_TASK_ALREADY_TIMEOUT;
        } else {
            code = ERR_TASK_ALREADY_FAILED;
        }
        return new NopException(code)
                .param(ARG_TASK_NAME, taskRt.getTaskName())
                .param(ARG_TASK_INSTANCE_ID, taskRt.getTaskInstanceId());
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