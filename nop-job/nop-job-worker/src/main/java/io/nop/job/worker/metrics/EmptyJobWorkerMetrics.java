package io.nop.job.worker.metrics;

public class EmptyJobWorkerMetrics implements IJobWorkerMetrics {
    @Override
    public void onTasksClaimed(int count) {
    }

    @Override
    public void onTaskSuccess(long durationMs) {
    }

    @Override
    public void onTaskFailure(long durationMs) {
    }

    @Override
    public void onTaskTimeout(long durationMs) {
    }

    @Override
    public void onRejected(int runningCount) {
    }
}
