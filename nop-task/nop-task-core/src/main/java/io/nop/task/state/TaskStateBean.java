package io.nop.task.state;

import io.nop.task.ITaskState;
import io.nop.task.TaskStepReturn;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskStateBean extends AbstractTaskStateCommon implements ITaskState {
    private String jobInstanceId;
    private String taskName;
    private Long taskVersion;
    private String taskInstanceId;
    private Integer taskStatus;
    private String description;

    private Map<String, Object> requestHeaders;
    private Object request;
    private Map<String, Object> responseHeaders;

    private Object response;

    private Map<String, Object> taskVars;

    private int nextRunId;

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getJobInstanceId() {
        return jobInstanceId;
    }

    @Override
    public void setJobInstanceId(String jobInstanceId) {
        this.jobInstanceId = jobInstanceId;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public Long getTaskVersion() {
        return taskVersion;
    }

    @Override
    public void setTaskVersion(Long taskVersion) {
        this.taskVersion = taskVersion;
    }

    @Override
    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    @Override
    public void setTaskInstanceId(String taskInstanceId) {
        this.taskInstanceId = taskInstanceId;
    }

    @Override
    public synchronized int newRunId() {
        return nextRunId++;
    }

    @Override
    public Integer getTaskStatus() {
        return taskStatus;
    }

    @Override
    public void setTaskStatus(Integer taskStatus) {
        this.taskStatus = taskStatus;
    }

    @Override
    public Object getRequest() {
        return request;
    }

    @Override
    public void setRequest(Object request) {
        this.request = request;
    }

    @Override
    public Object getResponse() {
        return response;
    }

    @Override
    public void setResponse(Object response) {
        this.response = response;
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public void setRequestHeaders(Map<String, Object> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    @Override
    public Map<String, Object> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public void setResponseHeaders(Map<String, Object> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    @Override
    public Map<String, Object> getTaskVars() {
        if (taskVars == null)
            taskVars = new LinkedHashMap<>();
        return taskVars;
    }

    @Override
    public void setTaskVar(String name, Object value) {
        getTaskVars().put(name, value);
    }

    @Override
    public void result(TaskStepReturn result) {

    }
}
