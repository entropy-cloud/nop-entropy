package io.nop.job.retry.adapter;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.retry.IJobRetryBridge;
import io.nop.job.api.retry.JobFireFailedEvent;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class NopRetryJobRetryBridge implements IJobRetryBridge {

    static final Logger LOG = LoggerFactory.getLogger(NopRetryJobRetryBridge.class);

    static final String SERVICE_NAME = "NopJobService";
    static final String SERVICE_METHOD = "fireJob";

    private IRetryEngine retryEngine;

    @Inject
    public void setRetryEngine(IRetryEngine retryEngine) {
        this.retryEngine = retryEngine;
    }

    @Override
    public String onFireFailed(JobFireFailedEvent event) {
        if (retryEngine == null) {
            LOG.warn("nop.job.retry.engine-not-available:fireId={}", event.getJobFireId());
            return null;
        }

        try {
            IRetryTask task = retryEngine.newRetryTask(SERVICE_NAME, SERVICE_METHOD)
                    .withPolicyId(event.getRetryPolicyId())
                    .withIdempotentId(event.getJobFireId())
                    .withNamespaceId(event.getNamespaceId())
                    .withGroupId(event.getGroupId());

            Map<String, Object> data = new HashMap<>();
            data.put("jobFireId", event.getJobFireId());
            data.put("jobScheduleId", event.getJobScheduleId());
            data.put("jobName", event.getJobName());
            data.put("executorKind", event.getExecutorKind());
            data.put("errorCode", event.getErrorCode());
            data.put("errorMessage", event.getErrorMessage());

            ApiRequest<Map<String, Object>> request = new ApiRequest<>();
            request.setData(data);

            task.callAsync(request, null)
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            LOG.error("nop.job.retry.submit-failed:fireId={}", event.getJobFireId(), err);
                        } else if (resp != null && !resp.isOk()) {
                            LOG.warn("nop.job.retry.submit-error:fireId={},code={}", event.getJobFireId(),
                                    resp.getCode());
                        }
                    });

            return null;
        } catch (Exception e) {
            LOG.error("nop.job.retry.bridge-error:fireId={}", event.getJobFireId(), e);
            return null;
        }
    }
}
