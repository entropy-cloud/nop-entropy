package io.nop.job.api.retry;

public interface IJobRetryBridge {
    void onFireFailed(JobFireFailedEvent event);
}
