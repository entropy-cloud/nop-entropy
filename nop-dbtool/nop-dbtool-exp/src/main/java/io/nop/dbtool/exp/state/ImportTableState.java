package io.nop.dbtool.exp.state;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ImportTableState {
    private boolean completed;
    private long completedIndex;
    private long skipCount;
    private long completedCount;
    private long processedCount;
    private double speed;

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public long getCompletedIndex() {
        return completedIndex;
    }

    public void setCompletedIndex(long completedIndex) {
        this.completedIndex = completedIndex;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(long processedCount) {
        this.processedCount = processedCount;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(long skipCount) {
        this.skipCount = skipCount;
    }
}
