package io.nop.core.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class TaskQueueStats {
    private final int currentTaskCount;
    private final int runningTaskCount;
    private final int pendingTaskCount;
    private final long completedTaskCount;
    private final long failedTaskCount;
    private final long cancelledTaskCount;
    private final long totalTaskCount;

    public TaskQueueStats(@JsonProperty("currentTaskCount") int currentTaskCount,
                          @JsonProperty("runningTaskCount") int runningTaskCount,
                          @JsonProperty("pendingTaskCount") int pendingTaskCount,
                          @JsonProperty("completedTaskCount") long completedTaskCount,
                          @JsonProperty("failedTaskCount") long failedTaskCount,
                          @JsonProperty("cancelledTaskCount") long cancelledTaskCount,
                          @JsonProperty("totalTaskCount") long totalTaskCount) {
        this.currentTaskCount = currentTaskCount;
        this.runningTaskCount = runningTaskCount;
        this.pendingTaskCount = pendingTaskCount;
        this.completedTaskCount = completedTaskCount;
        this.failedTaskCount = failedTaskCount;
        this.cancelledTaskCount = cancelledTaskCount;
        this.totalTaskCount = totalTaskCount;
    }

    public int getCurrentTaskCount() {
        return currentTaskCount;
    }

    public int getRunningTaskCount() {
        return runningTaskCount;
    }

    public int getPendingTaskCount() {
        return pendingTaskCount;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public long getFailedTaskCount() {
        return failedTaskCount;
    }

    public long getCancelledTaskCount() {
        return cancelledTaskCount;
    }

    public long getTotalTaskCount() {
        return totalTaskCount;
    }
}