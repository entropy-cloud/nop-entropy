package io.nop.job.coordinator.metrics;

public interface IJobCompletionMetrics {
    void onFiresCompleted(int count);

    void onFireSuccess(long durationMs);

    void onFireFailure(long durationMs);

    void onFireTimeout(long durationMs);
}
