package io.nop.message.pulsar;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        @Override
        protected org.apache.pulsar.client.api.ConsumerBuilder newConsumerBuilder(
                org.apache.pulsar.client.api.Schema<?> schema) {
            return builder;
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
    void capabilitiesDeclareSeek() {
        assertTrue(new PulsarMessageService().getCapabilities()
                .contains(io.nop.api.core.message.MessageCapability.SEEK));
    }
}
