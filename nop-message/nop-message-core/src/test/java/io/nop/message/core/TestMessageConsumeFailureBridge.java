/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.core;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WI21 (D6 layer three): {@code MessageConsumeFailureBridge} forwards a consume failure to
 * the deployment-declared replay entry point via the nop-retry engine. It is a forwarder,
 * not a replay mechanism: no engine/replay target → WARN, zero behavior.
 */
class TestMessageConsumeFailureBridge {

    private ConsumeFailure failure() {
        ApiMessage message = mock(ApiMessage.class);
        when(message.getData()).thenReturn("payload");
        return new ConsumeFailure("orders", "orders-0@42", message, 3,
                new IllegalStateException("handler-boom"));
    }

    @Test
    void submitsReplayTaskWithFailureContext() {
        IRetryEngine engine = mock(IRetryEngine.class);
        IRetryTask task = mock(IRetryTask.class);
        when(engine.newRetryTask(anyString(), anyString())).thenReturn(task);
        when(task.withIdempotentId(any())).thenReturn(task);
        when(task.withGroupId(any())).thenReturn(task);
        when(task.callAsync(any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        MessageConsumeFailureBridge bridge = new MessageConsumeFailureBridge();
        bridge.setRetryEngine(engine);
        bridge.setServiceName("my-service");
        bridge.setServiceMethod("MySvc__replayMessage");
        bridge.submit(failure());

        verify(engine).newRetryTask("my-service", "MySvc__replayMessage");
        verify(task).withIdempotentId("orders-0@42");
        verify(task).withGroupId("orders");
        // policyId absent → engine default policy (no explicit call)
        verify(task, never()).withPolicyId(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ApiRequest<Object>> req = ArgumentCaptor.forClass(ApiRequest.class);
        verify(task).callAsync(req.capture(), any());
        // The request payload is the failed ApiMessage as-is; its data is the original body.
        assertEquals("payload", ((ApiMessage) req.getValue().getData()).getData());
        Map<String, Object> headers = req.getValue().getHeaders();
        assertNotNull(headers);
        assertEquals("orders", headers.get("nop-msg-topic"));
        assertEquals("orders-0@42", headers.get("nop-msg-location"));
        assertEquals(3, headers.get("nop-msg-attempt"));
    }

    @Test
    void explicitPolicyIdIsForwarded() {
        IRetryEngine engine = mock(IRetryEngine.class);
        IRetryTask task = mock(IRetryTask.class);
        when(engine.newRetryTask(anyString(), anyString())).thenReturn(task);
        when(task.withIdempotentId(any())).thenReturn(task);
        when(task.withGroupId(any())).thenReturn(task);
        when(task.withPolicyId(any())).thenReturn(task);
        when(task.callAsync(any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        MessageConsumeFailureBridge bridge = new MessageConsumeFailureBridge();
        bridge.setRetryEngine(engine);
        bridge.setServiceName("svc");
        bridge.setServiceMethod("m");
        bridge.setPolicyId("poison-policy");
        bridge.submit(failure());

        verify(task).withPolicyId("poison-policy");
    }

    @Test
    void missingEngineOrReplayTargetMeansZeroBehavior() {
        MessageConsumeFailureBridge noEngine = new MessageConsumeFailureBridge();
        noEngine.setServiceName("svc");
        noEngine.setServiceMethod("m");
        noEngine.submit(failure()); // WARN, no throw, no engine call possible

        IRetryEngine engine = mock(IRetryEngine.class);
        MessageConsumeFailureBridge noTarget = new MessageConsumeFailureBridge();
        noTarget.setRetryEngine(engine);
        noTarget.submit(failure()); // WARN, no throw
        verify(engine, never()).newRetryTask(anyString(), anyString());
    }

    @Test
    void submissionFailureIsLoggedNotThrown() {
        IRetryEngine engine = mock(IRetryEngine.class);
        when(engine.newRetryTask(anyString(), anyString()))
                .thenThrow(new IllegalStateException("engine-broken"));

        MessageConsumeFailureBridge bridge = new MessageConsumeFailureBridge();
        bridge.setRetryEngine(engine);
        bridge.setServiceName("svc");
        bridge.setServiceMethod("m");
        // The bridge is auxiliary: an engine outage must not break the caller.
        bridge.submit(failure());
        assertTrue(true, "submit must not throw");
    }
}
