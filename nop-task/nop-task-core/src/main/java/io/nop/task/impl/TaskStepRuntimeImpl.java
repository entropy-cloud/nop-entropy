package io.nop.task.impl;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;

import java.util.Set;

public class TaskStepRuntimeImpl implements ITaskStepRuntime {
    private final ITaskRuntime taskRt;
    private final ITaskStateStore stateStore;
    private final IEvalScope scope;
    private ICancelToken cancelToken;
    private Set<String> outputNames;
    private ITaskStepState stepState;
    private boolean recoverMode;
    private Set<String> persistVars;

    public TaskStepRuntimeImpl(ITaskRuntime taskRt, ITaskStateStore stateStore) {
        this.taskRt = taskRt;
        this.stateStore = stateStore;
        this.scope = taskRt.getEvalScope().newChildScope();
        this.scope.setLocalValue(TaskConstants.VAR_STEP_RT, this);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public ITaskRuntime getTaskRuntime() {
        return taskRt;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public void setCancelToken(ICancelToken cancelToken) {
        this.cancelToken = cancelToken;
    }

    @Override
    public Set<String> getOutputNames() {
        return outputNames;
    }

    @Override
    public void setOutputNames(Set<String> outputNames) {
        this.outputNames = outputNames;
    }

    @Override
    public ITaskStepState getState() {
        return stepState;
    }

    public void setState(ITaskStepState stepState) {
        this.stepState = stepState;
    }

    @Override
    public void saveState() {
        stateStore.saveStepState(this);
    }

    @Override
    public boolean isSupportPersist() {
        return stateStore.isSupportPersist();
    }

    @Override
    public boolean isRecoverMode() {
        return recoverMode;
    }

    public void setRecoverMode(boolean recoverMode) {
        this.recoverMode = recoverMode;
    }

    public Set<String> getPersistVars() {
        return persistVars;
    }

    public void setPersistVars(Set<String> persistVars) {
        this.persistVars = persistVars;
    }

    @Override
    public ITaskStepRuntime newStepRuntime(String stepName, String stepType, Set<String> persistVars) {
        TaskStepRuntimeImpl newStepRt = new TaskStepRuntimeImpl(taskRt, stateStore);

        ITaskStepState newState = stateStore.loadStepState(stepState, stepName, stepType, taskRt);
        if (newState != null) {
            newStepRt.setRecoverMode(true);
        } else {
            newState = stateStore.newStepState(stepState, stepName, stepType, taskRt);
        }
        newStepRt.setState(newState);
        newStepRt.setPersistVars(persistVars);
        newStepRt.setCancelToken(cancelToken);
        return newStepRt;
    }
}