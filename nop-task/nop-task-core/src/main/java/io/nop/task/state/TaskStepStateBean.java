/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.state;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;

import java.util.Map;

public class TaskStepStateBean extends AbstractTaskStateCommon implements ITaskStepState {
    private String taskInstanceId;
    private String stepInstanceId;
    private String stepPath;
    private int runId;

    private int bodyStepIndex;
    private String parentStepPath;
    private int parentRunId;
    private String stepType;
    private Integer stepStatus;
    private String workerId;
    private Object stateBean;
    private Map<String, Object> outputs;
    private String savedNextStepName;
    private Map<String, Object> persistVarsSnapshot;
    private transient Throwable exception;

    @Override
    public void afterLoad(ITaskRuntime taskRt) {

    }

    @Override
    public void beforeSave(ITaskRuntime taskRt) {

    }

    @Override
    public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
        // W-08（CRA 8.48 R4-08，MA-15 裁定 C）：step 终态守卫原子化——check-then-act 收敛到
        // bean 内部监视器（8 写点全部经 succeed/fail/setStepStatus 三方法进入，bean 内加锁即
        // 结构性覆盖全部写点、调用侧零 diff；锁序=taskState→stepState 单向：守卫方法体零回调
        // 零 taskRt/taskState 访问，无 ABBA——设计文档裁定 C 锁序评审）。
        // 终态守卫语义保留（plan 349 Phase 2）：已终态（如 kill 竞态先到）不得被覆写为 COMPLETED
        synchronized (this) {
            if (isDone())
                return;
            setResultValue(result);
            setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
        }
    }

    @Override
    public void fail(Throwable exception, ITaskRuntime taskRt) {
        // plan 364 [维度05-01]：与 succeed() 对称的 first-terminal-wins 守卫——
        // 已终态（如 KILLED 先到）的步骤不被后到的 fail() 覆写 exception，
        // 避免 resume 重抛的异常与状态标注不符（状态=KILLED、errorBeanData=另一错误）。
        // retry 的非终态记账（状态仍 ACTIVE）不受影响。
        // W-08（MA-15 裁定 C）：exception 写随 fail 入锁——「COMPLETED 行携带 exception」
        // 交错窗闭合（C3-006）
        synchronized (this) {
            if (isDone())
                return;
            exception(exception);
        }
    }

    @Override
    public boolean isDone() {
        Integer status = getStepStatus();
        if (status == null)
            return false;
        return status.equals(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED)
                || status.equals(_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED)
                || status.equals(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED)
                || status.equals(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED);
    }

    @Override
    public boolean isSuccess() {
        return Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED).equals(getStepStatus());
    }

    @Override
    public boolean needSave() {
        return true;
    }

    @Override
    public TaskStepReturn result() {
        if (getResultValue() == null)
            return null;
        return TaskStepReturn.RETURN_RESULT(getResultValue());
    }

    @Override
    public Throwable exception() {
        return exception;
    }

    @Override
    public void exception(Throwable exp) {
        this.exception = exp;
    }

    @Override
    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    @Override
    public void setTaskInstanceId(String taskInstanceId) {
        this.taskInstanceId = taskInstanceId;
    }

    @Override
    public String getStepInstanceId() {
        return stepInstanceId;
    }

    @Override
    public void setStepInstanceId(String stepInstanceId) {
        this.stepInstanceId = stepInstanceId;
    }

    @Override
    public String getStepPath() {
        return stepPath;
    }

    @Override
    public void setStepPath(String stepPath) {
        this.stepPath = stepPath;
    }

    @Override
    public int getRunId() {
        return runId;
    }

    @Override
    public void setRunId(int runId) {
        this.runId = runId;
    }

    @Override
    public int getBodyStepIndex() {
        return bodyStepIndex;
    }

    @Override
    public void setBodyStepIndex(int bodyStepIndex) {
        this.bodyStepIndex = bodyStepIndex;
    }

    @Override
    public String getParentStepPath() {
        return parentStepPath;
    }

    @Override
    public void setParentStepPath(String parentStepPath) {
        this.parentStepPath = parentStepPath;
    }

    @Override
    public int getParentRunId() {
        return parentRunId;
    }

    @Override
    public void setParentRunId(int parentRunId) {
        this.parentRunId = parentRunId;
    }

    @Override
    public String getStepType() {
        return stepType;
    }

    @Override
    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    @Override
    public Integer getStepStatus() {
        return stepStatus;
    }

    @Override
    public void setStepStatus(Integer stepStatus) {
        // W-08（CRA 8.48 R4-08，MA-15 裁定 C）：守卫判定+写入原子化（与 succeed/fail 同一
        // 监视器；EXPIRED/KILLED driver 经此方法直写的写点同被覆盖）。
        // 终态守卫语义保留（plan 349 Phase 2）：已终态时忽略不同的状态设置（first-terminal-wins），
        // 相同终态的重复设置幂等放行
        synchronized (this) {
            if (stepStatus != null && isDone() && !stepStatus.equals(this.stepStatus))
                return;
            this.stepStatus = stepStatus;
        }
    }

    @Override
    public String getWorkerId() {
        return workerId;
    }

    @Override
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getStateBean(Class<T> beanType) {
        if (stateBean == null)
            return null;
        if (beanType.isInstance(stateBean))
            return (T) stateBean;
        // plan 349 Phase 6：DB round-trip 后 stateBean 以通用 Map 形态恢复（无类型信息），
        // 按请求类型转换（如 LoopStateBean/ForkStateBean 的 @DataBean 结构）。
        // plan 364 [维度03-04]：转换结果写回字段——resume 后异步循环每迭代重入 execute()
        // 重调 getStateBean，不写回会对同一 runtime 反复 serialize+parse
        if (stateBean instanceof Map) {
            T converted = (T) io.nop.core.lang.json.JsonTool.parseBeanFromText(
                    io.nop.core.lang.json.JsonTool.serialize(stateBean, false), beanType);
            this.stateBean = converted;
            return converted;
        }
        return (T) stateBean;
    }

    @Override
    public void setStateBean(Object stateBean) {
        this.stateBean = stateBean;
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    @Override
    public String getSavedNextStepName() {
        return savedNextStepName;
    }

    @Override
    public void setSavedNextStepName(String savedNextStepName) {
        this.savedNextStepName = savedNextStepName;
    }

    @Override
    public Map<String, Object> getPersistVarsSnapshot() {
        return persistVarsSnapshot;
    }

    @Override
    public void setPersistVarsSnapshot(Map<String, Object> persistVarsSnapshot) {
        this.persistVarsSnapshot = persistVarsSnapshot;
    }

    @Override
    public void result(TaskStepReturn result) {
        setResultValue(result == null ? null : result.getResult());
    }

}