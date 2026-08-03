/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.ApiHeaders;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Component-level test for {@link KafkaMessageService#sendAsync}. Mocks the
 * {@link KafkaProducer} so the {@link ProducerRecord} construction and the
 * callback → {@link CompletableFuture} wiring can be verified without a live broker
 * (mirrors the Pulsar test pattern of mocking the client).
 */
class TestKafkaMessageServiceSend {

    private KafkaMessageService service;
    private KafkaProducer<String, String> producer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        producer = mock(KafkaProducer.class);
        service = new KafkaMessageService();
        service.config = new KafkaClientConfig();
        service.config.setBootstrapServers("localhost:9092");
        service.producer = producer;
        service.initialized = true;
    }

    @Test
    void sendAsyncBuildsProducerRecordWithBizKeyAndHeaders() throws Exception {
        ApiRequest<Object> message = new ApiRequest<>();
        ApiHeaders.setBizKey(message, "user-key");
        message.setData("payload");
        message.setHeader("trace-id", "abc");

        Capture<ProducerRecord<String, String>> captured = new Capture<>();
        doAnswer(inv -> {
            ProducerRecord<String, String> rec = inv.getArgument(0);
            captured.value = rec;
            return null;
        }).when(producer).send(any(), any());

        CompletionStage<Void> future = service.sendAsync("orders", message, null);

        assertNotNull(captured.value);
        assertEquals("orders", captured.value.topic());
        assertEquals("user-key", captured.value.key());
        assertEquals("payload", captured.value.value());
        Headers headers = captured.value.headers();
        assertEquals("abc", new String(headers.lastHeader("trace-id").value(),
                java.nio.charset.StandardCharsets.UTF_8));

        // Not yet completed until callback fires.
        assertTrue(!future.toCompletableFuture().isDone());
    }

    @Test
    void sendAsyncCompletesOnCallbackSuccess() throws Exception {
        doAnswer(inv -> {
            Callback cb = inv.getArgument(1);
            cb.onCompletion(null, null);
            return null;
        }).when(producer).send(any(), any());

        CompletionStage<Void> future = service.sendAsync("t", "v", null);
        future.toCompletableFuture().get(2, TimeUnit.SECONDS);
        // No exception — future completed normally.
    }

    @Test
    void sendAsyncCompletesExceptionallyOnCallbackError() {
        doAnswer(inv -> {
            Callback cb = inv.getArgument(1);
            cb.onCompletion(null, new RuntimeException("broker-down"));
            return null;
        }).when(producer).send(any(), any());

        CompletionStage<Void> future = service.sendAsync("t", "v", null);
        // The adapted NopException surfaces as CompletionException when joining.
        assertThrows(CompletionException.class, () ->
                future.toCompletableFuture().join());
    }

    @Test
    void sendAsyncFailsBeforeInitWhenNotInitialized() {
        KafkaMessageService uninit = new KafkaMessageService();
        assertThrows(NopException.class, () -> uninit.sendAsync("t", "v", null));
    }

    @Test
    void nonApiMessageIsSentAsToStringValue() throws Exception {
        Capture<ProducerRecord<String, String>> captured = new Capture<>();
        doAnswer(inv -> {
            captured.value = inv.getArgument(0);
            return null;
        }).when(producer).send(any(), any());

        service.sendAsync("t", 12345L, new MessageSendOptions());

        assertNotNull(captured.value);
        assertEquals("12345", captured.value.value());
        assertEquals(null, captured.value.key());
    }

    private static <T> void assertNotNull(T value) {
        assertTrue(value != null);
    }

    private static class Capture<T> {
        T value;
    }
}
