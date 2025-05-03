package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.concurrent.semaphore.DefaultSemaphore;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepLib;
import io.nop.task.TaskConstants;
import io.nop.task.builder.ITaskFlowBuilder;
import io.nop.task.builder.ITaskStepLibBuilder;
import io.nop.task.builder.TaskFlowBuilder;
import io.nop.task.builder.TaskStepLibBuilder;
import io.nop.task.metrics.TaskFlowMetricsImpl;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import static io.nop.task.TaskConfigs.CFG_TASK_MAX_GLOBAL_RATE_LIMITERS;
import static io.nop.task.TaskConfigs.CFG_TASK_MAX_GLOBAL_SEMAPHORES;
import static io.nop.task.TaskErrors.ARG_TASK_INSTANCE_ID;
import static io.nop.task.TaskErrors.ERR_TASK_NO_PERSIST_STATE_STORE;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_TASK_INSTANCE;

public class TaskFlowManagerImpl implements ITaskFlowManagerImplementor {
    private IScheduledExecutor scheduledExecutor;

    private ITaskStateStore taskStateStore;

    private ITaskStateStore nonPersistStateStore = DefaultTaskStateStore.INSTANCE;

    private final LocalCache<String, IRateLimiter> globalRateLimiters = LocalCache.newCache(
            "task-global-rate-limiter", CacheConfig.newConfig(CFG_TASK_MAX_GLOBAL_RATE_LIMITERS.get()));

    private final LocalCache<String, ISemaphore> globalSemaphores = LocalCache.newCache(
            "task-global-semaphore", CacheConfig.newConfig(CFG_TASK_MAX_GLOBAL_SEMAPHORES.get()));

    public void setNonPersistStateStore(ITaskStateStore stateStore) {
        this.nonPersistStateStore = stateStore;
    }

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @Override
    public IScheduledExecutor getScheduledExecutor() {
        if (scheduledExecutor == null)
            return GlobalExecutors.globalTimer();
        return scheduledExecutor;
    }

    @Override
    public IThreadPoolExecutor getThreadPoolExecutor(IBeanProvider beanProvider, String executorBean) {
        IThreadPoolExecutor executor = GlobalExecutors.getExecutor(executorBean);
        if (executor == null)
            executor = (IThreadPoolExecutor) beanProvider.getBean(executorBean);
        return executor;
    }

    @Inject
    public void setTaskStateStore(@Nullable ITaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    @Override
    public ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx, IEvalScope scope) {
        ITaskStateStore stateStore = saveState ? requirePersistStateState() : nonPersistStateStore;
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, false);

        ITaskState taskState = stateStore.newTaskState(task.getTaskName(), task.getTaskVersion(), taskRt);
        taskRt.setTaskState(taskState);

        prepareTaskRuntime(taskRt, task);
        return taskRt;
    }

    protected void prepareTaskRuntime(TaskRuntimeImpl taskRt, ITask task) {
        String taskName = task.getTaskName();
        long taskVersion = task.getTaskVersion();
        taskRt.setMetrics(new TaskFlowMetricsImpl(GlobalMeterRegistry.instance(), null, taskName, taskVersion));
    }

    private ITaskStateStore requirePersistStateState() {
        if (this.taskStateStore == null)
            throw new NopException(ERR_TASK_NO_PERSIST_STATE_STORE);
        return this.taskStateStore;
    }

    @Override
    public ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx, IEvalScope scope) {
        ITaskStateStore stateStore = requirePersistStateState();
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, true);
        ITaskState taskState = stateStore.loadTaskState(taskInstanceId, taskRt);
        if (taskState == null)
            throw new NopException(ERR_TASK_UNKNOWN_TASK_INSTANCE)
                    .param(ARG_TASK_INSTANCE_ID, taskInstanceId);
        taskRt.setTaskState(taskState);
        return taskRt;
    }

    @Override
    public ITask getTask(String taskName, long taskVersion) {
        TaskFlowModel taskFlowModel = getTaskFlowModel(taskName, taskVersion);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public TaskFlowModel getTaskFlowModel(String taskName, long taskVersion) {
        String path = ResourceVersionHelper.buildResolvePath(TaskConstants.MODEL_TYPE_TASK, taskName, taskVersion);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel;
    }

    @Override
    public ITask parseTask(IResource resource) {
        TaskFlowModel taskFlowModel = (TaskFlowModel) new DslModelParser().parseFromResource(resource);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public ITask loadTaskFromPath(String path) {
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public ITaskStepLib getTaskStepLib(String libName, long libVersion) {
        String path = ResourceVersionHelper.buildResolvePath(TaskConstants.MODEL_TYPE_TASK, libName, libVersion);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTaskStepLib(newTaskStepLibBuilder());
    }

    @Override
    public IRateLimiter getRateLimiter(ITaskRuntime taskRt, String key, double requestPerSecond, boolean global) {
        if (global) {
            return globalRateLimiters.computeIfAbsent(taskRt.getTaskName() + ":" + key, k -> new DefaultRateLimiter(requestPerSecond));
        }
        return (IRateLimiter) taskRt.computeAttributeIfAbsent("rate-limit:" + key, k -> {
            return new DefaultRateLimiter(requestPerSecond);
        });
    }

    @Override
    public ISemaphore getSemaphore(ITaskRuntime taskRt, String key, int maxPermits, boolean global) {
        if (global)
            return globalSemaphores.computeIfAbsent(taskRt.getTaskName() + ":" + key, k -> new DefaultSemaphore(maxPermits));
        return (ISemaphore) taskRt.computeAttributeIfAbsent("semaphore:" + key, k -> {
            return new DefaultSemaphore(maxPermits);
        });
    }

    @Override
    public Map<String, ISemaphore.SemaphoreStats> getGlobalSemaphoreStats() {
        Map<String, ISemaphore.SemaphoreStats> ret = new HashMap<>();
        globalSemaphores.forEachEntry((k, v) -> {
            ret.put(k, v.getStats());
        });
        return ret;
    }

    @Override
    public Map<String, IRateLimiter.RateLimiterStats> getGlobalRateLimiterStats() {
        Map<String, IRateLimiter.RateLimiterStats> ret = new HashMap<>();
        globalRateLimiters.forEachEntry((k, v) -> {
            ret.put(k, v.getStats());
        });
        return ret;
    }

    @Override
    public void resetGlobalStats() {
        globalSemaphores.forEachEntry((k, v) -> {
            v.resetStats();
        });
    }

    protected ITaskFlowBuilder newTaskFlowBuilder() {
        return new TaskFlowBuilder();
    }

    protected ITaskStepLibBuilder newTaskStepLibBuilder() {
        return new TaskStepLibBuilder();
    }
}