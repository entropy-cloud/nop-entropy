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
    private transient Throwable exception;

    @Override
    public void afterLoad(ITaskRuntime taskRt) {

    }

    @Override
    public void beforeSave(ITaskRuntime taskRt) {

    }

    @Override
    public void succeed(Object result, String nextStepId, ITaskRuntime taskRt) {
        setResultValue(result);
        setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
    }

    @Override
    public void fail(Throwable exception, ITaskRuntime taskRt) {
        exception(exception);
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
        this.stepStatus = stepStatus;
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
    public <T> T getStateBean(Class<T> beanType) {
        return (T) stateBean;
    }

    @Override
    public void setStateBean(Object stateBean) {
        this.stateBean = stateBean;
    }

    @Override
    public void result(TaskStepReturn result) {
        setResultValue(result == null ? null : result.getResult());
    }

}