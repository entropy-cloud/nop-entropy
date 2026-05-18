package io.nop.job.coordinator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.commons.metrics.GlobalMeterRegistry;

public class JobDispatcherMetricsImpl implements IJobDispatcherMetrics {
    private final Counter waitingFiresCounter;
    private final Counter dispatchConflictCounter;
    private final Counter firesDispatchedCounter;

    public JobDispatcherMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public JobDispatcherMetricsImpl(MeterRegistry registry) {
        this.waitingFiresCounter = registry.counter("nop.job.dispatcher.waiting-fires");
        this.dispatchConflictCounter = registry.counter("nop.job.dispatcher.dispatch-conflict");
        this.firesDispatchedCounter = registry.counter("nop.job.dispatcher.fires-dispatched");
    }

    @Override
    public void onWaitingFires(int count) {
        waitingFiresCounter.increment(count);
    }

    @Override
    public void onDispatchConflicts(int count) {
        dispatchConflictCounter.increment(count);
    }

    @Override
    public void onFiresDispatched(int count) {
        firesDispatchedCounter.increment(count);
    }
}
