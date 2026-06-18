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
}