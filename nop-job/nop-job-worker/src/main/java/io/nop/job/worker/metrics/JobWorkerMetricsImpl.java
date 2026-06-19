package io.nop.job.worker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nop.commons.metrics.GlobalMeterRegistry;

import java.time.Duration;

public class JobWorkerMetricsImpl implements IJobWorkerMetrics {
    private final Counter tasksClaimedCounter;
    private final Timer taskSuccessTimer;
    private final Counter taskFailureCounter;
    private final Counter taskTimeoutCounter;
    private final Counter rejectedCounter;
    private final Counter taskExecuteFailedCounter;

    public JobWorkerMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public JobWorkerMetricsImpl(MeterRegistry registry) {
        this.tasksClaimedCounter = registry.counter("nop.job.worker.tasks-claimed");
        this.taskSuccessTimer = registry.timer("nop.job.worker.task-success");
        this.taskFailureCounter = registry.counter("nop.job.worker.task-failure");
        this.taskTimeoutCounter = registry.counter("nop.job.worker.task-timeout");
        this.rejectedCounter = registry.counter("nop.job.worker.task-rejected");
        this.taskExecuteFailedCounter = registry.counter("nop.job.worker.task-execute-failed");
    }

    @Override
    public void onTasksClaimed(int count) {
        tasksClaimedCounter.increment(count);
    }

    @Override
    public void onTaskSuccess(long durationMs) {
        taskSuccessTimer.record(Duration.ofMillis(durationMs));
    }

    @Override
    public void onTaskFailure(long durationMs) {
        taskFailureCounter.increment();
    }

    @Override
    public void onTaskTimeout(long durationMs) {
        taskTimeoutCounter.increment();
    }

    @Override
    public void onRejected(int runningCount) {
        rejectedCounter.increment();
    }

    @Override
    public void onTaskExecuteFailed(int count) {
        taskExecuteFailedCounter.increment(count);
    }
}
