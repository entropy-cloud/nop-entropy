package io.nop.core.execution;

import io.nop.commons.service.ILifeCycle;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
     * 返回等待执行的任务个数
     */
    int getPendingTaskCount();

    /**
     * 返回所有已经完成的任务个数。已经完成的任务会自动从任务队列中删除
     */
    long getCompletedTaskCount();

    /**
     * 返回失败的任务个数
     */
    long getFailedTaskCount();

    /**
     * 返回被取消的任务个数
     */
    long getCancelledTaskCount();

    /**
     * 添加任务，如果任务已经存在，则返回已经存在的任务状态。如果不存在，则新建任务，返回新建任务的状态
     *
     * @param taskRef     任务唯一标识引用
     * @param taskName    任务显示名称（可重复）
     * @param source      任务来源，用于区分是谁或者从哪里提交的任务
     * @param description 任务描述
     * @param task        任务执行体
     * @return 已经存在的任务状态或者新建的任务状态
     */
    ITaskExecutionState addTaskIfAbsent(String taskRef, String taskName, String source, String description, IExecution<?> task);

    /**
     * 替换任务，如果任务已经存在，则取消该任务，新建任务，并返回新建任务的任务状态
     *
     * @param taskRef     任务唯一标识引用
     * @param taskName    任务显示名称（可重复）
     * @param source      任务来源，用于区分是谁或者从哪里提交的任务
     * @param description 任务描述
     * @param task        任务执行体
     * @return 新建任务的任务状态
     */
    ITaskExecutionState replaceTask(String taskRef, String taskName, String source, String description, IExecution<?> task);

    /**
     * 根据任务唯一标识引用获取任务状态
     */
    ITaskExecutionState getTaskState(String taskRef);

    /**
     * 获取所有任务状态
     */
    List<? extends ITaskExecutionState> getTaskStates();

    /**
     * 根据来源获取任务状态列表
     */
    List<? extends ITaskExecutionState> getTaskStatesBySource(String source);

    /**
     * 根据任务名称获取任务状态列表（可能有多个同名任务）
     */
    List<? extends ITaskExecutionState> getTaskStatesByName(String taskName);


    /**
     * 等待任务完成
     *
     * @param taskRef 任务唯一标识
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 任务完成状态，如果超时返回null
     */
    ITaskExecutionState waitForTask(String taskRef, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 等待所有任务完成
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 是否所有任务都在超时前完成
     */
    boolean waitForAllTasks(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 移除已完成的任务（包括成功、失败、取消的任务）
     *
     * @param taskRef 任务唯一标识
     * @return 是否成功移除
     */
    boolean removeCompletedTask(String taskRef);

    /**
     * 移除所有已完成的任务
     */
    void removeAllCompletedTasks();


    default void cancelTask(String taskRef, String cancelReason) {
        ITaskExecutionState state = getTaskState(taskRef);
        if (state != null) state.cancel(cancelReason);
    }

    default void cancelAllTasks(String cancelReason) {
        for (ITaskExecutionState state : getTaskStates()) {
            state.cancel(cancelReason);
        }
    }

    /**
     * 取消指定来源的所有任务
     */
    default void cancelTasksBySource(String source, String cancelReason) {
        for (ITaskExecutionState state : getTaskStatesBySource(source)) {
            state.cancel(cancelReason);
        }
    }

}
