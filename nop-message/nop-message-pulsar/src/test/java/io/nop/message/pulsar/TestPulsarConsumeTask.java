package io.nop.message.pulsar;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestPulsarConsumeTask {

    @Test
    void testStart_setsActive() {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("test-topic", msgConsumer, options);

        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        assertFalse(task.isActive());

        task.start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(task.isActive());

        task.stop();
        executor.shutdown();
    }

    @Test
    void testStop_deactivates() {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("test-topic", msgConsumer, options);

        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        assertFalse(task.isActive());

        task.start();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(task.isActive());

        task.stop();
        assertFalse(task.isActive());

        executor.shutdown();
    }

    @Test
    void batchPartialCommitNacksBoundaryRecordAndRemainder() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Messages<Object> messages = mock(org.apache.pulsar.client.api.Messages.class);
        org.apache.pulsar.client.api.Message<Object> m0 = mock(org.apache.pulsar.client.api.Message.class);
        org.apache.pulsar.client.api.Message<Object> m1 = mock(org.apache.pulsar.client.api.Message.class);
        org.apache.pulsar.client.api.Message<Object> m2 = mock(org.apache.pulsar.client.api.Message.class);
        org.apache.pulsar.client.api.Message<Object> m3 = mock(org.apache.pulsar.client.api.Message.class);

        when(messages.iterator()).thenAnswer(inv -> java.util.Arrays.asList(m0, m1, m2, m3).iterator());
        when(messages.size()).thenReturn(4);
        when(m0.getTopicName()).thenReturn("persistent://public/default/t-partial");
        when(m1.getTopicName()).thenReturn("persistent://public/default/t-partial");
        when(m2.getTopicName()).thenReturn("persistent://public/default/t-partial");
        when(m3.getTopicName()).thenReturn("persistent://public/default/t-partial");
        org.apache.pulsar.client.api.MessageId id0 = mock(org.apache.pulsar.client.api.MessageId.class);
        org.apache.pulsar.client.api.MessageId id1 = mock(org.apache.pulsar.client.api.MessageId.class);
        org.apache.pulsar.client.api.MessageId id2 = mock(org.apache.pulsar.client.api.MessageId.class);
        org.apache.pulsar.client.api.MessageId id3 = mock(org.apache.pulsar.client.api.MessageId.class);
        when(m0.getMessageId()).thenReturn(id0);
        when(m1.getMessageId()).thenReturn(id1);
        when(m2.getMessageId()).thenReturn(id2);
        when(m3.getMessageId()).thenReturn(id3);
        when(consumer.batchReceive()).thenReturn(messages, (org.apache.pulsar.client.api.Messages<Object>) null);
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(service.getAckTopic(org.mockito.ArgumentMatchers.anyString())).thenReturn("ack-t-partial");

        // Records 0/1 complete; record 2 carries PartialCommit(2): ack [0..1], nack [2..3].
        IMessageConsumer batchConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public java.util.List<Object> onMessageBatch(java.util.List<io.nop.api.core.message.TopicMessage> msgList,
                                                         io.nop.api.core.message.IMessageConsumeContext ctx) {
                java.util.List<Object> responses = new java.util.ArrayList<>();
                responses.add(null);
                responses.add(null);
                responses.add(new io.nop.api.core.message.PartialCommit(2));
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-partial", batchConsumer, options);

        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.batchConsume();

        // The boundary record itself must be negatively acknowledged (closure audit Major-1).
        verify(consumer).negativeAcknowledge(m2.getMessageId());
        verify(consumer).negativeAcknowledge(m3.getMessageId());
        verify(consumer, never()).negativeAcknowledge(m0.getMessageId());
        verify(consumer, never()).negativeAcknowledge(m1.getMessageId());
        executor.shutdown();
    }

    // ------------------------------------------------------------------
    // WI8/D3: seek resolution on the subscribe path (service-level, via the
    // newConsumerBuilder seam — no real broker, no Mockito mock of the service).
    // ------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class TestPulsarService extends PulsarMessageService {
        org.apache.pulsar.client.api.ConsumerBuilder builder;
        org.apache.pulsar.client.api.Consumer<Object> consumer;
        io.nop.api.core.message.IMessageSubscription subscription;
        org.apache.pulsar.client.api.Producer<?> producerForTest;
        final java.util.List<String> producedTopics = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        Producer<?> getProducer(String topic) {
            if (producerForTest != null) {
                return producerForTest;
            }
            return super.getProducer(topic);
        }

        @Override
        protected org.apache.pulsar.client.api.ConsumerBuilder newConsumerBuilder(
                org.apache.pulsar.client.api.Schema<?> schema) {
            return builder;
        }

        org.apache.pulsar.client.api.TypedMessageBuilder<Object> typedBuilder;

        @Override
        org.apache.pulsar.client.api.Producer<?> buildProducer(String topic,
                                                               org.apache.pulsar.client.api.Schema<?> schema) {
            producedTopics.add(topic);
            org.apache.pulsar.client.api.Producer producer = mock(org.apache.pulsar.client.api.Producer.class);
            when(producer.newMessage()).thenReturn((org.apache.pulsar.client.api.TypedMessageBuilder) typedBuilder);
            return producer;
        }
    }

    private TestPulsarService preparedService(MessageSubscribeOptions options) throws Exception {
        return preparedService(options, new io.nop.api.core.message.MessageSubscriptionConfig("t-seek",
                (t, m, c) -> null, options));
    }

    private TestPulsarService preparedService(MessageSubscribeOptions options,
                                              io.nop.api.core.message.MessageSubscriptionConfig config) throws Exception {
        TestPulsarService svc = new TestPulsarService();
        svc.builder = mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        svc.consumer = mock(org.apache.pulsar.client.api.Consumer.class);
        when(svc.builder.subscribe()).thenReturn(svc.consumer);
        when(svc.builder.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        when(svc.builder.subscriptionName(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        when(svc.builder.subscriptionType(org.mockito.ArgumentMatchers.any())).thenReturn(svc.builder);
        when(svc.builder.subscriptionInitialPosition(org.mockito.ArgumentMatchers.any())).thenReturn(svc.builder);
        // receive() must block rather than spin: tests cancel the subscription (which stops
        // the task) right after their assertions.
        when(svc.consumer.receive()).thenAnswer(inv -> {
            new java.util.concurrent.CountDownLatch(1).await();
            return null;
        });
        svc.subscription = svc.doSubscribe(config);
        return svc;
    }

    @Test
    void initSeekToBeginUsesSubscriptionInitialPositionEarliest() throws Exception {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-init-begin");
        options.setSeekMode(io.nop.api.core.message.SeekMode.INIT_SEEK_TO_BEGIN);
        TestPulsarService svc = preparedService(options);
        try {
            verify(svc.builder).subscriptionInitialPosition(
                    org.apache.pulsar.client.api.SubscriptionInitialPosition.Earliest);
            verify(svc.consumer, never()).seek(org.apache.pulsar.client.api.MessageId.earliest);
        } finally {
            svc.subscription.cancel();
        }
    }

    @Test
    void initSeekToEndUsesSubscriptionInitialPositionLatest() throws Exception {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-init-end");
        options.setSeekMode(io.nop.api.core.message.SeekMode.INIT_SEEK_TO_END);
        TestPulsarService svc = preparedService(options);
        try {
            verify(svc.builder).subscriptionInitialPosition(
                    org.apache.pulsar.client.api.SubscriptionInitialPosition.Latest);
        } finally {
            svc.subscription.cancel();
        }
    }

    @Test
    void alwaysSeekToEndSeeksSynchronouslyAfterSubscribe() throws Exception {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-always-end");
        options.setSeekMode(io.nop.api.core.message.SeekMode.ALWAYS_SEEK_TO_END);
        TestPulsarService svc = preparedService(options);
        try {
            verify(svc.consumer).seek(org.apache.pulsar.client.api.MessageId.latest);
            verify(svc.builder, never()).subscriptionInitialPosition(org.mockito.ArgumentMatchers.any());
        } finally {
            svc.subscription.cancel();
        }
    }

    @Test
    void seekToTimeSeeksSynchronouslyAfterSubscribe() throws Exception {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-time");
        options.setSeekToTime(1234567890L);
        TestPulsarService svc = preparedService(options);
        try {
            verify(svc.consumer).seek(1234567890L);
        } finally {
            svc.subscription.cancel();
        }
    }

    @Test
    void seekToMessageRejectedSynchronouslyBeforeClientUse() {
        PulsarMessageService svc = new PulsarMessageService(); // client == null on purpose
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-bad");
        options.setSeekToMessage("some-msg-id");
        io.nop.api.core.exceptions.NopException e = org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> svc.subscribe("t-seek", (t, m, c) -> null, options));
        org.junit.jupiter.api.Assertions.assertEquals(
                io.nop.api.core.message.MessageErrors.ERR_SEEK_MESSAGE_NOT_SUPPORTED.getErrorCode(),
                e.getErrorCode());
    }

    @Test
    void acknowledgeReplyLandsOnValidFqnAckTopicAndMessageIsAcked() throws Exception {
        String requestTopic = "persistent://public/default/orders";
        String ackTopic = io.nop.message.pulsar.PulsarHelper.deriveAckTopic(requestTopic);

        org.apache.pulsar.client.api.Message<Object> request =
                mock(org.apache.pulsar.client.api.Message.class);
        when(request.getTopicName()).thenReturn(requestTopic);
        when(request.getValue()).thenReturn("request-payload");
        when(request.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));

        java.util.concurrent.CountDownLatch afterFirstDelivery = new java.util.concurrent.CountDownLatch(1);
        org.apache.pulsar.client.api.Consumer<Object> consumer =
                mock(org.apache.pulsar.client.api.Consumer.class);
        when(consumer.receive()).thenReturn(request).thenAnswer(inv -> {
            afterFirstDelivery.await();
            return null;
        });
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        org.apache.pulsar.client.api.TypedMessageBuilder<Object> typedBuilder =
                mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        when(typedBuilder.sendAsync())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        mock(org.apache.pulsar.client.api.MessageId.class)));

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-ack-e2e");
        io.nop.api.core.message.MessageSubscriptionConfig config =
                new io.nop.api.core.message.MessageSubscriptionConfig(requestTopic,
                        (t, m, c) -> new io.nop.api.core.message.Acknowledge("reply-payload"), options);

        TestPulsarService svc = new TestPulsarService();
        svc.builder = mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        svc.consumer = consumer;
        svc.typedBuilder = typedBuilder;
        when(svc.builder.subscribe()).thenReturn(consumer);
        when(svc.builder.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        when(svc.builder.subscriptionName(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        // Register the derived ack topic so getProducer routes to the overridden
        // buildProducer — a wrong derivation would miss the map and NPE loudly.
        svc.setTopicSchemas(java.util.Map.of(ackTopic,
                org.apache.pulsar.client.api.Schema.STRING));
        svc.subscription = svc.doSubscribe(config);
        try {
            org.mockito.Mockito.verify(typedBuilder, org.mockito.Mockito.timeout(5000))
                    .value("reply-payload");
            // The reply went to a producer created FOR the derived ack topic (real
            // derivation, not a mocked getAckTopic).
            org.mockito.Mockito.verify(typedBuilder, org.mockito.Mockito.timeout(5000)).sendAsync();
            org.junit.jupiter.api.Assertions.assertTrue(
                    svc.producedTopics.contains(ackTopic),
                    "reply producer must be created for the derived ack topic, got: "
                            + svc.producedTopics);
            org.mockito.Mockito.verify(consumer, org.mockito.Mockito.timeout(5000))
                    .acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any());
            org.mockito.Mockito.verify(consumer, org.mockito.Mockito.never())
                    .negativeAcknowledge(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any());
        } finally {
            afterFirstDelivery.countDown();
            svc.subscription.cancel();
        }
    }

    @Test
    void plainReplyLandsOnValidFqnAckTopicWithoutNegativeAck() throws Exception {
        String requestTopic = "persistent://public/default/orders";
        String ackTopic = io.nop.message.pulsar.PulsarHelper.deriveAckTopic(requestTopic);

        org.apache.pulsar.client.api.Message<Object> request =
                mock(org.apache.pulsar.client.api.Message.class);
        when(request.getTopicName()).thenReturn(requestTopic);
        when(request.getValue()).thenReturn("request-payload");
        when(request.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));

        java.util.concurrent.CountDownLatch afterFirstDelivery = new java.util.concurrent.CountDownLatch(1);
        org.apache.pulsar.client.api.Consumer<Object> consumer =
                mock(org.apache.pulsar.client.api.Consumer.class);
        when(consumer.receive()).thenReturn(request).thenAnswer(inv -> {
            afterFirstDelivery.await();
            return null;
        });
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        org.apache.pulsar.client.api.TypedMessageBuilder<Object> typedBuilder =
                mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        when(typedBuilder.sendAsync())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        mock(org.apache.pulsar.client.api.MessageId.class)));

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-plain-e2e");
        io.nop.api.core.message.MessageSubscriptionConfig config =
                new io.nop.api.core.message.MessageSubscriptionConfig(requestTopic,
                        (t, m, c) -> "plain-reply", options);

        TestPulsarService svc = new TestPulsarService();
        svc.builder = mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        svc.consumer = consumer;
        svc.typedBuilder = typedBuilder;
        when(svc.builder.subscribe()).thenReturn(consumer);
        when(svc.builder.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        when(svc.builder.subscriptionName(org.mockito.ArgumentMatchers.anyString())).thenReturn(svc.builder);
        svc.setTopicSchemas(java.util.Map.of(ackTopic,
                org.apache.pulsar.client.api.Schema.STRING));
        svc.subscription = svc.doSubscribe(config);
        try {
            org.mockito.Mockito.verify(typedBuilder, org.mockito.Mockito.timeout(5000))
                    .value("plain-reply");
            org.junit.jupiter.api.Assertions.assertTrue(svc.producedTopics.contains(ackTopic));
            org.mockito.Mockito.verify(consumer, org.mockito.Mockito.timeout(5000))
                    .acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any());
            org.mockito.Mockito.verify(consumer, org.mockito.Mockito.never())
                    .negativeAcknowledge(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any());
        } finally {
            afterFirstDelivery.countDown();
            svc.subscription.cancel();
        }
    }

    @Test
    void consumeThrowsAfterNegativeAckForHandlerFailure() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(message.getTopicName()).thenReturn("persistent://public/default/t-fail");

        org.apache.pulsar.client.api.Messages<Object> messages =
                mock(org.apache.pulsar.client.api.Messages.class);
        when(consumer.receive()).thenReturn(message);
        IMessageConsumer failingConsumer = mock(IMessageConsumer.class);
        doThrow(new IllegalStateException("handler-boom"))
                .when(failingConsumer).onMessage(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-fail", failingConsumer,
                new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        // MSG-F-05: the message-level failure must escape consume() (previously swallowed).
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, task::consume);
        verify(consumer).negativeAcknowledge(message.getMessageId());
        executor.shutdown();
    }

    @Test
    void runTaskInvokesOnExceptionAndBacksOffOnMessageFailure() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(message.getTopicName()).thenReturn("persistent://public/default/t-fail");

        java.util.concurrent.CountDownLatch afterFailure = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicLong secondReceiveAt = new java.util.concurrent.atomic.AtomicLong();
        when(consumer.receive()).thenReturn(message).thenAnswer(inv -> {
            secondReceiveAt.set(System.nanoTime());
            afterFailure.await();
            return null;
        });

        RuntimeException boom = new IllegalStateException("handler-boom");
        IMessageConsumer handler = mock(IMessageConsumer.class);
        doThrow(boom).when(handler).onMessage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-fail", handler,
                new MessageSubscribeOptions());
        long backoffMs = 50L;
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config, backoffMs);

        long firstReceiveAt = System.nanoTime();
        task.start();
        try {
            org.mockito.Mockito.verify(handler, org.mockito.Mockito.timeout(5000)).onException(boom);
            // The loop must survive the failure and reach receive() again — after the
            // configured backoff sleep (lower-bound assertion, CI-stable).
            org.junit.jupiter.api.Assertions.assertTrue(
                    secondReceiveAt.get() != 0L
                            || afterFailure.await(5, java.util.concurrent.TimeUnit.SECONDS) == false,
                    "second receive never attempted");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (secondReceiveAt.get() == 0L && System.nanoTime() < deadline) {
                Thread.sleep(20L);
            }
            long gapMs = (secondReceiveAt.get() - firstReceiveAt) / 1_000_000L;
            org.junit.jupiter.api.Assertions.assertTrue(gapMs >= backoffMs,
                    "backoff sleep must delay the next receive attempt, gap=" + gapMs + "ms");
        } finally {
            afterFailure.countDown();
            task.stop();
            executor.shutdownNow();
        }
    }

    @Test
    void cancelledTokenRejectsWithoutSending() throws Exception {
        TestPulsarService svc = new TestPulsarService();
        svc.builder = mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        svc.typedBuilder = mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        when(svc.typedBuilder.sendAsync())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        MessageSendOptions options = new MessageSendOptions();
        io.nop.api.core.util.ICancelToken token = mock(io.nop.api.core.util.ICancelToken.class);
        when(token.isCancelled()).thenReturn(true);
        options.setCancelToken(token);

        CompletionStage<Void> stage = svc.sendAsync("t-cancel", "m", options);
        ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> stage.toCompletableFuture().get(2, TimeUnit.SECONDS));
        org.junit.jupiter.api.Assertions.assertEquals(
                io.nop.api.core.message.MessageErrors.ERR_SEND_CANCELLED.getErrorCode(),
                io.nop.api.core.exceptions.NopException.class.cast(e.getCause()).getErrorCode());
        // The producer path was never reached (no message builder interaction).
        verify(svc.typedBuilder, never()).sendAsync();
    }

    @Test
    void sendTimeoutBoundsPulsarFuture() throws Exception {
        TestPulsarService svc = new TestPulsarService();
        svc.builder = mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        svc.typedBuilder = mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        // sendAsync never completes: the per-message bound must fire.
        when(svc.typedBuilder.sendAsync())
                .thenReturn(new java.util.concurrent.CompletableFuture<>());
        svc.producerForTest = mock(org.apache.pulsar.client.api.Producer.class);
        when(svc.producerForTest.newMessage()).thenReturn(
                (org.apache.pulsar.client.api.TypedMessageBuilder) svc.typedBuilder);

        MessageSendOptions options = new MessageSendOptions();
        options.setSendTimeout(50L);
        CompletionStage<Void> stage = svc.sendAsync("t-timeout", "m", options);

        ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
        org.junit.jupiter.api.Assertions.assertTrue(
                e.getCause() instanceof java.util.concurrent.TimeoutException,
                "cause must be the orTimeout TimeoutException, was: " + e.getCause());
    }

    @Test
    void capabilitiesDeclareSeek() {
        assertTrue(new PulsarMessageService().getCapabilities()
                .contains(io.nop.api.core.message.MessageCapability.SEEK));
    }
}
