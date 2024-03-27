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