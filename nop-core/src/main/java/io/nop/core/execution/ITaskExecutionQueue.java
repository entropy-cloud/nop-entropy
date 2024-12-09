package io.nop.core.execution;

import io.nop.commons.service.ILifeCycle;

import java.util.List;

public interface ITaskExecutionQueue extends ILifeCycle {
    /**
     * 返回当前任务队列中所有任务的个数
     */
    int getCurrentTaskCount();

    /**
     * 返回任务队列中正在执行的任务个数
     */
    int getRunningTaskCount();

    /**
     * 返回所有已经完成的任务个数。已经完成的任务会自动从任务队列中删除
     */
    long getCompletedTaskCount();

    /**
     * 添加任务，如果任务已经存在，则返回已经存在的任务状态。如果不存在，则新建任务，返回新建任务的状态
     *
     * @param taskName 任务名称
     * @param task     任务
     * @return 已经存在的任务状态或者新建的任务状态
     */
    ITaskExecutionState addTaskIfAbsent(String taskName, String description, IExecution<?> task);

    /**
     * 替换任务，如果任务已经存在，则取消该任务，新建任务，并返回新建任务的任务状态
     *
     * @param taskName 任务名称
     * @param task     任务
     * @return 新建任务的任务状态
     */
    ITaskExecutionState replaceTask(String taskName, String description, IExecution<?> task);

    ITaskExecutionState getTaskState(String taskName);

    List<? extends ITaskExecutionState> getTaskStates();

    default void cancelTask(String taskName, String cancelReason) {
        ITaskExecutionState state = getTaskState(taskName);
        if (state != null) state.cancel(cancelReason);
    }

    default void cancelAllTasks(String cancelReason) {
        for (ITaskExecutionState state : getTaskStates()) {
            state.cancel(cancelReason);
        }
    }
}