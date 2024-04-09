/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ErrorBean;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class TaskStatusBean {
    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILURE = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_TIMEOUT = 5;
    public static final int STATUS_NOT_FOUND = 6;

    private String taskName;
    private String taskId;
    private int taskStatus;
    private String taskState;

    private ErrorBean error;
    private Map<String, Object> details;

    @PropMeta(propId = 1)
    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @PropMeta(propId = 2, mandatory = true)
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @JsonIgnore
    public boolean isCompleted() {
        return taskStatus > STATUS_RUNNING;
    }

    @PropMeta(propId = 3, mandatory = true)
    public int getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(int taskStatus) {
        this.taskStatus = taskStatus;
    }

    @PropMeta(propId = 4)
    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    @PropMeta(propId = 5)
    public ErrorBean getError() {
        return error;
    }

    public void setError(ErrorBean error) {
        this.error = error;
    }

    @PropMeta(propId = 6)
    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetail(String name, Object value) {
        if (details == null)
            details = new LinkedHashMap<>();
        details.put(name, value);
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
