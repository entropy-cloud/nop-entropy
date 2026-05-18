package io.nop.job.coordinator.metrics;

public interface IJobDispatcherMetrics {
    void onWaitingFires(int count);

    void onDispatchConflicts(int count);

    void onFiresDispatched(int count);
}
