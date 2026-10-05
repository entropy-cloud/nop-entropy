/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.core;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * WI21 (D6 layer three): forwards consume failures into the nop-retry engine. Shape-matched
 * to the {@code NopRetryJobRetryBridge} precedent: the bridge is a FORWARDER from a
 * subsystem failure event to a deployment-declared replay entry point — it is NOT a message
 * replay mechanism, and it owns no retry policy (policy selection is nop-retry's).
 *
 * <p>Contract: both the {@link IRetryEngine} and the replay target (serviceName +
 * serviceMethod of a service able to re-process a message payload) are injected. If either
 * is missing, {@link #submit(ConsumeFailure)} logs a one-time-style WARN and does nothing —
 * a deployment that declares no replay target gets zero layer-three behavior.
 *
 * <p>Payload mapping: {@code ApiRequest.data} carries the failed {@link ConsumeFailure#getMessage()}
 * (the ApiMessage as-is); topic / location / attempt are carried as request headers;
 * {@code idempotentId} is the consume location, {@code groupId} the topic. {@code policyId}
 * is only set when the deployment provides one explicitly (otherwise the engine's default
 * policy applies).
 */
public class MessageConsumeFailureBridge {
    static final Logger LOG = LoggerFactory.getLogger(MessageConsumeFailureBridge.class);

    private IRetryEngine retryEngine;
    private String serviceName;
    private String serviceMethod;
    private String policyId;
    private String namespaceId;

    public void setRetryEngine(IRetryEngine retryEngine) {
        this.retryEngine = retryEngine;
    }

    /** Service able to re-process a message payload (deployment-declared replay target). */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    /** Optional: explicit nop-retry policy; absent = engine default. */
    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * Forwards a consume failure to the configured replay target via the retry engine.
     * Never throws — submission failures are logged (the failure itself was already proven
     * by the consume loop's WARN; this bridge is an auxiliary operations channel).
     */
    public void submit(ConsumeFailure failure) {
        if (retryEngine == null || serviceName == null || serviceMethod == null) {
            LOG.warn("nop.err.message.bridge.not-configured:retryEngine={},serviceName={},serviceMethod={}"
                            + " - layer-three DLQ forwarding disabled",
                    retryEngine != null, serviceName, serviceMethod);
            return;
        }

        try {
            IRetryTask task = retryEngine.newRetryTask(serviceName, serviceMethod)
                    .withIdempotentId(failure.getLocation())
                    .withGroupId(failure.getTopic());
            if (policyId != null) {
                task = task.withPolicyId(policyId);
            }
            if (namespaceId != null) {
                task = task.withNamespaceId(namespaceId);
            }

            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("nop-msg-topic", failure.getTopic());
            headers.put("nop-msg-location", failure.getLocation());
            headers.put("nop-msg-attempt", failure.getAttempt());

            ApiRequest<Object> request = new ApiRequest<>();
            request.setData(failure.getMessage());
            request.setHeaders(headers);

            task.callAsync(request, null).whenComplete((resp, err) -> {
                if (err != null) {
                    LOG.error("nop.err.message.bridge.submit-failed:location={}",
                            failure.getLocation(), err);
                } else if (resp != null && !resp.isOk()) {
                    LOG.warn("nop.err.message.bridge.submit-error:location={},code={}",
                            failure.getLocation(), resp.getCode());
                }
            });
        } catch (Exception e) {
            LOG.error("nop.err.message.bridge.error:location={}", failure.getLocation(), e);
        }
    }
}
