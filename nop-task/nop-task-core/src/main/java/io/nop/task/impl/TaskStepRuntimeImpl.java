package io.nop.task.impl;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TaskStepRuntimeImpl implements ITaskStepRuntime {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepRuntimeImpl.class);

    private final ITaskRuntime taskRt;
    private final ITaskStateStore stateStore;
    private final IEvalScope scope;
    private ICancelToken cancelToken;
    private Set<String> outputNames;
    private ITaskStepState stepState;

    private Set<String> enabledFlags = Collections.emptySet();
    private boolean recoverMode;
    private Set<String> persistVars;

    private List<Runnable> stepCleanups;
    private Throwable exception;

    public TaskStepRuntimeImpl(ITaskRuntime taskRt, ITaskStateStore stateStore, IEvalScope scope) {
        this.taskRt = taskRt;
        this.stateStore = stateStore;
        this.scope = scope;
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

    @Nonnull
    @Override
    public Set<String> getEnabledFlags() {
        return enabledFlags;
    }

    @Override
    public void setEnabledFlags(Set<String> enabledFlags) {
        this.enabledFlags = enabledFlags == null ? Collections.emptySet() : enabledFlags;
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
    public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                           Set<String> persistVars, boolean useParentScope, boolean concurrent) {
        IEvalScope baseScope = useParentScope ? scope : getTaskRuntime().getEvalScope();
        IEvalScope childScope = baseScope.newChildScope(true, concurrent);
        TaskStepRuntimeImpl newStepRt = new TaskStepRuntimeImpl(taskRt, stateStore, childScope);

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

    @Override
    public synchronized void addStepCleanup(Runnable cleanup) {
        if (cleanup == null)
            return;
        if (stepCleanups == null)
            stepCleanups = new ArrayList<>();
        stepCleanups.add(cleanup);
    }

    @Override
    public void runStepCleanups() {
        if (stepCleanups != null) {
            stepCleanups.forEach(this::runCleanup);
            stepCleanups.clear();
        }
    }

    private void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            LOG.error("nop.err.xpt.run-cleanup-error", e);
        }
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }
}