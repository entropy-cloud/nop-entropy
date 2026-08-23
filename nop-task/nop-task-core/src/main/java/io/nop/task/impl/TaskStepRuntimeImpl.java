package io.nop.task.impl;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TaskStepRuntimeImpl implements ITaskStepRuntime {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepRuntimeImpl.class);

    /**
     * task 级 attribute key：记录本次 task 执行内已实例化过的 stepPath 集合。
     * 用于把 loadStepState（resume 语义）限定在每个 stepPath 的首次实例化上。
     */
    private static final String ATTR_FIRST_INSTANTIATED_STEP_PATHS = "nop.task.first-instantiated-step-paths";

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
        this.scope.setLocalValue(TaskConstants.VAR_TASK_STEP_RT, this);
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
    public Set<String> getTagSet() {
        return enabledFlags;
    }

    @Override
    public void setTagSet(Set<String> enabledFlags) {
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

        ITaskStepState newState = null;
        // loadStepState（resume 语义）只对本次 task 执行内该 stepPath 的首次实例化生效。
        // 同一执行内相同 stepPath 的后续实例化（loop 迭代 2+、fork/forkN 分支 2+ 共用同一
        // stepPath）必须使用全新 state 正常执行，否则 continuation-skip 会命中首轮持久化的
        // 终态行，静默跳过后续迭代/分支并复用首轮结果（loadStepState 仅按 taskInstanceId +
        // stepPath 定位，无法区分迭代）。每次重新执行（resume / re-execution）都会构建新的
        // task runtime，其 attribute 集合为空，故唯一路径步骤的 resume-by-load 语义不受影响。
        if (isFirstInstantiation(stepName)) {
            newState = stateStore.loadStepState(stepState, stepName, stepType, taskRt);
        }
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

    /**
     * 记录 stepPath 并返回它是否是本次 task 执行内的首次实例化。
     * fork 分支可能并发实例化同名步骤，使用并发集合保证 add 的原子性。
     */
    @SuppressWarnings("unchecked")
    private boolean isFirstInstantiation(String stepName) {
        Set<String> instantiated = (Set<String>) taskRt.computeAttributeIfAbsent(
                ATTR_FIRST_INSTANTIATED_STEP_PATHS, key -> ConcurrentHashMap.newKeySet());
        String parentPath = stepState == null ? null : stepState.getStepPath();
        return instantiated.add(TaskStepHelper.buildStepPath(parentPath, stepName));
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