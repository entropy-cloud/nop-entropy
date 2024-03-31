package io.nop.task.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.xlang.api.XLang;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class TaskRuntimeImpl implements ITaskRuntime {
    private final Map<String, Object> attrs = new ConcurrentHashMap<>();

    private final ITaskManagerImplementor taskManager;

    private final ITaskStateStore stateStore;

    private final IServiceContext svcCtx;

    private final IEvalScope scope;

    private final boolean recoverMode;

    private ITaskState taskState;

    public TaskRuntimeImpl(ITaskManagerImplementor taskManager,
                           ITaskStateStore stateStore,
                           IServiceContext svcCtx, boolean recoverMode) {
        this.taskManager = taskManager;
        this.stateStore = stateStore;
        this.svcCtx = svcCtx;
        this.recoverMode = recoverMode;
        this.scope = svcCtx != null ? svcCtx.getEvalScope().newChildScope() : XLang.newEvalScope();
        this.scope.setLocalValue(TaskConstants.VAR_TASK_RT, this);
    }

    public boolean isRecoverMode() {
        return recoverMode;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public IServiceContext getSvcCtx() {
        return svcCtx;
    }

    @Override
    public ITaskState getTaskState() {
        return taskState;
    }

    public void setTaskState(ITaskState taskState) {
        this.taskState = Guard.notNull(taskState, "taskState");
    }

    @Override
    public ITaskManager getTaskManager() {
        return taskManager;
    }

    @Override
    public int newRunId() {
        return taskState.newRunId();
    }

    @Override
    public ITaskRuntime newChildRuntime(String taskName, long taskVersion, boolean saveState) {
        return taskManager.newTaskRuntime(taskName, taskVersion, saveState, getSvcCtx());
    }

    @Override
    public Object getAttribute(String name) {
        return attrs.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            attrs.remove(name);
        } else {
            attrs.put(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        attrs.remove(name);
    }

    @Override
    public Set<String> getAttributeKeys() {
        return attrs.keySet();
    }

    @Override
    public Object computeAttributeIfAbsent(String name, Function<String, Object> action) {
        return attrs.computeIfAbsent(name, action);
    }

    @Override
    public IScheduledExecutor getScheduledExecutor() {
        return taskManager.getScheduledExecutor();
    }

    @Override
    public ITaskStepRuntime newMainStepRuntime() {
        ITaskStepState state = stateStore.newMainStepState(this.getTaskState());
        // 使用taskRt的scope
        TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(this, stateStore, getEvalScope());
        stepRt.setCancelToken(getSvcCtx());
        stepRt.setState(state);
        stepRt.setRecoverMode(recoverMode);
        return stepRt;
    }
}