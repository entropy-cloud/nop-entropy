/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.state;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

public class TaskStepState extends AbstractTaskStateCommon implements ITaskStepState {
    private String stepId;
    private int runId;

    private ITaskStepState parentState;
    private String parentStepId;
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

    }

    @Override
    public void fail(Throwable exception, ITaskRuntime taskRt) {

    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public boolean needSave() {
        return true;
    }

    @Override
    public TaskStepResult result() {
        return null;
    }

    @Override
    public Throwable exception() {
        return exception;
    }

    @Override
    public void exception(Throwable exp) {

    }

    @Override
    public IEvalScope getEvalScope() {
        return null;
    }

    @Override
    public void setEvalScope(IEvalScope scope) {

    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
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
    public ITaskStepState getParentState() {
        return null;
    }

    @Override
    public String getParentStepId() {
        return parentStepId;
    }

    @Override
    public void setParentStepId(String parentStepId) {
        this.parentStepId = parentStepId;
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
    public void result(TaskStepResult result) {

    }

    public void setInput(String name, Object value) {

    }

}