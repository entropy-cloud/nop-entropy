package io.nop.message.pulsar;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        // WI19: batchConsume now awaits batchReceiveAsync with a bounded timeout (retention
        // pattern) — the stub returns an already-completed future.
        when(consumer.batchReceiveAsync()).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture(messages));
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
        protected void doStart() {
            // WI29: doSubscribe now calls checkIsActive — go through the real lifecycle
            // but skip actual client creation (mock suffices for the resolver tests).
            client = mock(PulsarClient.class);
        }

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
        // receive(timeout, unit) must block rather than spin: tests cancel the subscription
        // (which stops the task) right after their assertions. WI19 migrated the stub from
        // the no-arg receive() to the bounded form (anyInt — Pulsar 2.8 takes an int).
        when(svc.consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            new java.util.concurrent.CountDownLatch(1).await();
            return null;
        });
        svc.init();
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
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(request).thenAnswer(inv -> {
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
        svc.init();
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
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(request).thenAnswer(inv -> {
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
        svc.init();
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
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(message);
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
    void runTaskInvokesOnConsumeFailureWithFullContextAndBacksOff() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.MessageId messageId =
                mock(org.apache.pulsar.client.api.MessageId.class);
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getMessageId()).thenReturn(messageId);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-fail");
        when(message.getValue()).thenReturn("payload");

        java.util.concurrent.CountDownLatch afterFailure = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicLong secondReceiveAt = new java.util.concurrent.atomic.AtomicLong();
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(message).thenAnswer(inv -> {
            secondReceiveAt.set(System.nanoTime());
            afterFailure.await();
            return null;
        });
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        RuntimeException boom = new IllegalStateException("handler-boom");
        // WI16: override the NEW callback; capture the full context.
        java.util.List<io.nop.api.core.message.ConsumeFailure> failures =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object m, io.nop.api.core.message.IMessageConsumeContext ctx) {
                throw boom;
            }

            @Override
            public void onConsumeFailure(io.nop.api.core.message.ConsumeFailure failure) {
                failures.add(failure);
            }
        };

        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-fail", handler,
                new MessageSubscribeOptions());
        long backoffMs = 50L;
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config, backoffMs);

        long firstReceiveAt = System.nanoTime();
        task.start();
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (failures.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(20L);
            }
            org.junit.jupiter.api.Assertions.assertEquals(1, failures.size());
            io.nop.api.core.message.ConsumeFailure failure = failures.get(0);
            org.junit.jupiter.api.Assertions.assertSame(boom, failure.getThrowable());
            org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(messageId),
                    failure.getLocation());
            org.junit.jupiter.api.Assertions.assertEquals("payload", failure.getMessage());
            org.junit.jupiter.api.Assertions.assertEquals(1, failure.getAttempt());
            long deadline2 = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (secondReceiveAt.get() == 0L && System.nanoTime() < deadline2) {
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

    // ------------------------------------------------------------------
    // WI19: thread model (F-24) and bounded shutdown (F-54)
    // ------------------------------------------------------------------

    /** Daemon-thread executor so a test failure cannot leak a non-daemon thread into CI. */
    private static ExecutorService daemonExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pulsar-task-test");
            t.setDaemon(true);
            return t;
        });
    }

    @Test
    void stopExitsBoundedlyWithoutCloseWhenNoMessageArrives() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        // Behave like the real idle API: sleep the FULL requested timeout, then null.
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                    Thread.sleep(Math.max(1, inv.getArgument(0, Integer.class)));
                    return null;
                });
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-stop",
                (t, m, c) -> null, new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(100);

        task.start();
        Thread.sleep(50); // the task is now inside a receive round with no message in sight
        task.stop();
        executor.shutdown(); // no new submissions; the RUNNING task must still finish itself

        // Bounded exit: within one receive round + slack, WITHOUT close() unblocking it.
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
                "stop must terminate the loop within one receive timeout");
        verify(consumer, never()).close();
    }

    @Test
    void stopExitsBoundedlyInBatchModeAndRetainsSinglePendingRequest() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        // A batch request that never completes: the loop can only exit via the timeout
        // round noticing active == false.
        when(consumer.batchReceiveAsync())
                .thenReturn(new java.util.concurrent.CompletableFuture<>());
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10); // allowBatchConsume() == true
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-stop-batch",
                (t, m, c) -> null, options);
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(100);

        task.start();
        Thread.sleep(50);
        task.stop();
        executor.shutdown();

        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
                "stop must terminate the batch loop within one receive timeout");
        // Retention: exactly ONE batchReceiveAsync was issued — no re-issue storm per
        // timeout round (an abandoned pending request would strand unacked messages).
        verify(consumer, org.mockito.Mockito.times(1)).batchReceiveAsync();
        verify(consumer, never()).close();
    }

    @Test
    void batchReceiveFailureClearsPendingBatchAndPropagates() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        java.util.concurrent.CompletableFuture<org.apache.pulsar.client.api.Messages<Object>> failed =
                new java.util.concurrent.CompletableFuture<>();
        failed.completeExceptionally(
                new org.apache.pulsar.client.api.PulsarClientException.AlreadyClosedException("closed"));
        when(consumer.batchReceiveAsync()).thenReturn(failed);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-batch-fail",
                (t, m, c) -> null, options);
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(100);

        // The cause is unwrapped and rethrown so the run loop's backoff path takes over
        // (retaining a dead future would loop on immediate errors forever).
        org.junit.jupiter.api.Assertions.assertThrows(
                org.apache.pulsar.client.api.PulsarClientException.class, task::batchConsume);
        // The dead future was cleared: the next round issues a NEW request (which fails the
        // same way here — the mock always fails) instead of hitting the SAME dead future.
        org.junit.jupiter.api.Assertions.assertThrows(
                org.apache.pulsar.client.api.PulsarClientException.class, task::batchConsume);
        verify(consumer, org.mockito.Mockito.times(2)).batchReceiveAsync();
        executor.shutdownNow();
    }

    @Test
    void batchReceiveTimeoutRetainsSamePendingFutureAcrossRounds() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        java.util.concurrent.CompletableFuture<org.apache.pulsar.client.api.Messages<Object>> pending =
                new java.util.concurrent.CompletableFuture<>();
        when(consumer.batchReceiveAsync()).thenReturn(pending);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-batch-hold",
                (t, m, c) -> null, options);
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(50);

        // Two empty rounds (timeouts) must NOT re-issue the request.
        task.batchConsume();
        task.batchConsume();
        verify(consumer, org.mockito.Mockito.times(1)).batchReceiveAsync();

        // Completing the retained future is consumed by the next round (empty batch).
        org.apache.pulsar.client.api.Messages<Object> empty =
                mock(org.apache.pulsar.client.api.Messages.class);
        when(empty.size()).thenReturn(0);
        when(empty.iterator()).thenReturn(java.util.Collections.emptyIterator());
        pending.complete(empty);
        task.batchConsume();
        verify(consumer, org.mockito.Mockito.times(1)).batchReceiveAsync();
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------
    // WI22: retry cap (failure endpoint) and escalating backoff
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // WI25 (F-16): batch arity deterministic behavior
    // ------------------------------------------------------------------

    private org.apache.pulsar.client.api.Message<Object> batchMessage(String topic) {
        org.apache.pulsar.client.api.Message<Object> m =
                mock(org.apache.pulsar.client.api.Message.class);
        when(m.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(m.getTopicName()).thenReturn(topic);
        return m;
    }

    private void stubMessages(org.apache.pulsar.client.api.Consumer<Object> consumer,
                              org.apache.pulsar.client.api.Message<Object>... msgs) {
        org.apache.pulsar.client.api.Messages<Object> messages =
                mock(org.apache.pulsar.client.api.Messages.class);
        when(messages.size()).thenReturn(msgs.length);
        // Fresh iterator per call: batchConsume iterates the messages twice (list build +
        // dispatch) and real Pulsar Messages yield independent iterators.
        when(messages.iterator())
                .thenAnswer(inv -> java.util.Arrays.asList(msgs).iterator());
        when(consumer.batchReceiveAsync()).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture(messages));
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    @Test
    void batchExtraResponsesAreWarnedAndIgnoredWithoutNack() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        when(service.getAckTopic(org.mockito.ArgumentMatchers.anyString())).thenReturn("ack-t");
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> m0 = batchMessage("t-arity");
        stubMessages(consumer, m0);
        when(service.sendAsync(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        // 1 message, 2 responses: the extra response must be ignored (no
        // NoSuchElementException), the paired record acked.
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public java.util.List<Object> onMessageBatch(java.util.List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                java.util.List<Object> responses = new java.util.ArrayList<>();
                responses.add("reply");
                responses.add("extra");
                return responses;
            }
        };
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-arity", handler,
                new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(50);

        task.batchConsume(); // must NOT throw NoSuchElementException

        verify(consumer).acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any());
        verify(consumer, never()).negativeAcknowledge(
                org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.MessageId>any());
        verify(service).sendAsync(org.mockito.ArgumentMatchers.eq("ack-t"),
                org.mockito.ArgumentMatchers.eq("reply"), org.mockito.ArgumentMatchers.any());
        executor.shutdownNow();
    }

    @Test
    void batchUnhandledTailIsNackedForRedelivery() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        when(service.getAckTopic(org.mockito.ArgumentMatchers.anyString())).thenReturn("ack-t");
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> m0 = batchMessage("t-arity-tail");
        org.apache.pulsar.client.api.Message<Object> m1 = batchMessage("t-arity-tail");
        stubMessages(consumer, m0, m1);

        // 2 messages, 1 response: m0 acked, m1 (unhandled tail) nacked for redelivery.
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public java.util.List<Object> onMessageBatch(java.util.List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                java.util.List<Object> responses = new java.util.ArrayList<>();
                responses.add(null);
                return responses;
            }
        };
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-arity-tail", handler,
                new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);
        task.setReceiveTimeoutMs(50);

        task.batchConsume();

        verify(consumer).acknowledgeAsync(org.mockito.ArgumentMatchers.eq(m0));
        verify(consumer).negativeAcknowledge(m1.getMessageId());
        executor.shutdownNow();
    }

    @Test
    void consumeAttemptCapAcksPermanentlyInsteadOfNacking() throws Exception {
        PulsarMessageService service = mock(PulsarMessageService.class);
        when(service.maxConsumeAttempts()).thenReturn(1);
        ExecutorService executor = daemonExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.MessageId messageId =
                mock(org.apache.pulsar.client.api.MessageId.class);
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getMessageId()).thenReturn(messageId);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-cap");
        when(message.getValue()).thenReturn("payload");
        when(message.getRedeliveryCount()).thenReturn(0);
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(message);
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        IMessageConsumer failing = mock(IMessageConsumer.class);
        doThrow(new IllegalStateException("handler-boom")).when(failing).onMessage(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-cap", failing,
                new MessageSubscribeOptions());
        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        // Round 1: local attempt 1 (not > cap 1) → normal nack + rethrow.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, task::consume);
        verify(consumer).negativeAcknowledge(messageId);

        // Round 2: attempt 2 > cap 1 → failure endpoint: permanent ack, no rethrow.
        task.consume();
        verify(consumer).acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any());
        verify(consumer, times(1)).negativeAcknowledge(messageId);

        // Broker-side redelivery count participates (a fresh message with redeliveryCount 5
        // hits the endpoint on its FIRST local failure), AND the endpoint ack failure is
        // WARN-only — the endpoint never throws (broker unacked redelivery takes over).
        when(consumer.acknowledgeAsync(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Message<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture
                        .failedFuture(new org.apache.pulsar.client.api.PulsarClientException
                                .AlreadyClosedException("closed")));
        org.apache.pulsar.client.api.Message<Object> redelivered =
                mock(org.apache.pulsar.client.api.Message.class);
        when(redelivered.getMessageId())
                .thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(redelivered.getTopicName()).thenReturn("persistent://public/default/t-cap");
        when(redelivered.getValue()).thenReturn("payload");
        when(redelivered.getRedeliveryCount()).thenReturn(5);
        when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(redelivered);
        // max(local 1, broker 5) = 5 > cap 1 → endpoint on the first local failure; the
        // failing ack must not escape consume().
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(task::consume);
        verify(consumer, times(2)).acknowledgeAsync(
                org.mockito.ArgumentMatchers.<org.apache.pulsar.client.api.Message<Object>>any());
        verify(consumer, times(1)).negativeAcknowledge(messageId);
        executor.shutdownNow();
    }

    @Test
    void computeBackoffEscalatesAndCaps() {
        assertEquals(1000L, PulsarConsumeTask.computeBackoffMs(1000, 30000, 0));
        assertEquals(2000L, PulsarConsumeTask.computeBackoffMs(1000, 30000, 1));
        assertEquals(30000L, PulsarConsumeTask.computeBackoffMs(1000, 30000, 50));
    }

    @Test
    void doSubscribeRunsOnNamedDaemonConsumeThread() throws Exception {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-thread-model");
        TestPulsarService svc = preparedService(options);
        try {
            String prefix = "nop-msg-pulsar-consume-sub-thread-model-";
            long deadline = System.currentTimeMillis() + 5000;
            Thread consumeThread = null;
            while (consumeThread == null && System.currentTimeMillis() < deadline) {
                consumeThread = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith(prefix))
                        .findFirst().orElse(null);
                if (consumeThread == null)
                    Thread.sleep(20);
            }
            org.junit.jupiter.api.Assertions.assertNotNull(consumeThread,
                    "doSubscribe must start a named consume thread with prefix " + prefix);
            assertTrue(consumeThread.isDaemon(), "consume thread must be daemon (F-24)");
        } finally {
            svc.subscription.cancel();
        }
    }
}
