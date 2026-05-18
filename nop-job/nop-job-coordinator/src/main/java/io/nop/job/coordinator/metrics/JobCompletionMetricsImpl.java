package io.nop.job.coordinator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nop.commons.metrics.GlobalMeterRegistry;

import java.time.Duration;

public class JobCompletionMetricsImpl implements IJobCompletionMetrics {
    private final Counter firesCompletedCounter;
    private final Timer fireSuccessTimer;
    private final Counter fireFailureCounter;
    private final Counter fireTimeoutCounter;

    public JobCompletionMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public JobCompletionMetricsImpl(MeterRegistry registry) {
        this.firesCompletedCounter = registry.counter("nop.job.completion.fires-completed");
        this.fireSuccessTimer = registry.timer("nop.job.completion.fire-success");
        this.fireFailureCounter = registry.counter("nop.job.completion.fire-failure");
        this.fireTimeoutCounter = registry.counter("nop.job.completion.fire-timeout");
    }

    @Override
    public void onFiresCompleted(int count) {
        firesCompletedCounter.increment(count);
    }

    @Override
    public void onFireSuccess(long durationMs) {
        fireSuccessTimer.record(Duration.ofMillis(durationMs));
    }

    @Override
    public void onFireFailure(long durationMs) {
        fireFailureCounter.increment();
    }

    @Override
    public void onFireTimeout(long durationMs) {
        fireTimeoutCounter.increment();
    }
}
