package io.nop.task;

public class TaskStepStateId {
    private final String taskInstanceId;
    private final String stepId;
    private final int runId;

    public TaskStepStateId(String taskInstanceId, String stepId, int runId) {
        this.taskInstanceId = taskInstanceId;
        this.stepId = stepId;
        this.runId = runId;
    }

    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    public String getStepId() {
        return stepId;
    }

    public int getRunId() {
        return runId;
    }

    public int hashCode() {
        int h = taskInstanceId.hashCode();
        h = h * 37 + stepId.hashCode();
        return h * 37 + runId;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof TaskStepStateId))
            return false;

        TaskStepStateId other = (TaskStepStateId) o;
        return stepId.equals(other.stepId) && runId == other.runId;
    }
}
