package io.nop.batch.exp.state;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.time.CoreMetrics;

@DataBean
public class EtlTableState {
    private boolean completed;
    private long completedIndex;
    private long skipCount;
    private long completedCount;
    private long historyCount;
    private long processedCount;
    private long errorCount;
    private double speed;

    private long totalRunTime;
    private long lastSaveTime;
    private long lastRunTime;
    private long lastCompleteCount;

    public void save(long count) {
        long now = CoreMetrics.currentTimeMillis();
        if (lastSaveTime <= 0)
            lastSaveTime = now;

        long diff = now - lastSaveTime;
        if (diff > 0) {
            speed = count / (diff / 1000.0);
            totalRunTime += diff;
        } else {
            speed = -1;
        }
        lastCompleteCount = count;
        lastRunTime = diff;
        lastSaveTime = now;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getLastCompleteCount() {
        return lastCompleteCount;
    }

    public void setLastCompleteCount(long lastCompleteCount) {
        this.lastCompleteCount = lastCompleteCount;
    }

    public long getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(long lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

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

    public long getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(long historyCount) {
        this.historyCount = historyCount;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(long skipCount) {
        this.skipCount = skipCount;
    }

    public long getTotalRunTime() {
        return totalRunTime;
    }

    public void setTotalRunTime(long totalRunTime) {
        this.totalRunTime = totalRunTime;
    }

    public long getLastSaveTime() {
        return lastSaveTime;
    }

    public void setLastSaveTime(long lastSaveTime) {
        this.lastSaveTime = lastSaveTime;
    }
}
