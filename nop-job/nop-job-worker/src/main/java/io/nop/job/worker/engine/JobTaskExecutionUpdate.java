package io.nop.job.worker.engine;

import io.nop.api.core.beans.ErrorBean;

public class JobTaskExecutionUpdate {
    private final int taskStatus;
    private final ErrorBean error;
    private final Long nextScheduleTime;
    private final boolean completed;

    public JobTaskExecutionUpdate(int taskStatus, ErrorBean error, Long nextScheduleTime, boolean completed) {
        this.taskStatus = taskStatus;
        this.error = error;
        this.nextScheduleTime = nextScheduleTime;
        this.completed = completed;
    }

    public int getTaskStatus() {
        return taskStatus;
    }

    public ErrorBean getError() {
        return error;
    }

    public Long getNextScheduleTime() {
        return nextScheduleTime;
    }

    public boolean isCompleted() {
        return completed;
    }
}
