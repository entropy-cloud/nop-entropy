package io.nop.job.coordinator.retry;

import io.nop.job.api.retry.IJobRetryBridge;
import io.nop.job.api.retry.JobFireFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpJobRetryBridge implements IJobRetryBridge {
    static final Logger LOG = LoggerFactory.getLogger(NoOpJobRetryBridge.class);

    @Override
    public String onFireFailed(JobFireFailedEvent event) {
        LOG.debug("nop.job.retry.noop:fireId={},policyId={}", event.getJobFireId(), event.getRetryPolicyId());
        return null;
    }
}
