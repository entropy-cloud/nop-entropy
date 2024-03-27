package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepLib;
import io.nop.task.TaskConstants;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.state.DefaultTaskStateStore;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import static io.nop.task.TaskErrors.ARG_TASK_INSTANCE_ID;
import static io.nop.task.TaskErrors.ERR_TASK_NO_PERSIST_STATE_STORE;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_TASK_INSTANCE;

public class TaskManagerImpl implements ITaskManagerImplementor {
    private IScheduledExecutor scheduledExecutor;

    private ITaskStateStore taskStateStore;

    private ITaskStateStore nonPersistStateStore = DefaultTaskStateStore.INSTANCE;

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

    @Inject
    public void setTaskStateStore(@Nullable ITaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    @Override
    public ITaskRuntime newTaskRuntime(String taskName, long version, boolean saveState, IServiceContext svcCtx) {
        ITaskStateStore stateStore = saveState ? requirePersistStateState() : nonPersistStateStore;
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, false);

        ITaskState taskState = taskStateStore.newTaskState(taskName, version, taskRt);
        taskRt.setTaskState(taskState);
        return taskRt;
    }

    private ITaskStateStore requirePersistStateState() {
        if (this.taskStateStore == null)
            throw new NopException(ERR_TASK_NO_PERSIST_STATE_STORE);
        return this.taskStateStore;
    }

    @Override
    public ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx) {
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, requirePersistStateState(), svcCtx,true);
        ITaskState taskState = taskStateStore.loadTaskState(taskInstanceId, taskRt);
        if (taskState == null)
            throw new NopException(ERR_TASK_UNKNOWN_TASK_INSTANCE)
                    .param(ARG_TASK_INSTANCE_ID, taskInstanceId);
        taskRt.setTaskState(taskState);
        return taskRt;
    }

    @Override
    public ITask getTask(String taskName, long taskVersion) {
        String path = ResourceVersionHelper.buildPath(TaskConstants.TASK_PATH, taskName, taskVersion, TaskConstants.FILE_TYPE_TASK);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTask();
    }

    @Override
    public ITaskStepLib getTaskStepLib(String libName, long libVersion) {
        String path = ResourceVersionHelper.buildPath(TaskConstants.TASK_PATH, libName, libVersion, TaskConstants.FILE_TYPE_TASK_LIB);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTaskStepLib();
    }
}