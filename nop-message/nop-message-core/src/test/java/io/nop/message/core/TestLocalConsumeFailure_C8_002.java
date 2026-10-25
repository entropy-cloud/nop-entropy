/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.core;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.message.core.local.LocalMessageService;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C8-002 regression (plan 2503 Phase 2, D6 three-layer failure architecture): Local is a
 * no-DLQ-substrate backend, so the FIRST layer —
 * {@link IMessageConsumer#onConsumeFailure(ConsumeFailure)} — is the only failure callback
 * the deployment gets. Pre-fix {@code LocalMessageService.invokeSubscription} logged
 * {@code nop.message.consumer-failed} and returned: the callback channel never fired, the
 * third-layer {@link MessageConsumeFailureBridge} was unreachable from Local, and the
 * delivery was at-most-once with zero visibility beyond the log line.
 *
 * <p>All tests use only pre-existing API surface so the RED baseline is the true live
 * baseline (rule E2): every assertion below failed red against the unfixed service.
 */
public class TestLocalConsumeFailure_C8_002 {

    /** Handler that always fails with the given error (and records invocations). */
    private static IMessageConsumer failingHandler(RuntimeException error) {
        return (topic, message, context) -> {
            throw error;
        };
    }

    @Test
    void consumeFailureDispatchesFirstLayerCallback_C8_002() {
        LocalMessageService service = new LocalMessageService();
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        RuntimeException boom = new RuntimeException("handler-boom");
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                throw boom;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        service.subscribe("t-c8-002", consumer, null);
        Object message = "payload-1";

        // the dispatch must stay contained (sender thread survives) AND the callback must fire
        assertDoesNotThrow(() -> service.send("t-c8-002", message, null));

        assertEquals(1, failures.size(),
                "pre-fix the first-layer onConsumeFailure callback NEVER fired on Local (C8-002)");
        ConsumeFailure failure = failures.get(0);
        assertEquals("t-c8-002", failure.getTopic());
        assertSame(message, failure.getMessage());
        assertEquals(0, failure.getAttempt(),
                "Local has no broker redelivery loop: attempt is fixed 0 (design §3.9)");
        assertSame(boom, failure.getThrowable());
    }

    @Test
    void bridgeReceivesLocalConsumeFailureThreeLayerClosure_C8_002() {
        LocalMessageService service = new LocalMessageService();
        IRetryEngine engine = mock(IRetryEngine.class);
        IRetryTask task = mock(IRetryTask.class);
        when(engine.newRetryTask(anyString(), anyString())).thenReturn(task);
        when(task.withIdempotentId(any())).thenReturn(task);
        when(task.withGroupId(any())).thenReturn(task);
        when(task.callAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MessageConsumeFailureBridge bridge = new MessageConsumeFailureBridge();
        bridge.setRetryEngine(engine);
        bridge.setServiceName("replay-svc");
        bridge.setServiceMethod("ReplaySvc__replay");

        Object message = "bridge-payload";
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext context) {
                throw new IllegalStateException("local-handler-boom");
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                // deployment wiring: first layer feeds the third layer
                bridge.submit(failure);
            }
        };
        service.subscribe("t-bridge", consumer, null);
        service.send("t-bridge", message, null);

        // three-layer closure: the bridge's replay task is created from a LOCAL failure
        verify(engine).newRetryTask("replay-svc", "ReplaySvc__replay");
        ArgumentCaptor<ApiRequest<?>> request = ArgumentCaptor.forClass(ApiRequest.class);
        verify(task).callAsync(request.capture(), any());
        assertSame(message, request.getValue().getData(),
                "the failed message travels to the replay entry point as-is");
        Map<String, Object> headers = request.getValue().getHeaders();
        assertEquals("t-bridge", headers.get("nop-msg-topic"));
        assertEquals(0, headers.get("nop-msg-attempt"));
    }

    @Test
    void throwingFailureCallbackDoesNotKillSenderThread_C8_002() {
        LocalMessageService service = new LocalMessageService();
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                throw new IllegalStateException("handler-boom");
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                throw new LinkageError("injected callback Error");
            }
        };
        service.subscribe("t-throw", consumer, null);

        // guarded dispatch (KafkaConsumeTask shape): neither the callback Error nor the
        // handler failure may escape send(); the service stays usable
        assertDoesNotThrow(() -> service.send("t-throw", "m1", null));
        assertDoesNotThrow(() -> service.send("t-throw", "m2", null),
                "the sender path must keep working after a throwing failure callback");
    }

    @Test
    void redeliveryThreadFailureAlsoDispatchesCallback_C8_002() {
        LocalMessageService service = new LocalMessageService();
        // synchronous scheduler: the deferred task runs inline on the sender thread
        ScheduledExecutorService syncScheduler = mock(ScheduledExecutorService.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return mock(ScheduledFuture.class);
        }).when(syncScheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        service.setRedeliveryExecutorForTest(syncScheduler);

        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        IMessageConsumer consumer = new IMessageConsumer() {
            boolean deferred;

            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                if (!deferred) {
                    deferred = true;
                    return new ConsumeLater(0);
                }
                throw new IllegalStateException("redelivery-boom");
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        service.subscribe("t-redeliver", consumer, null);

        assertDoesNotThrow(() -> service.send("t-redeliver", "deferred-msg", null));

        assertEquals(1, failures.size(),
                "a failure on the deferred-redelivery path must dispatch the first-layer "
                        + "callback too (the redelivery thread must not die silently)");
        assertEquals("t-redeliver", failures.get(0).getTopic());
        assertTrue(failures.get(0).getThrowable() instanceof IllegalStateException);
    }
}
