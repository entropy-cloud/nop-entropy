package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-01（plan 2558 裁定 A）carrier（Pulsar）：bounded {@code consumeAwaitTimeoutMs}
 * on the single/batch consume await points.
 *
 * <ul>
 * <li>Never-completing handler future → dedicated timeout branch: nack + WARN,
 * attempt=0 semantics, never the maxConsumeAttempts/poison budget.</li>
 * <li>Non-CompletableFuture stage → typed {@code ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC}
 * (never a silent unbounded wait).</li>
 * <li>The StackOverflowError loop-barrier (isActive misreport) face is the 2496
 * regression pin family — covered by {@code TestPulsarConsumeTaskErrorBarrier}.</li>
 * </ul>
 */
class TestConsumeAwaitTimeout {

    @SuppressWarnings("unchecked")
    private Consumer<Object> consumer() {
        return mock(Consumer.class);
    }

    private PulsarConsumeTask task(PulsarMessageService service, Consumer<Object> consumer,
                                   IMessageConsumer handler, long awaitTimeoutMs) {
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-await", handler,
                new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service,
                Executors.newSingleThreadExecutor(), consumer, config);
        task.setConsumeAwaitTimeoutMs(awaitTimeoutMs);
        return task;
    }

    @Test
    void neverCompletingFutureTimesOutAndNacksWithoutPoisonBudget() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        Consumer<Object> consumer = consumer();

        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-await");
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(consumer.receive(anyInt(), any())).thenReturn(message);

        IMessageConsumer handler = (topic, msg, ctx) -> new CompletableFuture<>(); // never completes
        PulsarConsumeTask task = task(service, consumer, handler, 120L);

        // Dedicated timeout branch: returns true (message consumed via nack), no throw.
        boolean dispatched = task.consume();

        assertTrue(dispatched);
        verify(consumer).negativeAcknowledge(message.getMessageId());
        // attempt=0 semantics: the poison budget was never consulted.
        verify(service, org.mockito.Mockito.never()).maxConsumeAttempts();
    }

    @Test
    void batchNeverCompletingFutureTimesOutAndNacksBoundaryAndTail() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        Consumer<Object> consumer = consumer();

        org.apache.pulsar.client.api.Messages<Object> messages =
                mock(org.apache.pulsar.client.api.Messages.class);
        List<org.apache.pulsar.client.api.Message<Object>> msgs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            org.apache.pulsar.client.api.Message<Object> m =
                    mock(org.apache.pulsar.client.api.Message.class);
            when(m.getTopicName()).thenReturn("persistent://public/default/t-await");
            when(m.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
            msgs.add(m);
        }
        when(messages.iterator()).thenAnswer(inv -> msgs.iterator()); // fresh per call — batchConsume iterates twice
        when(messages.size()).thenReturn(3);
        when(consumer.batchReceiveAsync()).thenReturn(
                CompletableFuture.completedFuture(messages));

        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                responses.add(null);
                responses.add(new CompletableFuture<>()); // record 1 never completes
                return responses;
            }
        };
        PulsarConsumeTask task = task(service, consumer, handler, 120L);

        boolean dispatched = task.batchConsume();

        // Boundary (the timed-out message 1) and the tail (message 2) nack; message 0
        // was already dispatched (and replied) — dispatched reflects that earlier record.
        assertTrue(dispatched);
        verify(consumer).negativeAcknowledge(msgs.get(1).getMessageId());
        verify(consumer).negativeAcknowledge(msgs.get(2).getMessageId());
    }

    @Test
    void nonCompletableFutureStageIsTypedRejected() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        Consumer<Object> consumer = consumer();

        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-await");
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(consumer.receive(anyInt(), any()))
                .thenReturn(message);

        IMessageConsumer handler = (topic, msg, ctx) -> new CompletableFuture<Void>() {
            @Override
            public CompletableFuture<Void> toCompletableFuture() {
                throw new UnsupportedOperationException("not a CompletableFuture");
            }
        };
        PulsarConsumeTask task = task(service, consumer, handler, 120L);

        NopException e = assertThrows(NopException.class, () -> task.consume());
        assertEquals(PulsarErrors.ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC.getErrorCode(),
                e.getErrorCode());
    }

    @Test
    void completingFutureStillResolvesAndAcks() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        Consumer<Object> consumer = consumer();

        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-await");
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(consumer.receive(anyInt(), any()))
                .thenReturn(message);
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        IMessageConsumer handler = (topic, msg, ctx) ->
                CompletableFuture.completedFuture("done");
        PulsarConsumeTask task = task(service, consumer, handler, 120L);

        assertTrue(task.consume());
        verify(consumer).acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any());
    }
}
