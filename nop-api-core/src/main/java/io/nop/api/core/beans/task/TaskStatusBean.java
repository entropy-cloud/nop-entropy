/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.task;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ErrorBean;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class TaskStatusBean {
    private String taskName;
    private String taskId;
    private int status;

    private String stateId;

    private ErrorBean error;
    private Map<String, Object> details;

    @PropMeta(propId = 1)
    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @JsonIgnore
    public boolean isCompleted() {
        return false;
    }

    @PropMeta(propId = 2)
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @PropMeta(propId = 3)
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @PropMeta(propId = 4)
    public String getStateId() {
        return stateId;
    }

    public void setStateId(String stateId) {
        this.stateId = stateId;
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
