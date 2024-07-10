package io.nop.task.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.metrics.EmptyTaskFlowMetrics;
import io.nop.task.metrics.ITaskFlowMetrics;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class TaskRuntimeImpl implements ITaskRuntime {
    private final Map<String, Object> attrs = new ConcurrentHashMap<>();

    private final ITaskFlowManagerImplementor taskManager;

    private final ITaskStateStore stateStore;

    private final IServiceContext svcCtx;

    private final IContext context;

    private final IEvalScope scope;

    private final boolean recoverMode;

    private Set<String> enabledFlags = Collections.emptySet();

    private ITaskState taskState;

    private ITaskFlowMetrics metrics = EmptyTaskFlowMetrics.INSTANCE;

    public TaskRuntimeImpl(ITaskFlowManagerImplementor taskManager,
                           ITaskStateStore stateStore,
                           IServiceContext svcCtx, boolean recoverMode) {
        this.taskManager = taskManager;
        this.stateStore = stateStore;
        this.svcCtx = svcCtx;
        this.recoverMode = recoverMode;
        // taskRt可能会被多线程访问，所以这里scope线程安全
        this.scope = svcCtx != null ? svcCtx.getEvalScope().newChildScope(true, true)
                : XLang.newEvalScope(CollectionHelper.newConcurrentMap(4));
        this.scope.setLocalValue(TaskConstants.VAR_TASK_RT, this);
        this.context = svcCtx == null ? ContextProvider.getOrCreateContext() : svcCtx.getContext();
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
    public IContext getContext() {
        return context;
    }

    @Override
    public ITaskState getTaskState() {
        return taskState;
    }

    public void setTaskState(ITaskState taskState) {
        this.taskState = Guard.notNull(taskState, "taskState");
    }

    @Override
    public ITaskFlowMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(ITaskFlowMetrics metrics) {
        this.metrics = Guard.notNull(metrics, "metrics");
    }

    @Override
    public ITaskFlowManager getTaskManager() {
        return taskManager;
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
    public int newRunId() {
        return taskState.newRunId();
    }

    @Override
    public ITaskRuntime newChildRuntime(ITask task, boolean saveState) {
        return taskManager.newTaskRuntime(task, saveState, getSvcCtx());
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
    public IThreadPoolExecutor getThreadPoolExecutor(String executorBean) {
        return taskManager.getThreadPoolExecutor(getEvalScope().getBeanProvider(), executorBean);
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

    @Override
    public IRateLimiter getRateLimiter(String key, double requestsPerSecond, boolean global) {
        return taskManager.getRateLimiter(this, key, requestsPerSecond, global);
    }
}