/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import java.util.Map;

public interface ITaskState extends ITaskStateCommon {

    /**
     * Task实例是否已进入终态（COMPLETED/KILLED/FAILED/TIMEOUT）。
     * <p>
     * 镜像 {@link ITaskStepState#isDone()} 的 task 级终态判定，使 task envelope 能表达
     * 「已终态」语义。resume 路径据此短路（设计裁定 3）：终态 task 不重跑 mainStep。
     */
    default boolean isTerminal() {
        Integer status = getTaskStatus();
        if (status == null)
            return false;
        return status == TaskConstants.TASK_STATUS_COMPLETED
                || status == TaskConstants.TASK_STATUS_KILLED
                || status == TaskConstants.TASK_STATUS_FAILED
                || status == TaskConstants.TASK_STATUS_TIMEOUT;
    }

    /**
     * Task实例是否成功终态（COMPLETED）。镜像 {@link ITaskStepState#isSuccess()}。
     */
    default boolean isSuccess() {
        return Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED).equals(getTaskStatus());
    }

    /**
     * 终态失败时捕获的异常。resume 路径据此重抛（非静默跳过，设计裁定 3/4）。
     * 镜像 {@link ITaskStepState#exception()} 的 task 级异常访问器。
     */
    Throwable exception();

    void exception(Throwable exp);

    /**
     * 一个JobInstance可能包含多个TaskInstance
     *
     * @return
     */
    String getJobInstanceId();

    void setJobInstanceId(String jobInstanceId);

    /**
     * 可以根据taskName和taskVersion来唯一确定Task模型的定义
     *
     * @return
     */
    String getTaskName();

    void setTaskName(String taskName);

    Long getTaskVersion();

    void setTaskVersion(Long taskVersion);


    String getDescription();

    void setDescription(String description);

    /**
     * Task的每次执行对应一个TaskInstance
     *
     * @return
     */
    String getTaskInstanceId();

    void setTaskInstanceId(String taskInstanceId);

    int newRunId();

    /**
     * TaskInstance的当前执行状态，参见TaskConstants中的状态常量定义
     *
     * @return
     */
    Integer getTaskStatus();

    void setTaskStatus(Integer taskStatus);

    Object getRequest();

    void setRequest(Object request);

    Object getResponse();

    void setResponse(Object response);

    Map<String, Object> getRequestHeaders();

    void setRequestHeaders(Map<String, Object> requestHeaders);

    Map<String, Object> getResponseHeaders();

    void setResponseHeaders(Map<String, Object> responseHeaders);

    Map<String, Object> getTaskVars();

    void setTaskVar(String name, Object value);

    /**
     * task 级状态从持久化存储中恢复后调用（对称 step 级 {@link ITaskStepState#afterLoad(ITaskRuntime)}）。
     * <p>
     * {@link io.nop.task.dao.store.DaoTaskStateStore#loadTaskState} 在 {@code toTaskStateBean} 完成
     * 既有内联 reconstruction 之后、返回之前调用本 hook。默认 no-op 作为扩展点基类，custom 子类可 override
     * 重建 transient 字段、校验状态一致性等。镜像 step 级 hook 接线语义，闭合 task 级持久化生命周期缺口。
     */
    default void afterLoad(ITaskRuntime taskRt) {
    }

    /**
     * task 级状态持久化到存储之前调用（对称 step 级 {@link ITaskStepState#beforeSave(ITaskRuntime)}）。
     * <p>
     * {@link io.nop.task.dao.store.DaoTaskStateStore#saveTaskState} 在既有状态拷贝到 entity 之前调用本 hook。
     * 默认 no-op 作为扩展点基类，custom 子类可 override 参与归一化、清理性写入等。
     */
    default void beforeSave(ITaskRuntime taskRt) {
    }
}