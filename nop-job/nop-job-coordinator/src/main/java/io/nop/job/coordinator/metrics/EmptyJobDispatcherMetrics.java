package io.nop.job.coordinator.metrics;

public class EmptyJobDispatcherMetrics implements IJobDispatcherMetrics {
    @Override
    public void onWaitingFires(int count) {
    }

    @Override
    public void onDispatchConflicts(int count) {
    }

    @Override
    public void onFiresDispatched(int count) {
    }
}
