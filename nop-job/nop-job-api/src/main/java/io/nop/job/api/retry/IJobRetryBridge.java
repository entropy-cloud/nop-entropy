package io.nop.job.api.retry;

public interface IJobRetryBridge {
    String onFireFailed(JobFireFailedEvent event);
}
