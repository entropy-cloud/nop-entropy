package io.nop.job.worker.metrics;

public interface IJobWorkerMetrics {
    void onTasksClaimed(int count);

    void onTaskSuccess(long durationMs);

    void onTaskFailure(long durationMs);

    void onTaskTimeout(long durationMs);

    void onRejected(int runningCount);
}
