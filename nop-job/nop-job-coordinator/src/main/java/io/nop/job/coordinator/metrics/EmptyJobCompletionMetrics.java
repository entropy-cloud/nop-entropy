package io.nop.job.coordinator.metrics;

public class EmptyJobCompletionMetrics implements IJobCompletionMetrics {
    @Override
    public void onFiresCompleted(int count) {
    }

    @Override
    public void onFireSuccess(long durationMs) {
    }

    @Override
    public void onFireFailure(long durationMs) {
    }

    @Override
    public void onFireTimeout(long durationMs) {
    }
}
